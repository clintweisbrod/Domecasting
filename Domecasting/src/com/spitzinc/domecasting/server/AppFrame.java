package com.spitzinc.domecasting.server;

import java.awt.BorderLayout;
import java.awt.Dimension;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import com.spitzinc.domecasting.server.Application;

public class AppFrame extends JFrame
{
	private static final long serialVersionUID = 1L;
	private JPanel contentPane;

	/**
	 * Create the frame.
	 */
	public AppFrame(Application theApp, Dimension inPreferredSize)
	{
		setType(Type.UTILITY);
		theApp.appFrame = this;
		
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);	// Application WindowListener handles close
		
		setPreferredSize(inPreferredSize);
		
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		contentPane.setLayout(new BorderLayout(0, 0));
		setContentPane(contentPane);
		
		theApp.positionFrame(this);
		setResizable(false);
	}

}
