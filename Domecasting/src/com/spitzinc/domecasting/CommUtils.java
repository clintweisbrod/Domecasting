package com.spitzinc.domecasting;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;

public class CommUtils
{
	public static boolean readInputStream(InputStream is, byte[] buffer, int offset, int len, String caller)
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
			
		} catch (SocketException e) {
			System.out.println(caller + ": SocketException reading InputStream. " + e.getMessage());
		} catch (IOException e) {
			System.out.println(caller + ": IOException reading InputStream. " + e.getMessage());
		} catch (IndexOutOfBoundsException e) {
			System.out.println(caller + ": IndexOutOfBoundsException reading InputStream" +
					". buffer.length=" + buffer.length +
					", offset=" + offset +
					", len=" + len +
					", totalBytesRead=" + totalBytesRead +
					", bytesLeftToRead=" + bytesLeftToRead +
					".");
		}
		
		return (bytesLeftToRead == 0);
	}
	
	public static boolean writeOutputStream(OutputStream os, byte[] buffer, int offset, int len, String caller)
	{
		boolean result = true;
		
		try
		{
			os.write(buffer, offset, len);
			System.out.println(caller + ": Wrote " + len + " bytes to socket.");
		}
		catch (IOException e) {
			result = false;
			System.out.println(caller + ": Failed writing outbound socket. " + e.getMessage());
		}
		
		return result;
	}
	
	public static boolean writeHeader(OutputStream os, ClientHeader hdr,
									  int msgLen, String msgSrc, String msgDst, String msgType,
			  						  String caller)
	{
		hdr.messageLen = msgLen;
		hdr.messageSource = msgSrc;
		hdr.messageDestination = msgDst;
		hdr.messageType = msgType;
		hdr.buildHeaderBuffer();
		return writeOutputStream(os, hdr.bytes, 0, ClientHeader.kHdrByteCount, caller);
	}
}
