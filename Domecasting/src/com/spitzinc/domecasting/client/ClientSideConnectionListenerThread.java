package com.spitzinc.domecasting.client;

import java.io.IOException;
import java.net.Socket;

import com.spitzinc.domecasting.TCPConnectionListenerThread;

public class ClientSideConnectionListenerThread extends TCPConnectionListenerThread
{
	protected TCPNode outboundNode;
	
	public ClientSideConnectionListenerThread(int inboundPort, TCPNode outBoundNode, int maxConnections) throws IOException
	{
		super(inboundPort, maxConnections);
		
		this.outboundNode = outBoundNode;
	}

	@Override
	protected void handleSocketConnection(Socket clientSocket)
	{
		// Launch a new thread to handle connection
		SNTCPPassThruThread thread = new SNTCPPassThruThread(this, clientSocket, outboundNode);
		connectionHandlerThreads.add(thread);
		thread.start();
	}
}
