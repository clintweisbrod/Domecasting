package com.spitzinc.domecasting.client;

import java.io.IOException;

public class SNTCPPassThruServer
{
	private TCPConnectionListenerThread sendListenerThread;
	private TCPConnectionListenerThread recvListenerThread;
	
	public SNTCPPassThruServer(int sendListenerPort, int recvListenerPort) throws IOException
	{
		sendListenerThread = new TCPConnectionListenerThread(sendListenerPort, new TCPNode("localhost", 56897, recvListenerPort));
		recvListenerThread = new TCPConnectionListenerThread(recvListenerPort, new TCPNode("localhost", 56896));
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
