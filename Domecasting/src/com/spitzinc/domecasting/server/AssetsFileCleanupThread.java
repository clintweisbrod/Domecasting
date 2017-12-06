package com.spitzinc.domecasting.server;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;

import org.apache.commons.io.FileUtils;

import com.spitzinc.domecasting.BasicProcessorThread;
import com.spitzinc.domecasting.Log;

/*
 * This thread wakes up once every 24 hours at k24HourTimeWhenCleanupOccurs and removes any folders holding
 * assets that were owned by presenter threads that no longer exist.
 */
public class AssetsFileCleanupThread extends BasicProcessorThread
{
	private static final int k24HourTimeWhenCleanupOccurs = 400;	// 4 am. DO NOT use preceeding zero as in, "0400". JVM interprets as octal.
	private static final int kMinimumFolderAgeHoursToDelete = 6;	// When a folder is found without an active presentation,
																	// it must be at least this old before it is deleted.
	public void run()
	{
		ServerApplication inst = (ServerApplication) ServerApplication.inst();
		String programDataPath = inst.getProgramDataPath();
		File programDataFolder = new File(programDataPath);
		
		// Periodically clean-up any assets files that don't have corresponding presenter thread connections
		while (!stopped.get())
		{
			// Figure out how many milliseconds until k24HourTimeWhenCleanupOccurs
			Calendar cal = Calendar.getInstance();
			long timeNow = cal.getTimeInMillis();
			cal.set(Calendar.HOUR_OF_DAY, k24HourTimeWhenCleanupOccurs / 100);
			cal.set(Calendar.MINUTE, k24HourTimeWhenCleanupOccurs % 100);
			cal.set(Calendar.SECOND, 0);
			long timeAtCleanup = cal.getTimeInMillis();
			if (timeAtCleanup < timeNow)
			{
				// Current time is past k24HourTimeWhenCleanupOccurs so we have to add a day
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
					// Compute the age of the folder in hours
					int folderAgeHours = (int)(timeNow - folderElement.lastModified()) / (1000 * 60 * 60);
					
					// Make sure the folder is at least kMinimumFolderAgeHoursToDelete hours old
					if (folderAgeHours > kMinimumFolderAgeHoursToDelete)
					{
						// Determine if there is a live presenter connection with domecastID == name
						if (!inst.connectionListenerThread.presenterConnectionExists(name))
						{
							// If there is no live presenter connection, remove this folder.
							Log.inst().info("Removing folder: " + folderElement.getAbsolutePath());
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
		}
		
		Log.inst().info("Exiting thread.");
	}
}
