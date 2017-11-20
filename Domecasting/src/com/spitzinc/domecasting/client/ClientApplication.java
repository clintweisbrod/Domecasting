package com.spitzinc.domecasting.client;

import java.awt.Dimension;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.*;

import com.spitzinc.domecasting.ApplicationBase;
import com.spitzinc.domecasting.CommUtils;
import com.spitzinc.domecasting.Log;
import com.spitzinc.domecasting.SortedProperties;

public class ClientApplication extends ApplicationBase implements WindowListener
{
	private static final int kAppDefaultWidth = 400;
	private static final int kAppDefaultHeight = 200;
	private static final Dimension kPreferredFrameSize = new Dimension(kAppDefaultWidth, kAppDefaultHeight);
	private static final String kApplicationWindowTitle = "Spitz Dome Casting Client";
	
	private static ApplicationBase createSingleInstance() {
		if (singleInstance == null)
			singleInstance = new ClientApplication();
		return singleInstance; // could be null. only we should be able to create our own
	}
	
	// Prefs (with defaults)
	protected static final String kPrefsFileName = "client.properties";
	public String domecastingServerHostname = "localhost";
	public int domecastingServerPort = 80;
	public int maxClientConnections = 5;
	public int rbPrefs_DomeServer_TCPPort = 56897;	// For typical two-machine setup, this should be the usual 56895.
													// For testing on a single machine, needs to be 56897.
	public int pfPrefs_DomeServer_TCPPort = 56895;
	public int pfPrefs_DomeServer_TCPReplyPort = 56896;
	public int passThruReceiveListenerPort = 56898;
	
	public ClientAppFrame appFrame;
	private SNTCPPassThruServer snPassThru = null;
	
	public ServerConnection serverConnection;
	public AtomicReference<String> statusText;
	public AtomicBoolean isConnected;
	public AtomicBoolean isPeerReady;
	public AtomicBoolean isDomecastIDUnique;
	public String availableDomecasts;
	
	public byte clientType = CommUtils.kHostID;
	
	public ClientApplication()
	{
		// Configure logger
		configureLog4j("src/com/spitzinc/domecasting/client");
		
		Log.inst().info("Starting instance of " + getClass().getSimpleName());
		
		readPrefs();

		// Status from server
		this.statusText = new AtomicReference<String>();
		this.isConnected = new AtomicBoolean(false);
		this.isPeerReady = new AtomicBoolean(false);
		this.isDomecastIDUnique = new AtomicBoolean(false);
	
		// Create object to manage connection with server
		serverConnection = new ServerConnection(this, domecastingServerHostname, domecastingServerPort);
		
		// Start threads to handle pass-thru of local SN comm. Both presenter and host modes
		// of the client require these connections to be established.
		try
		{
			snPassThru = new SNTCPPassThruServer(pfPrefs_DomeServer_TCPPort, passThruReceiveListenerPort, maxClientConnections);
			snPassThru.start();
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
			domecastingServerHostname = getStringProperty(props, "domecastingServerHostname", domecastingServerHostname);
			domecastingServerPort = getIntegerProperty(props, "domecastingServerPort", domecastingServerPort);
			maxClientConnections = getIntegerProperty(props, "maxClientConnections", maxClientConnections);
			pfPrefs_DomeServer_TCPPort = getIntegerProperty(props, "pfPrefs_DomeServer_TCPPort", pfPrefs_DomeServer_TCPPort);
			pfPrefs_DomeServer_TCPReplyPort = getIntegerProperty(props, "pfPrefs_DomeServer_TCPReplyPort", pfPrefs_DomeServer_TCPReplyPort);
			rbPrefs_DomeServer_TCPPort = getIntegerProperty(props, "rbPrefs_DomeServer_TCPPort", rbPrefs_DomeServer_TCPPort);
			passThruReceiveListenerPort = getIntegerProperty(props, "passThruReceiveListenerPort", passThruReceiveListenerPort);
		}
	}
	
	private void writePrefs()
	{
		Properties props = new SortedProperties();
		
		props.setProperty("domecastingServerHostname", domecastingServerHostname);
		props.setProperty("domecastingServerPort", Integer.toString(domecastingServerPort));
		props.setProperty("maxClientConnections", Integer.toString(maxClientConnections));
		props.setProperty("pfPrefs_DomeServer_TCPPort", Integer.toString(pfPrefs_DomeServer_TCPPort));
		props.setProperty("pfPrefs_DomeServer_TCPReplyPort", Integer.toString(pfPrefs_DomeServer_TCPReplyPort));
		props.setProperty("rbPrefs_DomeServer_TCPPort", Integer.toString(rbPrefs_DomeServer_TCPPort));
		props.setProperty("passThruReceiveListenerPort", Integer.toString(passThruReceiveListenerPort));
		
		this.writePropertiesToFile(getPropertiesFile(kPrefsFileName), props);
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
	
	public void updateStatusText(String inStatusText)
	{
		statusText.set(inStatusText);

		// Notify the UI of changes
		synchronized(appFrame.tabbedPane) {
			appFrame.tabbedPane.notify();
		}
	}
	
	public synchronized boolean routeComm()
	{
		// Decide if the necessary conditions are in place to begin routing comm
		boolean result = false;
		
		if (clientType == CommUtils.kPresenterID)
		{
			String domecastID = appFrame.presenterPanel.getDomecastID();
			result = isPeerReady.get() && (domecastID.length() >= PresenterPanel.kMinDomecastIDLength);
		}
		else
		{
			String domecastID = appFrame.hostPanel.getDomecastID();
			boolean domecastStarted = appFrame.hostPanel.domecastOn.get();
			result = isPeerReady.get() && domecastStarted && (domecastID.length() >= PresenterPanel.kMinDomecastIDLength);
		}
		
		return result;
	}
	
	public InputStream getServerInputStream()
	{
		InputStream result = null;
		
		if (serverConnection != null)
			result = serverConnection.getInputStream();

		return result;
	}
	
	public OutputStream getServerOutputStream()
	{
		OutputStream result = null;
		
		if (serverConnection != null)
			result = serverConnection.getOutputStream();

		return result;
	}

	@Override
	public void windowClosing(WindowEvent arg0)
	{
		appFrame.stopUpdateThread();

		// Force the server connection threads to finish
		serverConnection.shutdown();
		
		if (snPassThru != null)
			snPassThru.stop();
		
		writePrefs();

		System.exit(0);
	}
	
	public static void main(String[] args)
	{
		SwingUtilities.invokeLater(new Runnable() {
			public void run()
			{
				ClientApplication.createSingleInstance();
			}
		});
	}
}
