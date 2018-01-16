package com.spitzinc.domecasting.server;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import com.spitzinc.domecasting.ClientHeader;
import com.spitzinc.domecasting.CommUtils;
import com.spitzinc.domecasting.Log;
import com.spitzinc.domecasting.TCPConnectionHandlerThread;
import com.spitzinc.domecasting.TCPConnectionListenerThread;

public class ServerSideConnectionHandlerThread extends TCPConnectionHandlerThread
{
	private ServerSideConnectionListenerThread owner;
	private ArrayList<ServerSideConnectionHandlerThread> peerConnectionThreads;
	private InputStream in;
	private OutputStream out;
	private Object outputStreamLock;
	private ClientHeader outHdr;
	private ClientHeader inHdr;
	private byte[] commBuffer;
	private String domecastID;
	private byte clientType;
	private AtomicBoolean isHostListening;	// Only valid for host connections
	private final String connectionID; 
	
	public ServerSideConnectionHandlerThread(ServerSideConnectionListenerThread owner, Socket inboundSocket, long connectionID)
	{
		super(inboundSocket);
		
		this.owner = owner;
		this.connectionID = Long.toString(connectionID);
		this.peerConnectionThreads = null;
		this.outputStreamLock = new Object();
		this.outHdr = new ClientHeader();
		this.inHdr = new ClientHeader();
		this.commBuffer = new byte[CommUtils.kCommBufferSize];
		this.domecastID = null;
		this.isHostListening = new AtomicBoolean(false);	// Only valid for host connections
	}
	
	public synchronized String getDomecastID() {
		return domecastID;
	}
	
	public byte getClientType() {
		return clientType;
	}
	
	public boolean isHostListening() {
		return isHostListening.get();
	}
	
	public String getConnectionID() {
		return connectionID;
	}
	
