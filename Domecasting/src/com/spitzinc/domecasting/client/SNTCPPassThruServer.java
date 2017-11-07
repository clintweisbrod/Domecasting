package com.spitzinc.domecasting.client;

import java.io.IOException;

public class SNTCPPassThruServer
{
	private ClientSideConnectionListenerThread sendListenerThread;
	private ClientSideConnectionListenerThread recvListenerThread;

	public SNTCPPassThruServer(int sendListenerPort, int recvListenerPort) throws IOException
	{
		// TODO: Add UI for this value?
		final int kRBPrefs_DomeServer_TCPPort = 56897;	// For typical two-machine setup, this should be the usual 56895.
														// For testing on a single machine, needs to be 56897.
		final int kPFPrefs_DomeServer_TCPReplyPort = 56896;
		sendListenerThread = new ClientSideConnectionListenerThread(sendListenerPort, new TCPNode("localhost", kRBPrefs_DomeServer_TCPPort, recvListenerPort));
		recvListenerThread = new ClientSideConnectionListenerThread(recvListenerPort, new TCPNode("localhost", kPFPrefs_DomeServer_TCPReplyPort));
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
