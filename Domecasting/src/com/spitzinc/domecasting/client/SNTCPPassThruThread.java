package com.spitzinc.domecasting.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import com.spitzinc.domecasting.BasicProcessorThread;
import com.spitzinc.domecasting.ClientHeader;
import com.spitzinc.domecasting.CommUtils;
import com.spitzinc.domecasting.Log;
import com.spitzinc.domecasting.TCPConnectionHandlerThread;

/*
 * This thread handles the communication stream that either originates from a StarrtNight
 * client or from StarrNight Renderbox. An instance of ClientSideConnectionListenerThread
 * waits for a connection request. The resulting Socket from that connection request is
 * given to this thread and an InputStream is created from it. This thread then attempts
 * to establish a socket connection to the given TCPNode and creates an OutputStream from
 * that Socket.
 * 
 * For any client of SN, there will be two of these threads since this is how SN
 * communication works.
 * 
 * This thread behaves in two distinct modes: In "StarryNight pass-thru" mode, this thread will
 * read data from it's InputStream and write it to it's OutputStream. The data is read as SN
 * packets where the first 120 bytes consists of a header containing information on how many more
 * bytes should be read. Within this header, a "reply-to" port is encoded that RB uses to connect
 * back to the client with. Unfortunately, with this code running on a Preflight system, in order
 * to intercept the connection request from RB, this reply-to port has to be modified for every SN
 * packet that is sent from a client.
 * 
 * The second mode of operation; "domecast routing" mode is different depending on which of these
 * two threads we're talking about and also whether the domecast client is running as a "host or a
 * "presenter":
 * 
 * For presenters, the thread which handles data being sent to RB, continues to write it's
 * OutputStream but also writes to the OutputStream that belongs to the domecast server connection
 * with this client. The other thread that handles responses from the local RB instead waits for
 * new data on the domecast server's InputStream and writes this data to it's OutputStream.
 * 
 * For hosts, the thread which handles data being sent to RB, waits for data on the domecast
 * server's InputStream and writes this data to it's OutputStream. The other thread that handles
 * responses from RB, continues to write it's OutputStream but also writes to the OutputStream
 * that belongs to domecast server connection with the client.
 * 
 *  Yes, it's complicated. Try coding it all.
 *  
 *  While in domecast routing mode, an additional thread is needed to continue reading data off
 *  the InputStream that effectively gets ignored. If we don't do this, the ignored InputStream
 *  eventually gets disconnected, making it impossible to switch back to SN pass-thru mode without
 *  detecting and re-establishing lost connections. ReadIgnoredInputStreamThread is responsible for
 *  reading this ignored data. Care must be taken when switching back to SN pass-thru mode. This
 *  additional thread must be stopped before the pass-thru can be resumed.
 *  
 *  Finally, when switching to domecast routing mode, presenter instances of this client must ask
 *  the thread that handles responses from the local RB to send a request back to PF that causes
 *  PF to send a "SetLiveCommunicationWithRB". This is imperative so that all remote RB instances
 *  listening to the domecast are correctly initialized with the local PF's preferences, options,
 *  viewing locations, etc. This request to PF must be the last thing sent to PF before switching
 *  to domecast routing mode. The method; sendSetLiveCommand() is responsible for actually sending
 *  the SN packet to PF to make this request.
 */
public class SNTCPPassThruThread extends TCPConnectionHandlerThread
{
	private final static int kSNHeaderFieldLength = 10;
	private final static int kSNHeaderLength = 120;
	private final static int kSNHeaderPacketLengthPosition = 0;
	private final static int kSNHeaderDataLengthPosition = 20;
	private final static int kSNHeaderReplyPortPosition = 50;
	private final static int kSNHeaderClientAppNamePosition = 60;
	private final static int kSNHeaderDomecastHostIDPosition = 70;
	
