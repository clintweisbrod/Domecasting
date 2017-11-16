package com.spitzinc.domecasting.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

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
	private byte[] replyPortBytes = null;
	private String clientAppName = null;
	private ClientApplication theApp;
	private boolean modifyReplyPort;

	protected InputStream in;
	protected OutputStream out;
	
	private ClientHeader inHdr;
	private ClientHeader outHdr;
	
	public SNTCPPassThruThread(ClientSideConnectionListenerThread owner, Socket inboundSocket, TCPNode outboundNode)
	{
		super(owner, inboundSocket);
		
		this.owner = owner;
		this.outboundNode = outboundNode;
		this.modifyReplyPort = (outboundNode.replyPort != -1);
		this.inHdr = new ClientHeader();
		this.outHdr = new ClientHeader();

		// Build a byte buffer to replace the contents of the replyPort field in a SN TCP message header
		if (outboundNode.replyPort != -1)
		{
			// Get string representation of integer port
			String replyPortStr = Integer.toString(outboundNode.replyPort);

			// Right-pad the string so it is exactly kSNHeaderFieldLength chars long
			replyPortStr = String.format("%1$-" + kSNHeaderFieldLength + "s", replyPortStr);

			// Convert string to bytes
			replyPortBytes = replyPortStr.getBytes();
		}
		
		this.theApp = (ClientApplication)ClientApplication.inst();

		this.setName(getClass().getSimpleName() + "_" + inboundSocket.getLocalPort() + "->" + outboundNode.port);	
	}
	
	public void run()
	{
		// Attempt to connect to outbound host
		Log.inst().info("Attempting to establish outbound connection.");
		outboundSocket = TCPConnectionHandlerThread.connectToHost(outboundNode.hostname, outboundNode.port, this.getName());
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

				// Begin reading data from inbound stream and writing it to outbound stream
				while (!stopped.get())
					starryNightPassThru(buffer);
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
	private void starryNightPassThru(byte[] buffer)
	{
		// If modifyReplyPort is true, this means we are dealing with communication that has
		// originated from PF | ATM4. If theApp.routeComm is true, then we must
		
		// Read the SN header from the inbound socket
		try
		{
			handleInputStreamCommRouting(buffer, 0, kSNHeaderLength, getName());
//			CommUtils.readInputStream(in, buffer, 0, kSNHeaderLength, getName());

			// Get total length of incoming message and modify the replyToPort
			int messageLength = 0;
			if (!stopped.get())
			{
				// Get the total length of the message that we are receiving.
				String messageLengthStr = new String(buffer, 0, kSNHeaderFieldLength).trim();
				try {
					messageLength = Integer.parseInt(messageLengthStr);
				} catch (NumberFormatException e) {
					Log.inst().error("messageLengthStr: " + messageLengthStr + ".");
					throw new IOException(e.getMessage());
				}
//				Log.inst().info("Parsed messageLength = " + messageLength);

				if (modifyReplyPort)
				{
					// Change the buffer so that the return port is set to outboundNode.replyPort
					// The return port digits start at pos 50 in the buffer.
					if (replyPortBytes != null)
						System.arraycopy(replyPortBytes, 0, buffer, kSNHeaderReplyPortPosition, replyPortBytes.length);
				}
				
				// Obtain the clientAppName from the header
				if (clientAppName == null)
					clientAppName = new String(buffer, kSNHeaderClientAppNamePosition, kSNHeaderFieldLength).trim();

				// Write the header to the outbound socket
				handleOutputStreamCommRouting(buffer, 0, kSNHeaderLength, getName());
//				CommUtils.writeOutputStream(out, buffer, 0, kSNHeaderLength, getName());
			}

			// Now read/write the remainder of the message
			int bytesLeftToReceive = messageLength - kSNHeaderLength;
			while (!stopped.get() && (bytesLeftToReceive > 0))
			{
				// Read as much of the message as our buffer will hold
				int bytesToRead = Math.min(bytesLeftToReceive, buffer.length);
				handleInputStreamCommRouting(buffer, 0, bytesToRead, getName());
//				CommUtils.readInputStream(in, buffer, 0, bytesToRead, getName());

				// Write the buffer
				handleOutputStreamCommRouting(buffer, 0, bytesToRead, getName());
//				CommUtils.writeOutputStream(out, buffer, 0, bytesToRead, getName());

				bytesLeftToReceive -= bytesToRead;
			}
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			stopped.set(true);
			e1.printStackTrace();
		}
	}
	
	private void handleInputStreamCommRouting(byte[] buffer, int offset, int len, String caller) throws IOException
	{
		// Do the usual read from PF or ATM4. This may be overwritten below
		CommUtils.readInputStream(in, buffer, 0, len, caller);
		
		if (theApp.routeComm())
		{
			InputStream dcsIn = theApp.getServerInputStream();
			if (dcsIn != null)
			{
				if (theApp.clientType == CommUtils.kPresenterID)
				{
					if (modifyReplyPort)
					{
					}
					else
					{
						// If we get here, this is the thread that reads responses from the local RB. We have already
						// read this data above but we will now ignore it. We want to read the response from the remote RB.
						// Read and parse the header
						synchronized(dcsIn) {
							do {
								// Read header
								CommUtils.readInputStream(dcsIn, inHdr.bytes, 0, ClientHeader.kHdrByteCount, caller);
								inHdr.parseHeaderBuffer();
								// Read the data
								CommUtils.readInputStream(dcsIn, buffer, 0, inHdr.messageLen, caller);
								
								if (!inHdr.messageType.equals(ClientHeader.kCOMM))
									Log.inst().info("WHOA!!! Received " + inHdr.messageType + " header");
								
							} while (!inHdr.messageType.equals(ClientHeader.kCOMM));
							
							String receivedData = new String(buffer, 0, inHdr.messageLen);
							Log.inst().info("Received from server: ");
							Log.inst().info(receivedData);
						}
					}
				}
				else	// Host
				{
					if (modifyReplyPort)
					{
						// If we get here, we're reading data from PF or ATM4. We have already read this data above but
						// we will now ignore it. We want to read the data from the remote PF or ATM4.
						// Read and parse the header
						synchronized(dcsIn) {
							do {
								// Read header
								CommUtils.readInputStream(dcsIn, inHdr.bytes, 0, ClientHeader.kHdrByteCount, caller);
								inHdr.parseHeaderBuffer();
								// Read the data
								CommUtils.readInputStream(dcsIn, buffer, 0, inHdr.messageLen, caller);
								
								if (!inHdr.messageType.equals(ClientHeader.kCOMM))
									Log.inst().info("WHOA!!! Received " + inHdr.messageType + " header");
								
							} while (!inHdr.messageType.equals(ClientHeader.kCOMM));
							
							String receivedData = new String(buffer, 0, inHdr.messageLen);
							Log.inst().info("Received from server: ");
							Log.inst().info(receivedData);
						}
					}
					else
					{
					}
				}
			}
		}
	}
	
	private void handleOutputStreamCommRouting(byte[] buffer, int offset, int len, String caller) throws IOException
	{
		if (theApp.routeComm())
		{
			OutputStream dcsOut = theApp.getServerOutputStream();
			if (dcsOut != null)
			{
				String msgSrc, msgDst;
				if (theApp.clientType == CommUtils.kPresenterID)
				{
					// The thread that has modifyReplyPort set is the one sending data from PF or ATM4 to RB. As a presenter,
					// and during comm routing, we want to send this data across the server connection as well as to the local RB.
					if (modifyReplyPort)
					{
						msgSrc = clientAppName;
						msgDst = ClientHeader.kSNRB;

						synchronized(dcsOut) {
							CommUtils.writeHeader(dcsOut, outHdr, len, msgSrc, msgDst, ClientHeader.kCOMM, caller);
							CommUtils.writeOutputStream(dcsOut, buffer, 0, len, caller);
							
							String sentData = new String(buffer, 0, len);
							Log.inst().info("Sent to local RB: ");
							Log.inst().info(sentData);
						}
						
						// Also do the usual pass-thru to the local RB
						CommUtils.writeOutputStream(out, buffer, 0, len, caller);
					}
					else
					{
						// If we get here, this is the thread that handles responses from RB to either PF or ATM4.
						// The local OutputStream (out) is a connection to either PF or ATM4. For presenters, we want
						// to receive the remote RB's responses, not the local ones, so we do not write the local OutputStream.
					}
				}
				else	// Host
				{
					// The thread that has modifyReplyPort set is the one sending data from PF or ATM4 to RB. As a host,
					// and during comm routing, we still write the contents of the buffer to the local OutputStream but
					// the buffer contents have been filled with data from the remote PF or ATM4.
					if (modifyReplyPort)
					{
						// Just do the usual pass-thru
						CommUtils.writeOutputStream(out, buffer, 0, len, caller);
						
						String sentData = new String(buffer, 0, len);
						Log.inst().info("Sent to local RB: ");
						Log.inst().info(sentData);
					}
					else
					{
						// If we get here, this is the thread that is reading responses from the host RB. We want to write
						// these responses to both the local PF (or ATM-4) AND send this data across the server connection.
						msgSrc = ClientHeader.kSNRB;
						msgDst = clientAppName;
						
						synchronized(dcsOut) {
							CommUtils.writeHeader(dcsOut, outHdr, len, msgSrc, msgDst, ClientHeader.kCOMM, caller);
							CommUtils.writeOutputStream(dcsOut, buffer, 0, len, caller);
						}
						
						// Also do the usual pass-thru to the local PF or ATM4.
						CommUtils.writeOutputStream(out, buffer, 0, len, caller);
					}
				}
			}
		}
		else
		{
			// Just do the usual pass-thru
			CommUtils.writeOutputStream(out, buffer, 0, len, caller);
		}
	}
}
