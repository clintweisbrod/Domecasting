package com.spitzinc.domecasting;

public class ClientHeader
{
	public int messageLen;
	public String messageSource;
	public String messageDestination;
	public String messageType;
	
	public boolean parseHeader(byte[] headerBuf)
	{
		// Every header sent to the server must be a fixed length of 25 bytes.
		// 0-9: String representation of length of entire message. Right-padded.
		// 10-14: Source of message. ex. "DCC", "SNPF", "SNRB", "ATM4", "TLEPF", "TLERB". Right-padded.
		// 15-19: Destination for message. Same possibilities as 10-19.
		// 20-24: Message type: "COMM", "INFO", "FILE". Right-padded.
		
		// message length
		String hdrField = new String(headerBuf, 0, 10).trim();
		try {
			messageLen = Integer.parseInt(hdrField);
		} catch (NumberFormatException e) {
			System.out.println(getClass().getSimpleName() + ": msgLenStr: " + hdrField + ".");
			e.printStackTrace();
			return false;
		}
		
		// message source
		messageSource = new String(headerBuf, 10, 5).trim();
		
		// message destination
		messageDestination = new String(headerBuf, 15, 5).trim();
		
		// message type
		messageType = new String(headerBuf, 20, 5).trim();
		
		// message type
		messageType = new String(headerBuf, 20, 5).trim();
		
		return true;
	}
}
