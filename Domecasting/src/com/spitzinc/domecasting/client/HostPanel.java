package com.spitzinc.domecasting.client;

import javax.swing.JPanel;
import javax.swing.JLabel;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;

import java.awt.Insets;
import javax.swing.JButton;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.ActionEvent;
import javax.swing.JComboBox;

public class HostPanel extends JPanel
{
	private static final long serialVersionUID = 1L;
	private boolean ignoreDomecastComboboxChanges;
	private JButton btnPresentationControl;
	private JLabel lblStatusText;
	private JComboBox<String> availableDomecasts;
	private JLabel lblNewLabel;

	/**
	 * Create the panel.
	 */
	public HostPanel()
	{
		ignoreDomecastComboboxChanges = true;
		
		GridBagLayout gridBagLayout = new GridBagLayout();
		gridBagLayout.columnWidths = new int[]{0};
		gridBagLayout.rowHeights = new int[]{0, 0, 0, 0, 0, 0};
		gridBagLayout.columnWeights = new double[]{1.0};
		gridBagLayout.rowWeights = new double[]{0.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE};
		setLayout(gridBagLayout);
		
		lblNewLabel = new JLabel("Choose the domecast to listen to");
		GridBagConstraints gbc_lblNewLabel = new GridBagConstraints();
		gbc_lblNewLabel.insets = new Insets(10, 0, 5, 0);
		gbc_lblNewLabel.gridx = 0;
		gbc_lblNewLabel.gridy = 0;
		add(lblNewLabel, gbc_lblNewLabel);
		
		availableDomecasts = new JComboBox<String>();
		availableDomecasts.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent event) {
				if (event.getStateChange() == ItemEvent.SELECTED)
				{
					if (!ignoreDomecastComboboxChanges)
					{
						String item = (String)event.getItem();
						ClientApplication inst = (ClientApplication) ClientApplication.inst();
						inst.sendDomecastID(item);
					}
				}
			}
		});
		GridBagConstraints gbc_availableDomecasts = new GridBagConstraints();
		gbc_availableDomecasts.insets = new Insets(0, 0, 5, 0);
		gbc_availableDomecasts.gridx = 0;
		gbc_availableDomecasts.gridy = 1;
		add(availableDomecasts, gbc_availableDomecasts);
		
		JButton btnGetPresentationAssets = new JButton("Download Presentation Assets...");
		btnGetPresentationAssets.setEnabled(false);
		GridBagConstraints gbc_btnGetPresentationAssets = new GridBagConstraints();
		gbc_btnGetPresentationAssets.insets = new Insets(0, 0, 5, 0);
		gbc_btnGetPresentationAssets.gridx = 0;
		gbc_btnGetPresentationAssets.gridy = 2;
		add(btnGetPresentationAssets, gbc_btnGetPresentationAssets);
		
		btnPresentationControl = new JButton("Allow Remote Control");
		btnPresentationControl.setEnabled(false);
		btnPresentationControl.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				ClientApplication inst = (ClientApplication) ClientApplication.inst();
				if (btnPresentationControl.getText() == "Allow Remote Control")
				{
					// Tell the server we're ready to be controlled by presenter
					inst.sendReadyToCast(true);
					
					// Disable controls so that only the btnPresentationControl is enabled
					availableDomecasts.setEnabled(false);
					HostPanel.this.getParent().setEnabled(false);
					
					// Change the button text
					btnPresentationControl.setText("Stop Remote Control");
				}
				else
				{
					// Tell the server we're not willing to allow more control from presenter
					inst.sendReadyToCast(false);
					
					// Enable controls we disabled
					availableDomecasts.setEnabled(true);
					HostPanel.this.getParent().setEnabled(true);
					
					// Change the button text
					btnPresentationControl.setText("Allow Remote Control");
				}
			}
		});
		GridBagConstraints gbc_btnWaitForPresentation = new GridBagConstraints();
		gbc_btnWaitForPresentation.insets = new Insets(0, 0, 5, 0);
		gbc_btnWaitForPresentation.gridx = 0;
		gbc_btnWaitForPresentation.gridy = 3;
		add(btnPresentationControl, gbc_btnWaitForPresentation);
		
		lblStatusText = new JLabel("Server Status");
		GridBagConstraints gbc_lblStatusText = new GridBagConstraints();
		gbc_lblStatusText.insets = new Insets(10, 0, 10, 0);
		gbc_lblStatusText.gridx = 0;
		gbc_lblStatusText.gridy = 4;
		add(lblStatusText, gbc_lblStatusText);

	}

	public void setPanelStatus(ClientAppFrame.ConnectionStatus status, String[] domecasts)
	{
		if (lblStatusText != null)
		{
			switch (status)
			{
			case eNotConnected:
				lblStatusText.setText("Spitz domecasting server not available.");
				btnPresentationControl.setEnabled(false);
				break;
			case eConnectedNoPeer:
				lblStatusText.setText("Waiting for domecasting presenter to connect...");
				btnPresentationControl.setEnabled(false);
				break;
			case eConnectedWithPeer:
				lblStatusText.setText("Domecast is ready.");
				btnPresentationControl.setEnabled(true);
				break;
			}
		}
		
		// Update the combobox with the hosts that are currently connected
		if (availableDomecasts != null)
		{
			ignoreDomecastComboboxChanges = true;

			// Remember selected item
			String selectedItem = (String)availableDomecasts.getSelectedItem();
			
			// It's tempting to just call removeAllItems() and add the domecasts, but this causes bad behavior when 
			// the dropdown list is visible. So, we just add/remove items from the list as needed.
			if (domecasts != null)
			{
				// Add items that are not already in the list
				for (String domecast : domecasts)
				{
					boolean hostExistsInList = false;
					for (int i = 0; i < availableDomecasts.getItemCount(); i++)
					{
						if (domecast.equals(availableDomecasts.getItemAt(i)))
						{
							hostExistsInList = true;
							break;
						}
					}
					if (!hostExistsInList)
						availableDomecasts.addItem(domecast);
				}
				
				// Remove items from the list that are not in hosts
				while (availableDomecasts.getItemCount() > domecasts.length)
				{
					for (int i = 0; i < availableDomecasts.getItemCount(); i++)
					{
						String item = availableDomecasts.getItemAt(i);
						boolean itemFoundInHosts = false;
						for (String domecast : domecasts)
						{
							if (item.equals(domecast))
							{
								itemFoundInHosts = true;
								break;
							}
						}
						if (!itemFoundInHosts)
						{
							availableDomecasts.removeItem(item);
							break;
						}
					}
				}
			}
			
			// Re-select the item that was selected before we screwed with the list
			availableDomecasts.setSelectedItem(selectedItem);
			
			ignoreDomecastComboboxChanges = false;
		}
	}
	
	public String getDomecastID()
	{
		String result = "";

		if ((availableDomecasts.getItemCount() > 0) && (availableDomecasts.getSelectedIndex() >= 0))
			result = (String)availableDomecasts.getSelectedItem();
		
		return result;
	}
}
