package com.spitzinc.domecasting;

public class ClientHeader
{
	private static final int kFieldLength_MessageLength = 10;
	private static final int kFieldLength_MessageSource = 5;
	private static final int kFieldLength_MessageDestination = 5;
	private static final int kFieldLength_MessageType = 5;
	
	public static final int kHdrByteCount = kFieldLength_MessageLength + kFieldLength_MessageSource +
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
	public static final String kREQU = "REQU";	// Domecasting-specific info request
	public static final String kFILE = "FILE";	// File transfer
	
	
	public int messageLen;	// This is interpreted as the number of bytes to receive AFTER the header
	public String messageSource;
	public String messageDestination;
	public String messageType;
	
	public boolean parseHeaderBuffer(byte[] headerBuf)
	{
		// Every header sent to the server must be a fixed length of 25 bytes.
		// 0-9: String representation of length of entire message. Right-padded.
		// 10-14: Source of message. ex. "DCC", "DCS", "SNPF", "SNRB", "ATM4", "TLEC", "TLES". Right-padded.
		// 15-19: Destination for message. Same possibilities as 10-19.
		// 20-24: Message type: "COMM", "INFO", "FILE". Right-padded.
		
		// message length
		String hdrField = new String(headerBuf, kFieldPos_MessageLength, kFieldLength_MessageLength).trim();
		try {
			messageLen = Integer.parseInt(hdrField);
		} catch (NumberFormatException e) {
			System.out.println(getClass().getSimpleName() + ": msgLenStr: " + hdrField + ".");
			e.printStackTrace();
			return false;
		}
		
		// message source
		messageSource = new String(headerBuf, kFieldPos_MessageSource, kFieldLength_MessageSource).trim();
		
		// message destination
		messageDestination = new String(headerBuf, kFieldPos_MessageDestination, kFieldLength_MessageDestination).trim();
		
		// message type
		messageType = new String(headerBuf, kFieldPos_MessageType, kFieldLength_MessageType).trim();
		
		return true;
	}
	
	public boolean buildHeaderBuffer(byte[] headerBuf)
	{
		if (headerBuf.length < kHdrByteCount)
			return false;
		if (messageSource.length() > kFieldLength_MessageSource)
			return false;
		if (messageDestination.length() > kFieldLength_MessageDestination)
			return false;
		if (messageType.length() > kFieldLength_MessageType)
			return false;
		
		// Initialize buffer contents to all spaces
		for (int i = 0; i < kHdrByteCount; i++)
			headerBuf[i] = ' ';
		
		// message length
		String hdrField = Integer.toUnsignedString(messageLen);
		System.arraycopy(hdrField.getBytes(), 0, headerBuf, 0, hdrField.length());
		
		// message source
		System.arraycopy(messageSource.getBytes(), 0, headerBuf, kFieldPos_MessageSource, messageSource.length());
		
		// message destination
		System.arraycopy(messageDestination.getBytes(), 0, headerBuf, kFieldPos_MessageDestination, messageDestination.length());
		
		// message type
		System.arraycopy(messageType.getBytes(), 0, headerBuf, kFieldPos_MessageType, messageType.length());
		
		return true;
	}
}
