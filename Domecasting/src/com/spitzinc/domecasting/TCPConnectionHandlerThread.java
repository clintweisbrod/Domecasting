package com.spitzinc.domecasting;

import java.io.IOException;
import java.net.Socket;

public class TCPConnectionHandlerThread extends BasicProcessorThread
{
	protected Socket socket;
	
	public TCPConnectionHandlerThread(Socket socket)
	{
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
