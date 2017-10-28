package com.spitzinc.domecasting.server;

import java.awt.Dimension;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

import javax.swing.SwingUtilities;

import com.spitzinc.domecasting.ApplicationBase;
import com.spitzinc.domecasting.server.AppFrame;

public class Application extends ApplicationBase implements WindowListener
{
	private static final int kAppDefaultWidth = 400;
	private static final int kAppDefaultHeight = 200;
	private static final Dimension kPreferredFrameSize = new Dimension(kAppDefaultWidth, kAppDefaultHeight);
	private static final String kApplicationWindowTitle = "Spitz Dome Casting Server";
	
	private static ApplicationBase createSingleInstance() {
		if (singleInstance == null)
			singleInstance = new Application();
		return singleInstance; // could be null. only we should be able to create our own
	}
	
	public AppFrame appFrame;
	
	protected void createUIElements()
	{
		appFrame = new AppFrame(this, kPreferredFrameSize);
		appFrame.setTitle(kApplicationWindowTitle);
		appFrame.addWindowListener(this);
		appFrame.pack();
		appFrame.setResizable(false);
		appFrame.setVisible(true);
	}
	
	@Override
	public void windowClosing(WindowEvent arg0)
	{
		System.exit(0);
	}
	
	public static void main(String[] args)
	{
//		final String[] argsCopy = args;
		SwingUtilities.invokeLater(new Runnable() {
			public void run()
			{
				Application.createSingleInstance();
			}
		});
	}
}
