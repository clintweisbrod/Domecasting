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
	
	public void notifyHostsOfAvailableDomecasts() throws IOException
	{
		ArrayList<String> availableDomecasts = getAvailableDomecasts();
		for (TCPConnectionHandlerThread aThread : connectionHandlerThreads)
		{
			ServerSideConnectionHandlerThread theThread = (ServerSideConnectionHandlerThread)aThread;
			byte theClientType = theThread.getClientType();
			if (theClientType == CommUtils.kHostID)
				theThread.sendHostAvailableDomecasts(availableDomecasts);
		}
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
			{
				String domecastID = theThread.getDomecastID();
				if (domecastID != null)
					result.add(domecastID);
			}
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
	
	public void threadDying(TCPConnectionHandlerThread thread)
	{
		super.threadDying(thread);
		
		try {
			sendStatusToThreads();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void sendStatusToThreads() throws IOException
	{
		for (TCPConnectionHandlerThread aThread1 : connectionHandlerThreads)
		{
			String status = null;
			
			ServerSideConnectionHandlerThread theThread1 = (ServerSideConnectionHandlerThread)aThread1;
			String theDomecastID = theThread1.getDomecastID();
			
			if ((theThread1.getClientType() == CommUtils.kPresenterID))
			{
				// Count how many host connection with domecastID equal to theDomecastID
				int numHostsListening = 0;
				int numHostsPaused = 0;
				for (TCPConnectionHandlerThread aThread2 : connectionHandlerThreads)
				{
					if (aThread2 == aThread1)
						continue;

					ServerSideConnectionHandlerThread theThread2 = (ServerSideConnectionHandlerThread)aThread2;
					if (theDomecastID.equals(theThread2.getDomecastID()))
					{
						if (theThread2.isHostListening())
							numHostsListening++;
						else
							numHostsPaused++;
					}
				}
				
				// Build status text message
				status = numHostsListening + " hosts are listening. " + numHostsPaused + " hosts are paused.";
			}
			else
			{
				// Determine if any presenters are available
				ArrayList<String> domecasts = getAvailableDomecasts();
				
				if (domecasts.isEmpty())
					status = "No available domecasts.";
				else 
				{
					if (theDomecastID == null)
						status = "Domecast(s) available.";
					else
					{
						if (domecasts.contains(theDomecastID))
						{
							if (theThread1.isHostListening())
								status = "Listening to domecast.";
							else
								status = "Ignoring domecast.";
						}
					}
				}
			}
			
			// Send the status
			theThread1.sendText(CommUtils.kStatusText, status);
		}
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
