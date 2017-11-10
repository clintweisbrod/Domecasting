package com.spitzinc.domecasting;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.channels.ClosedByInterruptException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ServerSocketFactory;

public abstract class TCPConnectionListenerThread extends Thread
{	
	abstract protected void handleSocketConnection(Socket clientSocket);
	
	protected AtomicBoolean stopped;
	protected int maxConnections;
	protected ServerSocket serverSocket;
	protected ArrayList<TCPConnectionHandlerThread> connectionHandlerThreads;
	
	public TCPConnectionListenerThread(int inPortToListenOn, int maxConnections) throws IOException
	{
		this.maxConnections = maxConnections;

		// Create thread pool
		this.connectionHandlerThreads = new ArrayList<TCPConnectionHandlerThread>();
						
		serverSocket = ServerSocketFactory.getDefault().createServerSocket(inPortToListenOn, 10);
				
		this.setName(this.getClass().getSimpleName() + "_port" + inPortToListenOn);
		this.stopped = new AtomicBoolean(false);
	}
	
	public void setStopped() {
		stopped.set(true);
	}
	
	public boolean getStopped() {
		return stopped.get();
	}
	
	/**
	 * This method should be called to terminate this thread.
	 */
	public void interrupt()
	{
		setStopped();
		
		// Close the socket
		if (serverSocket != null)
		{
			try {
				serverSocket.close();
				serverSocket = null;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		super.interrupt();
		
		// Wait here for thread to die gracefully
		try {
			join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		// Stop any threads that may still be running
		for (TCPConnectionHandlerThread thread : connectionHandlerThreads)
			thread.interrupt();
		
		// Clear the list of threads
		connectionHandlerThreads.clear();
	}
	
	public void threadDying(TCPConnectionHandlerThread thread) {
		connectionHandlerThreads.remove(thread);
	}
	
	public void run()
	{
		if (serverSocket != null)
		{
			while (!stopped.get())
			{
				Socket clientSocket = null;
				try
				{
					// Wait for connection from client
					System.out.println(this.getName() + ": Waiting for inbound connection request.");
					clientSocket = serverSocket.accept();
					System.out.println(this.getName() + ": Inbound connection accepted.");
				}
				catch (SocketException e)
				{
					// This exception will be triggered when our overridden interrupt() method is called.  
					// Time to shutdown.
					break;
				}
				catch (ClosedByInterruptException e)
				{
					// interrupt() has been called on this thread. Time to shutdown.
					break;
				}
				catch (IOException e)
				{
					System.out.println(this.getName() + ": ServerSocket.accept() failed.");
					e.printStackTrace();
				}
				
				if (clientSocket != null)
				{
					if (connectionHandlerThreads.size() < maxConnections)
						handleSocketConnection(clientSocket);
					else
						System.out.println(this.getName() + ": Maximum of " + maxConnections + " connections reached.");
				}
			}
		}
		
		System.out.println(this.getName() + ": Exiting thread.");
	}
}
