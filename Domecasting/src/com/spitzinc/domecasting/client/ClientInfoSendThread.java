package com.spitzinc.domecasting.client;

/**
 * Create and run instances of this thread so that we're not trying to comm with server using
 * event dispatch thread. 
 */
public class ClientInfoSendThread extends Thread
{
	private Byte clientType;
	private String domecastID;
	private Boolean readyToCast;
		
	public ClientInfoSendThread(Byte clientType, String domecastID, Boolean readyToCast)
	{
		this.clientType = clientType;
		this.domecastID = domecastID;
		this.readyToCast = readyToCast;
		this.setName(getClass().getSimpleName());
	}
	
	public void run()
	{
		System.out.println(getName() + " is starting.");
		
		ClientApplication inst = (ClientApplication) ClientApplication.inst();
		if (clientType != null)
			inst.sendClientType(clientType.byteValue());
		if (readyToCast != null)
			inst.sendHostReadyToCast(readyToCast.booleanValue());
		if (domecastID != null)
			inst.sendDomecastID(domecastID);

		System.out.println(getName() + " is exiting.");
	}
}
