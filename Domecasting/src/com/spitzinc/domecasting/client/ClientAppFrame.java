package com.spitzinc.domecasting.client;

import com.spitzinc.domecasting.CommUtils;

import java.awt.Dimension;

import javax.swing.JFrame;
import javax.swing.border.EmptyBorder;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;

public class ClientAppFrame extends JFrame
{
	private static final long serialVersionUID = 1L;
	private ClientApplication theApp;
	private JTabbedPane tabbedPane;
	private HostPanel hostPanel;
	private PresenterPanel presenterPanel;

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
	}
	
	public synchronized void setStatusText(String status)
	{
		if (theApp.clientType == CommUtils.kHostID)
		{
			if (hostPanel != null)
				hostPanel.setStatusText(status);
		}
		else
		{
			if (presenterPanel != null)
				presenterPanel.setStatusText(status);
		}
	}
}
