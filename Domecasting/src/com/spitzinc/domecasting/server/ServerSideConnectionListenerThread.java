package com.spitzinc.domecasting.server;

import java.io.IOException;
import java.net.Socket;

import com.spitzinc.domecasting.CommUtils;
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

		byte clientType = inThread.getClientType();
		if (clientType == CommUtils.kHostID)
		{
			// If we're a "host", we're looking for another thread whose hostIDToControl matches this thread's hostID.
			String hostID = inThread.getHostID();
			for (TCPConnectionHandlerThread aThread : connectionHandlerThreads)
			{
				if (aThread != inThread)
				{
					ServerSideConnectionHandlerThread otherThread = (ServerSideConnectionHandlerThread)aThread;
					String hostIDToControl = otherThread.getHostIDToControl();
					byte otherClientType = otherThread.getClientType();
					if ((otherClientType != clientType) && hostID.equals(hostIDToControl))
					{
						result = otherThread;
						break;
					}
				}
			}
		}
		else
		{
			// If we're a "presenter", we're looking for another thread whose hostID matches this thread's hostIDToControl.
			String hostIDToControl = inThread.getHostIDToControl();
			for (TCPConnectionHandlerThread aThread : connectionHandlerThreads)
			{
				if (aThread != inThread)
				{
					ServerSideConnectionHandlerThread otherThread = (ServerSideConnectionHandlerThread)aThread;
					String hostID = otherThread.getHostID();
					byte otherClientType = otherThread.getClientType();
					if ((otherClientType != clientType) && hostIDToControl.equals(hostID))
					{
						result = otherThread;
						break;
					}
				}
			}
		}

		return result;
	}
}
