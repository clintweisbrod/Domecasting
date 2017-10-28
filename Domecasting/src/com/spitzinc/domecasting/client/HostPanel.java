package com.spitzinc.domecasting.client;

import javax.swing.JPanel;
import javax.swing.JLabel;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import javax.swing.JTextField;
import java.awt.Insets;
import javax.swing.JButton;

public class HostPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	private JTextField textField;

	/**
	 * Create the panel.
	 */
	public HostPanel() {
		GridBagLayout gridBagLayout = new GridBagLayout();
		gridBagLayout.columnWidths = new int[]{0, 0, 0};
		gridBagLayout.rowHeights = new int[]{0, 0, 0, 0};
		gridBagLayout.columnWeights = new double[]{0.0, 1.0, Double.MIN_VALUE};
		gridBagLayout.rowWeights = new double[]{0.0, 0.0, 0.0, Double.MIN_VALUE};
		setLayout(gridBagLayout);
		
		JLabel lblPresentationId = new JLabel("Presentation ID:");
		GridBagConstraints gbc_lblPresentationId = new GridBagConstraints();
		gbc_lblPresentationId.weightx = 0.5;
		gbc_lblPresentationId.insets = new Insets(10, 0, 5, 5);
		gbc_lblPresentationId.anchor = GridBagConstraints.EAST;
		gbc_lblPresentationId.gridx = 0;
		gbc_lblPresentationId.gridy = 0;
		add(lblPresentationId, gbc_lblPresentationId);
		
		textField = new JTextField();
		GridBagConstraints gbc_textField = new GridBagConstraints();
		gbc_textField.weightx = 0.5;
		gbc_textField.anchor = GridBagConstraints.WEST;
		gbc_textField.insets = new Insets(10, 0, 5, 0);
		gbc_textField.gridx = 1;
		gbc_textField.gridy = 0;
		add(textField, gbc_textField);
		textField.setColumns(20);
		
		JButton btnGetPresentationAssets = new JButton("Get Presentation Assets...");
		GridBagConstraints gbc_btnGetPresentationAssets = new GridBagConstraints();
		gbc_btnGetPresentationAssets.gridwidth = 2;
		gbc_btnGetPresentationAssets.insets = new Insets(0, 0, 5, 5);
		gbc_btnGetPresentationAssets.gridx = 0;
		gbc_btnGetPresentationAssets.gridy = 1;
		add(btnGetPresentationAssets, gbc_btnGetPresentationAssets);
		
		JButton btnWaitForPresentation = new JButton("Wait for remote presenter to begin");
		GridBagConstraints gbc_btnWaitForPresentation = new GridBagConstraints();
		gbc_btnWaitForPresentation.gridwidth = 2;
		gbc_btnWaitForPresentation.insets = new Insets(0, 0, 0, 5);
		gbc_btnWaitForPresentation.gridx = 0;
		gbc_btnWaitForPresentation.gridy = 2;
		add(btnWaitForPresentation, gbc_btnWaitForPresentation);

	}

}
