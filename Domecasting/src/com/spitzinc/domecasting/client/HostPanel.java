package com.spitzinc.domecasting.client;

import javax.swing.JPanel;

import com.spitzinc.domecasting.JSwitchButton;
import com.spitzinc.domecasting.Log;

import javax.swing.JLabel;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;

import java.awt.Insets;
import javax.swing.JButton;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.awt.event.ActionEvent;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JProgressBar;

public class HostPanel extends JPanel
{
	private static final long serialVersionUID = 1L;
	
	private static final String buttonTextListenToDomecast = "Listen to Domecast";
	private static final String buttonTextStopListeningToDomecast = "Stop Listening to Domecast";
	
	private boolean ignoreDomecastComboboxChanges;
	private JButton btnDownloadAssets;
	private JSwitchButton btnDomecastListen;
	private JLabel lblStatusText;
	private JComboBox<String> cmbAvailableDomecasts;
	private JLabel lblNewLabel;
	private JFileChooser fileChooser;
	private JProgressBar progressBar;

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
						ClientInfoSendThread sendThread = new ClientInfoSendThread();
						sendThread.setDomecastID(domecastID);
						sendThread.setIsPeerConnected();
						sendThread.start();
					}
				}
			}
		});
		GridBagConstraints gbc_availableDomecasts = new GridBagConstraints();
		gbc_availableDomecasts.insets = new Insets(0, 0, 5, 0);
		gbc_availableDomecasts.gridx = 0;
		gbc_availableDomecasts.gridy = 1;
		add(cmbAvailableDomecasts, gbc_availableDomecasts);
		
		btnDownloadAssets = new JButton("Download Presentation Assets...");
		btnDownloadAssets.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0)
			{
				// Attempt to initialize file chooser dialog to correct folder
				ClientApplication inst = (ClientApplication)ClientApplication.inst();
				String currentDirectory = parsePackagedShowFolderFromSiteConfigFile();
				if ((currentDirectory == null) && (inst.lastAssetsSaveFolder != null))
					currentDirectory = inst.lastAssetsSaveFolder;
				if (currentDirectory != null)
					fileChooser.setCurrentDirectory(new File(currentDirectory));
			
				// Display modal file chooser dialog
				int returnValue = fileChooser.showOpenDialog(HostPanel.this);
				if (returnValue == JFileChooser.APPROVE_OPTION)
				{
					// Allow user to choose folder where assets file will be saved to
					File theAssetsFolder = fileChooser.getSelectedFile();
					inst.lastAssetsSaveFolder = theAssetsFolder.getAbsolutePath();
					
					// Show progress bar during file upload
					btnDownloadAssets.setVisible(false);
					progressBar.setString("Downloading assets file...");
					progressBar.setVisible(true);
					
					// Download the asset file and save it to this folder
					ClientInfoSendThread sendThread = new ClientInfoSendThread();
					sendThread.setGetAssetsFile(true);
					sendThread.start();
				}
			}
		});
		btnDownloadAssets.setEnabled(false);
		GridBagConstraints gbc_btnGetPresentationAssets = new GridBagConstraints();
		gbc_btnGetPresentationAssets.insets = new Insets(0, 5, 0, 5);
		gbc_btnGetPresentationAssets.gridx = 0;
		gbc_btnGetPresentationAssets.gridy = 2;
		add(btnDownloadAssets, gbc_btnGetPresentationAssets);
		
		progressBar = new JProgressBar();
		progressBar.setMinimum(0);
		progressBar.setMaximum(100);
		progressBar.setValue(0);
		progressBar.setStringPainted(true);
		progressBar.setVisible(false);
		GridBagConstraints gbc_progressBar = new GridBagConstraints();
		gbc_progressBar.fill = GridBagConstraints.HORIZONTAL;
		gbc_progressBar.insets = new Insets(0, 5, 0, 5);
		gbc_progressBar.gridx = 0;
		gbc_progressBar.gridy = 2;
		add(progressBar, gbc_progressBar);
		
		btnDomecastListen = new JSwitchButton("On", "Off");
		btnDomecastListen.setEnabled(false);
		btnDomecastListen.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				ClientApplication inst = (ClientApplication) ClientApplication.inst();
				if (!inst.isHostListening.get())
				{
					Log.inst().info("Listening to domecast.");
					
					inst.isHostListening.set(true);
					inst.snPassThru.notifyThreadsOfCommModeChange();
					
					// Tell the server we're ready to be controlled by presenter
					ClientInfoSendThread sendThread = new ClientInfoSendThread();
					sendThread.setIsHostListening(true);
					sendThread.start();
					
					// Disable controls so that only the btnPresentationControl is enabled
					cmbAvailableDomecasts.setEnabled(false);
					inst.appFrame.tabbedPane.setEnabled(false);
					
					// Change the button text
//					btnDomecastListen.setText(buttonTextStopListeningToDomecast);
				}
				else
				{
					Log.inst().info("Not listening to domecast.");
					
					inst.isHostListening.set(false);
					inst.snPassThru.notifyThreadsOfCommModeChange();
					
					// Tell the server we're not ready to be controlled by presenter
					ClientInfoSendThread sendThread = new ClientInfoSendThread();
					sendThread.setIsHostListening(false);
					sendThread.start();
					
					// Enable controls we disabled
					cmbAvailableDomecasts.setEnabled(true);
					inst.appFrame.tabbedPane.setEnabled(true);
					
					// Change the button text
//					btnDomecastListen.setText(buttonTextListenToDomecast);
				}
			}
		});
		GridBagConstraints gbc_btnWaitForPresentation = new GridBagConstraints();
		gbc_btnWaitForPresentation.insets = new Insets(5, 5, 0, 5);
		gbc_btnWaitForPresentation.gridx = 0;
		gbc_btnWaitForPresentation.gridy = 3;
		add(btnDomecastListen, gbc_btnWaitForPresentation);
		
		lblStatusText = new JLabel("status text");
		GridBagConstraints gbc_lblStatusText = new GridBagConstraints();
		gbc_lblStatusText.insets = new Insets(5, 0, 0, 0);
		gbc_lblStatusText.gridx = 0;
		gbc_lblStatusText.gridy = 4;
		add(lblStatusText, gbc_lblStatusText);

	}
	
	public void resetUI()
	{
		cmbAvailableDomecasts.removeAllItems();
		btnDownloadAssets.setEnabled(false);
		progressBar.setVisible(false);
		btnDownloadAssets.setVisible(true);
		btnDomecastListen.setSelected(false);
		btnDomecastListen.setEnabled(false);
	}

	public void updatePanel(String[] domecasts)
	{
		ClientApplication inst = (ClientApplication) ClientApplication.inst();
		
		if (lblStatusText != null)
			lblStatusText.setText(inst.statusText.get());
		
		if (progressBar != null)
		{
			int progressValue = inst.fileProgress.get();
			if (progressValue == 0)
			{
				progressBar.setVisible(false);
				btnDownloadAssets.setVisible(true);
			}
		
			progressBar.setValue(progressValue);
		}
		
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

		// Enable "Listen to Domecast" button accordingly
		btnDomecastListen.setEnabled(inst.isPeerConnected.get());
		btnDomecastListen.setSelected(inst.isHostListening.get());
		
		// Enable "Download Presentation Assets..." button accordingly
		btnDownloadAssets.setEnabled(inst.assetsFileAvailable.get());
	}
	
	public String getDomecastID()
	{
		String result = "";

		if ((cmbAvailableDomecasts.getItemCount() > 0) && (cmbAvailableDomecasts.getSelectedIndex() >= 0))
			result = (String)cmbAvailableDomecasts.getSelectedItem();
		
		return result;
	}
	
	/*
	 * Attempt to locate a config line called "PackagedShowDirectory"
	 */
	private String parsePackagedShowFolderFromSiteConfigFile()
	{
		String result = null;
		
		// Attempt to parse the folder location where ShowPack.exe-generated ZIPs should
		// be saved.
		String siteConfigFilePath = System.getenv("ProgramFiles(X86)") + File.separator +
									"Spitz, Inc" + File.separator + 
									"Atm-4" + File.separator +
									"site.config";
		File siteConfigFile = new File(siteConfigFilePath);
		if (siteConfigFile.exists())
		{
			BufferedReader br = null;
			try
			{
				br = new BufferedReader(new FileReader(siteConfigFile));
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			String line;
			try
			{
				while ((line = br.readLine()) != null)
				{
					line = line.trim();
					int equalsIndex = line.indexOf('=');
					if (equalsIndex > 0)
					{
						String valueName = line.substring(0, equalsIndex).toLowerCase().trim();
						if (valueName.equals("packagedshowdirectory"))
						{
							result = line.substring(equalsIndex + 1).trim();
							break;
						}
					}
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			try {
				br.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		else
			Log.inst().info("Unable to locate site.config file");
		
		return result;
	}
}
