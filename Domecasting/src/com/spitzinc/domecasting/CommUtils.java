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

public class CommUtils
{
	public static final int kSecurityCodeLength = 20;
	public static final int kCommBufferSize = 120 * 1024;	// This should be enough for all SNF files
	public static final byte kHostID = 'H';
	public static final byte kPresenterID = 'P';
	
	// Commands sent between client and server
	public static final String kIsConnected = "isConnected";
	public static final String kIsDomecastIDUnique = "isDomecastIDUnique";
	public static final String kGetAvailableDomecasts = "getAvailableDomecasts";
	public static final String kDomecastID = "domecastID";
	public static final String kClientType = "clientType";
	public static final String kIsHostListening = "isHostListening";
	public static final String kStatusText = "statusText";
	
	public static final String kNoAvailableDomecastIDs = "<none>";
	
	public static Socket connectToHost(String hostName, int port)
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
			Log.inst().info("Unknown host: " + hostName);
		}
		catch (SocketTimeoutException e) {
			result = null;
			Log.inst().info("Connect timeout.");
		}
		catch (IOException e) {
			result = null;
			Log.inst().info("Connect failed.");
		}
		
		return result;
	}
	
	public static void readInputStream(InputStream is, byte[] buffer, int offset, int len) throws IOException
	{
		int bytesLeftToRead = len;
		int totalBytesRead = 0;
		try
		{
			while (bytesLeftToRead > 0)
			{
				int bytesRead = is.read(buffer, offset + totalBytesRead, bytesLeftToRead);
				if (bytesRead == -1)
					throw new IOException("Connection lost.");
//				Log.inst().info(caller + ": Read " + bytesRead + " bytes from socket.");
				bytesLeftToRead -= bytesRead;
				totalBytesRead += bytesRead;
			}
			
		} catch (IOException e) {
			throw new IOException(e.getMessage());
		} catch (IndexOutOfBoundsException e) {
			Log.inst().error("IndexOutOfBoundsException reading InputStream" +
					". buffer.length=" + buffer.length +
					", offset=" + offset +
					", len=" + len +
					", totalBytesRead=" + totalBytesRead +
					", bytesLeftToRead=" + bytesLeftToRead +
					".");
			e.printStackTrace();
			throw new IOException(e.getMessage());
		}
	}
	
	public static void writeOutputStream(OutputStream os, byte[] buffer, int offset, int len) throws IOException
	{
		try
		{
			os.write(buffer, offset, len);
//			Log.inst().info(caller + ": Wrote " + len + " bytes to socket.");
		}
		catch (IOException | IndexOutOfBoundsException | NullPointerException e) {
			throw new IOException(e.getMessage());
		}
	}
	
	public static void writeHeader(OutputStream os, ClientHeader hdr,
								   int msgLen, String msgSrc, String msgType) throws IOException
	{
		hdr.messageLen = msgLen;
		hdr.messageSource = msgSrc;
		hdr.messageType = msgType;
		hdr.buildHeaderBuffer();
		writeOutputStream(os, hdr.bytes, 0, ClientHeader.kHdrByteCount);
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
