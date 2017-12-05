package com.spitzinc.domecasting.server;

import java.io.File;
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
	
	public void notifyHostsOfAvailableAssetsFile(ServerSideConnectionHandlerThread inThread) throws IOException
	{
		// This is only called by a presenter thread so we will get list of connected hosts
		ArrayList<ServerSideConnectionHandlerThread> hosts = findPeerConnectionThreads(inThread);
		for (ServerSideConnectionHandlerThread host : hosts)
			host.sendBoolean(CommUtils.kAssetsFileAvailable, true);
	}
	
	/*
	 * Called by AssetsFileCleanupThread.
	 */
	public boolean presenterConnectionExists(String domecastID)
	{
		boolean result = false;
		
		for (TCPConnectionHandlerThread aThread : connectionHandlerThreads)
		{
			ServerSideConnectionHandlerThread theThread = (ServerSideConnectionHandlerThread)aThread;
			if (theThread.getClientType() == CommUtils.kPresenterID)
			{
				if (theThread.getDomecastID().equals(domecastID))
				{
					result = true;
					break;
				}
			}
		}
		
		return result;
	}
	
	/**
	 * Clients in host mode use a JComboBox to display all current domecasts.
	 * This method returns a list of domecastIDs owned by presenter connections. 
	 */
	public ArrayList<String> getAvailableDomecasts()
	{
		ArrayList<String> result = new ArrayList<String>();
		
		// Add all live presenter connections
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
		
		// Now enumerate the folders in the ProgramData folder and add those folder names if they
		// don't already exist in result. We do this so that hosts can download assets files without
		// the presenter connection being present.
		ServerApplication inst = (ServerApplication) ServerApplication.inst();
		String programDataPath = inst.getProgramDataPath();
		File programDataFolder = new File(programDataPath);
		String[] names = programDataFolder.list();
		for (String name : names)
		{
			File folderElement = new File(programDataPath + name);
			if (folderElement.isDirectory())
			{
				if (!result.contains(name))
					result.add(name);
			}
		}

		return result;
	}
	
	/**
	 * Given a ServerSideConnectionHandlerThread instance, returns the ServerSideConnectionHandlerThread
	 * instance with the same domecastID but opposite clientType (if one exists).
	 */
	public ArrayList<ServerSideConnectionHandlerThread> findPeerConnectionThreads(ServerSideConnectionHandlerThread inThread)
	{
		ArrayList<ServerSideConnectionHandlerThread> result = new ArrayList<ServerSideConnectionHandlerThread>();

		byte clientType = inThread.getClientType();
		String domecastID = inThread.getDomecastID();
		if (domecastID != null)
		{
			// Find the one presenter with the same domecastID
			for (TCPConnectionHandlerThread aThread : connectionHandlerThreads)
			{
				if (aThread != inThread)
				{
					ServerSideConnectionHandlerThread otherThread = (ServerSideConnectionHandlerThread)aThread;
					String otherDomecastID = otherThread.getDomecastID();
					if (otherDomecastID == null)
						continue;
					
					if (domecastID.equals(otherDomecastID))
					{
						if ((clientType == CommUtils.kHostID) && (otherThread.getClientType() == CommUtils.kPresenterID))
						{
							result.add(otherThread);
							break;
						}
						if ((clientType == CommUtils.kPresenterID) && (otherThread.getClientType() == CommUtils.kHostID))
							result.add(otherThread);
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
				if (theDomecastID == null)
					continue;

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
				StringBuffer buf = new StringBuffer();
				if (numHostsListening > 0)
				{
					if (numHostsListening == 1)
						buf.append("One host is ");
					else
						buf.append(numHostsListening + " hosts are ");
					buf.append("listening. ");
				}
				if (numHostsPaused > 0)
				{
					if (numHostsPaused == 1)
						buf.append("One host is ");
					else
						buf.append(numHostsPaused + " hosts are ");
					buf.append("paused.");
				}
				if (buf.length() == 0)
					status = "No hosts present.";
				else
					status = buf.toString();
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
							{
								if (presenterConnectionExists(theDomecastID))
									status = "Currently not listening to domecast.";
								else
									status = "Presenter currently not connected.";
							}
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
