package com.spitzinc.domecasting.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicBoolean;

public class TCPPassThruThread extends Thread
{
	final static int kSNHeaderFieldLength = 10;
	final static int kSNHeaderLength = 120;
	final static int kSNHeaderReplyPortPosition = 50;
	
	private AtomicBoolean stopped;
	private boolean outboundConnectionFailed;
	private Socket inboundSocket = null;
	private Socket outboundSocket = null;
	private TCPNode outboundNode;
	private TCPConnectionListenerThread owner = null;
	private byte[] replyPortBytes = null;

	protected InputStream in;
	protected OutputStream out;
	
	public TCPPassThruThread(TCPConnectionListenerThread owner, Socket inboundSocket, TCPNode outboundNode)
	{
		this.owner = owner;
		this.inboundSocket = inboundSocket;
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

		this.setName(this.getClass().getSimpleName() + "_" + inboundSocket.getLocalPort() + "->" + outboundNode.port);
		this.stopped = new AtomicBoolean(false);
		
	}
	
	public boolean getStopped() {
		return stopped.get();
	}
	
	public void setStopped() {
		stopped.set(true);
	}
	
	public void run()
	{
		// Attempt to connect to outbound host
		try
		{
			final int kConnectionTimeoutMS = 1000;
			InetAddress addr = InetAddress.getByName(outboundNode.hostname);
			SocketAddress sockaddr = new InetSocketAddress(addr, outboundNode.port);

			outboundConnectionFailed = false;
			outboundSocket = new Socket();
			outboundSocket.setKeepAlive(true);
			System.out.println(this.getName() + ": Attempting to establish outbound connection.");
			outboundSocket.connect(sockaddr, kConnectionTimeoutMS);

			System.out.println(this.getName() + ": Outbound connection established.");
		}
		catch (UnknownHostException e) {
			outboundConnectionFailed = true;
			System.out.println(this.getName() + ": Unknown host: " + outboundNode.hostname);
		}
		catch (SocketTimeoutException e) {
			outboundConnectionFailed = true;
			System.out.println(this.getName() + ": Connect timeout.");
		}
		catch (IOException e) {
			outboundConnectionFailed = true;
			System.out.println(this.getName() + ": Connect failed.");
		}

		// Don't proceed unless outbound connection was established
		if (!outboundConnectionFailed)
		{
			try
			{
				in = inboundSocket.getInputStream();
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
				if (outboundNode.replyPort == -1)
				{
					while (!stopped.get())
						simplePassThru(buffer);
				}
				else
				{
					while (!stopped.get())
						modifyReplyPortPassThru(buffer);
				}
			}

			// Close streams
			System.out.println(this.getName() + ": Shutting down connection streams.");
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
		System.out.println(this.getName() + ": Closing sockets.");
		try
		{
			inboundSocket.close();
			outboundSocket.close();
		}
		catch (IOException e) {
		}
		
		// Notify owner this thread is dying.
		owner.threadDying(this);

		System.out.println(this.getName() + ": Exiting thread.");
	}
	
	private void simplePassThru(byte[] buffer)
	{
		// Read data from inbound socket
		int count = 0;
		try
		{
			count = in.read(buffer);
			System.out.println(this.getName() + ": Read " + count + " bytes from inbound socket.");
		}
		catch (IOException e) {
			stopped.set(true);
			System.out.println(this.getName() + ": Failed reading inbound socket.");
		}

		// Write data to outbound socket
		if (!stopped.get() && (count > 0))
		{
			try
			{
				out.write(buffer, 0, count);
				System.out.println(this.getName() + ": Wrote " + count + " bytes to outbound socket.");
			}
			catch (IOException e) {
				stopped.set(true);
				System.out.println(this.getName() + ": Failed writing outbound socket.");
			}
		}
		else
			stopped.set(true);
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
	private void modifyReplyPortPassThru(byte[] buffer)
	{	
		// Read the SN header from the inbound socket
		int totalBytesRead = 0;
		try
		{
			while (totalBytesRead < kSNHeaderLength)
			{
				int bytesRead = in.read(buffer, totalBytesRead, kSNHeaderLength - totalBytesRead);
				if (bytesRead == -1)
				{
					stopped.set(true);
					break;
				}
				System.out.println(this.getName() + ": Read " + bytesRead + " bytes from inbound socket.");
				totalBytesRead += bytesRead;
			}
		}
		catch (IOException e) {
			stopped.set(true);
			System.out.println(this.getName() + ": Failed reading inbound socket.");
		}

		// Get total length of incoming message and modify the replyToPort
		int messageLength = 0;
		if (!stopped.get())
		{
			// Get the total length of the message that we are receiving.
			String messageLengthStr = new String(buffer, 0, kSNHeaderFieldLength).trim();
			messageLength = Integer.parseInt(messageLengthStr);
			System.out.println(this.getName() + ": Parsed messageLength = " + messageLength);

			// Change the buffer so that the return port is set to outboundNode.replyPort
			// The return port digits start at pos 50 in the buffer.
			if (replyPortBytes != null)
				System.arraycopy(replyPortBytes, 0, buffer, kSNHeaderReplyPortPosition, replyPortBytes.length);

			// Write the header to the outbound socket
			try
			{
				out.write(buffer, 0, totalBytesRead);
				System.out.println(this.getName() + ": Wrote " + totalBytesRead + " bytes to outbound socket.");
			}
			catch (IOException e) {
				stopped.set(true);
				System.out.println(this.getName() + ": Failed writing outbound socket.");
			}
		}

		// Now read/write the remainder of the message
		int bytesLeftToReceive = messageLength - kSNHeaderLength;
		while (!stopped.get() && (bytesLeftToReceive > 0))
		{
			// Read as much of the message as our buffer will hold
			int bytesToRead = Math.min(bytesLeftToReceive, buffer.length);
			totalBytesRead = 0;
			try
			{
				while (totalBytesRead < bytesToRead)
				{
					int bytesRead = in.read(buffer, 0, bytesToRead);
					if (bytesRead == -1)
					{
						stopped.set(true);
						break;
					}
					System.out.println(this.getName() + ": Read " + bytesRead + " bytes from inbound socket.");
					totalBytesRead += bytesRead;
				}
			}
			catch (IOException e) {
				stopped.set(true);
				System.out.println(this.getName() + ": Failed reading inbound socket.");
			}

			// Write the buffer
			if (!stopped.get() && (totalBytesRead > 0))
			{
				try
				{
					out.write(buffer, 0, totalBytesRead);
					System.out.println(this.getName() + ": Wrote " + totalBytesRead + " bytes to outbound socket.");
				}
				catch (IOException e) {
					stopped.set(true);
					System.out.println(this.getName() + ": Failed writing outbound socket.");
				}
			}

			bytesLeftToReceive -= totalBytesRead;
		}
	}
}
