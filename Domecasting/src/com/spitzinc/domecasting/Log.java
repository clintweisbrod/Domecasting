package com.spitzinc.domecasting;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

public class Log
{
	private static Log instance = null;
	
	public static void configure(String configPath, String propertiesPath, String level)
	{
		// Configure logger to read a configuration file from same folder as properties file and
		// write log file to same place.
		System.setProperty("log4j.logpath", propertiesPath);
		System.setProperty("log4j.configurationFile", configPath + "/log4j2.xml");
		
		// By default, log4j2 caches a thread's name the first time a logging event is called.
		// SNTCPPassThruThread.establishOutboundConnection() changes it's thread name after it
		// has established what outbound port to use. Setting the log4j ThreadNameStrategy is 
		// a performance hit but hopefully not significant. In the meantime, I've filed a Jira
		// report with the log4j2 Apache project, suggesting the addition of a new method in
		// org.apache.logging.log4j.Logger called something like refreshCachedThreadName().
		System.setProperty("AsyncLogger.ThreadNameStrategy", "UNCACHED");
		
		setLevel(level);
	}
	
	private static void setLevel(String logLevel)
	{
		if (logLevel == null)
			return;

		Level level = getLog4jLevelFromString(logLevel);
		if (level != null)
		{
			LoggerContext context = (org.apache.logging.log4j.core.LoggerContext)LogManager.getContext(false);
			Configuration config = context.getConfiguration();
			LoggerConfig loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
			loggerConfig.setLevel(level);
			context.updateLoggers();
			context.close();
		}
	}
	
	private static Level getLog4jLevelFromString(String s)
	{
		Level result = null;
		
		s = s.toLowerCase();
		if (s.equals("all"))
			result = Level.ALL;
		else if (s.equals("trace"))
			result = Level.TRACE;
		else if (s.equals("debug"))
			result = Level.DEBUG;
		else if (s.equals("info"))
			result = Level.INFO;
		else if (s.equals("warn"))
			result = Level.WARN;
		else if (s.equals("error"))
			result = Level.ERROR;
		else if (s.equals("fatal"))
			result = Level.FATAL;
		else if (s.equals("off"))
			result = Level.OFF;
		
		return result;
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
	
	private Logger logger;
	
	protected Log() {
		logger = LogManager.getLogger(Logger.class.getName());
	}
}
