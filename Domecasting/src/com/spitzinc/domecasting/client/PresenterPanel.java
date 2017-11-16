package com.spitzinc.domecasting.client;

import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import com.spitzinc.domecasting.Log;

import javax.swing.JLabel;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import javax.swing.JButton;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class PresenterPanel extends JPanel
{
	private static final long serialVersionUID = 1L;
	public static final int kMinDomecastIDLength = 8;
	private JTextField txtDomecastID;
	private JButton btnUploadAssets;
	private JLabel lblStatusText;
	private DomecastIDSendThread domecastSendThread;
	
	private class DomecastIDSendThread extends Thread
	{
		private static final long kDelaySeconds = 2;
		private String domecastID;
		private AtomicLong triggerTime;
		public AtomicBoolean abort;
		
		public DomecastIDSendThread()
		{
			this.triggerTime = new AtomicLong(System.currentTimeMillis() + kDelaySeconds * 1000);
			this.abort = new AtomicBoolean(false);
			this.setName(getClass().getSimpleName());
		}
		
		public void arm(String domecastID)
		{
			this.domecastID = domecastID;
			triggerTime.set(System.currentTimeMillis() + kDelaySeconds * 1000);
			abort.set(false);
			Log.inst().info("Armed.");
		}
		
		public void run()
		{
			// Periodically check if enough time has elapsed
			while (triggerTime.get() > System.currentTimeMillis())
			{
				// Sleep for a second
				try {
					sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
			if (!abort.get())
			{
				// First make sure the supplied domecastID is unique on the server
				ClientApplication inst = (ClientApplication)ClientApplication.inst();
				if (inst.isConnected())
				{
					if (inst.isDomecastIDUnique(domecastID))
					{
						Log.inst().info("Sending " + domecastID);
						
						// Time to send the domecastID
						inst.sendDomecastID(domecastID);
						
						// Enable the button to upload assets
						SwingUtilities.invokeLater(new Runnable() {
							public void run() {
								btnUploadAssets.setEnabled(true);
							}
						});
					}
					else
					{
						// Notify the user that this domecastID is already used
						SwingUtilities.invokeLater(new Runnable() {
							public void run() {
								inst.appFrame.infoBox("Please provide a different domecast ID.",
													  "The supplied domecast ID is already in use.");
							}
						});
					}
				}
			}
		}
	}

	/**
	 * Create the panel.
	 */
	public PresenterPanel()
	{
		domecastSendThread = null;
		
		GridBagLayout gridBagLayout = new GridBagLayout();
		gridBagLayout.columnWidths = new int[]{0, 0};
		gridBagLayout.rowHeights = new int[]{0, 0, 0, 0};
		gridBagLayout.columnWeights = new double[]{0.0, Double.MIN_VALUE};
		gridBagLayout.rowWeights = new double[]{0.0, 0.0, 0.0, Double.MIN_VALUE};
		setLayout(gridBagLayout);
		
		JLabel lblPresentationId = new JLabel("Enter the name of your domecast:");
		GridBagConstraints gbc_lblPresentationId = new GridBagConstraints();
		gbc_lblPresentationId.insets = new Insets(10, 10, 5, 5);
		gbc_lblPresentationId.anchor = GridBagConstraints.EAST;
		gbc_lblPresentationId.gridx = 0;
		gbc_lblPresentationId.gridy = 0;
		add(lblPresentationId, gbc_lblPresentationId);
		
		txtDomecastID = new JTextField();
		txtDomecastID.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent arg0) {
				// I don't want a separate button like "Send domecastID to server". Instead,
				// whenever a key is pressed, we first test to make sure the entered domecastID
				// is at least some minimum length. If so, we "arm" a thread that will execute
				// once after a few seconds without typing have elapsed. When the thread executes, it
				// will call ClientApplication.sendDomecastID() and enable btnUploadAssets.
				String domecastID = txtDomecastID.getText();
				if (domecastID.length() >= kMinDomecastIDLength)
				{
					// If the domecastSendThread has not been created or has already dies, create a new one.
					if ((domecastSendThread == null) || ((domecastSendThread != null) && !domecastSendThread.isAlive()))
					{
						domecastSendThread = new DomecastIDSendThread();
						domecastSendThread.start();
					}
					
					// Arm the thread
					domecastSendThread.arm(domecastID);
				}
				else
				{
					// If the domecastID has been made shorter than the minimum, don't send the last valid length domecastID.
					if ((domecastSendThread != null) && domecastSendThread.isAlive())
						domecastSendThread.abort.set(true);
					btnUploadAssets.setEnabled(false);
				}
			}
		});
		GridBagConstraints gbc_textField = new GridBagConstraints();
		gbc_textField.insets = new Insets(10, 0, 5, 5);
		gbc_textField.fill = GridBagConstraints.HORIZONTAL;
		gbc_textField.weightx = 1.0;
		gbc_textField.anchor = GridBagConstraints.WEST;
		gbc_textField.gridx = 1;
		gbc_textField.gridy = 0;
		add(txtDomecastID, gbc_textField);
		
		btnUploadAssets = new JButton("Upload Presentation Assets...");
		btnUploadAssets.setEnabled(false);
		GridBagConstraints gbc_btnUploadAssets = new GridBagConstraints();
		gbc_btnUploadAssets.insets = new Insets(0, 0, 5, 0);
		gbc_btnUploadAssets.gridwidth = 2;
		gbc_btnUploadAssets.gridx = 0;
		gbc_btnUploadAssets.gridy = 1;
		add(btnUploadAssets, gbc_btnUploadAssets);
		
		lblStatusText = new JLabel("Server Status");
		GridBagConstraints gbc_lblStatusText = new GridBagConstraints();
		gbc_lblStatusText.gridwidth = 2;
		gbc_lblStatusText.insets = new Insets(10, 0, 10, 0);
		gbc_lblStatusText.gridx = 0;
		gbc_lblStatusText.gridy = 2;
		add(lblStatusText, gbc_lblStatusText);

	}
	
	public void setPanelStatus(ClientAppFrame.ConnectionStatus status)
	{
		if (lblStatusText != null)
		{
			switch (status)
			{
			case eNotConnected:
				lblStatusText.setText("Spitz domecasting server not available.");
				break;
			case eConnectedNoPeer:
				lblStatusText.setText("Waiting for domecasting host to connect...");
				break;
			case eConnectedPeerNotReady:
				lblStatusText.setText("Waiting for connected host to start domecast...");
				break;
			case eConnectedPeerReady:
				lblStatusText.setText("Domecast in progress.");
				break;
			}
		}
	}
	
	public String getDomecastID() {
		return txtDomecastID.getText();
	}
}
