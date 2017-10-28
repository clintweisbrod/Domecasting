package com.spitzinc.domecasting.client;

public class TCPNode
{
	public String hostname;
	public int port;
	public int replyPort;
	
	public TCPNode(String inHostName, int inPort)
	{
		this.hostname = inHostName;
		this.port = inPort;
		this.replyPort = -1;
	}
	
	public TCPNode(String inHostName, int inPort, int replyPort)
	{
		this.hostname = inHostName;
		this.port = inPort;
		this.replyPort = replyPort;
	}
}
