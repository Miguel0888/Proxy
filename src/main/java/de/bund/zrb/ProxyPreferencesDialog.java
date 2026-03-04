package de.bund.zrb;

import de.bund.zrb.config.ProxyConfig;
import de.bund.zrb.service.ProxyConfigService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * Preferences dialog for proxy configuration.
 * All settings are loaded on open and saved on OK.
 */
public class ProxyPreferencesDialog extends JDialog {

    private final ProxyConfigService configService;

    // MITM Settings
    private JTextField keystoreField;
    private JCheckBox mitmCheckBox;
    private JCheckBox rewriteCheckBox;
    private JTextField rewriteModelField;
    private JTextField rewriteTemperatureField;

    // Gateway Settings
    private JCheckBox gatewayCheckBox;
    private JCheckBox relayModeCheckBox;
    
    // Client Outbound Proxy (WPAD/PAC)
    private JCheckBox clientOutboundProxyCheckBox;
    private JTextField clientOutboundProxyScriptField;

    public ProxyPreferencesDialog(Frame owner, ProxyConfigService configService) {
        super(owner, "Preferences", true);
        this.configService = configService;

        initComponents();
        layoutComponents();
        loadFromConfig();
        pack();
        setLocationRelativeTo(owner);
    }

    private void initComponents() {
        // MITM
        keystoreField = new JTextField(30);
        mitmCheckBox = new JCheckBox("Enable MITM for api.openai.com");
        rewriteCheckBox = new JCheckBox("Rewrite model/temperature for /v1/chat/completions");
        rewriteModelField = new JTextField("gpt-5-mini", 16);
        rewriteTemperatureField = new JTextField("1.0", 4);

        // Gateway
        gatewayCheckBox = new JCheckBox("Accept gateway client connections (required for reverse proxy)");
        relayModeCheckBox = new JCheckBox("Relay mode: Keep local server running when connected to remote");
        
        // Client Outbound
        clientOutboundProxyCheckBox = new JCheckBox("Use Windows system proxy (WPAD/PAC) for outbound connections");
        clientOutboundProxyScriptField = new JTextField(30);

        // Listeners
        mitmCheckBox.addActionListener(e -> updateControls());
        rewriteCheckBox.addActionListener(e -> updateControls());
        clientOutboundProxyCheckBox.addActionListener(e -> updateControls());
    }

    private void layoutComponents() {
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(new EmptyBorder(10, 10, 10, 10));
        setContentPane(content);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        // === MITM Panel ===
        JPanel mitmPanel = new JPanel(new GridBagLayout());
        mitmPanel.setBorder(new TitledBorder("MITM (Man-in-the-Middle)"));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 4, 4, 4);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 1;
        mitmPanel.add(new JLabel("Keystore (.jks):"), gc);
        gc.gridx = 1; gc.weightx = 1.0;
        mitmPanel.add(keystoreField, gc);
        JButton browse = new JButton("Browse...");
        browse.addActionListener(e -> chooseKeystore());
        gc.gridx = 2; gc.weightx = 0;
        mitmPanel.add(browse, gc);

        row++;
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 3;
        mitmPanel.add(mitmCheckBox, gc);

        row++;
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 3;
        JPanel rewritePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        rewritePanel.add(rewriteCheckBox);
        rewritePanel.add(new JLabel("Model:"));
        rewritePanel.add(rewriteModelField);
        rewritePanel.add(new JLabel("Temp:"));
        rewritePanel.add(rewriteTemperatureField);
        mitmPanel.add(rewritePanel, gc);

        mainPanel.add(mitmPanel);

        // === Gateway Panel ===
        JPanel gatewayPanel = new JPanel(new GridBagLayout());
        gatewayPanel.setBorder(new TitledBorder("Gateway / Reverse Proxy"));
        gc = new GridBagConstraints();
        gc.insets = new Insets(4, 4, 4, 4);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.gridwidth = 1;

        row = 0;
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 2;
        gatewayPanel.add(gatewayCheckBox, gc);

        row++;
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 2;
        gatewayPanel.add(relayModeCheckBox, gc);

        mainPanel.add(gatewayPanel);

        // === Client Outbound Panel ===
        JPanel outboundPanel = new JPanel(new GridBagLayout());
        outboundPanel.setBorder(new TitledBorder("Client Outbound (when connecting to targets)"));
        gc = new GridBagConstraints();
        gc.insets = new Insets(4, 4, 4, 4);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;

        row = 0;
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 3;
        outboundPanel.add(clientOutboundProxyCheckBox, gc);

        row++;
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 1;
        outboundPanel.add(new JLabel("Custom Script (optional):"), gc);
        gc.gridx = 1; gc.weightx = 1.0;
        outboundPanel.add(clientOutboundProxyScriptField, gc);
        JButton browseScript = new JButton("Browse...");
        browseScript.addActionListener(e -> chooseScript());
        gc.gridx = 2; gc.weightx = 0;
        outboundPanel.add(browseScript, gc);

        mainPanel.add(outboundPanel);

        content.add(mainPanel, BorderLayout.CENTER);

