package com.spitzinc.domecasting.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.spitzinc.domecasting.ClientHeader;
import com.spitzinc.domecasting.TCPConnectionHandlerThread;

public class ServerConnectionThread extends TCPConnectionHandlerThread
{
	public static final char kHostID = 'H';
	public static final char kPresenterID = 'P';
	
	public enum ClientConnectionType {HOST, PRESENTER};
	
	protected InputStream in;
	protected OutputStream out;
	protected ClientHeader hdr;
	protected byte[] hdrBuffer;
	
	public ServerConnectionThread()
	{
		super(null, null);
		
		hdr = new ClientHeader();
		hdrBuffer = new byte[ClientHeader.kHdrByteCount];
	}
	
	public boolean writeHeader(int messageLen, String messageSource, String messageDestination, String messageType)
	{
		hdr.messageLen = messageLen;
		hdr.messageSource = messageSource;
		hdr.messageDestination = messageDestination;
		hdr.messageType = messageType;
		hdr.buildHeaderBuffer(hdrBuffer);
		return writeOutputStream(out, hdrBuffer, 0, ClientHeader.kHdrByteCount);
	}
	
	public void run()
	{
		final String hostName = "localhost";
		final int port = 80;
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
				// Allocate byte buffer to handle comm
				byte[] buffer = new byte[16*1024];
				
				// Insert daily security code in buffer
				String securityCode = getDailySecurityCode();
				System.arraycopy(securityCode.getBytes(), 0, buffer, 0, securityCode.length());
				
				// Add 'H' or 'P'
				buffer[TCPConnectionHandlerThread.kSecurityCodeLength] = kHostID;
				writeOutputStream(out, buffer, 0, TCPConnectionHandlerThread.kSecurityCodeLength + 1);

				// Write header
				writeHeader(12345, "SNPF", "SNRB", "COMM");
				
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
	}
}
