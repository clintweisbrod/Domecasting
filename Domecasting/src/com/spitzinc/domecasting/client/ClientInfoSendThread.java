package com.spitzinc.domecasting.client;

import com.spitzinc.domecasting.Log;

/**
 * Create and run instances of this thread so that we're not trying to comm with server using
 * event dispatch thread. 
 */
public class ClientInfoSendThread extends Thread
{
	private Byte clientType;
	private String domecastID;
	private Boolean isHostListening;
		
	public ClientInfoSendThread(Byte clientType, String domecastID, Boolean isHostListening)
	{
		this.clientType = clientType;
		this.domecastID = domecastID;
		this.isHostListening = isHostListening;
		this.setName(getClass().getSimpleName());
	}
	
	public void run()
	{
		Log.inst().info("Starting.");
		
		ClientApplication inst = (ClientApplication) ClientApplication.inst();
		if (inst.serverConnection != null)
		{
			if (clientType != null)
				inst.serverConnection.sendClientType(clientType.byteValue());
			if (isHostListening != null)
				inst.serverConnection.sendIsHostListening(isHostListening.booleanValue());
			if (domecastID != null)
				inst.serverConnection.sendDomecastID(domecastID);
		}

		Log.inst().info("Exiting.");
	}
}
