package com.spitzinc.domecasting;

public class ClientHeader
{
	private static final int kFieldLength_MessageLength = 10;
	private static final int kFieldLength_MessageSource = 5;
	private static final int kFieldLength_MessageDestination = 5;
	private static final int kFieldLength_MessageType = 5;
	
	public static final int kHdrCharCount = kFieldLength_MessageLength + kFieldLength_MessageSource +
											kFieldLength_MessageDestination + kFieldLength_MessageType;
	
	private static final int kFieldPos_MessageLength = 0;
	private static final int kFieldPos_MessageSource = kFieldPos_MessageLength + kFieldLength_MessageLength;
	private static final int kFieldPos_MessageDestination = kFieldPos_MessageSource + kFieldLength_MessageSource;
	private static final int kFieldPos_MessageType = kFieldPos_MessageDestination + kFieldLength_MessageDestination;
	
	// Message source/destination IDs
	public static final String kDCC = "DCC";	// Domecasting client
	public static final String kDCS = "DCS";	// Domecasting server
	public static final String kSNPF = "SNPF";	// Starrynight Preflight
	public static final String kSNRB = "SNRB";	// Starrynight Renderbox
	public static final String kATM4 = "ATM4";	// ATM-4
	public static final String kTLEPF = "TLEC";	// The Layered Earth client
	public static final String kTLERB = "TLES";	// The Layered Earth server
	
	// Message type IDs
	public static final String kCOMM = "COMM";	// TCP/IP communication routing
	public static final String kINFO = "INFO";	// Domecasting-specific info
	public static final String kFILE = "FILE";	// File transfer
	
	
	public int messageLen;	// This is interpreted as the number of bytes to receive AFTER the header
	public String messageSource;
	public String messageDestination;
	public String messageType;
	public char[] chars;
	
	public ClientHeader()
	{
		this.chars = new char[ClientHeader.kHdrCharCount];
	}
	
	public boolean parseHeaderBuffer()
	{
		// Every header sent to the server must be a fixed length of 25 bytes.
		// 0-9: String representation of length of entire message. Right-padded.
		// 10-14: Source of message. ex. "DCC", "DCS", "SNPF", "SNRB", "ATM4", "TLEC", "TLES". Right-padded.
		// 15-19: Destination for message. Same possibilities as 10-19.
		// 20-24: Message type: "COMM", "INFO", "FILE". Right-padded.
		
		// message length
		String hdrField = new String(chars, kFieldPos_MessageLength, kFieldLength_MessageLength).trim();
		try {
			messageLen = Integer.parseInt(hdrField);
		} catch (NumberFormatException e) {
			Log.inst().info("msgLenStr: " + hdrField);
			e.printStackTrace();
			return false;
		}
		
		// message source
		messageSource = new String(chars, kFieldPos_MessageSource, kFieldLength_MessageSource).trim();
		
		// message destination
		messageDestination = new String(chars, kFieldPos_MessageDestination, kFieldLength_MessageDestination).trim();
		
		// message type
		messageType = new String(chars, kFieldPos_MessageType, kFieldLength_MessageType).trim();
		
		return true;
	}
	
	public boolean buildHeaderBuffer()
	{
		if (messageSource.length() > kFieldLength_MessageSource)
			return false;
		if (messageDestination.length() > kFieldLength_MessageDestination)
			return false;
		if (messageType.length() > kFieldLength_MessageType)
			return false;
		
		// Initialize buffer contents to all spaces
		for (int i = 0; i < kHdrCharCount; i++)
			chars[i] = ' ';
		
		// Allocate char array to facilitate copy
		char[] fieldChars = new char[kFieldLength_MessageLength];
		
		// message length
		String messageLenStr = Integer.toUnsignedString(messageLen);
		messageLenStr.getChars(0, messageLenStr.length(), fieldChars, 0);
		System.arraycopy(fieldChars, 0, chars, 0, messageLenStr.length());
		
		// message source
		messageSource.getChars(0, messageSource.length(), fieldChars, 0);
		System.arraycopy(fieldChars, 0, chars, kFieldPos_MessageSource, messageSource.length());
		
		// message destination
		messageDestination.getChars(0, messageDestination.length(), fieldChars, 0);
		System.arraycopy(fieldChars, 0, chars, kFieldPos_MessageDestination, messageDestination.length());
		
		// message type
		messageType.getChars(0, messageType.length(), fieldChars, 0);
		System.arraycopy(fieldChars, 0, chars, kFieldPos_MessageType, messageType.length());
		
		return true;
	}
}
