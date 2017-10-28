package com.spitzinc.domecasting;

import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public class TCPConnectionHandlerThread extends Thread
{
	protected AtomicBoolean stopped;
	protected Socket inboundSocket;
	protected TCPConnectionListenerThread owner;
	
	public TCPConnectionHandlerThread(TCPConnectionListenerThread owner, Socket inboundSocket)
	{
		this.owner = owner;
		this.inboundSocket = inboundSocket;
	}
	
	public boolean getStopped() {
		return stopped.get();
	}
	
	public void setStopped() {
		stopped.set(true);
	}

}
