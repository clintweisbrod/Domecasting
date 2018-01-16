package com.spitzinc.domecasting.client;

public class TCPNode
{
	public String hostname;
	public int port;
	
	public TCPNode(String inHostName, int inPort)
	{
		this.hostname = inHostName;
		this.port = inPort;
	}
}
