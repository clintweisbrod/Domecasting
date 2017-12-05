package com.spitzinc.domecasting.server;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;

import org.apache.commons.io.FileUtils;

import com.spitzinc.domecasting.BasicProcessorThread;
import com.spitzinc.domecasting.Log;

/*
 * This thread wakes up once every 24 hours at k24HourWhenCleanupOccurs and removes any folders holding
 * assets that were owned by presenter threads that no longer exist.
 */
public class AssetsFileCleanupThread extends BasicProcessorThread
{
	private static final int k24HourWhenCleanupOccurs = 4;	// 4 am
	public void run()
	{
		ServerApplication inst = (ServerApplication) ServerApplication.inst();
		String programDataPath = inst.getProgramDataPath();
		File programDataFolder = new File(programDataPath);
		
		// Periodically clean-up any assets files that don't have corresponding presenter thread connections
		while (!stopped.get())
		{
			// Figure out how many milliseconds until k24HourWhenCleanupOccurs
			Calendar cal = Calendar.getInstance();
			long timeNow = cal.getTimeInMillis();
			cal.set(Calendar.HOUR_OF_DAY, k24HourWhenCleanupOccurs);
			cal.set(Calendar.MINUTE, 0);
			cal.set(Calendar.SECOND, 0);
			long timeAtCleanup = cal.getTimeInMillis();
			if (timeAtCleanup < timeNow)
			{
				cal.add(Calendar.DAY_OF_MONTH, 1);
				timeAtCleanup = cal.getTimeInMillis();
			}
			long millisecondsToWait = timeAtCleanup - timeNow;
			
			// Sleep the computed milliseconds
			Log.inst().info("Sleeping for " + (millisecondsToWait / 60000) + " minutes...");
			try {
				sleep(millisecondsToWait);
			} catch (InterruptedException e) {
			}
			
			// Don't do cleanup if this thread has been stopped
			if (stopped.get())
				break;

			// Iterate over each subfolder of C:\ProgramData\Spitz, Inc\Domecasting\
			Log.inst().info("Performing cleanup...");
			String[] names = programDataFolder.list();
			for (String name : names)
			{
				File folderElement = new File(programDataPath + name);
				if (folderElement.isDirectory())
				{
					// Determine if there is a live presenter connection with domecastID == name
					if (!inst.connectionListenerThread.presenterConnectionExists(name))
					{
						// If there is not, remove this folder.
						try {
							FileUtils.deleteDirectory(folderElement);
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
			}
		}
		
		Log.inst().info("Exiting thread.");
	}
}
