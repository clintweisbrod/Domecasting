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
	protected byte presentationMode;
	
	public ServerSideConnectionHandlerThread(TCPConnectionListenerThread owner, Socket inboundSocket)
	{
		super(owner, inboundSocket);
	}
	
	private void beginHandlingClientCommands()
	{
		// This is the "main loop" of server connection.
		byte[] hdrBuffer = new byte[ClientHeader.kHdrByteCount];
		ClientHeader hdr = new ClientHeader();
		while (!stopped.get())
		{
			if (!readInputStream(in, hdrBuffer, 0, ClientHeader.kHdrByteCount))
				break;
			if (!hdr.parseHeaderBuffer(hdrBuffer))
				break;
			
			// Now look at hdr contents to decide what to do.
			
			
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
			
			if (!readInputStream(in, buffer, 0, 1))
				throw new ParseException("Unable to read security code.", 0);
			
			presentationMode = buffer[0];
			if ((presentationMode != 'H') && (presentationMode != 'P'))
				throw new ParseException("Invalid presentation mode sent.", 0);
			
			beginHandlingClientCommands();
		}
		catch (ParseException e) {
			System.out.println(this.getName() + e.getMessage());
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
			socket.close();
		}
		catch (IOException e) {
		}
		
		// Notify owner this thread is dying.
		owner.threadDying(this);

		System.out.println(this.getName() + ": Exiting thread.");
	}
}
