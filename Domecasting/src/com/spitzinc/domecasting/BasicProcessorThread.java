package com.spitzinc.domecasting;

import java.util.concurrent.atomic.AtomicBoolean;

public class BasicProcessorThread extends Thread
{
	protected AtomicBoolean stopped;
	
	public BasicProcessorThread()
	{
		this.stopped = new AtomicBoolean(true);
		this.setName(getClass().getSimpleName());
	}
	
	public boolean getStopped() {
		return stopped.get();
	}
	
	public void setStopped() {
		stopped.set(true);
	}
	
	public void start() throws IllegalThreadStateException
	{
		Log.inst().info("Starting thread: " + getName() + ".");
		stopped.set(false);
		
		super.start();
	}
	
	public void interrupt()
	{
		setStopped();
		
		super.interrupt();
	}
}
