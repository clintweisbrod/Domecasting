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
import javax.swing.JFileChooser;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

public class PresenterPanel extends JPanel
{
	private static final long serialVersionUID = 1L;
	public static final int kMinDomecastIDLength = 8;
	
	private JTextField txtDomecastID;
	private JButton btnUploadAssets;
	private JLabel lblStatusText;
	private DomecastIDSendThread domecastSendThread;
	private JFileChooser fileChooser;
	
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
				if (inst.isConnected.get())
				{
					// Ask server if the supplied domecastID is unique. This is not a synchronous call
					// so we have to wait for ServerConnection.ServerInputHandlerThread to call notify().
					inst.serverConnection.isDomecastIDUnique(domecastID);
					synchronized(inst.isDomecastIDUnique) {
						try {
							inst.isDomecastIDUnique.wait();
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
					
					if (inst.isDomecastIDUnique.get())
					{
						Log.inst().info("Sending " + domecastID);
						
						// Time to send the domecastID
						inst.serverConnection.sendDomecastID(domecastID);
						
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
		
		fileChooser = new JFileChooser();
		fileChooser.setFileFilter(new AssetFileFilter());
		fileChooser.setDialogTitle("Select show assets file to upload");
		
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
		btnUploadAssets.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0)
			{
				// Display modal file chooser dialog
				ClientApplication inst = (ClientApplication)ClientApplication.inst();
				if (inst.lastAssetsOpenFolder != null)
					fileChooser.setCurrentDirectory(new File(inst.lastAssetsOpenFolder));
				
				int returnValue = fileChooser.showOpenDialog(PresenterPanel.this);
				if (returnValue == JFileChooser.APPROVE_OPTION)
				{
					File assetFile = fileChooser.getSelectedFile();
					inst.lastAssetsOpenFolder = assetFile.getParent();
					
					// TODO: Send this file to the server, giving progress of upload.
					ClientInfoSendThread sendThread = new ClientInfoSendThread();
					sendThread.setAssetsFile(assetFile);
					sendThread.start();
				}
			}
		});
		btnUploadAssets.setEnabled(false);
		GridBagConstraints gbc_btnUploadAssets = new GridBagConstraints();
		gbc_btnUploadAssets.insets = new Insets(0, 0, 5, 0);
		gbc_btnUploadAssets.gridwidth = 2;
		gbc_btnUploadAssets.gridx = 0;
		gbc_btnUploadAssets.gridy = 1;
		add(btnUploadAssets, gbc_btnUploadAssets);
		
		lblStatusText = new JLabel("");
		GridBagConstraints gbc_lblStatusText = new GridBagConstraints();
		gbc_lblStatusText.gridwidth = 2;
		gbc_lblStatusText.insets = new Insets(10, 0, 10, 0);
		gbc_lblStatusText.gridx = 0;
		gbc_lblStatusText.gridy = 2;
		add(lblStatusText, gbc_lblStatusText);

	}
	
	public void resetUI()
	{
		txtDomecastID.setText("");
		btnUploadAssets.setEnabled(false);
	}
	
	public void setPanelStatus(String statusText)
	{
		if (lblStatusText != null)
			lblStatusText.setText(statusText);
	}
	
	public String getDomecastID() {
		return txtDomecastID.getText();
	}
}
