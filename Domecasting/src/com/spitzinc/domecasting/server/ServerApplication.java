package com.spitzinc.domecasting.server;

import java.awt.Dimension;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.IOException;
import java.util.Properties;

import javax.swing.SwingUtilities;

import com.spitzinc.domecasting.ApplicationBase;
import com.spitzinc.domecasting.server.ServerAppFrame;

public class ServerApplication extends ApplicationBase implements WindowListener
{
	private static final String kProductName = "Domecasting Server";
	private static final int kAppDefaultWidth = 400;
	private static final int kAppDefaultHeight = 200;
	private static final Dimension kPreferredFrameSize = new Dimension(kAppDefaultWidth, kAppDefaultHeight);
	private static final String kApplicationWindowTitle = "Spitz Dome Casting Server";
	
	private static ApplicationBase createSingleInstance() {
		if (singleInstance == null)
			singleInstance = new ServerApplication();
		return singleInstance; // could be null. only we should be able to create our own
	}
	
	// Prefs (with defaults)
	public int domecastingServerPort = 80;
	
	public ServerAppFrame appFrame;
	private ServerSideConnectionListenerThread connectionListenerThread;
	
	public ServerApplication()
	{
		System.out.println("Starting instance of " + this.getClass().getSimpleName());
		
		readPrefs();
		
		// Start up thread to listen for incoming connections on port 80
		try {
			connectionListenerThread = new ServerSideConnectionListenerThread(80);
			connectionListenerThread.start();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void readPrefs()
	{
		Properties props = readPropertiesFromFile(getPropertiesFile(kProductName));
		if (props != null)
		{
			domecastingServerPort = getIntegerProperty(props, "domecastingServerPort", domecastingServerPort);
		}
	}
	
	private void writePrefs()
	{
		Properties props = new Properties();
		
		props.setProperty("domecastingServerPort", Integer.toString(domecastingServerPort));
		
		this.writePropertiesToFile(getPropertiesFile(kProductName), props);
	}
	
	protected void createUIElements()
	{
		appFrame = new ServerAppFrame(this, kPreferredFrameSize);
		appFrame.setTitle(kApplicationWindowTitle);
		appFrame.addWindowListener(this);
		appFrame.pack();
		appFrame.setResizable(false);
		appFrame.setVisible(true);
	}
	
	@Override
	public void windowClosing(WindowEvent arg0)
	{
		if (connectionListenerThread != null)
			connectionListenerThread.interrupt();
		
		writePrefs();
		
		System.exit(0);
	}
	
	public static void main(String[] args)
	{
//		final String[] argsCopy = args;
		SwingUtilities.invokeLater(new Runnable() {
			public void run()
			{
				ServerApplication.createSingleInstance();
			}
		});
	}
}
