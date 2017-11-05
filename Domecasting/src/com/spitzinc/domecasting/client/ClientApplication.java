package com.spitzinc.domecasting.client;

import java.awt.Dimension;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.IOException;

import javax.swing.*;

import com.spitzinc.domecasting.ApplicationBase;

public class ClientApplication extends ApplicationBase implements WindowListener
{
	private static final String kDomecastingServerHostname = "localhost";
	private static final int kDomecastingServerPort = 80;
	
	private static final int kAppDefaultWidth = 400;
	private static final int kAppDefaultHeight = 200;
	private static final Dimension kPreferredFrameSize = new Dimension(kAppDefaultWidth, kAppDefaultHeight);
	private static final String kApplicationWindowTitle = "Spitz Dome Casting Client";
	
	private static ApplicationBase createSingleInstance() {
		if (singleInstance == null)
			singleInstance = new ClientApplication();
		return singleInstance; // could be null. only we should be able to create our own
	}
	
	public ClientAppFrame appFrame;
	private SNTCPPassThruServer snPassThru = null;
	private ServerConnectionWriteThread serverConnectionThread;
	
	public ClientApplication()
	{
		System.out.println("Starting instance of " + this.getClass().getSimpleName());
		
		// Start thread to establish connection with server
		serverConnectionThread = new ServerConnectionWriteThread(kDomecastingServerHostname,
																 kDomecastingServerPort,
																 ServerConnectionWriteThread.ClientConnectionType.HOST);
		serverConnectionThread.start();
		
		// Start threads to handle pass-thru of local SN comm. Both presenter and host modes
		// of the client require these connections to be established.
		try
		{
			final int kPFPrefs_DomeServer_TCPPort = 56895;
			snPassThru = new SNTCPPassThruServer(kPFPrefs_DomeServer_TCPPort, 56898);
			snPassThru.start();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	protected void createUIElements()
	{
		appFrame = new ClientAppFrame(this, kPreferredFrameSize);
		appFrame.setTitle(kApplicationWindowTitle);
		appFrame.addWindowListener(this);
		appFrame.pack();
		appFrame.setResizable(false);
		appFrame.setVisible(true);
	}
	
	public void setPresentationID(String presentationID)
	{
		serverConnectionThread.sendPresentationID(presentationID);
	}

	@Override
	public void windowClosing(WindowEvent arg0)
	{
		// Force the server connection thread to finish
		serverConnectionThread.setStopped();
		serverConnectionThread.interrupt();
		try {
			serverConnectionThread.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if (snPassThru != null)
			snPassThru.stop();

		System.exit(0);
	}
	
	public static void main(String[] args)
	{
//		final String[] argsCopy = args;
		SwingUtilities.invokeLater(new Runnable() {
			public void run()
			{
				ClientApplication.createSingleInstance();
			}
		});
	}
}