	private final static String kSetLiveCommandHdr = "          L                   0         0                                                                               ";
	private final static String kSetLiveCommandData = "<HTML>\r\n" +
													  "<BODY>\r\n" +
													  "<SN_VALUE name=\"Version\" value=\"Renderbox (Win) - s740c-EW\">\r\n" +
													  "<SN_VALUE name=\"VersionSKU\" value=\"d740c-EW\">\r\n" +
													  "<SN_VALUE name=\"charset\" value=\"iso-8859-1\">\r\n" +
													  "<SN_VALUE name=\"CallSetLive\" value=\"True\">\r\n" +
													  "<SN_VALUE name=\"DomecastHostID\" value=\"@DomecastHostID@\">\r\n" +
													  "<SN_VALUE name=\"ValueListVersion\" value=\"2\">\r\n";
													  
	
	/*
	 * This thread is necessary to periodically read data off the connected InputStream
	 * that is being ignored during comm routing through the domecast server. If we don't
	 * do this, the connection will get lost.
	 */
	private class ReadIgnoredInputStreamThread extends BasicProcessorThread
	{
		private static final int kReadIntervalMilliseconds = 500; 
		public void run()
		{
			byte[] buffer = new byte[16 * 1024];
			while (!getStopped())
			{
				try {
					// Read the next SN header
					long messageLength = readSNHeader(buffer, domecastHostID);
					
					// Read the data
					readSNDataToNowhere(buffer, messageLength);
					
					Log.inst().info("Read " + messageLength + " bytes.");
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				// Sleep for a bit
				try {
					sleep(kReadIntervalMilliseconds);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				// Exit if no hosts are listening
				if (!theApp.isHostListening.get())
					break;
			}
			
			Log.inst().info("Exiting thread.");
		}
	}
	
	private Socket outboundSocket = null;
	private TCPNode outboundNode;
	private byte[] replyPortBytes = null;
	private String clientAppName = null;
	private ClientApplication theApp;
	private boolean modifyReplyPort;
	private InputStream in;
	private OutputStream out;
	private ClientHeader outHdr;
	private ReadIgnoredInputStreamThread readIgnoredStreamThread;
	private AtomicBoolean commModeChanged;
	private StringBuffer domecastHostID;
	
	public SNTCPPassThruThread(ClientSideConnectionListenerThread owner, Socket inboundSocket, TCPNode outboundNode)
	{
		super(owner, inboundSocket);

		this.outboundNode = outboundNode;
		this.modifyReplyPort = (outboundNode.replyPort != -1);
		this.outHdr = new ClientHeader();

		// Build a byte buffer to replace the contents of the replyPort field in a SN TCP message header
		if (modifyReplyPort)
			replyPortBytes = CommUtils.getRightPaddedByteArray(Integer.toString(outboundNode.replyPort), kSNHeaderFieldLength);
		
		this.theApp = (ClientApplication)ClientApplication.inst();
		this.readIgnoredStreamThread = null;
		this.commModeChanged = new AtomicBoolean(false);
		this.domecastHostID = new StringBuffer();

		this.setName(getClass().getSimpleName() + "_" + inboundSocket.getLocalPort() + "->" + outboundNode.port);	
	}
	
	public void setCommModeChanged()
	{
		Log.inst().debug("Setting " + this.getName() + ".commModeChanged to true.");
		commModeChanged.set(true);
	}
	
	public void run()
	{
		// Attempt to connect to outbound host
		Log.inst().info("Attempting to establish outbound connection.");
		outboundSocket = CommUtils.connectToHost(outboundNode.hostname, outboundNode.port);
		if (outboundSocket != null)
		{
			Log.inst().info("Outbound connection established.");

			try
			{
				in = socket.getInputStream();
				out = outboundSocket.getOutputStream();
			}
			catch (IOException e) {
				e.printStackTrace();
			}

			if ((in != null) && (out != null))
			{
				// Allocate byte buffer to handle comm
				byte[] buffer = new byte[CommUtils.kCommBufferSize];

				// Begin reading data from inbound stream and writing it to outbound stream in chunks of SN comm.
				try
				{
					while (!stopped.get())
					{
						// Depending on whether any host is listening, we perform communication differently
						boolean isAnyHostListening = theApp.isHostListening.get();
						
						// If theApp.isAnyHostListening was changed, we have to either launch or kill a thread
						// that reads data off the InputStream that is ignored when we're routing comm through
						// the domecast server.
						if (commModeChanged.get())
						{
							Log.inst().debug("Calling switchCommModes().");
							switchCommModes(isAnyHostListening);
							
							commModeChanged.set(false);
						}
						
						// Perform routing of a single SN header and data.
						if (isAnyHostListening)
						{
							// See comments for sendSetLiveCommand()
							if (!modifyReplyPort && (theApp.clientType == CommUtils.kPresenterID) &&
								((clientAppName != null) && (clientAppName.equals(ClientHeader.kSNPF))))
							{
								sendSetLiveCommand();
							}

							performDomecastRouting(buffer);
						}
						else
							starryNightPassThru(buffer);
						
//						Log.inst().debug("Handled comm.");
					}
				}
				catch (IOException e1) {
					// TODO Auto-generated catch block
					stopped.set(true);
					e1.printStackTrace();
				}
			}

			// Close streams
			Log.inst().info("Shutting down connection streams.");
			try
			{
				if (out != null)
					out.close();
				if (in != null)
					in.close();
			}
			catch (IOException e) {
			}
		}

		// Close sockets
		Log.inst().info("Closing sockets.");
		try
		{
			if (socket != null)
				socket.close();
			if (outboundSocket != null)
				outboundSocket.close();
		}
		catch (IOException e) {
		}
		
		// Stop the thread that might be reading InputStream
		stopReadIgnoredStreamThread();
		
		// Notify owner this thread is dying.
		owner.threadDying(this);

		Log.inst().info("Exiting thread.");
	}
	
	private void stopReadIgnoredStreamThread()
	{
		// Stop the thread we created to read InputStream we were ignoring.
		if ((readIgnoredStreamThread != null) && readIgnoredStreamThread.isAlive())
		{
			Log.inst().info("Stopping ReadIgnoredInputStreamThread.");
			
			// Signal this thread to stop without forcing an interrupt. If we call interrupt() on a thread
			// that is blocked on a socket read, it will cause the socket connection to be closed. We don't
			// want that.
			readIgnoredStreamThread.setStopped();
			
			// Wait for ReadIgnoredInputStreamThread to stop
			try {
				Log.inst().debug("Waiting for " + readIgnoredStreamThread.getName() + " to exit...");
				readIgnoredStreamThread.join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			readIgnoredStreamThread = null;
		}
	}
	
	private void switchCommModes(boolean isAnyHostListening)
	{
		if (((theApp.clientType == CommUtils.kPresenterID) && !modifyReplyPort) ||
			((theApp.clientType == CommUtils.kHostID) && modifyReplyPort))
		{
			if (isAnyHostListening)
			{
				// Create a thread to read the InputStream we are about to ignore when we begin routing comm
				readIgnoredStreamThread = new ReadIgnoredInputStreamThread();
				readIgnoredStreamThread.start();
			}
			else
				stopReadIgnoredStreamThread();
		}
	}
	
	/**
	 * What's the deal with this method? SNTCPPassThruServer is essentially concerned with acting as a man-in-the-middle
	 * for the TCP communication that occurs between Preflight and Renderbox. The SN communication protocol involves
	 * Preflight establishing a TCP connection to Renderbox on a specific port. Upon establishing this connection,
	 * Renderbox will then attempt to establish a TCP connection back to Preflight on a second port. The port that RB
	 * uses to connect back to PF is specified (along with other info) in the first 120 bytes of each message that PF
	 * sends to RB. As the man-in-the-middle running on the PF system, if we don't modify the port in the header before
	 * sending the message along to RB, RB will connect directly back to PF instead of our process. So, rather than
	 * blindly reading the bytes from the incoming socket and sending them out to the outgoing socket, we must read the
	 * incoming data according to SN's TCP message protocol, and modify the replyToPort in each header with the port this
	 * instance of SNTCPPassThruServer.recvListenerThread is listening on. Ya, a little complicated, but this beats
	 * screwing with the TCP communication details in SN Preflight and Renderbox, ATM-4, SN Intercept, TLE, etc. 
	 * @param buffer
	 * @throws IOException 
	 */
	private void starryNightPassThru(byte[] buffer) throws IOException
	{
		// Get total length of incoming message and modify the replyToPort
		long messageLength = readSNHeader(buffer, domecastHostID);
		
		// Write the header to the outbound socket
		CommUtils.writeOutputStream(out, buffer, 0, kSNHeaderLength);

		// Now read/write the remainder of the message
		int bytesLeftToReceive = (int)(messageLength - kSNHeaderLength);
		while (bytesLeftToReceive > 0)
		{
			// Read as much of the message as our buffer will hold
			int bytesToRead = Math.min(bytesLeftToReceive, buffer.length);
			CommUtils.readInputStream(in, buffer, 0, bytesToRead);
			
			// Write the buffer
			CommUtils.writeOutputStream(out, buffer, 0, bytesToRead);

			bytesLeftToReceive -= bytesToRead;
		}
	}
	
	private long readSNHeader(byte[] buffer, StringBuffer fullStateHostID) throws IOException
	{
		long result = 0;
		
		// Read the SN header from the inbound socket
		CommUtils.readInputStream(in, buffer, 0, kSNHeaderLength);

		// Get the total length of the message that we are receiving.
		String messageLengthStr = new String(buffer, 0, kSNHeaderFieldLength).trim();
		try {
			result = Long.parseLong(messageLengthStr);
		} catch (NumberFormatException e) {
			Log.inst().error("messageLengthStr: " + messageLengthStr);
			throw new IOException(e.getMessage());
		}
//		Log.inst().debug("Parsed messageLength = " + result);

		if (modifyReplyPort)
		{
			// Change the buffer so that the return port is set to outboundNode.replyPort
			// The return port digits start at pos 50 in the buffer.
			if (replyPortBytes != null)
				System.arraycopy(replyPortBytes, 0, buffer, kSNHeaderReplyPortPosition, replyPortBytes.length);
		}
		
		// Obtain the clientAppName from the header
		if (clientAppName == null)
		{
			byte[] hdrCopy = new byte[kSNHeaderFieldLength];
			System.arraycopy(buffer, kSNHeaderClientAppNamePosition, hdrCopy, 0, hdrCopy.length);
			clientAppName = new String(hdrCopy).trim();
			Log.inst().debug("clientAppName parsed from SN header: " + clientAppName);
		}
		
		// Determine if the field beginning at kSNHeaderDomecastHostIDPosition contains something
		if (buffer[kSNHeaderDomecastHostIDPosition] != ' ')
			fullStateHostID.insert(0, new String(buffer, kSNHeaderDomecastHostIDPosition, kSNHeaderFieldLength).trim());
		
		return result;
	}

	private void performDomecastRouting(byte[] buffer) throws IOException
	{
		OutputStream dcsOut = theApp.getServerOutputStream();
		if (dcsOut == null)
			return;

		if (theApp.clientType == CommUtils.kPresenterID)
		{
			if (modifyReplyPort)
			{
				// If we get here, this is the thread that reads data from the local PF or ATM4.
				// Do the usual pass-thru...
				long messageLength = readSNHeader(buffer, domecastHostID);		// Read the SN header
				
				// If domecastHostID contains something, we don't want to forward the SN packet to our local RB.
				if (domecastHostID.length() == 0)
					CommUtils.writeOutputStream(out, buffer, 0, kSNHeaderLength);	// Write the SN header to the outbound socket
				
				// ... and also write the incoming data to the domecast server.
				writeSNPacketToServer(buffer, dcsOut, messageLength, clientAppName);
			}
			else
			{
				// If we get here, this is the thread that, during pass-thru, reads responses from the local RB.
				// We read this data on a separate thread (ReadIgnoredInputStreamThread) but it will not be routed anywhere.
				// Here we route the data that is available from the domecast server to our local OutputStream.
				readSNPacketFromServer(buffer);
			}
		}
		else
		{
			if (modifyReplyPort)
			{
				// If we get here, this is the thread that, during pass-thru, reads data from the local PF or ATM4.
				// We read this data on a separate thread (ReadIgnoredInputStreamThread) but it will not be routed anywhere.
				// Here we route the data that is available from the domecast server to our local OutputStream.
				readSNPacketFromServer(buffer);
			}
			else
			{
				// If we get here, this is the thread that reads data from the local RB.
				// Do the usual pass-thru...
				long messageLength = readSNHeader(buffer, domecastHostID);		// Read the SN header
				CommUtils.writeOutputStream(out, buffer, 0, kSNHeaderLength);	// Write the SN header to the outbound socket
				
				// ... and also write the incoming data to the domecast server.
				writeSNPacketToServer(buffer, dcsOut, messageLength, clientAppName);
			}
		}
		
		// Clear domecastHostID.
		domecastHostID.setLength(0);
	}
	
	private void readSNDataToNowhere(byte[] buffer, long messageLength) throws IOException
	{
		int bytesLeftToReceive = (int)(messageLength - kSNHeaderLength);
		while (bytesLeftToReceive > 0)
		{
			// Read as much of the message as our buffer will hold
			int bytesToRead = Math.min(bytesLeftToReceive, buffer.length);
			CommUtils.readInputStream(in, buffer, 0, bytesToRead);

			bytesLeftToReceive -= bytesToRead;
		}
	}
	
	/*
	 * A "SN packet" is understood as the data containing both SN header and the stream
	 * of data following it. This is what theApp.serverConnection stores as ByteBuffer
	 * instances in one of two queues.
	 */
	private void readSNPacketFromServer(byte[] buffer) throws IOException
	{
		// We want to read the data sent to us from the domecast server.
		ByteBuffer nextPacket = theApp.serverConnection.getInputStreamData(clientAppName);
		if (nextPacket != null)
		{
			byte[] receivedBuffer = nextPacket.array();
			
			// Send all this data to the local OutputStream
			CommUtils.writeOutputStream(out, receivedBuffer, 0, receivedBuffer.length);
		}
	}
	
	private void writeSNPacketToServer(byte[] buffer, OutputStream dcsOut, long messageLength, String msgSrc) throws IOException
	{
		// Now we write to the domecast server OutputStream.
		// We must write the client header, then the SN header in buffer, read the rest of the packet from the local InputStream
		// and write it to the domecast server OutputStream while we have exclusive access to the
		// domecast server OutputStream.
		synchronized(theApp.serverConnection.outputStreamLock)
		{
			// Write the client header
			CommUtils.writeHeader(dcsOut, outHdr, messageLength, msgSrc, domecastHostID.toString(), ClientHeader.kCOMM);
			
			// Write the SN header currently in buffer
			CommUtils.writeOutputStream(dcsOut, buffer, 0, kSNHeaderLength);
			
			// Read/write the remainder of the data
			readSNDataToOutputStreams(buffer, messageLength, dcsOut);
		}
	}
	
	private void readSNDataToOutputStreams(byte[] buffer, long messageLength, OutputStream serverOutputStream) throws IOException
	{
		int bytesLeftToReceive = (int)(messageLength - kSNHeaderLength);
		while (bytesLeftToReceive > 0)
		{
			// Read as much of the message as our buffer will hold
			int bytesToRead = Math.min(bytesLeftToReceive, buffer.length);
			CommUtils.readInputStream(in, buffer, 0, bytesToRead);
	
			// If domecastHostID contains something, we don't want to forward the SN packet to our local OutputStream
			if (domecastHostID.length() == 0)
				CommUtils.writeOutputStream(out, buffer, 0, bytesToRead);
			
			// Also write the buffer to the domecast server OutputStream
			CommUtils.writeOutputStream(serverOutputStream, buffer, 0, bytesToRead);

			bytesLeftToReceive -= bytesToRead;
		}
	}

	/*
	 * When we switch to domecast routing, the first thing the local PF should do is call
	 * TStarryNightDoc::SetLiveCommunicationWithRB() so that the remote RB is initialized
	 * with the local PF's prefs, location, current view, etc.
	 */
	private void sendSetLiveCommand() throws IOException
	{
		String hostConnectionID = theApp.requestFullState.get();
		if (hostConnectionID.isEmpty())
			return;

		// Clear this value so we don't trigger more than one request
		theApp.requestFullState.set("");

		byte[] hdrBytes = kSetLiveCommandHdr.getBytes();
		
		// Encode hostConnectionID in the request
		String setLiveCommandData = kSetLiveCommandData.replaceFirst("@DomecastHostID@", hostConnectionID);
		byte[] dataBytes = setLiveCommandData.getBytes();
		
		// Write rbPrefs_DomeServer_TCPPort at position kSNHeaderReplyPortPosition
		byte[] replyPortBytes = CommUtils.getRightPaddedByteArray(Integer.toString(theApp.rbPrefs_DomeServer_TCPPort), kSNHeaderFieldLength);
		System.arraycopy(replyPortBytes, 0, hdrBytes, kSNHeaderReplyPortPosition, replyPortBytes.length);
		
		// Write length of dataBytes at position kSNHeaderDataLengthPosition
		byte[] dataLengthBytes = CommUtils.getRightPaddedByteArray(Integer.toString(dataBytes.length), kSNHeaderFieldLength);
		System.arraycopy(dataLengthBytes, 0, hdrBytes, kSNHeaderDataLengthPosition, replyPortBytes.length);
		
		// Write length of entire packet (message) at position kSNHeaderPacketLengthPosition
		byte[] packetLengthBytes = CommUtils.getRightPaddedByteArray(Integer.toString(dataBytes.length + kSNHeaderLength), kSNHeaderFieldLength);
		System.arraycopy(packetLengthBytes, 0, hdrBytes, kSNHeaderPacketLengthPosition, replyPortBytes.length);
		
		Log.inst().debug("Sending Set Live command:");
		
		CommUtils.writeOutputStream(out, hdrBytes, 0, hdrBytes.length);
		CommUtils.writeOutputStream(out, dataBytes, 0, dataBytes.length);
	}
}

