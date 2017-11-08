package com.spitzinc.domecasting.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import com.spitzinc.domecasting.CommUtils;
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

	protected InputStream in;
	protected OutputStream out;
	
	public SNTCPPassThruThread(ClientSideConnectionListenerThread owner, Socket inboundSocket, TCPNode outboundNode)
	{
		super(owner, inboundSocket);
		
		this.owner = owner;
		this.outboundNode = outboundNode;

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

		this.setName(getClass().getSimpleName() + "_" + inboundSocket.getLocalPort() + "->" + outboundNode.port);	
	}
	
	public void run()
	{
		// Attempt to connect to outbound host
		System.out.println(getName() + ": Attempting to establish outbound connection.");
		outboundSocket = TCPConnectionHandlerThread.connectToHost(outboundNode.hostname, outboundNode.port, this.getName());
		if (outboundSocket != null)
		{
			System.out.println(getName() + ": Outbound connection established.");

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
				byte[] buffer = new byte[16*1024];

				// Begin reading data from inbound stream and writing it to outbound stream
				boolean needToModifyReplyPort = (outboundNode.replyPort != -1);
				while (!stopped.get())
				{
//					if (!needToModifyReplyPort && (clientAppName != null))
//						simplePassThru(buffer);
//					else
						starryNightPassThru(buffer, needToModifyReplyPort);
				}
			}

			// Close streams
			System.out.println(getName() + ": Shutting down connection streams.");
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
		System.out.println(getName() + ": Closing sockets.");
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

		System.out.println(getName() + ": Exiting thread.");
	}
	
	/**
	 * Just read bytes from the InputStream and write them to the OutputStream.
	 */
	private void simplePassThru(byte[] buffer)
	{
		// Read data from inbound socket
		int count = 0;
		try
		{
			count = in.read(buffer);
			if (count == -1)
				stopped.set(true);
			System.out.println(getName() + ": Read " + count + " bytes from inbound socket.");
		}
		catch (IOException e) {
			stopped.set(true);
			System.out.println(getName() + ": Failed reading inbound socket.");
		}

		// Write data to outbound socket
		if (!stopped.get())
		{
			if (!CommUtils.writeOutputStream(out, buffer, 0, count, getName()))
				stopped.set(true);
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
	 */
	private void starryNightPassThru(byte[] buffer, boolean modifyReplyPort)
	{	
		// Read the SN header from the inbound socket
		if (!CommUtils.readInputStream(in, buffer, 0, kSNHeaderLength, getName()))
			stopped.set(true);
			
		// Get total length of incoming message and modify the replyToPort
		int messageLength = 0;
		if (!stopped.get())
		{
			// Get the total length of the message that we are receiving.
			String messageLengthStr = new String(buffer, 0, kSNHeaderFieldLength).trim();
			try {
				messageLength = Integer.parseInt(messageLengthStr);
			} catch (NumberFormatException e) {
				System.out.println(getName() + ": messageLengthStr: " + messageLengthStr + ".");
				e.printStackTrace();
				stopped.set(true);
				return;
			}
			System.out.println(getName() + ": Parsed messageLength = " + messageLength);

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
			if (!CommUtils.writeOutputStream(out, buffer, 0, kSNHeaderLength, getName()))
				stopped.set(true);
		}

		// Now read/write the remainder of the message
		int bytesLeftToReceive = messageLength - kSNHeaderLength;
		while (!stopped.get() && (bytesLeftToReceive > 0))
		{
			// Read as much of the message as our buffer will hold
			int bytesToRead = Math.min(bytesLeftToReceive, buffer.length);
			if (!CommUtils.readInputStream(in, buffer, 0, bytesToRead, getName()))
			{
				stopped.set(true);
				break;
			}

			// Write the buffer
			if (!CommUtils.writeOutputStream(out, buffer, 0, bytesToRead, getName()))
				stopped.set(true);

			bytesLeftToReceive -= bytesToRead;
		}
	}
}
