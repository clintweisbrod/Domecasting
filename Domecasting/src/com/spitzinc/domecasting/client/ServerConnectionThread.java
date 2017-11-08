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
	private String hostName;
	private int port;
	private Socket socket;
	private InputStream in;
	private OutputStream out;
	private Object outputStreamLock;
	private Object inputStreamLock;
	
	private ClientHeader hdr;
	
	private AtomicBoolean stopped;
	
	public ServerConnectionThread(String hostName, int port)
	{
		this.hostName = hostName;
		this.port = port;
		this.stopped = new AtomicBoolean(false);
		this.in = null;
		this.out = null;
		this.outputStreamLock = new Object();
		this.inputStreamLock = new Object();
		
		this.hdr = new ClientHeader();		
		this.setName(this.getClass().getSimpleName() + "_" + hostName + "_" + port);
	}
	
	public void setStopped() {
		stopped.set(true);
	}
	
	public boolean writeOutputStream(byte[] buffer, int offset, int len)
	{
		boolean result = true;
		
		try
		{
			out.write(buffer, offset, len);
			System.out.println(this.getName() + ": Wrote " + len + " bytes to socket.");
		}
		catch (IOException e) {
			result = false;
			System.out.println(this.getName() + ": Failed writing outbound socket.");
		}
		
		return result;
	}
	
	public boolean sendPresentationID(String presentationID)
	{
		boolean result = false;
		
		String infoStr = "PresentationID=" + presentationID;
		if (sendInfo(infoStr))
		{
			// Also send ClientApplication.clientType
			ClientApplication inst = (ClientApplication) ClientApplication.inst();
			infoStr = "ClientType=" + (char)inst.clientType;
			result = sendInfo(infoStr);
		}
		
		return result;
	}
	
	public boolean sendReadyToCast(boolean readyToCast)
	{
		String infoStr = "ReadyToCast=" + Boolean.toString(readyToCast);
		return sendInfo(infoStr);
	}
	
	private boolean sendInfo(String infoString)
	{
		boolean result = false;
		
		if (socket.isConnected())
		{
			byte[] theBytes = infoString.getBytes();
			
			// We're performing two writes to the OutputStream. They MUST be sequential.
			synchronized (outputStreamLock)
			{
				if (CommUtils.writeHeader(out, hdr, theBytes.length, ClientHeader.kDCC, ClientHeader.kDCS, ClientHeader.kINFO, this.getName()))
					result = CommUtils.writeOutputStream(out, theBytes, 0, theBytes.length, this.getName());
			}
		}
		
		return result;
	}
	
	private void sendInitialHandshake() throws IOException
	{
		synchronized (outputStreamLock)
		{
			// Write security code
			if (!writeSecurityCode())
				throw new IOException("Failed to write security code.");
		}
	}
	
	private boolean writeSecurityCode()
	{
		// Allocate byte buffer for security code
		byte[] buffer = new byte[TCPConnectionHandlerThread.kSecurityCodeLength];
		
		// Insert daily security code in buffer
		String securityCode = TCPConnectionHandlerThread.getDailySecurityCode();
		System.arraycopy(securityCode.getBytes(), 0, buffer, 0, securityCode.length());
	
		// Writing to the connectionThread output stream must be thread synchronized
		return writeOutputStream(buffer, 0, TCPConnectionHandlerThread.kSecurityCodeLength);
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
						sendInitialHandshake();
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
