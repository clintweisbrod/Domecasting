package com.spitzinc.domecasting.client;

import java.io.IOException;
import java.net.Socket;

import com.spitzinc.domecasting.TCPConnectionListenerThread;

public class ClientSideConnectionListenerThread extends TCPConnectionListenerThread
{
	protected TCPNode outboundNode;
	boolean outgoingToRB;
	SNTCPPassThruThread siblingThread;
	
	public ClientSideConnectionListenerThread(int inboundPort, TCPNode outBoundNode, boolean outgoingToRB,
											  SNTCPPassThruThread siblingThread, int maxConnections) throws IOException
	{
		super(inboundPort, maxConnections);
		
		this.outboundNode = outBoundNode;
		this.outgoingToRB = outgoingToRB;
		this.siblingThread = siblingThread;
	}

	@Override
	protected void handleSocketConnection(Socket clientSocket)
	{
		// Create a new thread to handle connection
		SNTCPPassThruThread thread = new SNTCPPassThruThread(this, clientSocket, outboundNode, outgoingToRB);
		
		// Associate the two threads so that when one thread is stopped, the sibling will also.
		if (siblingThread != null)
		{
			thread.setSiblingThread(siblingThread);
			siblingThread.setSiblingThread(thread);
		}
		
		// Add the new thread to the collection
		connectionHandlerThreads.add(thread);
		
		// Start the thread running
		thread.start();
	}
}
