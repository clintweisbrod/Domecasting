package com.spitzinc.domecasting;

import java.io.IOException;
import java.net.Socket;

public class TCPConnectionHandlerThread extends BasicProcessorThread
{
	protected Socket socket;
	protected TCPConnectionListenerThread owner;
	
	public TCPConnectionHandlerThread(TCPConnectionListenerThread owner, Socket socket)
	{
		this.owner = owner;
		this.socket = socket;
	}
	
	public void interrupt()
	{
		setStopped();

		if (socket != null)
			try {
				Log.inst().debug("Closing socket.");
				socket.close();
			} catch (IOException e) {
			}
		
		super.interrupt();
	}
}
