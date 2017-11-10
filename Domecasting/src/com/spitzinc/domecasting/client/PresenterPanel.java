package com.spitzinc.domecasting.client;

import javax.swing.JPanel;
import javax.swing.JLabel;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import javax.swing.JButton;
import javax.swing.JComboBox;

import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.ActionEvent;

public class PresenterPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	private boolean ignoreHostChanges;
	private JComboBox<String> availableHosts;
	private JLabel lblStatusText;

	/**
	 * Create the panel.
	 */
	public PresenterPanel()
	{
		ignoreHostChanges = true;
		
		GridBagLayout gridBagLayout = new GridBagLayout();
		gridBagLayout.columnWidths = new int[]{0, 0, 0, 0};
		gridBagLayout.rowHeights = new int[]{0, 0, 0, 0};
		gridBagLayout.columnWeights = new double[]{0.0, 1.0, 0.0, Double.MIN_VALUE};
		gridBagLayout.rowWeights = new double[]{0.0, 0.0, 0.0, Double.MIN_VALUE};
		setLayout(gridBagLayout);
		
		JLabel lblPresentationId = new JLabel("Choose the host system to control:");
		GridBagConstraints gbc_lblPresentationId = new GridBagConstraints();
		gbc_lblPresentationId.insets = new Insets(10, 10, 5, 5);
		gbc_lblPresentationId.anchor = GridBagConstraints.EAST;
		gbc_lblPresentationId.gridx = 0;
		gbc_lblPresentationId.gridy = 0;
		add(lblPresentationId, gbc_lblPresentationId);
		
		// Create PresentationID text field and enable "Send Presentation ID" button when at least
		// ClientApplication.kMinimumPresentationIDLength is entered.
		availableHosts = new JComboBox<String>();
		availableHosts.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(ItemEvent event) {
				if (event.getStateChange() == ItemEvent.SELECTED)
				{
					if (!ignoreHostChanges)
					{
						String item = (String)event.getItem();
						ClientApplication inst = (ClientApplication) ClientApplication.inst();
						inst.sendHostIDToControl(item);
					}
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
		add(availableHosts, gbc_textField);
		
		JButton btnNewButton = new JButton("Upload Presentation Assets...");
		btnNewButton.setEnabled(false);
		GridBagConstraints gbc_btnNewButton = new GridBagConstraints();
		gbc_btnNewButton.insets = new Insets(10, 0, 5, 0);
		gbc_btnNewButton.gridwidth = 3;
		gbc_btnNewButton.gridx = 0;
		gbc_btnNewButton.gridy = 1;
		add(btnNewButton, gbc_btnNewButton);
		
		lblStatusText = new JLabel("Status");
		GridBagConstraints gbc_lblStatusText = new GridBagConstraints();
		gbc_lblStatusText.gridwidth = 3;
		gbc_lblStatusText.insets = new Insets(10, 0, 0, 5);
		gbc_lblStatusText.gridx = 0;
		gbc_lblStatusText.gridy = 2;
		add(lblStatusText, gbc_lblStatusText);

	}
	
	public void setPanelStatus(ClientAppFrame.ConnectionStatus status, String[] hosts)
	{
		if (lblStatusText != null)
		{
			switch (status)
			{
			case eNotConnected:
				lblStatusText.setText("Spitz domecasting server not available.");
				break;
			case eConnectedNoPeer:
				lblStatusText.setText("Waiting for domecasting peer to connect...");
				break;
			case eConnectedWithPeer:
				lblStatusText.setText("Domecast is ready.");
				break;
			}
		}
		
		// Update the combobox with the hosts that are currently connected
		if (availableHosts != null)
		{
			ignoreHostChanges = true;

			// Remember selected item
			String selectedItem = (String)availableHosts.getSelectedItem();
			
			// It's tempting to just call removeAllItems() and add the hosts, but this causes bad behavior when 
			// the dropdown list is visible. So, we just add/remove items from the list as needed.
			if (hosts != null)
			{
				// Add items that are not already in the list
				for (String host : hosts)
				{
					boolean hostExistsInList = false;
					for (int i = 0; i < availableHosts.getItemCount(); i++)
					{
						if (host.equals(availableHosts.getItemAt(i)))
						{
							hostExistsInList = true;
							break;
						}
					}
					if (!hostExistsInList)
						availableHosts.addItem(host);
				}
				
				// Remove items from the list that are not in hosts
				while (availableHosts.getItemCount() > hosts.length)
				{
					for (int i = 0; i < availableHosts.getItemCount(); i++)
					{
						String item = availableHosts.getItemAt(i);
						boolean itemFoundInHosts = false;
						for (String host : hosts)
						{
							if (item.equals(host))
							{
								itemFoundInHosts = true;
								break;
							}
						}
						if (!itemFoundInHosts)
						{
							availableHosts.removeItem(item);
							break;
						}
					}
				}
			}
			
			// Re-select the item that was selected before we screwed with the list
			availableHosts.setSelectedItem(selectedItem);
			
			ignoreHostChanges = false;
		}
	}

}
