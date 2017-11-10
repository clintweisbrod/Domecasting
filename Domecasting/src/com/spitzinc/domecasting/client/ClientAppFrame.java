package com.spitzinc.domecasting.client;

import com.spitzinc.domecasting.CommUtils;

import java.awt.Dimension;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JFrame;
import javax.swing.border.EmptyBorder;
import javax.swing.JTabbedPane;
import javax.swing.SwingWorker;
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
	private class ServerStatusThread extends SwingWorker<Integer, String>
	{
		private static final int kPollIntervalSeconds = 5;
		
		public AtomicBoolean stopped;
		public AtomicBoolean paused;
		
		public ServerStatusThread()
		{
			this.stopped = new AtomicBoolean(false);
			this.paused = new AtomicBoolean(false);
		}
		
		protected Integer doInBackground() throws Exception
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
					String hosts = null;
					if (theApp.clientType == CommUtils.kPresenterID)
						hosts = theApp.getConnectedHosts();
					
					// Publish the results
					if ((hosts != null) && !hosts.equals("<none>"))
						publish(hosts);
					publish("isConnected=" + Boolean.toString(isConnected));
					publish("isPeerReady=" + Boolean.toString(isPeerReady));
					
					// Sleep for a few seconds
					try {
						Thread.sleep(kPollIntervalSeconds * 1000);
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
			
			return 0;
		}
		
		protected void process(List<String> publishedItems)
		{
			boolean isConnected = false;
			boolean isPeerReady = false;
			String[] hosts = null;
			for (String item : publishedItems)
			{
				if (item.contains("="))
				{
					String[] nameValuePair = item.split("=");
					if (nameValuePair[0].equals("isConnected"))
						isConnected = Boolean.parseBoolean(nameValuePair[1]);
					if (nameValuePair[0].equals("isPeerReady"))
						isPeerReady = Boolean.parseBoolean(nameValuePair[1]);
				}
				else if (item.contains("~"))
					hosts = item.split("~");
			}
			
			// Update the panel, but only if the thread is not paused
			if (!paused.get())
			{
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
		this.statusThread.execute();
		
	}
	
	public String getHostID() {
		return hostPanel.getHostID();
	}
	
	// Called by the ServerStatusThread.process() on the EDT.
	public void setPanelStatus(ConnectionStatus status, String[] hosts)
	{
		if (hostPanel != null)
			hostPanel.setPanelStatus(status);
		if (presenterPanel != null)
			presenterPanel.setPanelStatus(status, hosts);
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
