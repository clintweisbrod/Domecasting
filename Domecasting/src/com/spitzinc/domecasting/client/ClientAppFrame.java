package com.spitzinc.domecasting.client;

import com.spitzinc.domecasting.CommUtils;

import java.awt.Dimension;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JFrame;
import javax.swing.border.EmptyBorder;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;

public class ClientAppFrame extends JFrame
{
	private static final long serialVersionUID = 1L;
	
	public enum ConnectionStatus {eNotConnected, eConnectedNoPeer, eConnectedWithPeer};
	
	private ClientApplication theApp;
	private JTabbedPane tabbedPane;
	private HostPanel hostPanel;
	private PresenterPanel presenterPanel;
	private ServerStatusThread statusThread;
	
	// While domecast is not in progress, this thread periodically wakes up to poll server for info
	// and updates the UI accordingly.
	private class ServerStatusThread extends Thread
	{
		private static final int kPollIntervalSeconds = 5;
		
		public AtomicBoolean stopped;
		public AtomicBoolean paused;
		
		public ServerStatusThread()
		{
			this.stopped = new AtomicBoolean(false);
			this.paused = new AtomicBoolean(false);
		}
		
		public void run()
		{
			boolean isConnected, isPeerReady;
			while (!stopped.get())
			{
				if (!paused.get())
				{
					// Get server status
					isConnected = theApp.isConnected();
					isPeerReady = theApp.isPeerReady();
					
					// Get list of hosts currently connected to server
					String[] hosts = null;
					if (theApp.clientType == CommUtils.kPresenterID)
						hosts = theApp.getConnectedHosts();
					
					// Update panel
					if (!isConnected)
						setPanelStatus(ConnectionStatus.eNotConnected, hosts);
					else
					{
						if (!isPeerReady)
							setPanelStatus(ConnectionStatus.eConnectedNoPeer, hosts);
						else
							setPanelStatus(ConnectionStatus.eConnectedWithPeer, hosts);
					}
					
					// Sleep for a few seconds
					try {
						sleep(kPollIntervalSeconds * 1000);
					} catch (InterruptedException e) {
					}
				}
				
				// If domecast communication is in progress, we don't want this thread continually
				// requesting info from the server.
				if (paused.get())
				{
					synchronized(this) {
						try {
							wait();	// Wait here indefinitely until another thread calls this thread's notify() method.
						} catch (InterruptedException e) {}
					}
				}
			}
		}
	}

	/**
	 * Create the frame.
	 */
	public ClientAppFrame(ClientApplication theApp, Dimension inPreferredSize)
	{
		this.theApp = theApp;
		
		setType(Type.NORMAL);
		theApp.appFrame = this;

		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);	// Application WindowListener handles close

		setPreferredSize(inPreferredSize);

		hostPanel = new HostPanel();
		presenterPanel = new PresenterPanel();

		tabbedPane = new JTabbedPane();
		tabbedPane.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent arg0)
			{
				int newIndex = tabbedPane.getSelectedIndex();
				switch (newIndex)
				{
				case 0:
					theApp.clientType = CommUtils.kHostID;
					break;
				case 1:
					theApp.clientType = CommUtils.kPresenterID;
					break;
				}
			}
		});
		tabbedPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		tabbedPane.addTab("Host", hostPanel);
		tabbedPane.addTab("Presenter", presenterPanel);
		setContentPane(tabbedPane);

		theApp.positionFrame(this);
		setResizable(false);

		// Run status thread
		this.statusThread = new ServerStatusThread();
		this.statusThread.start();
		
	}
	
	public String getHostID() {
		return hostPanel.getHostID();
	}
	
	// Called by the ServerStatusThread
	public synchronized void setPanelStatus(ConnectionStatus status, String[] hosts)
	{
		if (theApp.clientType == CommUtils.kHostID)
		{
			if (hostPanel != null)
				hostPanel.setPanelStatus(status);
		}
		else
		{
			if (presenterPanel != null)
				presenterPanel.setPanelStatus(status, hosts);
		}
	}
	
	//
	//	Control of status update thread
	//
	
	public void pauseUpdateThread() {
		statusThread.paused.set(true);
	}
	
	public void unpauseUpdateThread()
	{
		statusThread.paused.set(false);
		synchronized (statusThread) {
			statusThread.notify();
		}
	}
	
	public void stopUpdateThread()
	{
		statusThread.stopped.set(true);
		
		if (statusThread.paused.get())
			unpauseUpdateThread();
	}
}
