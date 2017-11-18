package com.spitzinc.domecasting.client;

import java.io.IOException;

public class SNTCPPassThruServer
{
	private ClientSideConnectionListenerThread sendListenerThread;
	private ClientSideConnectionListenerThread recvListenerThread;

	public SNTCPPassThruServer(int sendListenerPort, int recvListenerPort, int maxClientConnections) throws IOException
	{
		ClientApplication inst = (ClientApplication) ClientApplication.inst();
		
		sendListenerThread = new ClientSideConnectionListenerThread(sendListenerPort,
																	new TCPNode("localhost", inst.rbPrefs_DomeServer_TCPPort, recvListenerPort),
																	maxClientConnections);
		recvListenerThread = new ClientSideConnectionListenerThread(recvListenerPort,
																	new TCPNode("localhost", inst.pfPrefs_DomeServer_TCPReplyPort),
																	maxClientConnections);
	}

	public void start()
	{
		// Launch the listener threads
		sendListenerThread.start();
//		recvListenerThread.start();
	}

	public void stop()
	{
		sendListenerThread.interrupt();
//		recvListenerThread.interrupt();
	}
}
