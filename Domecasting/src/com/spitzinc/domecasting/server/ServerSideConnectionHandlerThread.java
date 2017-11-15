package com.spitzinc.domecasting.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.text.ParseException;
import java.util.ArrayList;

import com.spitzinc.domecasting.ClientHeader;
import com.spitzinc.domecasting.CommUtils;
import com.spitzinc.domecasting.TCPConnectionHandlerThread;
import com.spitzinc.domecasting.TCPConnectionListenerThread;

public class ServerSideConnectionHandlerThread extends TCPConnectionHandlerThread
{
	protected ServerSideConnectionListenerThread listenerThread;
	protected ServerSideConnectionHandlerThread peerConnectionThread;
	public InputStream in;
	public OutputStream out;
	public Object outputStreamLock;
	private ClientHeader outHdr;
	private ClientHeader inHdr;
	private byte[] commBuffer;
	protected String domecastID;
	protected byte clientType;
	protected boolean hostReadyForDomecast;	// Only valid for host connections
	
	public ServerSideConnectionHandlerThread(TCPConnectionListenerThread owner, Socket inboundSocket)
	{
		super(owner, inboundSocket);
		
		this.peerConnectionThread = null;
		this.outputStreamLock = new Object();
		this.outHdr = new ClientHeader();
		this.inHdr = new ClientHeader();
		this.commBuffer = new byte[CommUtils.kCommBufferSize];
		this.listenerThread = (ServerSideConnectionListenerThread)owner;
		this.hostReadyForDomecast = false;
	}
	
	public String getDomecastID() {
		return domecastID;
	}
	
	public byte getClientType() {
		return clientType;
	}
	
	public boolean isHostReadyForDomecast() {
		return hostReadyForDomecast;
	}
	
	private void beginHandlingClientCommands()
	{
		// This is the "main loop" of server connection.
		try
		{
			while (!stopped.get())
			{
				// Read and parse the header
				CommUtils.readInputStream(in, inHdr.bytes, 0, ClientHeader.kHdrByteCount, getName());
				if (!inHdr.parseHeaderBuffer())
					break;
				
				// Now look at hdr contents to decide what to do.
				if (inHdr.messageType.equals(ClientHeader.kINFO))
					handleINFO(inHdr);
				else if (inHdr.messageType.equals(ClientHeader.kREQU))
					handleREQU(inHdr);
				else if (inHdr.messageType.equals(ClientHeader.kCOMM))
					handleCOMM(inHdr);
			}
		}
		catch (IOException e) {
		}
	}
	
	private void handleINFO(ClientHeader hdr) throws IOException
	{
		byte[] infoBytes = new byte[hdr.messageLen];
		CommUtils.readInputStream(in, infoBytes, 0, infoBytes.length, getName());
		
		// All INFO messages are of the form "variable=value".
		String msg = new String(infoBytes);
		System.out.println(this.getName() + ": Received: " + msg);
		String[] list = msg.split("=");
		if (list[0].equals(CommUtils.kDomecastID))
		{
			if (list.length == 2)
				domecastID = list[1];
			else
				domecastID = null;
		}
		else if (list[0].equals(CommUtils.kClientType))
			clientType = (byte)list[1].charAt(0);
		else if (list[0].equals(CommUtils.kHostReadyForDomecast))
			hostReadyForDomecast = Boolean.parseBoolean(list[1]);
	}
	
