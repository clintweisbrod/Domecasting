package com.spitzinc.domecasting.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.spitzinc.domecasting.TCPConnectionHandlerThread;

public class ServerConnectionThread extends TCPConnectionHandlerThread
{
	public static final char kHostID = 'H';
	public static final char kPresenterID = 'P';
	
	public enum ClientConnectionType {HOST, PRESENTER};
	
	protected InputStream in;
	protected OutputStream out;
	
	public ServerConnectionThread()
	{
		super(null, null);
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
			}
		}
	}
}
