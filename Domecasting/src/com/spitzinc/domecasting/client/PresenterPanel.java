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
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class PresenterPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	private JTextField presentationID;
	private JButton btnSendID;
	private JButton btnStartPresentation;
	private JLabel lblStatusText;

	/**
	 * Create the panel.
	 */
	public PresenterPanel() {
		GridBagLayout gridBagLayout = new GridBagLayout();
		gridBagLayout.columnWidths = new int[]{0, 0, 0, 0};
		gridBagLayout.rowHeights = new int[]{0, 0, 0, 0, 0};
		gridBagLayout.columnWeights = new double[]{0.0, 1.0, 0.0, Double.MIN_VALUE};
		gridBagLayout.rowWeights = new double[]{0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE};
		setLayout(gridBagLayout);
		
		JLabel lblPresentationId = new JLabel("Presentation ID:");
		GridBagConstraints gbc_lblPresentationId = new GridBagConstraints();
		gbc_lblPresentationId.insets = new Insets(10, 10, 5, 5);
		gbc_lblPresentationId.anchor = GridBagConstraints.EAST;
		gbc_lblPresentationId.gridx = 0;
		gbc_lblPresentationId.gridy = 0;
		add(lblPresentationId, gbc_lblPresentationId);
		
		// Create PresentationID text field and enable "Send Presentation ID" button when at least
		// ClientApplication.kMinimumPresentationIDLength is entered.
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
		gbc_textField.weightx = 1.0;
		gbc_textField.anchor = GridBagConstraints.WEST;
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
		gbc_btnSendID.gridx = 2;
		gbc_btnSendID.gridy = 0;
		add(btnSendID, gbc_btnSendID);
		
		JButton btnNewButton = new JButton("Upload Presentation Assets...");
		btnNewButton.setEnabled(false);
		GridBagConstraints gbc_btnNewButton = new GridBagConstraints();
		gbc_btnNewButton.insets = new Insets(10, 0, 5, 0);
		gbc_btnNewButton.gridwidth = 3;
		gbc_btnNewButton.gridx = 0;
		gbc_btnNewButton.gridy = 1;
		add(btnNewButton, gbc_btnNewButton);
		
		btnStartPresentation = new JButton("Start Presentation");
		btnStartPresentation.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				ClientApplication inst = (ClientApplication) ClientApplication.inst();
				if (btnStartPresentation.getText() == "Start Presentation")
				{
					inst.setPresenting(true);
					btnStartPresentation.setText("Stop Presentation");
				}
				else
				{
					inst.setPresenting(false);
					btnStartPresentation.setText("Start Presentation");
				}
			}
		});
		GridBagConstraints gbc_btnStartPresentation = new GridBagConstraints();
		gbc_btnStartPresentation.insets = new Insets(0, 0, 5, 0);
		gbc_btnStartPresentation.gridwidth = 3;
		gbc_btnStartPresentation.gridx = 0;
		gbc_btnStartPresentation.gridy = 2;
		add(btnStartPresentation, gbc_btnStartPresentation);
		
		lblStatusText = new JLabel("Status");
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
