package com.spitzinc.domecasting;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
		long currentUTCMilliseconds = System.currentTimeMillis();
		long currentUTCDays = currentUTCMilliseconds / (86400 * 1000);
		long securityCode = currentUTCDays * currentUTCDays * currentUTCDays;
		String result = Long.toString(securityCode);
		return result.substring(result.length() - 10);
	}
}
