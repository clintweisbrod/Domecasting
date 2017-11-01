package com.spitzinc.domecasting.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.spitzinc.domecasting.ClientHeader;
import com.spitzinc.domecasting.TCPConnectionHandlerThread;

public class ServerConnectionThread extends TCPConnectionHandlerThread
{
	public static final byte kHostID = 'H';
	public static final byte kPresenterID = 'P';
	
	public enum ClientConnectionType {HOST, PRESENTER};
	
	protected String hostName;
	protected int port;
	
	protected InputStream in;
	protected OutputStream out;
	protected ClientHeader hdr;
	public ClientConnectionType clientType;
	protected byte[] hdrBuffer;
	
	public ServerConnectionThread(String hostName, int port, ClientConnectionType clientType)
	{
		super(null, null);
		
		this.hostName = hostName;
		this.port = port;
		
		this.hdr = new ClientHeader();
		this.clientType = clientType;
		this.hdrBuffer = new byte[ClientHeader.kHdrByteCount];
	}
	
	protected boolean writeSecurityCode()
	{
		// Allocate byte buffer to handle comm
		byte[] buffer = new byte[TCPConnectionHandlerThread.kSecurityCodeLength];
		
		// Insert daily security code in buffer
		String securityCode = getDailySecurityCode();
		System.arraycopy(securityCode.getBytes(), 0, buffer, 0, securityCode.length());
		
		return writeOutputStream(out, buffer, 0, TCPConnectionHandlerThread.kSecurityCodeLength);
	}
	
	protected boolean writeHeader(int messageLen, String messageSource, String messageDestination, String messageType)
	{
		hdr.messageLen = messageLen;
		hdr.messageSource = messageSource;
		hdr.messageDestination = messageDestination;
		hdr.messageType = messageType;
		hdr.buildHeaderBuffer(hdrBuffer);
		return writeOutputStream(out, hdrBuffer, 0, ClientHeader.kHdrByteCount);
	}
	
	protected void handleCommunication()
	{
		// Write header
		writeHeader(12345, "SNPF", "SNRB", "COMM");
		
		// Loop here until the domecast is done
//		while (!stopped.get())
//		{
//			
//		}
	}
	
	public void run()
	{
		// We start by attempting to connect to the server
		socket = connectToHost(hostName, port);
		if (socket != null)
		{
			try
			{
				in = socket.getInputStream();
				out = socket.getOutputStream();
			}
			catch (IOException e) {
				e.printStackTrace();
			}
			
			if ((in != null) && (out != null))
			{
				try {
					// Write security code
					if (!writeSecurityCode())
						throw new IOException("Failed to write security code.");
				
					// Write 'H' or 'P' for client type
					byte[] clientTypeBuffer = new byte[1];
					clientTypeBuffer[TCPConnectionHandlerThread.kSecurityCodeLength] = (clientType == ClientConnectionType.HOST) ? kHostID : kPresenterID;
					if (!writeOutputStream(out, clientTypeBuffer, 0, 1))
						throw new IOException("Failed to write client type.");
					
					// Now begin handling communication with server
					handleCommunication();

				} catch (IOException e) {
					e.printStackTrace();
				}

				// Begin shutting down
				try {
					in.close();
					out.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
			try {
				socket.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		System.out.println(this.getName() + ": Exiting thread.");
	}
}
