package com.spitzinc.domecasting.server;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.text.ParseException;

import com.spitzinc.domecasting.TCPConnectionHandlerThread;
import com.spitzinc.domecasting.TCPConnectionListenerThread;

public class ServerSideConnectionHandlerThread extends TCPConnectionHandlerThread
{
	protected InputStream in;
	protected String presentationID;
	protected char presentationMode;
	
	public ServerSideConnectionHandlerThread(TCPConnectionListenerThread owner, Socket inboundSocket)
	{
		super(owner, inboundSocket);
	}
	
	public void run()
	{
		// When a domecast client connects to a domecast server, the agreed upon protocol
		// is that the client will send:
		// - A 20-character security code that will change everyday. If the code is
		//   incorrect, the connection will be refused by this server.
		// - The variable length (up to 32 chars) presentation ID.
		// - A tilde (~) character to delimit the presentation ID.
		// - Either a 'H' or an 'P' character to indicate host or presenter respectively.
		// For example, a host instance of the domecaster client wishing to host a 
		// presentation using security code 1234567890 and presentation ID of "MercuryRising",
		// will send the following (less the quotes):
		// "12345678901234567890MercuryRising~H"
		// This allows us to maintain a map of threads for all connections to the server
		// and ensure that we can "pair" host and presenter threads of the same
		// presentation ID.
		try {
			in = socket.getInputStream();
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}

		// Allocate byte buffer to handle initial handshake communication
		boolean headerIsValid = false;
		byte[] buffer = new byte[1024];		
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
		
			// Read off one character at a time until a tilde is found, then one more.
			int headerLen = 0;
			boolean clientInfoFound = false;
			try
			{
				final int kMaxClientHeaderChars = 32;
				for (int i = 0; i < kMaxClientHeaderChars; i++)
				{
					int bytesRead = in.read(buffer, i, 1);
					if (bytesRead == -1)
						break;
					if (buffer[i] == '~')
					{
						i++;
						bytesRead = in.read(buffer, i, 1);
						if (bytesRead == -1)
							break;
						if ((buffer[i] == 'H') || (buffer[i] == 'P'))
						{
							clientInfoFound = true;
							headerLen = i + 1;
						}
						break;
					}
				}
			} catch (IOException e) {
				throw new ParseException("IOException while reading client info.", 0);
			}
			
			if (!clientInfoFound || (headerLen <= 3))
				throw new ParseException("Unexpected client info received.", 0);
			
			presentationID = new String(buffer, 0, headerLen);
			presentationMode = presentationID.charAt(headerLen - 1);
			presentationID = presentationID.substring(0, headerLen - 2);
			
			headerIsValid = true;
		}
		catch (ParseException e) {
			System.out.println(this.getName() + e.getMessage());
		}
		
		// Are we good to go?
		if (headerIsValid)
		{
			
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
