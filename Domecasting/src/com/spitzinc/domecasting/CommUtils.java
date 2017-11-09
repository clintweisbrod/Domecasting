package com.spitzinc.domecasting;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class CommUtils
{
	public static final int kSecurityCodeLength = 20;
	public static final byte kHostID = 'H';
	public static final byte kPresenterID = 'P';
	
	public static void readInputStream(InputStream is, byte[] buffer, int offset, int len, String caller) throws IOException
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
				System.out.println(caller + ": Read " + bytesRead + " bytes from socket.");
				bytesLeftToRead -= bytesRead;
				totalBytesRead += bytesRead;
			}
			
		} catch (IOException e) {
			throw new IOException(caller + ": " + e.getMessage());
		} catch (IndexOutOfBoundsException e) {
			System.out.println(caller + ": IndexOutOfBoundsException reading InputStream" +
					". buffer.length=" + buffer.length +
					", offset=" + offset +
					", len=" + len +
					", totalBytesRead=" + totalBytesRead +
					", bytesLeftToRead=" + bytesLeftToRead +
					".");
			throw new IOException(caller + ": " + e.getMessage());
		}
	}
	
	public static void writeOutputStream(OutputStream os, byte[] buffer, int offset, int len, String caller) throws IOException
	{
		try
		{
			os.write(buffer, offset, len);
			System.out.println(caller + ": Wrote " + len + " bytes to socket.");
		}
		catch (IOException | IndexOutOfBoundsException | NullPointerException e) {
			throw new IOException(caller + ": " + e.getMessage());
		}
	}
	
	public static void writeHeader(OutputStream os, ClientHeader hdr,
								   int msgLen, String msgSrc, String msgDst, String msgType,
								   String caller) throws IOException
	{
		hdr.messageLen = msgLen;
		hdr.messageSource = msgSrc;
		hdr.messageDestination = msgDst;
		hdr.messageType = msgType;
		hdr.buildHeaderBuffer();
		writeOutputStream(os, hdr.bytes, 0, ClientHeader.kHdrByteCount, caller);
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
