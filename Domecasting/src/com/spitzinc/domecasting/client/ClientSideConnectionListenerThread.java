package com.spitzinc.domecasting.client;

import java.io.IOException;
import java.net.Socket;

import com.spitzinc.domecasting.TCPConnectionListenerThread;

public class ClientSideConnectionListenerThread extends TCPConnectionListenerThread
{
	protected TCPNode outboundNode;
	
	public ClientSideConnectionListenerThread(int inboundPort, TCPNode outBoundNode) throws IOException
	{
		super(inboundPort);
		
		this.outboundNode = outBoundNode;
	}

	@Override
	protected void handleSocketConnection(Socket clientSocket)
	{
		// Launch a new thread to handle connection
		TCPPassThruThread thread = new TCPPassThruThread(this, clientSocket, outboundNode);
		connectionHandlerThreads.add(thread);
		thread.start();
	}
}
