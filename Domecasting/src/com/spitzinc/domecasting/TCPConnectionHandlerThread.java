package com.spitzinc.domecasting;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicBoolean;

public class TCPConnectionHandlerThread extends Thread
{
	protected AtomicBoolean stopped;
	protected Socket socket;
	protected TCPConnectionListenerThread owner;
	
	public TCPConnectionHandlerThread(TCPConnectionListenerThread owner, Socket socket)
	{
		this.owner = owner;
		this.socket = socket;
		
		this.setName(this.getClass().getSimpleName());
		this.stopped = new AtomicBoolean(false);
	}
	
	public boolean getStopped() {
		return stopped.get();
	}
	
	public void setStopped() {
		stopped.set(true);
	}
	
	public static Socket connectToHost(String hostName, int port, String callingThreadName)
	{
		Socket result = null;
		
		// Attempt to connect to outbound host
		try
		{
			final int kConnectionTimeoutMS = 1000;
			InetAddress addr = InetAddress.getByName(hostName);
			SocketAddress sockaddr = new InetSocketAddress(addr, port);
	
			result = new Socket();
			result.setKeepAlive(true);
			result.connect(sockaddr, kConnectionTimeoutMS);
		}
		catch (UnknownHostException e) {
			result = null;
			System.out.println(callingThreadName + ": Unknown host: " + hostName);
		}
		catch (SocketTimeoutException e) {
			result = null;
			System.out.println(callingThreadName + ": Connect timeout.");
		}
		catch (IOException e) {
			result = null;
			System.out.println(callingThreadName + ": Connect failed.");
		}
		
		return result;
	}
}
