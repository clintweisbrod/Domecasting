/*
 * This thread establishes and maintains a socket connection with the domecasting server.
 * If the connection is lost during communication with the server via another thread, that
 * thread should call this thread's notify() method to wake it up and attempt to re-establish
 * the connection. Once the connection is re-established, this thread goes back to sleep and
 * waits for more shit to happen.
 */
package com.spitzinc.domecasting.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

import com.spitzinc.domecasting.ClientHeader;
import com.spitzinc.domecasting.CommUtils;
import com.spitzinc.domecasting.TCPConnectionHandlerThread;

public class ServerConnectionThread extends Thread
{
	private ClientApplication theApp;
	private String hostName;
	private int port;
	private Socket socket;
	private InputStream in;
	private OutputStream out;
	private Object outputStreamLock;
	private Object inputStreamLock;
	private ClientHeader inHdr;
	private ClientHeader outHdr;
	
	private AtomicBoolean stopped;
	
	public ServerConnectionThread(ClientApplication theApp, String hostName, int port)
	{
		this.theApp = theApp;
		this.hostName = hostName;
		this.port = port;
		this.stopped = new AtomicBoolean(false);
		this.in = null;
		this.out = null;
		this.outputStreamLock = new Object();
		this.inputStreamLock = new Object();
		this.inHdr = new ClientHeader();
		this.outHdr = new ClientHeader();
		
		this.setName(this.getClass().getSimpleName() + "_" + hostName + "_" + port);
	}
	
	public void setStopped() {
		stopped.set(true);
	}
	
	public boolean sendReadyToCast(boolean readyToCast)
	{
		String infoStr = "ReadyToCast=" + Boolean.toString(readyToCast);
		return sendINFO(infoStr);
	}
	
	public boolean isPeerReady()
	{
		boolean result = false;
		
		String reply = sendREQU("IsPeerReady");
		if (reply != null)
		{
			String[] list = reply.split("=");
			if (list[0].equals("IsPeerReady"))
				result = Boolean.parseBoolean(list[1]);
		}
		
		return result;
	}
	
	public boolean isConnected()
	{
		if ((socket != null) && socket.isConnected())
			return true;
		else
			return false;
	}
	
	public String getAvailableDomecasts()
	{
		return sendREQU("GetAvailableDomecasts");
	}
	
	public boolean sendDomecastID(String domecastID)
	{
		String infoStr = "DomecastID=" + domecastID;
		return sendINFO(infoStr);
	}
	
	public boolean sendClientType(byte clientType)
	{
		String infoStr = "ClientType=" + (char)clientType;
		return sendINFO(infoStr);
	}
	
	private boolean sendINFO(String infoString)
	{
		boolean result = false;
		
		if ((socket != null) && socket.isConnected())
		{
			byte[] theBytes = infoString.getBytes();
			
			// We're performing two writes to the OutputStream. They MUST be sequential.
			synchronized (outputStreamLock)
			{
				try {
					CommUtils.writeHeader(out, outHdr, theBytes.length, ClientHeader.kDCC, ClientHeader.kDCS, ClientHeader.kINFO, getName());
					CommUtils.writeOutputStream(out, theBytes, 0, theBytes.length, this.getName());
					result = false;
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		
		return result;
	}
	
	private String sendREQU(String requString)
	{
		String result = null;
		
		if ((socket != null) && socket.isConnected())
		{
			byte[] theBytes = requString.getBytes();
			
			// We're performing two writes to the OutputStream. They MUST be sequential.
			boolean requestSent = false;
			synchronized (outputStreamLock)
			{
				try {
					CommUtils.writeHeader(out, outHdr, theBytes.length, ClientHeader.kDCC, ClientHeader.kDCS, ClientHeader.kREQU, getName());
					CommUtils.writeOutputStream(out, theBytes, 0, theBytes.length, getName());
					requestSent = true;
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			if (requestSent)
			{
				synchronized (inputStreamLock)
				{
					// Read and parse the header
					try {
						CommUtils.readInputStream(in, inHdr.bytes, 0, ClientHeader.kHdrByteCount, getName());
						if (inHdr.parseHeaderBuffer())
						{
							if (inHdr.messageType.equals(ClientHeader.kREQU))
							{
								byte[] requBytes = new byte[inHdr.messageLen];
								CommUtils.readInputStream(in, requBytes, 0, requBytes.length, getName());
								result = new String(requBytes);
							}
						}
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
		
		return result;
	}
	
	private void writeSecurityCode() throws IOException
	{
		// Allocate byte buffer for security code
		byte[] buffer = new byte[CommUtils.kSecurityCodeLength];
		
		// Insert daily security code in buffer
		String securityCode = CommUtils.getDailySecurityCode();
		System.arraycopy(securityCode.getBytes(), 0, buffer, 0, securityCode.length());
	
		// Writing to the connectionThread output stream must be thread synchronized
		synchronized (outputStreamLock) {
			CommUtils.writeOutputStream(out, buffer, 0, CommUtils.kSecurityCodeLength, getName());
		}
	}
	
	public void run()
	{
		// Attempt to maintain a socket connection to the server
		while (!stopped.get())
		{
			if (socket == null)
			{
				System.out.println(this.getName() + ": Attempting server connection...");
				socket = TCPConnectionHandlerThread.connectToHost(hostName, port, this.getName());
				if (socket != null)
				{
					// If we get here, we've established a connection with the server
					try	{
						// Obtain streams to read/write
						in = socket.getInputStream();
						out = socket.getOutputStream();
						
						// Send initial handshake to server
						writeSecurityCode();
						byte clientType;
						if (theApp.appFrame.tabbedPane.getSelectedIndex() == 0)
							clientType = CommUtils.kHostID;
						else
							clientType = CommUtils.kPresenterID;
						sendClientType(clientType);
					}
					catch (IOException e) {
						e.printStackTrace();
					}
				}
				else
				{
					// Wait a few seconds and then try again
					final int kServerConnectionRetryIntervalSeconds = 5;
					System.out.println(this.getName() + ": Server connection failed. Trying again in " + kServerConnectionRetryIntervalSeconds + " seconds.");
					try {
						Thread.sleep(kServerConnectionRetryIntervalSeconds * 1000);
					} catch (InterruptedException e) {}
				}
			}
			
			if (!stopped.get() && (in != null) && (out != null))
			{
				// Everything looks good so wait() here until another thread calls notify() on this thread.
				// Another thread will call notify() if that thread determines that this.socket is not connected.
				synchronized(this) {
					try {
						wait();
					} catch (InterruptedException e) {
					}
				}
				
				// Check to see if socket is still connected
				if (!socket.isConnected())
				{
					// Close the socket. This will also close the input and output streams
					try {
						socket.close();
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					
					socket = null;
					in = null;
					out = null;
				}
			}
		}
		
		System.out.println(this.getName() + ": Exiting thread.");
	}
}
