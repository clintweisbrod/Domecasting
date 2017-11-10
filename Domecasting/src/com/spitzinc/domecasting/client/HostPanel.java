package com.spitzinc.domecasting.client;

import javax.swing.JPanel;
import javax.swing.JLabel;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import javax.swing.JTextField;

import java.awt.Insets;
import javax.swing.JButton;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import javax.swing.SwingConstants;

public class HostPanel extends JPanel
{
	private static final long serialVersionUID = 1L;
	private static final String kHostID = "TestHostName";
	
	private JTextField hostID;
	private JButton btnWaitForPresentation;
	private JLabel lblStatusText;

	/**
	 * Create the panel.
	 */
	public HostPanel() {
		GridBagLayout gridBagLayout = new GridBagLayout();
		gridBagLayout.columnWidths = new int[]{0};
		gridBagLayout.rowHeights = new int[]{0, 0, 0, 0, 0};
		gridBagLayout.columnWeights = new double[]{1.0};
		gridBagLayout.rowWeights = new double[]{0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE};
		setLayout(gridBagLayout);
		
		hostID = new JTextField();
		hostID.setEditable(false);
		hostID.setHorizontalAlignment(SwingConstants.CENTER);
		hostID.setText(kHostID);
		GridBagConstraints gbc_hostID = new GridBagConstraints();
		gbc_hostID.insets = new Insets(10, 0, 5, 5);
		gbc_hostID.gridx = 0;
		gbc_hostID.gridy = 0;
		add(hostID, gbc_hostID);
		hostID.setColumns(20);
		
		JButton btnGetPresentationAssets = new JButton("Download Presentation Assets...");
		btnGetPresentationAssets.setEnabled(false);
		GridBagConstraints gbc_btnGetPresentationAssets = new GridBagConstraints();
		gbc_btnGetPresentationAssets.insets = new Insets(10, 0, 5, 0);
		gbc_btnGetPresentationAssets.gridx = 0;
		gbc_btnGetPresentationAssets.gridy = 1;
		add(btnGetPresentationAssets, gbc_btnGetPresentationAssets);
		
		btnWaitForPresentation = new JButton("Allow Remote Control");
		btnWaitForPresentation.setEnabled(false);
		btnWaitForPresentation.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				ClientApplication inst = (ClientApplication) ClientApplication.inst();
				if (btnWaitForPresentation.getText() == "Allow Remote Control")
				{
					inst.setHosting(true);
					btnWaitForPresentation.setText("Stop Remote Control");
				}
				else
				{
					inst.setHosting(false);
					btnWaitForPresentation.setText("Allow Remote Control");
				}
			}
		});
		GridBagConstraints gbc_btnWaitForPresentation = new GridBagConstraints();
		gbc_btnWaitForPresentation.insets = new Insets(0, 0, 5, 0);
		gbc_btnWaitForPresentation.gridx = 0;
		gbc_btnWaitForPresentation.gridy = 2;
		add(btnWaitForPresentation, gbc_btnWaitForPresentation);
		
		lblStatusText = new JLabel("New label");
		GridBagConstraints gbc_lblStatusText = new GridBagConstraints();
		gbc_lblStatusText.insets = new Insets(10, 0, 0, 5);
		gbc_lblStatusText.gridx = 0;
		gbc_lblStatusText.gridy = 3;
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
				btnWaitForPresentation.setEnabled(false);
				break;
			case eConnectedNoPeer:
				lblStatusText.setText("Waiting for domecasting peer to connect...");
				btnWaitForPresentation.setEnabled(true);
				break;
			case eConnectedWithPeer:
				lblStatusText.setText("Domecast is ready.");
				btnWaitForPresentation.setEnabled(true);
				break;
			}
		}
	}
	
	public String getHostID() {
		return hostID.getText();
	}
}