        // Buttons
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Cancel");
        ok.addActionListener(e -> onOk());
        cancel.addActionListener(e -> dispose());
        buttons.add(ok);
        buttons.add(cancel);
        content.add(buttons, BorderLayout.SOUTH);
    }

    private void loadFromConfig() {
        ProxyConfig cfg = configService.loadConfig();
        
        // MITM
        keystoreField.setText(cfg.getKeystorePath() != null ? cfg.getKeystorePath() : "");
        mitmCheckBox.setSelected(cfg.isMitmEnabled());
        rewriteCheckBox.setSelected(cfg.isRewriteEnabled());
        rewriteModelField.setText(cfg.getRewriteModel() != null ? cfg.getRewriteModel() : "gpt-5-mini");
        rewriteTemperatureField.setText(cfg.getRewriteTemperature() != null ? cfg.getRewriteTemperature() : "1.0");
        
        // Gateway
        gatewayCheckBox.setSelected(cfg.isGatewayEnabled());
        relayModeCheckBox.setSelected(cfg.isRelayModeEnabled());
        
        // Client Outbound
        clientOutboundProxyCheckBox.setSelected(cfg.isClientOutboundProxyEnabled());
        clientOutboundProxyScriptField.setText(cfg.getClientOutboundProxyScriptPath());
        
        updateControls();
    }

    private void onOk() {
        // Validierung
        boolean mitmEnabled = mitmCheckBox.isSelected();
        String keystore = keystoreField.getText().trim();
        if (mitmEnabled && keystore.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Keystore path must not be empty when MITM is enabled.", 
                    "Validation Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        boolean rewriteEnabled = mitmEnabled && rewriteCheckBox.isSelected();
        String model = rewriteModelField.getText().trim();
        String temp = rewriteTemperatureField.getText().trim();

        if (rewriteEnabled) {
            if (model.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Model name must not be empty when rewrite is enabled.", 
                        "Validation Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (temp.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Temperature must not be empty when rewrite is enabled.", 
                        "Validation Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            try {
                Double.parseDouble(temp);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Temperature must be a valid decimal number.", 
                        "Validation Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        // Bestehende Config laden und nur die Werte aus diesem Dialog ändern
        ProxyConfig oldCfg = configService.loadConfig();
        
        // Neue Config mit alten Werten erstellen
        ProxyConfig cfg = new ProxyConfig(
                oldCfg.getPort(),
                keystore,
                mitmEnabled,
                rewriteEnabled,
                model,
                temp,
                gatewayCheckBox.isSelected(),
                oldCfg.getProxyMode(),
                oldCfg.getClientHost(),
                oldCfg.getClientPort(),
                oldCfg.getGatewayPasskey()
        );

        // Alle anderen Einstellungen übernehmen
        cfg.setShowHelpOnStart(oldCfg.isShowHelpOnStart());
        cfg.setServerPort(String.valueOf(oldCfg.getServerPort()));
        cfg.setServerGatewayPasskey(oldCfg.getServerGatewayPasskey());
        cfg.setClientGatewayPasskey(oldCfg.getClientGatewayPasskey());
        
        // WPAD/PAC
        cfg.setClientOutboundProxyEnabled(clientOutboundProxyCheckBox.isSelected());
        cfg.setClientOutboundProxyCacheTtlSeconds(oldCfg.getClientOutboundProxyCacheTtlSeconds());
        cfg.setClientOutboundProxyConnectTimeoutMillis(oldCfg.getClientOutboundProxyConnectTimeoutMillis());
        cfg.setClientOutboundProxyHandshakeTimeoutMillis(oldCfg.getClientOutboundProxyHandshakeTimeoutMillis());
        cfg.setClientOutboundProxyScriptPath(clientOutboundProxyScriptField.getText().trim());
        
        // Gateway Auth
        cfg.setGatewayAuthMode(oldCfg.getGatewayAuthMode());
        cfg.setGatewayEncryptionEnabled(oldCfg.isGatewayEncryptionEnabled());
        cfg.setGatewayUsername(oldCfg.getGatewayUsername());
        
        // Relay Mode
        cfg.setRelayModeEnabled(relayModeCheckBox.isSelected());
        cfg.setRemoteGatewayHost(oldCfg.getRemoteGatewayHost());

        try {
            configService.saveConfig(cfg);
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to save config: " + ex.getMessage(), 
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateControls() {
        boolean mitm = mitmCheckBox.isSelected();
        rewriteCheckBox.setEnabled(mitm);
        boolean rewrite = mitm && rewriteCheckBox.isSelected();
        rewriteModelField.setEnabled(rewrite);
        rewriteTemperatureField.setEnabled(rewrite);
        
        // Script field enabled only when WPAD is enabled
        boolean wpadEnabled = clientOutboundProxyCheckBox.isSelected();
        clientOutboundProxyScriptField.setEnabled(wpadEnabled);
    }

    private void chooseKeystore() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select myproxy.jks");
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            keystoreField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void chooseScript() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select proxy resolver script (.ps1)");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PowerShell Scripts (*.ps1)", "ps1"));
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            clientOutboundProxyScriptField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }
}