	private void beginHandlingClientCommands()
	{
		Log.inst().trace("beginHandlingClientCommands()");
		
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
			e.printStackTrace();
		}
	}
	
	private void handleINFO(ClientHeader hdr) throws IOException
	{
		Log.inst().trace("handleINFO()");
		
		byte[] infoBytes = new byte[(int)hdr.messageLen];
		CommUtils.readInputStream(in, infoBytes, 0, infoBytes.length);
		
		// All INFO messages are of the form "variable=value".
		String msg = new String(infoBytes);
		Log.inst().debug("handleINFO(): Received: " + msg);
		String[] list = msg.split("=");
		if (list[0].equals(CommUtils.kClientType))			// Sent from both host and presenter
		{
			clientType = (byte)list[1].charAt(0);
			
			// Send the client back a notice of connection
			sendBoolean(CommUtils.kIsConnectedToServer, true);

			// If we're a host connection
			if (clientType == CommUtils.kHostID)
			{
				// Send back available domecasts
				ArrayList<String> domecasts = owner.getAvailableDomecasts();
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
				owner.notifyHostsOfAvailableDomecasts();
			}
			if (clientType == CommUtils.kHostID)
			{
				// As a host, we want to know if there is an assets file available for download
				sendBoolean(CommUtils.kAssetsFileAvailable, (getAssetsFile() != null));
			}
			
			// Notify all peers of this connection
			peerConnectionThreads = owner.findPeerConnectionThreads(this);
			for (ServerSideConnectionHandlerThread peer : peerConnectionThreads)
				peer.sendBoolean(CommUtils.kIsPeerConnected, true);
						
			// Notify this connection if peer(s) connected
			sendBoolean(CommUtils.kIsPeerConnected, !peerConnectionThreads.isEmpty());

			owner.sendStatusToThreads();
		}
		else if (list[0].equals(CommUtils.kIsDomecastIDUnique))	// Sent only from presenter
			sendBoolean(CommUtils.kIsDomecastIDUnique, owner.isDomecastIDUnique(list[1]));
		else if (list[0].equals(CommUtils.kIsHostListening))	// Sent only from host
		{
			boolean newValue = Boolean.parseBoolean(list[1]);
			boolean requestFullState = newValue && (isHostListening.get() != newValue); 
			isHostListening.set(Boolean.parseBoolean(list[1]));
			
			// We have a problem! When we send kIsHostListening back to presenter, this
			// will cause a call to SNTCPPassThruThread.sendSetLiveCommand() which causes
			// the presenter's local PF to send a full state back across it's connection.
			// Like all other COMM, this gets sent to the local RB and ALL remote RBs if
			// their domecasting hosts are listening. We only want this host to receive
			// the full state. We don't even want the presenter's local RB to receive it.
			
			// We don't want to just send back isHostListening to the presenter. This
			// is only valid for 1:1 domecasts. In general, we want to send back a boolean
			// to the presenter indicating if ANY hosts are listening.
			boolean response = owner.anyHostsListening(domecastID);
			
			// Notify presenter of this change. Should only find one presenter. 
			peerConnectionThreads = owner.findPeerConnectionThreads(this);
			if (!peerConnectionThreads.isEmpty())
			{
				peerConnectionThreads.get(0).sendBoolean(CommUtils.kIsHostListening, response);
			
				// And ask presenter to request local PF to call SetLiveCommunicationWithRB(true) so that
				// we are sent a full state.
				if (requestFullState)
					peerConnectionThreads.get(0).sendText(CommUtils.kRequestFullState, connectionID);
			}
			
			owner.sendStatusToThreads();
		}
		else if (list[0].equals(CommUtils.kIsPeerConnected))	// Sent from both presenter and host
		{
			peerConnectionThreads = owner.findPeerConnectionThreads(this);
			sendBoolean(CommUtils.kIsPeerConnected, !peerConnectionThreads.isEmpty());
		}
		else if (list[0].equals(CommUtils.kGetAssetsFile))		// Sent only from host
			sendAssetsFile();
	}
	
	private void handleFILE(ClientHeader hdr) throws IOException
	{
		Log.inst().trace("handleFILE()");
		
		// Decide where the file will be stored
		// I think it's reasonable to create a subfolder in ProgramData to hold asset files.
		ServerApplication inst = (ServerApplication) ServerApplication.inst();
		String programDataPath = inst.getProgramDataPath() + domecastID + File.separator;
		File programDataFolder = new File(programDataPath);
		if (!programDataFolder.exists())
			programDataFolder.mkdirs();
		
		// See comments in ServerConnection.sendAssetsFile() regarding inclusion of filename field
		// after normal header.
		byte[] filenameBytes = new byte[CommUtils.kMaxPathLen];
		CommUtils.readInputStream(in, filenameBytes, 0, CommUtils.kMaxPathLen);
		String assetsFilename = new String(filenameBytes).trim();
		
		// Create a DataOutputStream to write the received data to
		File outputFile = new File(programDataPath + assetsFilename);
		
		Log.inst().info("Receiving file (" + hdr.messageLen + " bytes).");
		
		// Read the InputStream to specified file
		CommUtils.readInputStreamToFile(in, outputFile, hdr.messageLen, commBuffer, null, null);
		
		// When we get here, we have the following file saved: C:\ProgramData\Spitz, Inc\Domecasting\<domecastID>\assets.zip
		// Notify all connected hosts that an assets file is available for download.
		owner.notifyHostsOfAvailableAssetsFile(this);
	}
	
	public void sendBoolean(String name, boolean value) throws IOException
	{
		String reply = name + "=" + Boolean.toString(value);
		Log.inst().trace("sendBoolean(): " + reply);
		byte[] replyBytes = reply.getBytes();
		
		// We're performing two writes to the OutputStream. They MUST be sequential.
		synchronized (outputStreamLock)
		{
			CommUtils.writeHeader(out, outHdr, replyBytes.length, ClientHeader.kDCS, "", ClientHeader.kINFO);
			CommUtils.writeOutputStream(out, replyBytes, 0, replyBytes.length);
		}
	}
	
	public void sendText(String name, String value) throws IOException
	{
		String reply = name + "=" + value;
		Log.inst().trace("sendText(): " + reply);
		byte[] replyBytes = reply.getBytes();
		
		// We're performing two writes to the OutputStream. They MUST be sequential.
		synchronized (outputStreamLock)
		{
			CommUtils.writeHeader(out, outHdr, replyBytes.length, ClientHeader.kDCS, "", ClientHeader.kINFO);
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
		
		Log.inst().trace("sendHostAvailableDomecasts(): " + reply);
		
		byte[] replyBytes = reply.getBytes();
		
		// We're performing two writes to the OutputStream. They MUST be sequential.
		synchronized (outputStreamLock)
		{
			CommUtils.writeHeader(out, outHdr, replyBytes.length, ClientHeader.kDCS, "", ClientHeader.kINFO);
			CommUtils.writeOutputStream(out, replyBytes, 0, replyBytes.length);
		}
	}
	
	private void sendAssetsFile() throws IOException
	{
		// Create a reference to the file
		File inputFile = getAssetsFile();
		if (inputFile != null)
		{
			// Send it
			Log.inst().info("Sending assets file to host...");
			synchronized (outputStreamLock)
			{
				// Write the header
				CommUtils.writeHeader(out, outHdr, inputFile.length(), ClientHeader.kDCS, "", ClientHeader.kFILE);
				
				// See comments in ServerConnection.sendAssetsFile() regarding inclusion of filename field
				// after normal header.
				byte[] filenameBytes = CommUtils.getRightPaddedByteArray(inputFile.getName(), CommUtils.kMaxPathLen);
				CommUtils.writeOutputStream(out, filenameBytes, 0, filenameBytes.length);
				
				// Write the file contents
				CommUtils.writeOutputStreamFromFile(out, inputFile, commBuffer, null, null);
			}
			Log.inst().info("Sending complete.");
		}
	}
	
	/**
	 * Pass the hdr (already read) and the remaining data on the InputStream to the peer connections's OutputStream.
	 */
	private void handleCOMM(ClientHeader hdr) throws IOException
	{
		Log.inst().trace("handleComm()");
		
		peerConnectionThreads = owner.findPeerConnectionThreads(this);

		// Make sure our buffer is big enough
		if (hdr.messageLen > commBuffer.length)
		{
			Log.inst().info("handleCOMM(): Message length is " + hdr.messageLen + ". Reallocating commBuffer.");
			commBuffer = new byte[(int)(hdr.messageLen * 1.25)];	// Make new commBuffer 25% larger than what we need.
		}
		
		// Read the data after the header
		CommUtils.readInputStream(in, commBuffer, 0, (int)hdr.messageLen);

		if (clientType == CommUtils.kPresenterID)
		{
			boolean writeToSpecificHost = !hdr.messageDestination.isEmpty();
			
			// Write to one or more peer (host) threads
			for (ServerSideConnectionHandlerThread host : peerConnectionThreads)
			{
				// Only write to the host if it is listening
				if (host.isHostListening())
				{
					// Only write to the host if hdr.messageDestination is not specified OR hdr.messageDestination is
					// specified and is equal to the host's connectionID.
					if (!writeToSpecificHost || (writeToSpecificHost && (host.getConnectionID().equals(hdr.messageDestination))))
					{
						synchronized (host.outputStreamLock)
						{
							// Write the header we've received
							CommUtils.writeOutputStream(host.out, hdr.bytes, 0, ClientHeader.kHdrByteCount);
							
							// Write the data we've received
							CommUtils.writeOutputStream(host.out, commBuffer, 0, (int)hdr.messageLen);
						}
					}
				}
			}
		}
		if ((clientType == CommUtils.kHostID) && !peerConnectionThreads.isEmpty())
		{
			ServerSideConnectionHandlerThread presenter = peerConnectionThreads.get(0);
			
			// We only forward the packet if this thread is the "master host" thread.
			if (this == owner.findMasterHostConnectionThread(domecastID))
			{
				synchronized (presenter.outputStreamLock)
				{
					// Write the header we've received
					CommUtils.writeOutputStream(presenter.out, hdr.bytes, 0, ClientHeader.kHdrByteCount);
					
					// Write the data we've received
					CommUtils.writeOutputStream(presenter.out, commBuffer, 0, (int)hdr.messageLen);
				}
			}
		}
	}
	
	public File getAssetsFile()
	{
		if (domecastID == null)
			return null;
			
		File result = null;
		
		// Look in the folder where the assets file should be if it was uploaded
		ServerApplication inst = (ServerApplication) ServerApplication.inst();
		String programDataPath = inst.getProgramDataPath() + domecastID + File.separator;
		File programDataFolder = new File(programDataPath);
		if (programDataFolder.exists())
		{
			// If the assets folder exists, we should find only one file
			String[] files = programDataFolder.list();
			if (files.length > 0)
				result = new File(programDataPath + files[0]);
		}
		
		return result;
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
			Log.inst().trace("Verifying security code sent from client...");
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

		// Close streams. This will also close the socket.
		Log.inst().info("Shutting down connection stream.");
		try
		{
			if (in != null)
				in.close();
			if (out != null)
				out.close();
		}
		catch (IOException e) {
		}
		
		// Notify all peers of this connection shutting down
		try
		{
			peerConnectionThreads = owner.findPeerConnectionThreads(this);
			for (ServerSideConnectionHandlerThread peer : peerConnectionThreads)
				peer.sendBoolean(CommUtils.kIsPeerConnected, false);
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
			buf.append(CommUtils.kIsHostListening + Boolean.toString(isHostListening.get()));
		if (clientType == CommUtils.kPresenterID)
		{
			File assetsFile = getAssetsFile();
			if (assetsFile != null)
				buf.append("Asset file: " + assetsFile.getName());
		}
		
		return buf.toString();
	}
}
