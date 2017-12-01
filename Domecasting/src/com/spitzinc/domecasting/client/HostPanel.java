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
import java.io.File;
import java.awt.event.ActionEvent;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;

public class HostPanel extends JPanel
{
	private static final long serialVersionUID = 1L;
	
	private static final String buttonTextListenToDomecast = "Listen to Domecast";
	private static final String buttonTextStopListeningToDomecast = "Stop Listening to Domecast";
	
	private boolean ignoreDomecastComboboxChanges;
	private JButton btnGetPresentationAssets;
	private JButton btnDomecastListen;
	private JLabel lblStatusText;
	private JComboBox<String> cmbAvailableDomecasts;
	private JLabel lblNewLabel;
	private JFileChooser fileChooser;

	/**
	 * Create the panel.
	 */
	public HostPanel()
	{
		this.ignoreDomecastComboboxChanges = true;
		
		fileChooser = new JFileChooser();
		fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		fileChooser.setDialogTitle("Select folder to save downloaded assets file to");
		
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
		
		cmbAvailableDomecasts = new JComboBox<String>();
		cmbAvailableDomecasts.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent event) {
				if (event.getStateChange() == ItemEvent.SELECTED)
				{
					if (!ignoreDomecastComboboxChanges)
					{
						String domecastID = (String)event.getItem();
						ClientInfoSendThread sendThread = new ClientInfoSendThread(null, domecastID, null);
						sendThread.start();
						
						// Enable the button to listen to domecast
						btnDomecastListen.setEnabled(true);
					}
				}
			}
		});
		GridBagConstraints gbc_availableDomecasts = new GridBagConstraints();
		gbc_availableDomecasts.insets = new Insets(0, 0, 5, 0);
		gbc_availableDomecasts.gridx = 0;
		gbc_availableDomecasts.gridy = 1;
		add(cmbAvailableDomecasts, gbc_availableDomecasts);
		
		btnGetPresentationAssets = new JButton("Download Presentation Assets...");
		btnGetPresentationAssets.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0)
			{
				// Display modal file chooser dialog
				ClientApplication inst = (ClientApplication)ClientApplication.inst();
				if (inst.lastAssetsSaveFolder != null)
					fileChooser.setCurrentDirectory(new File(inst.lastAssetsSaveFolder));
				
				int returnValue = fileChooser.showOpenDialog(HostPanel.this);
				if (returnValue == JFileChooser.APPROVE_OPTION)
				{
					File theAssetsFolder = fileChooser.getSelectedFile();
					inst.lastAssetsSaveFolder = theAssetsFolder.getAbsolutePath();
					
					// TODO: Download the asset file and save it to this folder

				}
			}
		});
		btnGetPresentationAssets.setEnabled(false);
		GridBagConstraints gbc_btnGetPresentationAssets = new GridBagConstraints();
		gbc_btnGetPresentationAssets.insets = new Insets(0, 0, 5, 0);
		gbc_btnGetPresentationAssets.gridx = 0;
		gbc_btnGetPresentationAssets.gridy = 2;
		add(btnGetPresentationAssets, gbc_btnGetPresentationAssets);
		
		btnDomecastListen = new JButton(buttonTextListenToDomecast);
		btnDomecastListen.setEnabled(false);
		btnDomecastListen.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				ClientApplication inst = (ClientApplication) ClientApplication.inst();
				if (!inst.isHostListening.get())
				{
					inst.isHostListening.set(true);
					inst.snPassThru.notifyThreadsOfCommModeChange();
					
					// Tell the server we're ready to be controlled by presenter
					ClientInfoSendThread sendThread = new ClientInfoSendThread(null, null, true);
					sendThread.start();
					
					// Disable controls so that only the btnPresentationControl is enabled
					cmbAvailableDomecasts.setEnabled(false);
					inst.appFrame.tabbedPane.setEnabled(false);
					
					// Change the button text
					btnDomecastListen.setText(buttonTextStopListeningToDomecast);
				}
				else
				{
					inst.isHostListening.set(false);
					inst.snPassThru.notifyThreadsOfCommModeChange();
					
					// Tell the server we're not ready to be controlled by presenter
					ClientInfoSendThread sendThread = new ClientInfoSendThread(null, null, false);
					sendThread.start();
					
					// Enable controls we disabled
					cmbAvailableDomecasts.setEnabled(true);
					inst.appFrame.tabbedPane.setEnabled(true);
					
					// Change the button text
					btnDomecastListen.setText(buttonTextListenToDomecast);
				}
			}
		});
		GridBagConstraints gbc_btnWaitForPresentation = new GridBagConstraints();
		gbc_btnWaitForPresentation.insets = new Insets(0, 0, 5, 0);
		gbc_btnWaitForPresentation.gridx = 0;
		gbc_btnWaitForPresentation.gridy = 3;
		add(btnDomecastListen, gbc_btnWaitForPresentation);
		
		lblStatusText = new JLabel("");
		GridBagConstraints gbc_lblStatusText = new GridBagConstraints();
		gbc_lblStatusText.insets = new Insets(10, 0, 10, 0);
		gbc_lblStatusText.gridx = 0;
		gbc_lblStatusText.gridy = 4;
		add(lblStatusText, gbc_lblStatusText);

	}
	
	public void resetUI()
	{
		btnGetPresentationAssets.setEnabled(false);
		btnDomecastListen.setEnabled(false);
		cmbAvailableDomecasts.removeAllItems();
	}

	public void setPanelStatus(String statusText, String[] domecasts)
	{
		if (lblStatusText != null)
			lblStatusText.setText(statusText);
		
		// Update the combobox with the hosts that are currently connected
		String selectedItem = null;
		if (cmbAvailableDomecasts != null)
		{
			ignoreDomecastComboboxChanges = true;

			// Remember selected item
			selectedItem = (String)cmbAvailableDomecasts.getSelectedItem();
			
			// It's tempting to just call removeAllItems() and add the domecasts, but this causes bad behavior when 
			// the dropdown list is visible. So, we just add/remove items from the list as needed.
			if (domecasts != null)
			{
				// Add items that are not already in the list
				for (String domecast : domecasts)
				{
					boolean hostExistsInList = false;
					for (int i = 0; i < cmbAvailableDomecasts.getItemCount(); i++)
					{
						if (domecast.equals(cmbAvailableDomecasts.getItemAt(i)))
						{
							hostExistsInList = true;
							break;
						}
					}
					if (!hostExistsInList)
						cmbAvailableDomecasts.addItem(domecast);
				}
				
				// Remove items from the list that are not in hosts
				while (cmbAvailableDomecasts.getItemCount() > domecasts.length)
				{
					for (int i = 0; i < cmbAvailableDomecasts.getItemCount(); i++)
					{
						String item = cmbAvailableDomecasts.getItemAt(i);
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
							cmbAvailableDomecasts.removeItem(item);
							break;
						}
					}
				}
			}
			
			// Re-select the item that was selected before we screwed with the list
			cmbAvailableDomecasts.setSelectedItem(selectedItem);
			
			ignoreDomecastComboboxChanges = false;
		}

		// Enable domecast button accordingly.
		boolean enable = false;
		if (selectedItem != null)
		{
			// If the previously selected item still exist in the list ov available domecasts, we
			// can enable the button.
			for (String domecast:domecasts)
			{
				if (domecast.equals(selectedItem))
				{
					enable = true;
					break;
				}
			}
		}
		btnDomecastListen.setEnabled(enable);
	}
	
	public String getDomecastID()
	{
		String result = "";

		if ((cmbAvailableDomecasts.getItemCount() > 0) && (cmbAvailableDomecasts.getSelectedIndex() >= 0))
			result = (String)cmbAvailableDomecasts.getSelectedItem();
		
		return result;
	}
}
