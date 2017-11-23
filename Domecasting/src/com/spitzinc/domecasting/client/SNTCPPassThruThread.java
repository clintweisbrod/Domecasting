package com.spitzinc.domecasting.client;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.CharBuffer;

import com.spitzinc.domecasting.ClientHeader;
import com.spitzinc.domecasting.CommUtils;
import com.spitzinc.domecasting.Log;
import com.spitzinc.domecasting.TCPConnectionHandlerThread;

public class SNTCPPassThruThread extends TCPConnectionHandlerThread
{
	final static int kSNHeaderFieldLength = 10;
	final static int kSNHeaderLength = 120;
	final static int kSNHeaderReplyPortPosition = 50;
	final static int kSNHeaderClientAppNamePosition = 60;
	
	protected Socket outboundSocket = null;
	private TCPNode outboundNode;
	private char[] replyPortBytes = null;
	private String clientAppName = null;
	private ClientApplication theApp;
	private boolean modifyReplyPort;

	protected InputStreamReader in;
	protected OutputStreamWriter out;
	
	private ClientHeader outHdr;
	
	public SNTCPPassThruThread(ClientSideConnectionListenerThread owner, Socket inboundSocket, TCPNode outboundNode)
	{
		super(owner, inboundSocket);

		this.outboundNode = outboundNode;
		this.modifyReplyPort = (outboundNode.replyPort != -1);
		this.outHdr = new ClientHeader();

		// Build a byte buffer to replace the contents of the replyPort field in a SN TCP message header
		if (outboundNode.replyPort != -1)
		{
			// Get string representation of integer port
			String replyPortStr = Integer.toString(outboundNode.replyPort);

			// Right-pad the string so it is exactly kSNHeaderFieldLength chars long
			replyPortStr = String.format("%1$-" + kSNHeaderFieldLength + "s", replyPortStr);

			// Convert string to bytes
			replyPortBytes = new char[replyPortStr.length()];
			replyPortStr.getChars(0, replyPortStr.length(), replyPortBytes, 0);
		}
		
		this.theApp = (ClientApplication)ClientApplication.inst();

		this.setName(getClass().getSimpleName() + "_" + inboundSocket.getLocalPort() + "->" + outboundNode.port);	
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
				in = new InputStreamReader(socket.getInputStream());
				out = new OutputStreamWriter(outboundSocket.getOutputStream());
			}
			catch (IOException e) {
				e.printStackTrace();
			}

