package com.spitzinc.domecasting;

import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

public abstract class ApplicationBase implements WindowListener
{
	protected static ApplicationBase singleInstance;
	
	static
	{
		singleInstance = null;
	}
	
	abstract protected void createUIElements();
	
	public static ApplicationBase inst()
	{
		return singleInstance;
	}
	
	protected ApplicationBase()
	{
    	singleInstance = this;
    	
    	// Set application Look & Feel to Windows
    	try {
			UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException
				| UnsupportedLookAndFeelException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
    	createUIElements();
	}
	
	@Override
	public void windowActivated(WindowEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void windowClosed(WindowEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void windowClosing(WindowEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void windowDeactivated(WindowEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void windowDeiconified(WindowEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void windowIconified(WindowEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void windowOpened(WindowEvent arg0) {
		// TODO Auto-generated method stub
		
	}
}
