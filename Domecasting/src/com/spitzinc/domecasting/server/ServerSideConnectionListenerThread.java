package com.spitzinc.domecasting.server;

import java.io.IOException;
import java.net.Socket;

import com.spitzinc.domecasting.TCPConnectionHandlerThread;
import com.spitzinc.domecasting.TCPConnectionListenerThread;

public class ServerSideConnectionListenerThread extends TCPConnectionListenerThread
{
	private static final int kMaxConnectionThreads = 10;	// Should be an even number
	
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
	
	public ServerSideConnectionHandlerThread findPeerConnectionThread(ServerSideConnectionHandlerThread inThread)
	{
		ServerSideConnectionHandlerThread result = null;
		
		String presentationID = inThread.getPresentationID();
		byte clientType = inThread.getClientType();
		
		for (TCPConnectionHandlerThread aThread : connectionHandlerThreads)
		{
			if (aThread != inThread)
			{
				ServerSideConnectionHandlerThread otherThread = (ServerSideConnectionHandlerThread)aThread;
				String otherPresentationID = otherThread.getPresentationID();
				byte otherClientType = otherThread.getClientType();
				if ((otherClientType != clientType) && otherPresentationID.equals(presentationID))
				{
					result = otherThread;
					break;
				}
			}
		}
		
		return result;
	}
}
