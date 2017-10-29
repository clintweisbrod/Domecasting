package com.spitzinc.domecasting.server;

import java.io.IOException;
import java.net.Socket;

import com.spitzinc.domecasting.TCPConnectionListenerThread;

public class ServerSideConnectionListenerThread extends TCPConnectionListenerThread
{
	private static final int kMaxConnectionThreads = 10;
	
	public ServerSideConnectionListenerThread(int port) throws IOException
	{
		super(port, kMaxConnectionThreads);
	}

	@Override
	protected void handleSocketConnection(Socket clientSocket)
	{
		// Launch a new thread to handle connection
		ServerSideConnectionHandlerThread thread = new ServerSideConnectionHandlerThread(this, clientSocket);
		connectionHandlerThreads.add(thread);
		thread.start();
	}
}
