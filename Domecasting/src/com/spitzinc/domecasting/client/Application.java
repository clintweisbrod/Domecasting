package com.spitzinc.domecasting.client;

import java.awt.Dimension;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.IOException;

import javax.swing.*;

import com.spitzinc.domecasting.ApplicationBase;

public class Application extends ApplicationBase implements WindowListener
{	
	private static final int kAppDefaultWidth = 400;
	private static final int kAppDefaultHeight = 200;
	private static final Dimension kPreferredFrameSize = new Dimension(kAppDefaultWidth, kAppDefaultHeight);
	private static final String kApplicationWindowTitle = "Spitz Dome Casting Client";
	
	public AppFrame appFrame;
	private SNTCPPassThruServer snPassThru = null;
	
	private static ApplicationBase createSingleInstance() {
		if (singleInstance == null)
			singleInstance = new Application();
		return singleInstance; // could be null. only we should be able to create our own
	}
	
	protected void createUIElements()
	{
		appFrame = new AppFrame(this, kPreferredFrameSize);
		appFrame.setTitle(kApplicationWindowTitle);
		appFrame.addWindowListener(this);
		appFrame.pack();
		appFrame.setResizable(false);
		appFrame.setVisible(true);
	}
	
	private void stopHostThreads()
	{
		
	}
	
	private void stopPresenterThreads()
	{
		if (snPassThru != null)
		{
			snPassThru.stop();
			snPassThru = null;
		}
	}
	
	public void startHostThreads()
	{
		stopPresenterThreads();
	}
	
	public void startPresenterThreads()
	{
		stopHostThreads();
		
		try
		{
			snPassThru = new SNTCPPassThruServer(56895, 56898);
			snPassThru.start();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void windowClosing(WindowEvent arg0)
	{
		stopPresenterThreads();
		stopHostThreads();
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
