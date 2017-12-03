package com.spitzinc.domecasting.client;

import java.io.File;

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
	private File assetFile;
	private Boolean getAssetsFile;
	
	public ClientInfoSendThread()
	{
		this.clientType = null;
		this.domecastID = null;
		this.isHostListening = null;
		this.assetFile = null;
		this.getAssetsFile = null;
		
		setName(getClass().getSimpleName());
	}
	
	public void setClientType(Byte clientType) {
		this.clientType = clientType;
	}
	
	public void setDomecastID(String domecastID) {
		this.domecastID = domecastID;
	}
	
	public void setIsHostListening(Boolean isHostListening) {
		this.isHostListening = isHostListening;
	}
	
	public void setAssetsFile(File assetFile) {
		this.assetFile = assetFile;
	}
	
	public void setGetAssetsFile(Boolean getAssetsFile) {
		this.getAssetsFile = getAssetsFile;
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
			if (assetFile != null)
				inst.serverConnection.sendAssetsFile(assetFile);
			if (getAssetsFile != null)
				inst.serverConnection.sendGetAssetsFile();
		}

		Log.inst().info("Exiting.");
	}
}
