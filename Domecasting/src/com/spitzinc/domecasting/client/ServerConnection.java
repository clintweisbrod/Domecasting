/*
 * This thread establishes and maintains a socket connection with the domecasting server.
 * If the connection is lost during communication with the server via another thread, that
 * thread should call this thread's notify() method to wake it up and attempt to re-establish
 * the connection. Once the connection is re-established, this thread goes back to sleep and
 * waits for more shit to happen.
 */
package com.spitzinc.domecasting.client;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.spitzinc.domecasting.BasicProcessorThread;
import com.spitzinc.domecasting.ClientHeader;
import com.spitzinc.domecasting.CommUtils;
import com.spitzinc.domecasting.Log;

public class ServerConnection
{
	private static final int kMaxServerInputQueueSize = 20;
	
	private ClientApplication theApp;
	public Object outputStreamLock;	// This must be used from SNTCPPassThruThread!!!
	private ClientHeader inHdr;
	private ClientHeader outHdr;
	private ConnectionEstablishThread connectThread;
	private ServerInputHandlerThread serverInputHandlerThread;
	private InputStream in;
	private OutputStream out;
	private Socket socket;
	private byte[] infoBuffer;
	private ConcurrentHashMap<String, LinkedBlockingQueue<ByteBuffer>> inputQueues;

	/*
	 * This thread manages connection to domecast server. Once server connection is established,
	 * InputStream and OutputStream instances are created. The OutputStream instance (out) can
	 * be written by any thread so long as it gains a lock on outputStreamLock first.
	 * The InputStream instance (in) is read solely by an instance of ServerInputHandlerThread.
	 * This is necessary because ALL communication from potentially ATM4, PF, and the remote
	 * domecasting client instance come across this InputStream and in no predictable order.
	 */
	private class ConnectionEstablishThread extends BasicProcessorThread
	{
		private static final int kServerConnectionRetryIntervalSeconds = 10;
		
		private String hostName;
		private int port;
		public AtomicBoolean disconnected;

		private ConnectionEstablishThread(String hostName, int port)
		{
			this.hostName = hostName;
			this.port = port;

			this.stopped = new AtomicBoolean(false);
			this.disconnected = new AtomicBoolean(false);
			this.setName(getClass().getSimpleName() + "_" + hostName + "_" + port);
		}
		
		private void writeSecurityCode() throws IOException
		{
			// Allocate byte buffer for security code
			byte[] buffer = new byte[CommUtils.kSecurityCodeLength];
			
			// Insert daily security code in buffer
			String securityCode = CommUtils.getDailySecurityCode();
			System.arraycopy(securityCode.getBytes(), 0, buffer, 0, securityCode.length());
		
			// Writing to the connectionThread output stream must be thread synchronized
			synchronized (outputStreamLock) {
				CommUtils.writeOutputStream(out, buffer, 0, CommUtils.kSecurityCodeLength);
			}
		}
		
