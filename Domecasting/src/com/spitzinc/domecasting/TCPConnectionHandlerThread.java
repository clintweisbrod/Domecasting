package com.spitzinc.domecasting;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicBoolean;

public class TCPConnectionHandlerThread extends Thread
{
	public static final int kSecurityCodeLength = 20;
	
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
	
	public Socket connectToHost(String hostName, int port)
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
			System.out.println(this.getName() + ": Unknown host: " + hostName);
		}
		catch (SocketTimeoutException e) {
			System.out.println(this.getName() + ": Connect timeout.");
		}
		catch (IOException e) {
			System.out.println(this.getName() + ": Connect failed.");
		}
		
		return result;
	}
	
	public boolean readInputStream(InputStream is, byte[] buffer, int offset, int len)
	{
		int bytesLeftToRead = len;
		int totalBytesRead = 0;
		try
		{
			while (bytesLeftToRead > 0)
			{
				int bytesRead = is.read(buffer, offset + totalBytesRead, bytesLeftToRead);
				if (bytesRead == -1)
					break;
				System.out.println(this.getName() + ": Read " + bytesRead + " bytes from socket.");
				bytesLeftToRead -= bytesRead;
				totalBytesRead += bytesRead;
			}
			
		} catch (IOException e) {
			System.out.println(this.getName() + ": IOException reading InputStream.");
			e.printStackTrace();
		} catch (IndexOutOfBoundsException e) {
			System.out.println(this.getName() + ": IndexOutOfBoundsException reading InputStream" +
					". buffer.length=" + buffer.length +
					", offset=" + offset +
					", len=" + len +
					", totalBytesRead=" + totalBytesRead +
					", bytesLeftToRead=" + bytesLeftToRead +
					".");
			e.printStackTrace();
		}
		
		return (bytesLeftToRead == 0);
	}
	
	public boolean writeOutputStream(OutputStream os, byte[] buffer, int offset, int len)
	{
		boolean result = true;
		
		try
		{
			os.write(buffer, offset, len);
			System.out.println(this.getName() + ": Wrote " + len + " bytes to socket.");
		}
		catch (IOException e) {
			result = false;
			System.out.println(this.getName() + ": Failed writing outbound socket.");
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
