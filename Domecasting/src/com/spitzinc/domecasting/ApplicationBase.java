package com.spitzinc.domecasting;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.swing.JFrame;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;

import java.util.Properties;

public abstract class ApplicationBase implements WindowListener
{
	protected static final String kCompanyName = "Spitz, Inc";
	protected static final String kProductName = "Domecasting";
	
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
	
	protected void configureLog4j(String configPath)
	{
		// Configure logger to read a configuration file from same folder as properties file and
		// write log file to same place.
		String propsPath = getPropertiesPath();
		System.setProperty("log4j.logpath", propsPath);
		File log4jConfigFile = new File(configPath + File.separatorChar + "log4j2.xml");
		LoggerContext context = (org.apache.logging.log4j.core.LoggerContext)LogManager.getContext(false);
		context.setConfigLocation(log4jConfigFile.toURI());
	}
	
	protected String getPropertiesPath()
	{
		String propsFilePath = new String(System.getenv("APPDATA"));
		propsFilePath = propsFilePath.concat(File.separator).concat(kCompanyName);
		propsFilePath = propsFilePath.concat(File.separator).concat(kProductName);
		return propsFilePath;
	}
	
	protected File getPropertiesFile(String fileName)
	{
		return new File(getPropertiesPath().concat(File.separator).concat(fileName));
	}
	
	protected Properties readPropertiesFromFile(File inFile)
	{
		FileInputStream propFileStream = null;
		try
		{
			if (inFile.exists())
			{
				propFileStream = new FileInputStream(inFile);
				if (propFileStream != null) {
					Properties props = new Properties();
					props.load(propFileStream);
					return props;
				}
			}
		}
		catch (Exception e) {
			Log.inst().error("There was a problem with the properties file: " + inFile.getAbsolutePath());
			e.printStackTrace();
		}
		finally
		{
			if (propFileStream != null)
			{
				try {
					propFileStream.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		
		return null;
	}
	
	protected boolean writePropertiesToFile(File inFile, Properties props)
	{
		boolean writeSuccess = false;
		
		if (!inFile.exists()) {
			try {
				File folderPath = inFile.getParentFile();
				folderPath.mkdirs();
				inFile.createNewFile();
			} catch (IOException | SecurityException e) {
				writeSuccess = false;
			}
		}
		
		if (inFile.exists()) {
			String comments = null;
			FileOutputStream propFileStream = null;
			try {
				propFileStream = new FileOutputStream(inFile);
				if (propFileStream != null) {
					props.store(propFileStream, comments);
					writeSuccess = true; // if we didn't throw above, it was successful
				}
			} catch (Exception e) {
				writeSuccess = false;
			}
			finally
			{
				if (propFileStream != null)
				{
					try {
						propFileStream.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
		
		if (!writeSuccess)
			Log.inst().error("Couldn't write properties file: " +inFile.getAbsolutePath());

		return writeSuccess;
	}
	
	protected String getStringProperty(final Properties inProps, String inName, String defaultValue)
	{
		try {
			String propValue = inProps.getProperty(inName);
			if (propValue != null)
				return propValue;
		}
		catch (NullPointerException e) {
			e.printStackTrace();
		}
		
		return defaultValue;
	}
	
	protected int getIntegerProperty(final Properties inProps, String inName, int defaultValue)
	{
		try {
			String propValue = inProps.getProperty(inName);
			if (propValue != null)
				return Integer.valueOf(propValue);
		}
		catch (NullPointerException e) {
			e.printStackTrace();
		}
		
		return defaultValue;
	}

	public void positionFrame(JFrame frame)
	{
		// Center application on screen
		Dimension prefSize = frame.getPreferredSize();
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		int x = (screenSize.width - prefSize.width) / 2;
		int y = (screenSize.height - prefSize.height) / 2;
		frame.setLocation(x, y);
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
