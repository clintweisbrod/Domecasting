package com.spitzinc.domecasting.server;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;

import com.spitzinc.domecasting.CommUtils;
import com.spitzinc.domecasting.TCPConnectionHandlerThread;
import com.spitzinc.domecasting.TCPConnectionListenerThread;

public class ServerSideConnectionListenerThread extends TCPConnectionListenerThread
{
	public ServerSideConnectionListenerThread(int port, int maxConcurrentSessions) throws IOException
	{
		super(port, maxConcurrentSessions * 2);
	}

	@Override
	protected void handleSocketConnection(Socket clientSocket)
	{
		// Launch a new thread to handle connection
		ServerSideConnectionHandlerThread thread = new ServerSideConnectionHandlerThread(this, clientSocket);
		connectionHandlerThreads.add(thread);
		thread.start();
	}
	
	public ArrayList<String> getAvailableDomecasts()
	{
		ArrayList<String> result = new ArrayList<String>();
		
		for (TCPConnectionHandlerThread aThread : connectionHandlerThreads)
		{
			ServerSideConnectionHandlerThread theThread = (ServerSideConnectionHandlerThread)aThread;
			byte theClientType = theThread.getClientType();
			if (theClientType == CommUtils.kPresenterID)
				result.add(theThread.getDomecastID());
		}

		return result;
	}
	
	public ServerSideConnectionHandlerThread findPeerConnectionThread(ServerSideConnectionHandlerThread inThread)
	{
		ServerSideConnectionHandlerThread result = null;

		byte clientType = inThread.getClientType();
		String domecastID = inThread.getDomecastID();
		for (TCPConnectionHandlerThread aThread : connectionHandlerThreads)
		{
			if (aThread != inThread)
			{
				ServerSideConnectionHandlerThread otherThread = (ServerSideConnectionHandlerThread)aThread;
				String otherDomecastID = otherThread.getDomecastID();
				byte otherClientType = otherThread.getClientType();
				if ((otherClientType != clientType) && domecastID.equals(otherDomecastID))
				{
					result = otherThread;
					break;
				}
			}
		}

		return result;
	}
}
