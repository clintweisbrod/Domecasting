package com.spitzinc.domecasting.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import com.spitzinc.domecasting.TCPConnectionHandlerThread;

public class SNTCPPassThruServer
{
	private ClientSideConnectionListenerThread sendListenerThread;
	private ClientSideConnectionListenerThread recvListenerThread;
	private ConcurrentHashMap<String, Integer> replyPortMap;

	public SNTCPPassThruServer(int sendListenerPort, int recvListenerPort, int maxClientConnections) throws IOException
	{
		ClientApplication inst = (ClientApplication) ClientApplication.inst();
		
		replyPortMap = new ConcurrentHashMap<String, Integer>();
		
		sendListenerThread = new ClientSideConnectionListenerThread(sendListenerPort,
																	new TCPNode(inst.renderboxHostname, inst.rbPrefs_DomeServer_TCPPort, recvListenerPort),
																	maxClientConnections);
		recvListenerThread = new ClientSideConnectionListenerThread(recvListenerPort,
																	new TCPNode("localhost", 0),	// 0 because this gets set after first SN header is read
																	maxClientConnections);
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
	
	public void mapClientAppNameToOutgoingPort(String clientAppName, Integer port)
	{
		replyPortMap.putIfAbsent(clientAppName, port);
	}
	
	public int getOutgoingPortFromClientAppName(String clientAppName)
	{
		int result = 0;

		Integer value = replyPortMap.get(clientAppName);
		if (value != null)
			result = value.intValue();

		return result;
	}
	
	/*
	 * Whenever ClientApplication.isAnyHostListening is modified, this method must be called so that
	 * the SNTCPPassThruThread instances are aware of the change.
	 */
	public void notifyThreadsOfCommModeChange()
	{
		// Get ArrayList of all SNTCPPassThruThread contained in connectionHandlerThreads of both
		// sendListenerThread and recvListenerThread.
		ArrayList<TCPConnectionHandlerThread> threadList = new ArrayList<TCPConnectionHandlerThread>();
		threadList.addAll(sendListenerThread.connectionHandlerThreads);
		threadList.addAll(recvListenerThread.connectionHandlerThreads);
		
		for (TCPConnectionHandlerThread thread : threadList)
		{
			SNTCPPassThruThread theThread = (SNTCPPassThruThread)thread;
			theThread.setCommModeChanged();
		}
	}
}