		private void handleConnectionEstablished()
		{
			disconnected.set(false);
			Log.inst().info("Server connection established.");
			
			// If we get here, we've established a connection with the server.
			try
			{
				// Obtain streams to read/write
				in = socket.getInputStream();
				out = socket.getOutputStream();
				
				// Launch another thread to read the InputStream and process the data.
				serverInputHandlerThread = new ServerInputHandlerThread();
				serverInputHandlerThread.start();
				
				// Send initial handshake to server
				writeSecurityCode();
				sendClientType(theApp.clientType);
				
				theApp.statusText.set("Connection established with Spitz Domecasting server.");
				theApp.updateUI();
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		private void handleConnectionLost()
		{
			// If we get here, the server connection has been lost.
			theApp.handleServerDisconnect();
			
			// Shutdown the ServerInputHandlerThread instance
			if ((serverInputHandlerThread != null) && serverInputHandlerThread.isAlive())
			{
				serverInputHandlerThread.interrupt();
				try {
					Log.inst().debug("Waiting for " + serverInputHandlerThread.getName() + " to end...");
					serverInputHandlerThread.join();
					Log.inst().debug(serverInputHandlerThread.getName() + " finished.");
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

			// Close the socket. This will also close the input and output streams.
			if (socket != null)
			{
				try {
					socket.close();
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				
				socket = null;
				in = null;
				out = null;
			}
		}
		
		public void run()
		{
			// Attempt to maintain a socket connection to the server
			while (!stopped.get())
			{
				if (socket == null)
				{
					theApp.statusText.set("Attempting to connect with Spitz Domecasting server...");
					theApp.updateUI();

					Log.inst().info("Attempting server connection...");
					socket = CommUtils.connectToHost(hostName, port);
					if (socket != null)
						handleConnectionEstablished();
					else
					{
						// Wait a few seconds and then try again
						Log.inst().info("Server connection failed. Trying again in " + kServerConnectionRetryIntervalSeconds + " seconds.");
						try {
							Thread.sleep(kServerConnectionRetryIntervalSeconds * 1000);
						} catch (InterruptedException e) {}
					}
				}
				
				if (!stopped.get() && (in != null) && (out != null))
				{
					// Everything looks good so wait() here until another thread calls notify() on this thread.
					// Another thread will call notify() if that thread determines that this.socket is not connected.
					synchronized(this) {
						try {
							wait();
						} catch (InterruptedException e) {
						}
					}
					
					// Check to see if socket is still connected
					if (disconnected.get() || !socket.isConnected())
						handleConnectionLost();
				}
			}
			
			Log.inst().info("Exiting thread.");
		}
	}
	
	/*
	 * This thread is solely responsible for reading incoming communication packets from the domecasting
	 * server and dealing with each packet appropriately.
	 */
	private class ServerInputHandlerThread extends BasicProcessorThread
	{
		public void run()
		{
			try
			{
				while (!stopped.get())
				{
					// Read and parse the header
					CommUtils.readInputStream(in, inHdr.bytes, 0, ClientHeader.kHdrByteCount);
 					if (!inHdr.parseHeaderBuffer())
						break;
 					
 					// Now look at hdr contents to decide what to do.
 					if (inHdr.messageType.equals(ClientHeader.kINFO))
 						handleINFO(inHdr);
 					else if (inHdr.messageType.equals(ClientHeader.kCOMM))
 						handleCOMM(inHdr);
 					else if (inHdr.messageType.equals(ClientHeader.kFILE))
 						handleFILE(inHdr);
				}
			}
			catch (IOException e)
			{
				// If we get here, the server connection has been lost. Notify the ConnectionEstablishThread.
				try {
					connectThread.disconnected.set(true);
					socket.close();
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				synchronized(connectThread) {
					connectThread.notify();
				}
			}

			Log.inst().info("Exiting thread.");
		}
		
		private void handleINFO(ClientHeader hdr) throws IOException
		{
			CommUtils.readInputStream(in, infoBuffer, 0, (int)hdr.messageLen);
			String serverReply = new String(infoBuffer, 0, (int)hdr.messageLen);
			Log.inst().debug("Received: " + serverReply);
			String[] list = serverReply.split("=");
			if (list[0].equals(CommUtils.kIsDomecastIDUnique))				// Only received by presenter.
			{
				theApp.isDomecastIDUnique.set(Boolean.parseBoolean(list[1]));
				
				// Notify PresenterPanel.DomecastIDSendThread
				synchronized(theApp.isDomecastIDUnique) {
					theApp.isDomecastIDUnique.notify();
				}
			}
			else
			{
				if (list[0].equals(CommUtils.kStatusText))					// Received by both presenter and host
					theApp.statusText.set(list[1]);
				else if (list[0].equals(CommUtils.kIsConnected))			// Received by both presenter and host
					theApp.isConnected.set(Boolean.parseBoolean(list[1]));
				else if (list[0].equals(CommUtils.kGetAvailableDomecasts))	// Only received by host
					theApp.availableDomecasts = list[1];
				else if (list[0].equals(CommUtils.kIsHostListening))		// Only received by presenter. Host sets this locally.
				{
					// Only set the value if it's different
					boolean oldValue = theApp.isHostListening.get();
					boolean newValue = Boolean.parseBoolean(list[1]);
					if (newValue != oldValue)
					{
						theApp.isHostListening.set(Boolean.parseBoolean(list[1]));
						theApp.snPassThru.notifyThreadsOfCommModeChange();
					}
				}
				else if (list[0].equals(CommUtils.kAssetFileAvailable))		// Only received by host
					theApp.assetFileAvailable.set(Boolean.parseBoolean(list[1]));
				
				// Notify the UI of changes
				theApp.updateUI();
			}
		}

		private void handleCOMM(ClientHeader hdr) throws IOException
		{
			// Inspect the header to determine where the message should go.
			// We are dealing with incoming communication from the peer connection. There are
			// two possibilities: If we're a host, the incoming comm is from the presenter's
			// PF or ATM4. If we're a presenter, the incoming comm is from the host's RB. In
			// both cases, hdr.messageSource will indicate this.
			// So, we have no choice but to create ByteBuffer instances and throw them into
			// the appropriate LinkedBlockingQueue configured with some reasonable capacity.
			
			// Get the correct queue
			LinkedBlockingQueue<ByteBuffer> theQueue = inputQueues.get(hdr.messageSource);
			if (theQueue != null)
			{
				// Allocate a byte array large enough to hold the entire message
				byte[] buffer = new byte[(int)inHdr.messageLen];
				
				// Read the entire message
				CommUtils.readInputStream(in, buffer, 0, (int)inHdr.messageLen);
				
				// Wrap the byte array in a new ByteBuffer object (I hate that we have to do this)
				ByteBuffer theBuffer = ByteBuffer.wrap(buffer);
				
				// Push the new ByteBuffer into the queue
				if (!theQueue.offer(theBuffer))
				{
					// If we get here, the queue is already filled with kMaxServerInputQueueSize items
					Log.inst().error(hdr.messageSource + " input queue is full! Ignoring further received communication.");
				}
			}
		}
		
		private void handleFILE(ClientHeader hdr) throws IOException
		{
			// Create a DataOutputStream to write the received data to
			File outputFile = new File(theApp.lastAssetsSaveFolder + File.separator + CommUtils.kAssetsFilename);
			
			// Allocate buffer to use for read/write operations
			byte[] buffer = new byte[CommUtils.kFileBufferSize];
			
			Log.inst().info("Receiving file (" + hdr.messageLen + " bytes).");

			// Read the InputStream to specified file
			CommUtils.readInputStreamToFile(in, outputFile, hdr.messageLen, buffer, theApp.fileProgress, theApp.appFrame.tabbedPane);
			
			// Hide the progress bar
			theApp.fileProgress.set(0);
			theApp.updateUI();
			
			// When we get here, we have a new file saved in lastAssetsSaveFolder called "assets.zip".
			// We have to rename it to the .cue file inside the ZIP file.
			String internalName = getAssetsFileInternalName(outputFile);
			if (internalName != null)
			{
				// Rename outputFile to <internalName>.zip
				File newFile = new File(theApp.lastAssetsSaveFolder + File.separator + internalName + ".zip");
				Path movefrom = FileSystems.getDefault().getPath(outputFile.getAbsolutePath());
				Path target = FileSystems.getDefault().getPath(newFile.getAbsolutePath());
				Files.move(movefrom, target, StandardCopyOption.REPLACE_EXISTING);
				
				Log.inst().info("Received file: " + newFile.getName());
			}
		}
		
		private String getAssetsFileInternalName(File assetsFile)
		{
			String result = null;
			
			ZipFile zipFile = null;
			try
			{
				// Attempt to find .cue file in the ZIP file
				zipFile = new ZipFile(assetsFile);
				Enumeration<? extends ZipEntry> entries = zipFile.entries();
				while(entries.hasMoreElements())
				{
			        ZipEntry entry = entries.nextElement();
			        String entryName = entry.getName().toLowerCase();
			        if (entryName.endsWith(".cue"))
			        {
			        	String[] list = entryName.split("\\.");
			        	result = list[0];
			        	break;
			        }
			    }
			}
			catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			finally
			{
				if (zipFile != null)
					try {
						zipFile.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
			}
			
			return result;
		}
	}
	
	public ServerConnection(ClientApplication theApp, String hostName, int port)
	{
		this.theApp = theApp;
		this.in = null;
		this.out = null;
		this.outputStreamLock = new Object();
		this.inHdr = new ClientHeader();
		this.outHdr = new ClientHeader();
		this.infoBuffer = new byte[1024];	// Allocate byte buffer to handle reading InputStream for INFO messages
		
		// Allocate a map to hold queues to store incoming data from server. We need a queue for each possible
		// type of client connection supported by domecasting. For now, that is SNPF and ATM4. When we extend our
		// domecasting solution to support TLE and and Zygote, we will need a map capacity of 4. I don't see any
		// harm in allocating that extra space now.
		this.inputQueues = new ConcurrentHashMap<String, LinkedBlockingQueue<ByteBuffer>>(4);
		this.inputQueues.put(ClientHeader.kSNPF, new LinkedBlockingQueue<ByteBuffer>(kMaxServerInputQueueSize));
		this.inputQueues.put(ClientHeader.kATM4, new LinkedBlockingQueue<ByteBuffer>(kMaxServerInputQueueSize));
		
		// Launch thread to establish/re-establish connection to server
		this.connectThread = new ConnectionEstablishThread(hostName, port);
		this.connectThread.start();
		
		this.serverInputHandlerThread = null;
	}
	
	public void shutdown()
	{
		// Force the server connection thread to finish
		if ((connectThread != null) && connectThread.isAlive())
		{
			connectThread.interrupt();
			try {
				connectThread.join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		// Clean up the input queues
		for (LinkedBlockingQueue<ByteBuffer> queue : inputQueues.values())
			queue.clear();
		inputQueues.clear();
		
	}
	
	public OutputStream getOutputStream() {
		return out;
	}
	
	public ByteBuffer getInputStreamData(String src)
	{
		ByteBuffer result = null;
		
		LinkedBlockingQueue<ByteBuffer> theQueue = inputQueues.get(src);
		if (theQueue != null)
		{
			try {
				result = theQueue.take();	// This call will block until there is data in the queue.
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		return result;
	}
	
	public void sendIsHostListening(boolean isHostListening) {
		sendToServer(CommUtils.kIsHostListening + "=" + Boolean.toString(isHostListening), ClientHeader.kINFO);
	}
	
	public boolean isConnected() {
		return (socket != null);
	}
	
	public void isDomecastIDUnique(String domecastID) {
		sendToServer(CommUtils.kIsDomecastIDUnique + "=" + domecastID, ClientHeader.kINFO);
	}
	
	public void sendDomecastID(String domecastID) {
		sendToServer(CommUtils.kDomecastID + "=" + domecastID, ClientHeader.kINFO);
	}
	
	public void sendClientType(byte clientType)	{
		sendToServer(CommUtils.kClientType + "=" + (char)clientType, ClientHeader.kINFO);
	}
	
	public void sendGetAssetsFile() {
		sendToServer(CommUtils.kGetAssetsFile + "=true", ClientHeader.kINFO);
	}
	
	public void sendAssetsFile(File assetFile)
	{	
		try
		{
			// Allocate buffer for transfer
			byte[] buffer = new byte[CommUtils.kFileBufferSize];

			Log.inst().info("Uploading " + assetFile.getName() + " to domecasting server...");
			synchronized (outputStreamLock)
			{
				CommUtils.writeHeader(out, outHdr, assetFile.length(), ClientHeader.kDCC, ClientHeader.kFILE);
				CommUtils.writeOutputStreamFromFile(out, assetFile, buffer, theApp.fileProgress, theApp.appFrame.tabbedPane);
			}
			Log.inst().info("Upload complete.");
			
			// Hide the progress bar
			theApp.fileProgress.set(0);
			theApp.updateUI();
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void sendToServer(String serverCmd, String msgType)
	{
		if ((socket != null) && socket.isConnected())
		{
			byte[] theBytes = serverCmd.getBytes();
			
			// We're performing two writes to the OutputStream. They MUST be sequential.
			synchronized (outputStreamLock)
			{
				try {
					CommUtils.writeHeader(out, outHdr, theBytes.length, ClientHeader.kDCC, msgType);
					CommUtils.writeOutputStream(out, theBytes, 0, theBytes.length);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
}