			if ((in != null) && (out != null))
			{
				// Allocate byte buffer to handle comm
				char[] buffer = new char[CommUtils.kCommBufferSize];

				// Begin reading data from inbound stream and writing it to outbound stream in chunks
				// of SN comm.
				try
				{
					while (!stopped.get())
					{
						if (theApp.isHostListening.get())
							performDomecastRouting(buffer);
						else
							starryNightPassThru(buffer);
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
		
		// Notify owner this thread is dying.
		owner.threadDying(this);

		Log.inst().info("Exiting thread.");
	}
	
	/**
	 * Just read bytes from the InputStream and write them to the OutputStream.
	 */
/*
	private void simplePassThru(byte[] buffer)
	{
		// Read data from inbound socket
		int count = 0;
		try
		{
			count = in.read(buffer);
			if (count == -1)
				stopped.set(true);
			Log.inst().info(getName() + ": Read " + count + " bytes from inbound socket.");
		}
		catch (IOException e) {
			stopped.set(true);
			Log.inst().info(getName() + ": Failed reading inbound socket.");
		}

		// Write data to outbound socket
		if (!stopped.get())
		{
			if (!CommUtils.writeOutputStream(out, buffer, 0, count, getName()))
				stopped.set(true);
		}
	}
*/	
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
	private void starryNightPassThru(char[] buffer) throws IOException
	{
		// Get total length of incoming message and modify the replyToPort
		int messageLength = readSNHeader(buffer);

		// Write the header to the outbound socket
		CommUtils.writeOutputStream(out, buffer, 0, kSNHeaderLength);

		// Now read/write the remainder of the message
		int bytesLeftToReceive = messageLength - kSNHeaderLength;
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
	
	private int readSNHeader(char[] buffer) throws IOException
	{
		int result = 0;
		
		// Read the SN header from the inbound socket
		CommUtils.readInputStream(in, buffer, 0, kSNHeaderLength);

		// Get the total length of the message that we are receiving.
		String messageLengthStr = new String(buffer, 0, kSNHeaderFieldLength).trim();
		try {
			result = Integer.parseInt(messageLengthStr);
		} catch (NumberFormatException e) {
			Log.inst().error("messageLengthStr: " + messageLengthStr);
			throw new IOException(e.getMessage());
		}
//		Log.inst().info("Parsed messageLength = " + messageLength);

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
			clientAppName = new String(buffer, kSNHeaderClientAppNamePosition, kSNHeaderFieldLength).trim();
			Log.inst().debug("clientAppName parsed from SN header: " + clientAppName);
		}
		
		return result;
	}

	private void performDomecastRouting(char[] buffer) throws IOException
	{
		OutputStreamWriter dcsOut = theApp.getServerOutputStream();
		if (dcsOut == null)
			return;

		if (theApp.clientType == CommUtils.kPresenterID)
		{
			if (modifyReplyPort)
			{
				// If we get here, this is the thread that reads data from the local PF or ATM4.
				// Do the usual pass-thru but also write the incoming data to the domecast server.
				writeSNPacketToServer(buffer, dcsOut, clientAppName, ClientHeader.kSNRB);
			}
			else
			{
				// If we get here, this is the thread that, during pass-thru, reads responses from the local RB.
				// We read this data but it will not be routed anywhere. We then route the data that is available
				// from the domecast server to our local OutputStream.
				readSNPacketFromServer(buffer);
			}
		}
		else
		{
			if (modifyReplyPort)
			{
				// If we get here, this is the thread that, during pass-thru, reads data from the local PF or ATM4.
				// We read this data but it will not be routed anywhere. We then route the data that is available
				// from the domecast server to our local OutputStream.
				readSNPacketFromServer(buffer);
			}
			else
			{
				// If we get here, this is the thread that reads data from the local RB.
				// Do the usual pass-thru but also write the incoming data to the domecast server.
				writeSNPacketToServer(buffer, dcsOut, ClientHeader.kSNRB, clientAppName);
			}
		}
	}
	
	private void readSNDataToNowhere(char[] buffer, int messageLength) throws IOException
	{
		int bytesLeftToReceive = messageLength - kSNHeaderLength;
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
	private void readSNPacketFromServer(char[] buffer) throws IOException
	{
		// We still have to read what the local InputStream has for us, but the data is ignored.
		// THIS CAN BLOCK IF THERE'S NO DATA TO READ!!!
		if (in.ready())
		{
			int messageLength = readSNHeader(buffer);
			readSNDataToNowhere(buffer, messageLength);
			
			// We want to read the data sent to us from the domecast server.
			CharBuffer nextPacket = theApp.serverConnection.getInputStreamData(clientAppName);
			if (nextPacket != null)
			{
				char[] receivedBuffer = nextPacket.array();
				
				// Send all this data to the local OutputStream
				CommUtils.writeOutputStream(out, receivedBuffer, 0, receivedBuffer.length);
			}
		}
	}
	
	private void writeSNPacketToServer(char[] buffer, OutputStreamWriter dcsOut, String msgSrc, String msgDst) throws IOException
	{
		int messageLength = readSNHeader(buffer);
		
		// Write the SN header to the outbound socket
		CommUtils.writeOutputStream(out, buffer, 0, kSNHeaderLength);
		
		// Now we write to the domecast server OutputStream.
		// We must write the client header, then the SN header in buffer, read the rest of the packet from the local InputStream
		// and write it to the domecast server OutputStream while we have exclusive access to the
		// domecast server OutputStream.
		synchronized(dcsOut) {
			// Write the client header
			CommUtils.writeHeader(dcsOut, outHdr, messageLength, msgSrc, msgDst, ClientHeader.kCOMM);
			
			// Write the SN header currently in buffer
			CommUtils.writeOutputStream(dcsOut, buffer, 0, kSNHeaderLength);
			
			// Read/write the remainder of the data
			readSNDataToOutputStreams(buffer, messageLength, dcsOut);
		}
	}
	
	private void readSNDataToOutputStreams(char[] buffer, int messageLength, OutputStreamWriter serverOutputStream) throws IOException
	{
		int bytesLeftToReceive = messageLength - kSNHeaderLength;
		while (bytesLeftToReceive > 0)
		{
			// Read as much of the message as our buffer will hold
			int bytesToRead = Math.min(bytesLeftToReceive, buffer.length);
			CommUtils.readInputStream(in, buffer, 0, bytesToRead);
	
			// Write the buffer to the local OutputStream
			CommUtils.writeOutputStream(out, buffer, 0, bytesToRead);
			
			// Also write the buffer to the domecast server OutputStream
			CommUtils.writeOutputStream(serverOutputStream, buffer, 0, bytesToRead);

			bytesLeftToReceive -= bytesToRead;
		}
	}
}
