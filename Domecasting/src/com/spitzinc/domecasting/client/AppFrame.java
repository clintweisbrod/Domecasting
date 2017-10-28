package com.spitzinc.domecasting.client;

import java.awt.Dimension;
import java.awt.Toolkit;

import javax.swing.JFrame;
import javax.swing.border.EmptyBorder;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;

public class AppFrame extends JFrame
{
	private static final long serialVersionUID = 1L;
	private JTabbedPane tabbedPane;
	private HostPanel hostPanel;
	private PresenterPanel presenterPanel;

	/**
	 * Create the frame.
	 */
	public AppFrame(Application theApp, Dimension inPreferredSize)
	{
		setType(Type.UTILITY);
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
					theApp.startHostThreads();
					break;
				case 1:
					theApp.startPresenterThreads();
					break;
				}
			}
		});
		tabbedPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		tabbedPane.addTab("Host", hostPanel);
		tabbedPane.addTab("Presenter", presenterPanel);
		setContentPane(tabbedPane);
		
		positionFrame();
		setResizable(false);
	}
	
	private void positionFrame()
	{
		// Center application on screen
		Dimension prefSize = getPreferredSize();
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		int x = (screenSize.width - prefSize.width) / 2;
        int y = (screenSize.height - prefSize.height) / 2;
        setLocation(x, y);
	}
}
