package com.spitzinc.domecasting.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.spitzinc.domecasting.ClientHeader;
import com.spitzinc.domecasting.TCPConnectionHandlerThread;

public class ServerConnectionWriteThread extends TCPConnectionHandlerThread
{
	protected String hostName;
	protected int port;
	
	protected OutputStream out;
	protected ClientHeader hdr;
	protected byte[] hdrBuffer;
	
	public ServerConnectionWriteThread(String hostName, int port, ClientConnectionType clientType)
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
		// Allocate byte buffer for security code
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
	
	public void sendPresentationID(String presentationID)
	{
		System.out.println("sendPresentationID");
	}
	
	protected void handleCommunication()
	{
		// When we get here, we've established a connection with the server.
		// This thread will negotiate sending all outbound comm to the server.
		// But we also need to receive inbound comm from the server. As Ethernet
		// is a full-duplex networking technology, we can have a second thread happily
		// negotiating the receipt of inbound data on the same socket without any need
		// for thread synchronization. Very sweet! We therefore launch that thread here.
		
		writeHeader(12345, "SNPF", "SNRB", "COMM");
		
		// Loop here until the domecast is done
//		while (!stopped.get())
//		{
//			
//		}
	}
	
	public void run()
	{
		// We start by periodically attempting to connect to the server
		while (!stopped.get() && (socket == null))
		{
			socket = connectToHost(hostName, port);
			if (socket == null)
			{
				// Wait a few seconds and then try again
				final int kServerConnectionRetryIntervalSeconds = 3;
				try {
					Thread.sleep(kServerConnectionRetryIntervalSeconds * 1000);
				} catch (InterruptedException e) {
				}
			}
		}
		
		if (!stopped.get() && (socket != null))
		{
			// If we get here, we've established a connection with the server
			try	{
				out = socket.getOutputStream();
			}
			catch (IOException e) {
				e.printStackTrace();
			}
			
			if (out != null)
			{
				try {
					// Write security code
					if (!writeSecurityCode())
						throw new IOException("Failed to write security code.");
				
					// Write 'H' or 'P' for client type
					byte[] clientTypeBuffer = new byte[1];
					clientTypeBuffer[0] = (clientType == ClientConnectionType.HOST) ? kHostID : kPresenterID;
					if (!writeOutputStream(out, clientTypeBuffer, 0, 1))
						throw new IOException("Failed to write client type.");
					
					// Now begin handling communication with server
					handleCommunication();

				} catch (IOException e) {
					e.printStackTrace();
				}

				// Begin shutting down
				try {
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
