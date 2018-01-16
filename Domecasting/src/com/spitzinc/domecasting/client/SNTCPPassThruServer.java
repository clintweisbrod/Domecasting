package com.spitzinc.domecasting.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import com.spitzinc.domecasting.TCPConnectionHandlerThread;

public class SNTCPPassThruServer
{
	private ClientSideConnectionListenerThread sendListenerThread;
	private ConcurrentHashMap<Integer, ClientSideConnectionListenerThread> recvListenerThreads;
	private ConcurrentHashMap<String, Integer> replyPortMap;

	public SNTCPPassThruServer(int sendListenerPort, int maxConnections) throws IOException
	{
		ClientApplication inst = (ClientApplication) ClientApplication.inst();
		
		replyPortMap = new ConcurrentHashMap<String, Integer>();
		
		sendListenerThread = new ClientSideConnectionListenerThread(sendListenerPort,
																	new TCPNode(inst.renderboxHostname, inst.rbPrefs_DomeServer_TCPPort),
																	true,	// Outgoing connection to RB
																	null,
																	maxConnections);
		
		recvListenerThreads = new ConcurrentHashMap<Integer, ClientSideConnectionListenerThread>();
	}

	public void start()
	{
		// Launch the listener threads
		sendListenerThread.start();
	}

	public void stop()
	{
		sendListenerThread.interrupt();
		
		for (ClientSideConnectionListenerThread thread : recvListenerThreads.values())
			thread.interrupt();
		recvListenerThreads.clear();
	}
	
	public void addRecvListenerThread(int recvListenerPort, SNTCPPassThruThread siblingThread) throws IOException
	{
		if (!recvListenerThreads.containsKey(recvListenerPort))
		{
			ClientSideConnectionListenerThread newRecvThread = new ClientSideConnectionListenerThread(recvListenerPort,
																									  new TCPNode("localhost", 0),	// 0 because this gets set after first SN header is read);
																									  false,	// Incoming connection from RB
																									  siblingThread,
																									  1);		// We only need one connection accepted.
			recvListenerThreads.put(recvListenerPort, newRecvThread);
			newRecvThread.start();
		}
	}
	
	/*
	 * When a SN client connects, the SN headers it sends will include the TCP reply port that the client
	 * is listening on for subsequent RB responses. The (outgoing) SNTCPPassThruThread that handles the
	 * client connection parses this port from the header, but a different (incoming) SNTCPPassThruThread
	 * instance handles the communication from RB back to the client. This method is called from the outgoing
	 * SNTCPPassThruThread instance. 
	 */
	public void mapClientAppNameToOutgoingPort(String clientAppName, Integer port)
	{
		replyPortMap.putIfAbsent(clientAppName, port);
	}
	
	/*
	 * This method is called from an incoming instance of SNTCPPassThruThread just before attempting to establish
	 * the TCP connection to the SN client. 
	 */
	public int getOutgoingPortFromClientAppName(String clientAppName)
	{
		int result = 0;

		if (clientAppName != null)
		{
			Integer value = replyPortMap.get(clientAppName);
			if (value != null)
				result = value.intValue();
		}

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
		for (ClientSideConnectionListenerThread thread : recvListenerThreads.values())
			threadList.addAll(thread.connectionHandlerThreads);
		
		for (TCPConnectionHandlerThread thread : threadList)
		{
			SNTCPPassThruThread theThread = (SNTCPPassThruThread)thread;
			theThread.setCommModeChanged();
		}
	}
}