	private void handleREQU(ClientHeader hdr) throws IOException
	{
		byte[] requBytes = new byte[hdr.messageLen];
		CommUtils.readInputStream(in, requBytes, 0, requBytes.length, getName());
		
		// All REQU messages are of the form "request" and have varying responses
		String req = new String(requBytes);
		System.out.println(this.getName() + ": Received: " + req);
		
		if (req.equals(CommUtils.kIsConnected))
		{
			// If we're executing this, then yes, we're connected
			// Respond to request
			String reply = CommUtils.kIsConnected + "=" + Boolean.toString(true);
			byte[] replyBytes = reply.getBytes();
			
			// We're performing two writes to the OutputStream. They MUST be sequential.
			synchronized (outputStreamLock)
			{
				CommUtils.writeHeader(out, outHdr, replyBytes.length, ClientHeader.kDCS, ClientHeader.kDCC, ClientHeader.kREQU, this.getName());
				CommUtils.writeOutputStream(out, replyBytes, 0, replyBytes.length, getName());
			}
		}
		
		if (req.equals(CommUtils.kIsPeerPresent))
		{
			boolean isPeerPresent = false;
			
			// Look for peer connection on this server
			peerConnectionThread = listenerThread.findPeerConnectionThread(this);
			isPeerPresent = (peerConnectionThread != null);
			
			// Respond to request
			String reply = CommUtils.kIsPeerPresent + "=" + Boolean.toString(isPeerPresent);
			byte[] replyBytes = reply.getBytes();
			
			// We're performing two writes to the OutputStream. They MUST be sequential.
			synchronized (outputStreamLock)
			{
				CommUtils.writeHeader(out, outHdr, replyBytes.length, ClientHeader.kDCS, ClientHeader.kDCC, ClientHeader.kREQU, this.getName());
				CommUtils.writeOutputStream(out, replyBytes, 0, replyBytes.length, getName());
			}
		}
		else if (req.equals(CommUtils.kIsPeerReady))
		{
			boolean isPeerReady = false;
			
			// Look for peer connection on this server
			peerConnectionThread = listenerThread.findPeerConnectionThread(this);
			if (peerConnectionThread != null)
			{
				if (peerConnectionThread.clientType == CommUtils.kHostID)
					isPeerReady = peerConnectionThread.isHostReadyForDomecast();
				else
					isPeerReady = true;
			}
			
			// Respond to request
			String reply = CommUtils.kIsPeerReady + "=" + Boolean.toString(isPeerReady);
			byte[] replyBytes = reply.getBytes();
			
			// We're performing two writes to the OutputStream. They MUST be sequential.
			synchronized (outputStreamLock)
			{
				CommUtils.writeHeader(out, outHdr, replyBytes.length, ClientHeader.kDCS, ClientHeader.kDCC, ClientHeader.kREQU, this.getName());
				CommUtils.writeOutputStream(out, replyBytes, 0, replyBytes.length, getName());
			}
		}
		else if (req.startsWith(CommUtils.kIsDomecastIDUnique))
		{
			String[] list = req.split("=");
			boolean isDomecastIDUnique = listenerThread.isDomecastIDUnique(list[1]);
			
			// Respond to request
			String reply = CommUtils.kIsDomecastIDUnique + "=" + Boolean.toString(isDomecastIDUnique);
			byte[] replyBytes = reply.getBytes();
			
			// We're performing two writes to the OutputStream. They MUST be sequential.
			synchronized (outputStreamLock)
			{
				CommUtils.writeHeader(out, outHdr, replyBytes.length, ClientHeader.kDCS, ClientHeader.kDCC, ClientHeader.kREQU, this.getName());
				CommUtils.writeOutputStream(out, replyBytes, 0, replyBytes.length, getName());
			}
		}
		else if (req.equals(CommUtils.kGetAvailableDomecasts))
		{
			// Obtain list of available domecasts
			ArrayList<String> domecasts = listenerThread.getAvailableDomecasts();
			
			// Build a reply to send back
			String reply = null;
			if (domecasts.isEmpty())
				reply = "<none>";
			else
			{
				StringBuffer buf = new StringBuffer();
				for (String domecast : domecasts)
				{
					buf.append(domecast);
					buf.append("~");
				}
				reply = buf.toString();
			}
			
			byte[] replyBytes = reply.getBytes();
			
			// We're performing two writes to the OutputStream. They MUST be sequential.
			synchronized (outputStreamLock)
			{
				CommUtils.writeHeader(out, outHdr, replyBytes.length, ClientHeader.kDCS, ClientHeader.kDCC, ClientHeader.kREQU, this.getName());
				CommUtils.writeOutputStream(out, replyBytes, 0, replyBytes.length, getName());
			}
		}
	}
	
	/**
	 * Pass the hdr (already read) and the remaining data on the InputStream to the peer connections's OutputStream.
	 */
	private void handleCOMM(ClientHeader hdr) throws IOException
	{
		// Read the data after the header
		CommUtils.readInputStream(in, commBuffer, 0, hdr.messageLen, getName());
		
		if (peerConnectionThread != null)
		{
			synchronized (peerConnectionThread.outputStreamLock)
			{
				// Write the header we've received
				CommUtils.writeOutputStream(peerConnectionThread.out, hdr.bytes, 0, ClientHeader.kHdrByteCount, getName());
				
				// Write the data we've received
				CommUtils.writeOutputStream(peerConnectionThread.out, commBuffer, 0, hdr.messageLen, getName());
			}
		}
	}
	
	public void run()
	{
		// When a domecast client connects to a domecast server, the agreed upon protocol
		// is that the client will send:
		// - A 20-character security code that will change everyday. If the code is
		//   incorrect, the connection will be refused by this server.
		try {
			in = socket.getInputStream();
			out = socket.getOutputStream();
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}

		// Allocate byte buffer to handle initial handshake communication
		byte[] buffer = new byte[CommUtils.kSecurityCodeLength];		
		try
		{
			// Read off kSecurityCodeLength bytes. This is the security code.
			CommUtils.readInputStream(in, buffer, 0, CommUtils.kSecurityCodeLength, getName());
			
			// Verify the sent security code matches what we expect
			String securityCode = new String(buffer, 0, CommUtils.kSecurityCodeLength);
			String expectedSecurityCode = CommUtils.getDailySecurityCode();
			if (!securityCode.equals(expectedSecurityCode))
				throw new ParseException("Incorrect security code sent by client.", 0);
			
			// We can now begin negotiating the client connection
			beginHandlingClientCommands();
		}
		catch (IOException | ParseException e) {
			e.printStackTrace();
			System.out.println(this.getName() + ": " + e.getMessage());
		}

		// Close stream
		System.out.println(this.getName() + ": Shutting down connection stream.");
		try
		{
			if (in != null)
				in.close();
		}
		catch (IOException e) {
		}
		
		// Close socket
		System.out.println(this.getName() + ": Closing socket.");
		try
		{
			if (socket != null)
				socket.close();
		}
		catch (IOException e) {
		}
		
		// Notify owner this thread is dying.
		owner.threadDying(this);

		System.out.println(this.getName() + ": Exiting thread.");
	}
	
	public String toString()
	{
		StringBuffer buf = new StringBuffer();
		buf.append("domecastID=" + domecastID + ", ");
		buf.append("clientType=" + (char)clientType + ", ");
		if (clientType == CommUtils.kHostID)
			buf.append("hostReadyForDomecast=" + Boolean.toString(hostReadyForDomecast));
		
		return buf.toString();
	}
}
