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
	public static final int kSecurityCodeLength = 20;
	public static final byte kHostID = 'H';
	public static final byte kPresenterID = 'P';
	
//	public enum ClientConnectionType {HOST, PRESENTER};

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
	
	/**
	 * Generates a unique but predictable 10-digit security code that changes every day.
	 */
	public static String getDailySecurityCode()
	{
		String result = null;
		long currentUTCMilliseconds = System.currentTimeMillis();
		long currentUTCDays = currentUTCMilliseconds / (86400 * 1000) + 1324354657;
		long securityCode = currentUTCDays * currentUTCDays * currentUTCDays;
		result = Long.toString(securityCode);
		while (kSecurityCodeLength > result.length())
			result = result.concat(result);
		return result.substring(result.length() - kSecurityCodeLength);
	}
}
