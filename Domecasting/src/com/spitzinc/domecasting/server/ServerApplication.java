package com.spitzinc.domecasting.server;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.IOException;
import java.util.Properties;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.spitzinc.domecasting.ApplicationBase;
import com.spitzinc.domecasting.Log;
import com.spitzinc.domecasting.SortedProperties;
import com.spitzinc.domecasting.server.ServerAppFrame;

public class ServerApplication extends ApplicationBase implements WindowListener
{
	private static final int kAppDefaultWidth = 600;
	private static final int kAppDefaultHeight = 200;
	private static final Dimension kPreferredFrameSize = new Dimension(kAppDefaultWidth, kAppDefaultHeight);
	private static final String kApplicationWindowTitle = "Spitz Dome Casting Server";
	
	private static ApplicationBase createSingleInstance() {
		if (singleInstance == null)
			singleInstance = new ServerApplication();
		return singleInstance; // could be null. only we should be able to create our own
	}
	
	// Prefs (with defaults)
	protected static final String kPrefsFileName = "server.properties";
	public int domecastingServerPort = 80;
	public int maxConcurrentSessions = 5;
	
	public ServerAppFrame appFrame;
	public ServerSideConnectionListenerThread connectionListenerThread;
	
	public ServerApplication()
	{
		// Configure logger
		configureLog4j("src/com/spitzinc/domecasting/server");
		
		Log.inst().info("Starting instance of " + getClass().getSimpleName());
		
		readPrefs();
		
		// Start up thread to listen for incoming connections on port 80
		try {
			connectionListenerThread = new ServerSideConnectionListenerThread(80, maxConcurrentSessions);
			connectionListenerThread.start();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void readPrefs()
	{
		Properties props = readPropertiesFromFile(getPropertiesFile(kPrefsFileName));
		if (props != null)
		{
			domecastingServerPort = getIntegerProperty(props, "domecastingServerPort", domecastingServerPort);
			maxConcurrentSessions = getIntegerProperty(props, "maxConcurrentSessions", maxConcurrentSessions);
		}
	}
	
	private void writePrefs()
	{
		Properties props = new SortedProperties();
		
		props.setProperty("domecastingServerPort", Integer.toString(domecastingServerPort));
		props.setProperty("maxConcurrentSessions", Integer.toString(maxConcurrentSessions));
		
		this.writePropertiesToFile(getPropertiesFile(kPrefsFileName), props);
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
		SwingUtilities.invokeLater(new Runnable() {
			public void run()
			{
				ServerApplication.createSingleInstance();
				
				ActionListener taskPerformer = new ActionListener() {
				    public void actionPerformed(ActionEvent evt)
				    {
				    	ServerApplication inst = (ServerApplication)ServerApplication.inst();
				    	if (inst.connectionListenerThread != null)
				    		inst.connectionListenerThread.displayStatus(inst);
				    }
				};
				Timer timer = new Timer(2000, taskPerformer);
				timer.setRepeats(true);
				timer.start();
			}
		});
	}
}
