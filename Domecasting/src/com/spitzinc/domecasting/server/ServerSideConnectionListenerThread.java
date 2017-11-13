package com.spitzinc.domecasting.server;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;

import com.spitzinc.domecasting.CommUtils;
import com.spitzinc.domecasting.TCPConnectionHandlerThread;
import com.spitzinc.domecasting.TCPConnectionListenerThread;

public class ServerSideConnectionListenerThread extends TCPConnectionListenerThread
{
	public ServerSideConnectionListenerThread(int port, int maxConcurrentSessions) throws IOException
	{
		super(port, maxConcurrentSessions * 2);
	}

	@Override
	protected void handleSocketConnection(Socket clientSocket)
	{
		// Launch a new thread to handle connection
		ServerSideConnectionHandlerThread thread = new ServerSideConnectionHandlerThread(this, clientSocket);
		connectionHandlerThreads.add(thread);
		thread.start();
	}
	
	/**
	 * Clients in host mode use a JComboBox to display all current domecasts.
	 * This method returns a list of domecastIDs owned by presenter connections. 
	 */
	public ArrayList<String> getAvailableDomecasts()
	{
		ArrayList<String> result = new ArrayList<String>();
		
		for (TCPConnectionHandlerThread aThread : connectionHandlerThreads)
		{
			ServerSideConnectionHandlerThread theThread = (ServerSideConnectionHandlerThread)aThread;
			byte theClientType = theThread.getClientType();
			if (theClientType == CommUtils.kPresenterID)
				result.add(theThread.getDomecastID());
		}

		return result;
	}
	
	/**
	 * Given a ServerSideConnectionHandlerThread instance, returns the ServerSideConnectionHandlerThread
	 * instance with the same domecastID but opposite clientType (if one exists).
	 */
	public ServerSideConnectionHandlerThread findPeerConnectionThread(ServerSideConnectionHandlerThread inThread)
	{
		ServerSideConnectionHandlerThread result = null;

		byte clientType = inThread.getClientType();
		String domecastID = inThread.getDomecastID();
		if (domecastID != null)
		{
			for (TCPConnectionHandlerThread aThread : connectionHandlerThreads)
			{
				if (aThread != inThread)
				{
					ServerSideConnectionHandlerThread otherThread = (ServerSideConnectionHandlerThread)aThread;
					String otherDomecastID = otherThread.getDomecastID();
					if (otherDomecastID == null)
						continue;
					
					byte otherClientType = otherThread.getClientType();
					if ((otherClientType != clientType) && domecastID.equals(otherDomecastID))
					{
						result = otherThread;
						break;
					}
				}
			}
		}

		return result;
	}
	
	/**
	 * Clients in presenter mode must submit a unique domecastID. This method tests the supplied
	 * domecastID for uniqueness.
	 */
	public boolean isDomecastIDUnique(String domecastID)
	{
		boolean result = true;
		
		for (TCPConnectionHandlerThread aThread : connectionHandlerThreads)
		{
			ServerSideConnectionHandlerThread theThread = (ServerSideConnectionHandlerThread)aThread;
			if ((theThread.getClientType() == CommUtils.kPresenterID) && (domecastID.equals(theThread.getDomecastID())))
			{
				result = false;
				break;
			}
		}
		
		return result;
	}
	
	/**
	 * Displays vitals of each connection in the server application window.
	 */
	public void displayStatus(ServerApplication theApp)
	{
		StringBuffer buf = new StringBuffer();
		for (TCPConnectionHandlerThread aThread : connectionHandlerThreads)
		{
			ServerSideConnectionHandlerThread theThread = (ServerSideConnectionHandlerThread)aThread;
			buf.append(theThread.toString());
			buf.append('\n');
		}
		
		theApp.appFrame.textArea.setText(buf.toString());
	}
}
