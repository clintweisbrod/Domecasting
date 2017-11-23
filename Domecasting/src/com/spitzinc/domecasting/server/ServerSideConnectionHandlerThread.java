package com.spitzinc.domecasting.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.text.ParseException;
import java.util.ArrayList;

import com.spitzinc.domecasting.ClientHeader;
import com.spitzinc.domecasting.CommUtils;
import com.spitzinc.domecasting.Log;
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
	public boolean isHostListening;	// Only valid for host connections
	
	public ServerSideConnectionHandlerThread(TCPConnectionListenerThread owner, Socket inboundSocket)
	{
		super(owner, inboundSocket);
		
		this.peerConnectionThread = null;
		this.outputStreamLock = new Object();
		this.outHdr = new ClientHeader();
		this.inHdr = new ClientHeader();
		this.commBuffer = new byte[CommUtils.kCommBufferSize];
		this.domecastID = null;
		this.listenerThread = (ServerSideConnectionListenerThread)owner;
		this.isHostListening = false;
	}
	
	public String getDomecastID() {
		return domecastID;
	}
	
	public byte getClientType() {
		return clientType;
	}
	
	public boolean isHostListening() {
		return isHostListening;
	}
	
	private void beginHandlingClientCommands()
	{
		// This is the "main loop" of server connection.
		try
		{
			while (!stopped.get())
			{
				// Read and parse the header
				CommUtils.readInputStream(in, inHdr.bytes, 0, ClientHeader.kHdrByteCount);
				if (!inHdr.parseHeaderBuffer())
					break;
				
				// Now look at hdr contents to decide what to do.
				if (inHdr.messageType.equals(ClientHeader.kINFO))
					handleINFO(inHdr);
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
		CommUtils.readInputStream(in, infoBytes, 0, infoBytes.length);
		
		// All INFO messages are of the form "variable=value".
		String msg = new String(infoBytes);
		Log.inst().debug("Received: " + msg);
		String[] list = msg.split("=");
		if (list[0].equals(CommUtils.kClientType))			// Sent from both host and presenter
		{
			clientType = (byte)list[1].charAt(0);
			
			// Send the client back a notice of connection
			sendBoolean(CommUtils.kIsConnected, true);

			// If we're a host connection, send back available domecasts
			if (clientType == CommUtils.kHostID)
			{
				ArrayList<String> domecasts = listenerThread.getAvailableDomecasts();
				sendHostAvailableDomecasts(domecasts);
			}
		}
		else if (list[0].equals(CommUtils.kDomecastID))		// Sent from both host and presenter
		{
			if (list.length == 2)
				domecastID = list[1];
			else
				domecastID = null;
			
			// As a presenter, we want to notify all hosts that we're present
			if (clientType == CommUtils.kPresenterID)
				listenerThread.notifyHostsOfAvailableDomecasts();
			
			listenerThread.sendStatusToThreads();
		}
		else if (list[0].equals(CommUtils.kIsDomecastIDUnique))	// Sent only from presenter
			sendBoolean(CommUtils.kIsDomecastIDUnique, listenerThread.isDomecastIDUnique(list[1]));
		else if (list[0].equals(CommUtils.kIsHostListening))	// Sent only from host
		{
			isHostListening = Boolean.parseBoolean(list[1]);
			
			// Notify presenter of this change
			if (peerConnectionThread == null)
				peerConnectionThread = listenerThread.findPeerConnectionThread(this);
			peerConnectionThread.sendBoolean(CommUtils.kIsHostListening, isHostListening);
			
			listenerThread.sendStatusToThreads();
		}
	}
	
	public void sendBoolean(String name, boolean value) throws IOException
	{
		String reply = name + "=" + Boolean.toString(value);
		byte[] replyBytes = reply.getBytes();
		
		// We're performing two writes to the OutputStream. They MUST be sequential.
		synchronized (outputStreamLock)
		{
			CommUtils.writeHeader(out, outHdr, replyBytes.length, ClientHeader.kDCS, ClientHeader.kDCC, ClientHeader.kINFO);
			CommUtils.writeOutputStream(out, replyBytes, 0, replyBytes.length);
		}
	}
	
	public void sendText(String name, String value) throws IOException
	{
		String reply = name + "=" + value;
		byte[] replyBytes = reply.getBytes();
		
		// We're performing two writes to the OutputStream. They MUST be sequential.
		synchronized (outputStreamLock)
		{
			CommUtils.writeHeader(out, outHdr, replyBytes.length, ClientHeader.kDCS, ClientHeader.kDCC, ClientHeader.kINFO);
			CommUtils.writeOutputStream(out, replyBytes, 0, replyBytes.length);
		}
	}
	
	public void sendHostAvailableDomecasts(ArrayList<String> domecasts) throws IOException
	{
		// Build a reply to send back
		String reply = null;
		if (domecasts.isEmpty())
			reply = CommUtils.kGetAvailableDomecasts + "=" + CommUtils.kNoAvailableDomecastIDs;
		else
		{
			StringBuffer buf = new StringBuffer(CommUtils.kGetAvailableDomecasts + "=");
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
			CommUtils.writeHeader(out, outHdr, replyBytes.length, ClientHeader.kDCS, ClientHeader.kDCC, ClientHeader.kINFO);
			CommUtils.writeOutputStream(out, replyBytes, 0, replyBytes.length);
		}
	}
	
	/**
	 * Pass the hdr (already read) and the remaining data on the InputStream to the peer connections's OutputStream.
	 */
	private void handleCOMM(ClientHeader hdr) throws IOException
	{
		if (peerConnectionThread == null)
			peerConnectionThread = listenerThread.findPeerConnectionThread(this);

//		String hdrString =  new String(hdr.bytes);
//		Log.inst().info("Header:");
//		Log.inst().info(hdrString);
		
		// Make sure our buffer is big enough
		if (hdr.messageLen > commBuffer.length)
			commBuffer = new char[(int)(hdr.messageLen * 1.25)];	// Make new commBuffer 25% larger than what we need.
		
		// Read the data after the header
		CommUtils.readInputStream(in, commBuffer, 0, hdr.messageLen);
//		String bodyString =  new String(commBuffer, 0, hdr.messageLen);
//		Log.inst().info("Body:");
//		Log.inst().info(bodyString);
		
		if (peerConnectionThread != null)
		{
			// Only send the data if the host is "listening".
			if (((clientType == CommUtils.kHostID) && this.isHostListening) ||
				((clientType == CommUtils.kPresenterID) && peerConnectionThread.isHostListening))
			{
				synchronized (peerConnectionThread.outputStreamLock)
				{
					// Write the header we've received
					CommUtils.writeOutputStream(peerConnectionThread.out, hdr.bytes, 0, ClientHeader.kHdrByteCount);
					
					// Write the data we've received
					CommUtils.writeOutputStream(peerConnectionThread.out, commBuffer, 0, hdr.messageLen);
				}
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
			CommUtils.readInputStream(in, buffer, 0, CommUtils.kSecurityCodeLength);
			
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
			Log.inst().error(e.getMessage());
		}

		// Close stream
		Log.inst().info("Shutting down connection stream.");
		try
		{
			if (in != null)
				in.close();
		}
		catch (IOException e) {
		}
		
		// Close socket
		Log.inst().info("Closing socket.");
		try
		{
			if (socket != null)
				socket.close();
		}
		catch (IOException e) {
		}
		
		// Notify owner this thread is dying.
		owner.threadDying(this);

		Log.inst().info("Exiting thread.");
	}
	
	public String toString()
	{
		StringBuffer buf = new StringBuffer();
		buf.append("domecastID=" + domecastID + ", ");
		buf.append("clientType=" + (char)clientType + ", ");
		if (clientType == CommUtils.kHostID)
			buf.append(CommUtils.kIsHostListening + Boolean.toString(isHostListening));
		
		return buf.toString();
	}
}
