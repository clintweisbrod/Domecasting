package com.spitzinc.domecasting.client;

import java.awt.Dimension;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.*;

import com.spitzinc.domecasting.ApplicationBase;
import com.spitzinc.domecasting.CommUtils;
import com.spitzinc.domecasting.JSwitchButton;
import com.spitzinc.domecasting.Log;
import com.spitzinc.domecasting.SortedProperties;

public class ClientApplication extends ApplicationBase implements WindowListener
{
	private static final int kAppDefaultWidth = 400;
	private static final int kAppDefaultHeight = 200;
	private static final Dimension kPreferredFrameSize = new Dimension(kAppDefaultWidth, kAppDefaultHeight);
	private static final String kApplicationWindowTitle = "Spitz Dome Casting Client";
	private static final String kSNPrefsFolderPath = "Simulation Curriculum" + File.separator + "Starry Night Prefs" + File.separator +
													 "Preflight" + File.separator + "Prefs.txt";
	
	private static ApplicationBase createSingleInstance() {
		if (singleInstance == null)
			singleInstance = new ClientApplication();
		return singleInstance; // could be null. only we should be able to create our own
	}
	
	// Prefs (with defaults)
	protected static final String kPrefsFileName = "client.properties";
	public String domecastingServerHostname = "localhost";
	public int domecastingServerPort = 80;
	public String renderboxHostname = "localhost";
	public int maxClientConnections = 5;
	public int rbPrefs_DomeServer_TCPPort = 56897;	// For typical two-machine setup, this should be the usual 56895.
													// For testing on a single machine, needs to be 56897.
	public int pfPrefs_DomeServer_TCPPort = 56895;	// This is read from Preflight Prefs.txt
	public int passThruReceiveListenerPort = 56898;
	public String lastAssetsOpenFolder = null;
	public String lastAssetsSaveFolder = null;
	public String log4jLevel = "info";
	
	public ClientAppFrame appFrame;
	public SNTCPPassThruServer snPassThru;
	public ServerConnection serverConnection;
	
	public AtomicReference<String> statusText;
	public AtomicBoolean isConnectedToServer;
	public AtomicBoolean isPeerConnected;
	public AtomicBoolean isDomecastIDUnique;	// Relevant only for presenter
	public String availableDomecasts;			// Relevant only for hosts
	public AtomicBoolean isHostListening;		// Relevant for both host and presenter.
	public AtomicReference<String> requestFullState;
	public AtomicBoolean assetsFileAvailable;	// Relevant only for hosts
	public AtomicInteger fileProgress;
	
	public byte clientType = CommUtils.kHostID;
	
	public ClientApplication()
	{
		// Read preferences
		System.out.println("Reading preferences...");
		readPrefs();

		// Configure logger
		System.out.println("Configuring log4j...");
		Log.configure("com/spitzinc/domecasting/client", getPropertiesPath(), log4jLevel);
		
		Log.inst().info("Starting instance of " + getClass().getSimpleName());
		
		// Status from server
		this.statusText = new AtomicReference<String>();
		this.isConnectedToServer = new AtomicBoolean(false);
		this.isPeerConnected = new AtomicBoolean(false);
		this.isDomecastIDUnique = new AtomicBoolean(false);
		this.isHostListening = new AtomicBoolean(false);
		this.requestFullState = new AtomicReference<String>();
		this.assetsFileAvailable = new AtomicBoolean(false);
		this.fileProgress = new AtomicInteger(0);
	
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
		
		if (JSwitchButton.kDebug)
			this.isPeerConnected.set(true);
	}
	
