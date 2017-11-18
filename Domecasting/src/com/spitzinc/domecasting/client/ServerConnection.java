/*
 * This thread establishes and maintains a socket connection with the domecasting server.
 * If the connection is lost during communication with the server via another thread, that
 * thread should call this thread's notify() method to wake it up and attempt to re-establish
 * the connection. Once the connection is re-established, this thread goes back to sleep and
 * waits for more shit to happen.
 */
package com.spitzinc.domecasting.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

import com.spitzinc.domecasting.BasicProcessorThread;
import com.spitzinc.domecasting.ClientHeader;
import com.spitzinc.domecasting.CommUtils;
import com.spitzinc.domecasting.Log;

public class ServerConnection
{
	private ClientApplication theApp;
	private Object outputStreamLock;	// This must be used from SNTCPPassThruThread!!!
	private ClientHeader inHdr;
	private ClientHeader outHdr;
	private ConnectionEstablishThread connectThread;
	private ServerInputHandlerThread serverInputHandlerThread;
	private InputStream in;
	private OutputStream out;
	private Socket socket;
	private byte[] buffer;

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

		private ConnectionEstablishThread(String hostName, int port)
		{
			this.hostName = hostName;
			this.port = port;

			this.stopped = new AtomicBoolean(false);
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
				byte clientType;
				if (theApp.appFrame.tabbedPane.getSelectedIndex() == 0)
					clientType = CommUtils.kHostID;
				else
					clientType = CommUtils.kPresenterID;
				sendClientType(clientType);
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		private void handleConnectionLost()
		{
			Log.inst().info("Server connection lost.");
			
			// If we get here, the server connection has been lost.
			
			// Shutdown the ServerInputHandlerThread instance
			if ((serverInputHandlerThread != null) && serverInputHandlerThread.isAlive())
			{
				serverInputHandlerThread.interrupt();
				try {
					Log.inst().info("Waiting for " + serverInputHandlerThread.getName() + " to end...");
					serverInputHandlerThread.join();
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
					if (!socket.isConnected())
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
				}
			}
			catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			Log.inst().info("Exiting thread.");
		}
		
		private void handleINFO(ClientHeader hdr) throws IOException
		{
			CommUtils.readInputStream(in, buffer, 0, hdr.messageLen);
			String serverReply = new String(buffer, 0, hdr.messageLen);
			String[] list = serverReply.split("=");
			if (list[0].equals(CommUtils.kIsDomecastIDUnique))
			{
				theApp.isDomecastIDUnique.set(Boolean.parseBoolean(list[1]));
				
				// Notify PresenterPanel.DomecastIDSendThread
				synchronized(theApp.isDomecastIDUnique) {
					theApp.isDomecastIDUnique.notify();
				}
			}
			else
			{
				if (list[0].equals(CommUtils.kIsConnected))
					theApp.isConnected.set(Boolean.parseBoolean(list[1]));
				else if (list[0].equals(CommUtils.kIsPeerPresent))
					theApp.isPeerPresent.set(Boolean.parseBoolean(list[1]));
				else if (list[0].equals(CommUtils.kIsPeerReady))
					theApp.isPeerReady.set(Boolean.parseBoolean(list[1]));
				else if (list[0].equals(CommUtils.kGetAvailableDomecasts))
					theApp.availableDomecasts = list[1];
				
				// Notify the UI of changes
				synchronized(theApp.appFrame.tabbedPane) {
					theApp.appFrame.tabbedPane.notify();
				}
			}
		}

		private void handleCOMM(ClientHeader hdr) throws IOException
		{
			// Inspect the header to determine where the message should go
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
		this.buffer = new byte[CommUtils.kCommBufferSize];	// Allocate byte buffer to handle reading InputStream
		
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
	}
	
	public InputStream getInputStream() {
		return in;
	}
	
	public OutputStream getOutputStream() {
		return out;
	}
	
	public void sendHostReadyToCast(boolean readyToCast) {
		sendToServer(CommUtils.kHostReadyForDomecast + "=" + Boolean.toString(readyToCast), ClientHeader.kINFO);
	}
	
	public boolean isConnected() {
		return (socket != null);
	}
	
	public void isDomecastIDUnique(String domecastID) {
		sendToServer(CommUtils.kIsDomecastIDUnique + "=" + domecastID, ClientHeader.kREQU);
	}
	
	public void sendDomecastID(String domecastID) {
		sendToServer(CommUtils.kDomecastID + "=" + domecastID, ClientHeader.kREQU);
	}
	
	public void sendClientType(byte clientType)	{
		sendToServer(CommUtils.kClientType + "=" + (char)clientType, ClientHeader.kINFO);
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
					CommUtils.writeHeader(out, outHdr, theBytes.length, ClientHeader.kDCC, ClientHeader.kDCS, msgType);
					CommUtils.writeOutputStream(out, theBytes, 0, theBytes.length);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
}
