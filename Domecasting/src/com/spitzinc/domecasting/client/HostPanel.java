package com.spitzinc.domecasting.client;

import javax.swing.JPanel;
import javax.swing.JLabel;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import java.awt.Insets;
import javax.swing.JButton;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

public class HostPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	private JTextField presentationID;
	private JButton btnSendID;
	private JButton btnWaitForPresentation;
	private JLabel lblStatusText;

	/**
	 * Create the panel.
	 */
	public HostPanel() {
		GridBagLayout gridBagLayout = new GridBagLayout();
		gridBagLayout.columnWidths = new int[]{0, 0, 0};
		gridBagLayout.rowHeights = new int[]{0, 0, 0, 0, 0};
		gridBagLayout.columnWeights = new double[]{0.0, 1.0, 0.0};
		gridBagLayout.rowWeights = new double[]{0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE};
		setLayout(gridBagLayout);
		
		JLabel lblPresentationId = new JLabel("Presentation ID:");
		GridBagConstraints gbc_lblPresentationId = new GridBagConstraints();
		gbc_lblPresentationId.insets = new Insets(10, 10, 5, 5);
		gbc_lblPresentationId.anchor = GridBagConstraints.EAST;
		gbc_lblPresentationId.gridx = 0;
		gbc_lblPresentationId.gridy = 0;
		add(lblPresentationId, gbc_lblPresentationId);
		
		presentationID = new JTextField();
		presentationID.getDocument().addDocumentListener(new DocumentListener() {
			public void changedUpdate(DocumentEvent e) {
				updateButtonState();
			}
			public void removeUpdate(DocumentEvent e) {
				updateButtonState();
			}
			public void insertUpdate(DocumentEvent e) {
				updateButtonState();
			}
	
			public void updateButtonState() {
				btnSendID.setEnabled(presentationID.getText().length() >= ClientApplication.kMinimumPresentationIDLength);
			}
		});
		GridBagConstraints gbc_textField = new GridBagConstraints();
		gbc_textField.insets = new Insets(10, 0, 5, 5);
		gbc_textField.fill = GridBagConstraints.HORIZONTAL;
		gbc_textField.weightx = 0.5;
		gbc_textField.anchor = GridBagConstraints.EAST;
		gbc_textField.gridx = 1;
		gbc_textField.gridy = 0;
		add(presentationID, gbc_textField);
		presentationID.setColumns(20);
		
		btnSendID = new JButton("Send Presentation ID");
		btnSendID.setEnabled(false);
		btnSendID.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				ClientApplication inst = (ClientApplication) ClientApplication.inst();
				inst.setPresentationID(presentationID.getText());
			}
		});
		GridBagConstraints gbc_btnSendID = new GridBagConstraints();
		gbc_btnSendID.insets = new Insets(10, 0, 5, 10);
		gbc_btnSendID.anchor = GridBagConstraints.WEST;
		gbc_btnSendID.gridx = 2;
		gbc_btnSendID.gridy = 0;
		add(btnSendID, gbc_btnSendID);
		
		JButton btnGetPresentationAssets = new JButton("Download Presentation Assets...");
		btnGetPresentationAssets.setEnabled(false);
		GridBagConstraints gbc_btnGetPresentationAssets = new GridBagConstraints();
		gbc_btnGetPresentationAssets.gridwidth = 3;
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
		gbc_btnWaitForPresentation.gridwidth = 3;
		gbc_btnWaitForPresentation.gridx = 0;
		gbc_btnWaitForPresentation.gridy = 2;
		add(btnWaitForPresentation, gbc_btnWaitForPresentation);
		
		lblStatusText = new JLabel("New label");
		GridBagConstraints gbc_lblStatusText = new GridBagConstraints();
		gbc_lblStatusText.gridwidth = 3;
		gbc_lblStatusText.insets = new Insets(10, 0, 0, 5);
		gbc_lblStatusText.gridx = 0;
		gbc_lblStatusText.gridy = 3;
		add(lblStatusText, gbc_lblStatusText);

	}

	public void setStatusText(String status)
	{
		if (lblStatusText != null)
			lblStatusText.setText(status);
	}
}
