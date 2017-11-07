package com.spitzinc.domecasting.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.text.ParseException;

import com.spitzinc.domecasting.ClientHeader;
import com.spitzinc.domecasting.TCPConnectionHandlerThread;
import com.spitzinc.domecasting.TCPConnectionListenerThread;

public class ServerSideConnectionHandlerThread extends TCPConnectionHandlerThread
{
	protected InputStream in;
	protected OutputStream out;
	protected String presentationID;
	protected byte clientType;
	protected boolean readyToCast;
	
	public ServerSideConnectionHandlerThread(TCPConnectionListenerThread owner, Socket inboundSocket)
	{
		super(owner, inboundSocket);
		
		this.readyToCast = false;
	}
	
	private void beginHandlingClientCommands()
	{
		// This is the "main loop" of server connection.
		byte[] hdrBuffer = new byte[ClientHeader.kHdrByteCount];
		ClientHeader hdr = new ClientHeader();
		while (!stopped.get())
		{
			// Read and parse the header
			if (!readInputStream(in, hdrBuffer, 0, ClientHeader.kHdrByteCount))
				break;
			if (!hdr.parseHeaderBuffer(hdrBuffer))
				break;
			
			// Now look at hdr contents to decide what to do.
			if (hdr.messageType.equals(ClientHeader.kINFO))
				handleINFO(hdr);
		}
	}
	
	private void handleINFO(ClientHeader hdr)
	{
		byte[] infoBytes = new byte[hdr.messageLen];
		if (readInputStream(in, infoBytes, 0, infoBytes.length))
		{
			// All INFO messages are of the form variable=value.
			String msg = new String(infoBytes);
			System.out.println(this.getName() + ": Received: " + msg);
			String[] list = msg.split("=");
			if (list[0].equals("PresentationID"))
				presentationID = list[1];
			else if (list[0].equals("ClientType"))
				clientType = (byte)list[1].charAt(0);
			else if (list[0].equals("ReadyToCast"))
				readyToCast = Boolean.getBoolean(list[1]);
		}
	}
	
	public void run()
	{
		// When a domecast client connects to a domecast server, the agreed upon protocol
		// is that the client will send:
		// - A 20-character security code that will change everyday. If the code is
		//   incorrect, the connection will be refused by this server.
		// - One additional character that should be either a 'H' or 'P' to indicate the
		//   the client is a host or presenter, respectively.
		try {
			in = socket.getInputStream();
			out = socket.getOutputStream();
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}

		// Allocate byte buffer to handle initial handshake communication
		byte[] buffer = new byte[kSecurityCodeLength];		
		try
		{
			// Read off kSecurityCodeLength bytes. This is the security code.
			if (!readInputStream(in, buffer, 0, kSecurityCodeLength))
				throw new ParseException("Unable to read security code", 0);
			
			// Verify the sent security code matches what we expect
			String securityCode = new String(buffer, 0, TCPConnectionHandlerThread.kSecurityCodeLength);
			String expectedSecurityCode = TCPConnectionHandlerThread.getDailySecurityCode();
			if (!securityCode.equals(expectedSecurityCode))
				throw new ParseException("Incorrect security code sent by client.", 0);
			
			// We can now begin negotiating the client connection
			beginHandlingClientCommands();
		}
		catch (ParseException e) {
			System.out.println(this.getName() + ": " + e.getMessage());
		}

		// Close stream
		System.out.println(this.getName() + ": Shutting down connection stream.");
		try
		{
			if (in != null)
				in.close();
		}
		catch (IOException e) {
		}
		
		// Close socket
		System.out.println(this.getName() + ": Closing socket.");
		try
		{
			if (socket != null)
				socket.close();
		}
		catch (IOException e) {
		}
		
		// Notify owner this thread is dying.
		owner.threadDying(this);

		System.out.println(this.getName() + ": Exiting thread.");
	}
}
