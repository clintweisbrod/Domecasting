package com.spitzinc.domecasting.server;

import java.io.File;
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
	private static final String kAssetsFilename = "assets.zip";
	protected ServerSideConnectionListenerThread listenerThread;
	protected ArrayList<ServerSideConnectionHandlerThread> peerConnectionThreads;
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
		
		this.peerConnectionThreads = null;
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
				else if (inHdr.messageType.equals(ClientHeader.kFILE))
					handleFILE(inHdr);
			}
		}
		catch (IOException e) {
		}
	}
	
	private void handleINFO(ClientHeader hdr) throws IOException
	{
		byte[] infoBytes = new byte[(int)hdr.messageLen];
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

			// If we're a host connection
			if (clientType == CommUtils.kHostID)
			{
				// Send back available domecasts
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
			
			if (clientType == CommUtils.kPresenterID)
			{
				// As a presenter, we want to notify all hosts that we're present
				listenerThread.notifyHostsOfAvailableDomecasts();
			}
			if (clientType == CommUtils.kHostID)
			{
				// As a host, we want to know if there is an assets file available for download
				peerConnectionThreads = listenerThread.findPeerConnectionThreads(this);
				if (!peerConnectionThreads.isEmpty())
					sendBoolean(CommUtils.kAssetFileAvailable, peerConnectionThreads.get(0).isAssetsFilePresent());
			}
			
			listenerThread.sendStatusToThreads();
		}
		else if (list[0].equals(CommUtils.kIsDomecastIDUnique))	// Sent only from presenter
			sendBoolean(CommUtils.kIsDomecastIDUnique, listenerThread.isDomecastIDUnique(list[1]));
		else if (list[0].equals(CommUtils.kIsHostListening))	// Sent only from host
		{
			isHostListening = Boolean.parseBoolean(list[1]);
			
			// Notify presenter of this change
			// Should only find one presenter 
			peerConnectionThreads = listenerThread.findPeerConnectionThreads(this);
			if (!peerConnectionThreads.isEmpty())
				peerConnectionThreads.get(0).sendBoolean(CommUtils.kIsHostListening, isHostListening);
			
			listenerThread.sendStatusToThreads();
		}
		else if (list[0].equals(CommUtils.kGetAssetsFile))	// Sent only from host
			sendAssetsFile();
	}
	
	private String getAssetsFolderPath()
	{
		ServerApplication inst = (ServerApplication) ServerApplication.inst();
		String result = inst.getProgramDataPath() + File.separator + domecastID + File.separator;
		return result;
	}
	
	private void handleFILE(ClientHeader hdr) throws IOException
	{
		// Decide where the file will be stored
		// I think it's reasonable to create a subfolder in ProgramData to hold asset files.
		String programDataPath = getAssetsFolderPath();
		File programDataFolder = new File(programDataPath);
		if (!programDataFolder.exists())
			programDataFolder.mkdirs();
		
		// Create a DataOutputStream to write the received data to
		File outputFile = new File(programDataPath + kAssetsFilename);
		
		// Read the InputStream to specified file
		CommUtils.readInputStreamToFile(in, outputFile, hdr.messageLen, commBuffer);
		
		// When we get here, we have the following file saved: C:\ProgramData\Spitz, Inc\Domecasting\<domecastID>\assets.zip
		// Notify all connected hosts that an assets file is available for download.
		listenerThread.notifyHostsOfAvailableAssetsFile(this);
	}
	
	public void sendBoolean(String name, boolean value) throws IOException
	{
		String reply = name + "=" + Boolean.toString(value);
		byte[] replyBytes = reply.getBytes();
		
		// We're performing two writes to the OutputStream. They MUST be sequential.
		synchronized (outputStreamLock)
		{
			CommUtils.writeHeader(out, outHdr, replyBytes.length, ClientHeader.kDCS, ClientHeader.kINFO);
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
			CommUtils.writeHeader(out, outHdr, replyBytes.length, ClientHeader.kDCS, ClientHeader.kINFO);
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
			CommUtils.writeHeader(out, outHdr, replyBytes.length, ClientHeader.kDCS, ClientHeader.kINFO);
			CommUtils.writeOutputStream(out, replyBytes, 0, replyBytes.length);
		}
	}
	
	private void sendAssetsFile() throws IOException
	{
		String programDataPath = getAssetsFolderPath();
		
		// Create a reference to the file
		File inputFile = new File(programDataPath + "assets.zip");
		
		// Send it
		synchronized (outputStreamLock)
		{
			CommUtils.writeHeader(out, outHdr, inputFile.length(), ClientHeader.kDCS, ClientHeader.kFILE);
			CommUtils.writeOutputStreamFromFile(out, inputFile, commBuffer);
		}
	}
	
	/**
	 * Pass the hdr (already read) and the remaining data on the InputStream to the peer connections's OutputStream.
	 */
	private void handleCOMM(ClientHeader hdr) throws IOException
	{
		// Host connections will already have peerConnectionThreads initialized.
		// Presenter connections may have more than one peer.
		if (clientType == CommUtils.kPresenterID)
			peerConnectionThreads = listenerThread.findPeerConnectionThreads(this);

		// Make sure our buffer is big enough
		if (hdr.messageLen > commBuffer.length)
			commBuffer = new byte[(int)(hdr.messageLen * 1.25)];	// Make new commBuffer 25% larger than what we need.
		
		// Read the data after the header
		CommUtils.readInputStream(in, commBuffer, 0, (int)hdr.messageLen);
		
		// Write to each peer
		for (ServerSideConnectionHandlerThread peer : peerConnectionThreads)
		{
			synchronized (peer.outputStreamLock)
			{
				// Write the header we've received
				CommUtils.writeOutputStream(peer.out, hdr.bytes, 0, ClientHeader.kHdrByteCount);
				
				// Write the data we've received
				CommUtils.writeOutputStream(peer.out, commBuffer, 0, (int)hdr.messageLen);
			}
		}
	}
	
	public boolean isAssetsFilePresent() {
		File assetsFile = new File(getAssetsFolderPath() + kAssetsFilename);
		return assetsFile.exists();
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
				throw new ParseException("Incorrect security code (" + securityCode + ") sent by client.", 0);
			
			// We can now begin negotiating the client connection
			beginHandlingClientCommands();
		}
		catch (IOException | ParseException e) {
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
		if ((clientType == CommUtils.kPresenterID) && (domecastID != null) && isAssetsFilePresent())
			buf.append("asset file uploaded.");
		
		return buf.toString();
	}
}
