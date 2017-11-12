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
	protected InputStream in;
	protected OutputStream out;
	private Object outputStreamLock;
	private Object inputStreamLock;
	private ClientHeader outHdr;
	private ClientHeader inHdr;
	protected String domecastID;
	protected byte clientType;
	protected boolean readyToCast;
	
	public ServerSideConnectionHandlerThread(TCPConnectionListenerThread owner, Socket inboundSocket)
	{
		super(owner, inboundSocket);
		
		this.outputStreamLock = new Object();
		this.inputStreamLock = new Object();
		this.outHdr = new ClientHeader();
		this.inHdr = new ClientHeader();
		this.listenerThread = (ServerSideConnectionListenerThread)owner;
		this.readyToCast = false;
	}
	
	public String getDomecastID() {
		return domecastID;
	}
	
	public byte getClientType() {
		return clientType;
	}
	
	public boolean isReadyToCast() {
		return readyToCast;
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
		if (list[0].equals("DomecastID"))
		{
			if (list.length == 2)
				domecastID = list[1];
			else
				domecastID = null;
		}
		else if (list[0].equals("ClientType"))
			clientType = (byte)list[1].charAt(0);
		else if (list[0].equals("ReadyToCast"))
			readyToCast = Boolean.parseBoolean(list[1]);
	}
	
	private void handleREQU(ClientHeader hdr) throws IOException
	{
		byte[] requBytes = new byte[hdr.messageLen];
		CommUtils.readInputStream(in, requBytes, 0, requBytes.length, getName());
		
		// All REQU messages are of the form "request" and have varying responses
		String req = new String(requBytes);
		System.out.println(this.getName() + ": Received: " + req);
		if (req.equals("IsPeerReady"))
		{
			boolean isPeerReady = false;
			
			// Look for peer connection on this server
			ServerSideConnectionHandlerThread peerThread = listenerThread.findPeerConnectionThread(this);
			if (peerThread != null)
			{
				if (peerThread.clientType == CommUtils.kHostID)
					isPeerReady = peerThread.isReadyToCast();
				else
					isPeerReady = true;
			}
			
			// Respond to request
			String reply = "IsPeerReady=" + Boolean.toString(isPeerReady);
			byte[] replyBytes = reply.getBytes();
			
			// We're performing two writes to the OutputStream. They MUST be sequential.
			synchronized (outputStreamLock)
			{
				CommUtils.writeHeader(out, outHdr, replyBytes.length, ClientHeader.kDCS, ClientHeader.kDCC, ClientHeader.kREQU, this.getName());
				CommUtils.writeOutputStream(out, replyBytes, 0, replyBytes.length, getName());
			}
		}
		else if (req.equals("GetAvailableDomecasts"))
		{
			// Obtain list of available domecasts
			ArrayList<String> domecasts = listenerThread.getAvailableDomecasts();
			
			// Build a reply to send back
			String reply = null;
			if (domecasts.isEmpty())
				reply = "<none>";
			else if (domecasts.size() == 1)
				reply = domecasts.get(0);
			else
			{
				StringBuffer buf = new StringBuffer();
				for (String domecast : domecasts)
				{
					buf.append(domecast);
					buf.append("~");
				}
				reply = buf.substring(0, buf.length() - 1);
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
		buf.append("readyToCast=" + Boolean.toString(readyToCast));
		
		return buf.toString();
	}
}
