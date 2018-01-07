package com.spitzinc.domecasting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Log
{
	private static Log instance = null;
	
	private Logger logger;
	
	protected Log() {
		logger = LogManager.getLogger(Logger.class.getName());
	}
	
	public static Logger inst()
	{
		if (instance == null)
		{
			synchronized(Log.class)
			{
				if (instance == null)
					instance = new Log();
			}
		}
		return instance.logger;
	}
}