	private void readPrefs()
	{
		File propsFile = getPropertiesFile(kPrefsFileName);
		System.out.println("Attempting to read: " + propsFile.getAbsolutePath());
		Properties props = readPropertiesFromFile(propsFile);
		if (props != null)
		{
			domecastingServerHostname = getStringProperty(props, "domecastingServerHostname", domecastingServerHostname);
			domecastingServerPort = getIntegerProperty(props, "domecastingServerPort", domecastingServerPort);
			renderboxHostname = getStringProperty(props, "renderboxHostname", renderboxHostname);
			maxClientConnections = getIntegerProperty(props, "maxClientConnections", maxClientConnections);
			rbPrefs_DomeServer_TCPPort = getIntegerProperty(props, "rbPrefs_DomeServer_TCPPort", rbPrefs_DomeServer_TCPPort);
			passThruReceiveListenerPort = getIntegerProperty(props, "passThruReceiveListenerPort", passThruReceiveListenerPort);
			lastAssetsOpenFolder = getStringProperty(props, "lastAssetsOpenFolder", lastAssetsOpenFolder);
			lastAssetsSaveFolder = getStringProperty(props, "lastAssetsSaveFolder", lastAssetsSaveFolder);
			log4jLevel = getStringProperty(props, "log4jLevel", log4jLevel);
		}
		
		// To save configuration details, we also want to look in SN Preflight's Prefs.txt file to obtain the value
		// of DomeServer_TCPPort
		String domeServer_TCPPort = readSNPreflightPrefValue("DomeServer_TCPPort");
		if (domeServer_TCPPort != null)
		{
			try {
				pfPrefs_DomeServer_TCPPort = Integer.parseInt(domeServer_TCPPort);
			}
			catch (NumberFormatException e) {
				e.printStackTrace();
			}
		}
		else
		{
			// If we cannot locate PF Prefs, then we assume PF is not installed and exit.
			final String errMsg = "Unable to locate Starry Night PF preferences. Shutting down.";
			System.out.println(errMsg);
			JOptionPane.showMessageDialog(null, errMsg);
			System.exit(0);
		}
	}
	
	private void writePrefs()
	{
		Properties props = new SortedProperties();
		
		props.setProperty("domecastingServerHostname", domecastingServerHostname);
		props.setProperty("domecastingServerPort", Integer.toString(domecastingServerPort));
		props.setProperty("renderboxHostname", renderboxHostname);
		props.setProperty("maxClientConnections", Integer.toString(maxClientConnections));
		props.setProperty("rbPrefs_DomeServer_TCPPort", Integer.toString(rbPrefs_DomeServer_TCPPort));
		props.setProperty("passThruReceiveListenerPort", Integer.toString(passThruReceiveListenerPort));
		if (lastAssetsOpenFolder != null)
			props.setProperty("lastAssetsOpenFolder", lastAssetsOpenFolder);
		if (lastAssetsSaveFolder != null)
			props.setProperty("lastAssetsSaveFolder", lastAssetsSaveFolder);
		props.setProperty("log4jLevel", log4jLevel);
		
		this.writePropertiesToFile(getPropertiesFile(kPrefsFileName), props);
	}
	
	protected String readSNPreflightPrefValue(String valueName)
	{
		String result = null;

		String snPrefsFilePath = System.getenv("LOCALAPPDATA") + File.separator + kSNPrefsFolderPath;
		String lineStart = "<SN_VALUE name=\"" + valueName + "\"";
		try {
			System.out.println("Attempting to read: " + snPrefsFilePath);
			BufferedReader br = new BufferedReader(new FileReader(snPrefsFilePath));
			String line = br.readLine();
			while (line != null)
			{
				if (line.startsWith(lineStart))
				{
					int index1 = line.indexOf("value=");
					if (index1 != -1)
					{
						index1 = line.indexOf('"', index1);
						if (index1 != -1)
						{
							int index2 = line.indexOf('"', index1 + 1);
							if (index2 != -1)
							{
								result = line.substring(index1 + 1, index2);
								break;
							}
							else
								break;
						}
						else
							break;
					}
					else
						break;
				}
				line = br.readLine();
			}
			br.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return result;
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
	
	public void updateUI()
	{
		// Notify the UI of changes
		synchronized(appFrame.tabbedPane) {
			appFrame.tabbedPane.notify();
		}
	}
	
	public void handleServerDisconnect()
	{
		Log.inst().info("Server connection lost.");
		isConnectedToServer.set(false);
		availableDomecasts = null;
		assetsFileAvailable.set(false);
		statusText.set("Spitz Domecasting server not available.");
		updateUI();
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
		if (serverConnection != null)
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
