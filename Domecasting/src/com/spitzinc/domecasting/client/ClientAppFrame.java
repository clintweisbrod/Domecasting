package com.spitzinc.domecasting.client;

import com.spitzinc.domecasting.CommUtils;

import java.awt.Dimension;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.border.EmptyBorder;
import javax.swing.JTabbedPane;
import javax.swing.SwingWorker;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;

public class ClientAppFrame extends JFrame
{
	private static final long serialVersionUID = 1L;
	
	public enum ConnectionStatus {eNotConnected, eConnectedNoPeer, eConnectedPeerNotReady, eConnectedPeersAvailable, eConnectedPeerReady};
	
	private ClientApplication theApp;
	public JTabbedPane tabbedPane;
	public HostPanel hostPanel;
	public PresenterPanel presenterPanel;
	private ServerStatusThread statusThread;
	
	// While domecast is not in progress, this thread periodically wakes up to poll server for info
	// and updates the UI accordingly.
	private class ServerStatusThread extends SwingWorker<Integer, String>
	{
		private static final int kPollIntervalSeconds = 4;
		
		public AtomicBoolean stopped;
		
		public ServerStatusThread()
		{
			this.stopped = new AtomicBoolean(false);
		}
		
		protected Integer doInBackground() throws Exception
		{
			// Sleep for a second to allow for connection to occur before requesting server status.
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
			}
			
			while (!stopped.get())
			{
				// Only do this if we're not routing comm
				if (!theApp.routeComm())
				{
					// Get server status
					theApp.serverConnection.isConnected();
					theApp.serverConnection.isPeerPresent();
					theApp.serverConnection.isPeerReady();
					
					// Get list of domecasts currently connected to server
					if (theApp.clientType == CommUtils.kHostID)
						theApp.serverConnection.getAvailableDomecasts();
					
					// Sleep for a few seconds
					try {
						Thread.sleep(kPollIntervalSeconds * 1000);
					} catch (InterruptedException e) {
					}
					
					// Call publish() so that we have an EDT to read the results and update controls
					publish("Stuff");
				}
				else
				{
					// If domecast communication is in progress, we don't want this thread continually
					// requesting info from the server.
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
			String[] domecasts = null;
			if (!theApp.availableDomecasts.isEmpty())
				domecasts = theApp.availableDomecasts.split("~");
			
			// Update the panel
			if (!theApp.isConnected.get())
				setPanelStatus(ConnectionStatus.eNotConnected, domecasts);
			else
			{
				if (!theApp.isPeerPresent.get())
				{
					if (domecasts != null)
						setPanelStatus(ConnectionStatus.eConnectedPeersAvailable, domecasts);
					else
						setPanelStatus(ConnectionStatus.eConnectedNoPeer, domecasts);
				}
				else
				{
					if (!theApp.isPeerReady.get())
						setPanelStatus(ConnectionStatus.eConnectedPeerNotReady, domecasts);
					else
						setPanelStatus(ConnectionStatus.eConnectedPeerReady, domecasts);
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
				{
					theApp.clientType = CommUtils.kHostID;
					ClientInfoSendThread sendThread = new ClientInfoSendThread(theApp.clientType, hostPanel.getDomecastID(), null);
					sendThread.start();
					break;
				}
				case 1:
				{
					theApp.clientType = CommUtils.kPresenterID;
					ClientInfoSendThread sendThread = new ClientInfoSendThread(theApp.clientType, presenterPanel.getDomecastID(), null);
					sendThread.start();
					break;
				}
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
	
	// Called by the ServerStatusThread.process() on the EDT.
	public void setPanelStatus(ConnectionStatus status, String[] domecasts)
	{
		if (hostPanel != null)
			hostPanel.setPanelStatus(status, domecasts);
		if (presenterPanel != null)
			presenterPanel.setPanelStatus(status);
	}
	
	public void infoBox(String infoMessage, String titleBar)
	{
		JOptionPane.showMessageDialog(this, infoMessage, titleBar, JOptionPane.INFORMATION_MESSAGE);
	}
	
	//
	//	Control of status update thread
	//
	
	public void stopUpdateThread()
	{
		if (statusThread != null)
			statusThread.stopped.set(true);
	}
}
