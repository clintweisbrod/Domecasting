package com.spitzinc.domecasting.client;

import java.io.IOException;

public class SNTCPPassThruServer
{
	private ClientSideConnectionListenerThread sendListenerThread;
	private ClientSideConnectionListenerThread recvListenerThread;

	public SNTCPPassThruServer(int sendListenerPort, int recvListenerPort) throws IOException
	{
		sendListenerThread = new ClientSideConnectionListenerThread(sendListenerPort, new TCPNode("localhost", 56897, recvListenerPort));
		recvListenerThread = new ClientSideConnectionListenerThread(recvListenerPort, new TCPNode("localhost", 56896));
	}

	public void start()
	{
		// Launch the listener threads
		sendListenerThread.start();
		recvListenerThread.start();
	}

	public void stop()
	{
		sendListenerThread.interrupt();
		recvListenerThread.interrupt();
	}
}
