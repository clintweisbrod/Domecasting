package com.spitzinc.domecasting;

/*
 * Every "packet" of communication we send and receive from the domecast server is preceeded
 * with a fixed-length header that details what the remaining data is. This is quite similar
 * to how SN sends "packets" of communications between PF and RB. At present, this header is
 * only 20 bytes so not much overhead involved.
 * 
 * This class contains public declarations for communication details and methods to
 * serialize/deserialize the header info.
 */
public class ClientHeader
{
	private static final int kFieldLength_MessageLength = 10;
	private static final int kFieldLength_MessageSource = 5;
	private static final int kFieldLength_MessageDestination = 10;
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
	public static final String kATM4 = "ATM4";	// ATM-4
	public static final String kTLEPF = "TLEC";	// The Layered Earth client
	public static final String kTLERB = "TLES";	// The Layered Earth server
	
	// Message type IDs
	public static final String kCOMM = "COMM";	// TCP/IP communication routing
	public static final String kINFO = "INFO";	// Domecasting-specific info
	public static final String kFILE = "FILE";	// File transfer
	
	
	public long messageLen;	// This is interpreted as the number of bytes to receive AFTER the header
	public String messageSource;
	public String messageDestination;	// This is used to transmit a domecastHostID when sending full state from PF.
	public String messageType;
	public byte[] bytes;
	
	public ClientHeader()
	{
		this.bytes = new byte[ClientHeader.kHdrByteCount];
	}
	
	public boolean parseHeaderBuffer()
	{
		// Every header sent to the server must be a fixed length of 25 bytes.
		// 0-9: String representation of length of entire message. Right-padded.
		// 10-14: Source of message. ex. "DCC", "DCS", "SNPF", "ATM4", "TLEC", "TLES". Right-padded.
		// 15-24: Destination for message. Contains a domecastHostID.
		// 25-29: Message type: "COMM", "INFO", "FILE". Right-padded.
		
		// message length
		String hdrField = new String(bytes, kFieldPos_MessageLength, kFieldLength_MessageLength).trim();
		try {
			messageLen = Integer.parseInt(hdrField);
		} catch (NumberFormatException e) {
			Log.inst().info("msgLenStr: " + hdrField);
			e.printStackTrace();
			return false;
		}
		
		// message source
		messageSource = new String(bytes, kFieldPos_MessageSource, kFieldLength_MessageSource).trim();
		
		// message destination
		messageDestination = new String(bytes, kFieldPos_MessageDestination, kFieldLength_MessageDestination).trim();
		
		// message type
		messageType = new String(bytes, kFieldPos_MessageType, kFieldLength_MessageType).trim();
		
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
		for (int i = 0; i < kHdrByteCount; i++)
			bytes[i] = ' ';
		
		// message length
		String hdrField = Long.toUnsignedString(messageLen);
		System.arraycopy(hdrField.getBytes(), 0, bytes, 0, hdrField.length());
		
		// message source
		System.arraycopy(messageSource.getBytes(), 0, bytes, kFieldPos_MessageSource, messageSource.length());
		
		// message destination
		System.arraycopy(messageDestination.getBytes(), 0, bytes, kFieldPos_MessageDestination, messageDestination.length());
		
		// message type
		System.arraycopy(messageType.getBytes(), 0, bytes, kFieldPos_MessageType, messageType.length());
		
		return true;
	}
}
