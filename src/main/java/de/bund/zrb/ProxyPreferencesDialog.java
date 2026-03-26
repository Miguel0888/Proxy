package de.bund.zrb;

import de.bund.zrb.config.ProxyConfig;
import de.bund.zrb.service.ProxyConfigService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.net.URI;

/**
 * Preferences dialog for proxy configuration.
 * All settings are loaded on open and saved on OK.
 */
public class ProxyPreferencesDialog extends JDialog {

    private final ProxyConfigService configService;
    private final Runnable onSaveCallback;

    // MITM Settings
    private JTextField keystoreField;
    private JCheckBox mitmCheckBox;
    private JCheckBox rewriteCheckBox;
    private JTextField rewriteModelField;
    private JTextField rewriteTemperatureField;

    // Gateway Settings
    private JCheckBox gatewayCheckBox;
    private JCheckBox relayModeCheckBox;
    
    // Client Outbound Proxy (Windows System Proxy via win-proxy-java)
    private JCheckBox clientOutboundProxyCheckBox;
    private JTextField clientOutboundProxyTestUrlField;

    public ProxyPreferencesDialog(Frame owner, ProxyConfigService configService) {
        this(owner, configService, null);
    }

    public ProxyPreferencesDialog(Frame owner, ProxyConfigService configService, Runnable onSaveCallback) {
        super(owner, "Preferences", true);
        this.configService = configService;
        this.onSaveCallback = onSaveCallback;

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
        
        // Client Outbound (win-proxy-java)
        clientOutboundProxyCheckBox = new JCheckBox("Use Windows system proxy (WPAD/PAC) for outbound connections");
        clientOutboundProxyTestUrlField = new JTextField("https://www.google.com/", 30);

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
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 1; gc.weightx = 0;
        outboundPanel.add(new JLabel("Test URL:"), gc);
        gc.gridx = 1; gc.weightx = 1.0;
        outboundPanel.add(clientOutboundProxyTestUrlField, gc);
        JButton testButton = new JButton("Test");
        testButton.addActionListener(e -> testProxyResolution());
        gc.gridx = 2; gc.weightx = 0;
        outboundPanel.add(testButton, gc);

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
        
        // Gateway Auth
        cfg.setGatewayAuthMode(oldCfg.getGatewayAuthMode());
        cfg.setGatewayEncryptionEnabled(oldCfg.isGatewayEncryptionEnabled());
        cfg.setGatewayUsername(oldCfg.getGatewayUsername());
        
        // Relay Mode
        cfg.setRelayModeEnabled(relayModeCheckBox.isSelected());
        cfg.setRemoteGatewayHost(oldCfg.getRemoteGatewayHost());

        try {
            configService.saveConfig(cfg);
            
            // Callback für Proxy-Restart aufrufen
            if (onSaveCallback != null) {
                onSaveCallback.run();
            }
            
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
        
        // Test URL field enabled only when WPAD is enabled
        boolean wpadEnabled = clientOutboundProxyCheckBox.isSelected();
        clientOutboundProxyTestUrlField.setEnabled(wpadEnabled);
    }

    private void chooseKeystore() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select myproxy.jks");
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            keystoreField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    /**
     * Tests proxy resolution using the win-proxy-java library.
     */
    private void testProxyResolution() {
        String testUrl = clientOutboundProxyTestUrlField.getText().trim();
        if (testUrl.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a test URL.", "Proxy Test", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Check if Windows
        String os = System.getProperty("os.name");
        if (os == null || !os.toLowerCase().contains("win")) {
            JOptionPane.showMessageDialog(this, 
                    "Windows system proxy detection is only available on Windows.",
                    "Proxy Test", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            // Create resolver using win-proxy-java library
            WinProxyJavaResolver resolver = new WinProxyJavaResolver(300, null);

            // Parse URL to get host/port
            URI uri = new URI(testUrl);
            String host = uri.getHost();
            int port = uri.getPort();
            boolean https = "https".equalsIgnoreCase(uri.getScheme());
            if (port == -1) {
                port = https ? 443 : 80;
            }

            // Resolve proxy
            ProxyInfo proxyInfo = resolver.resolveProxy(host, port, https);

            // Build result message
            StringBuilder msg = new StringBuilder();
            msg.append("Proxy resolution for: ").append(testUrl).append("\n\n");
            
            if (proxyInfo.isDirect()) {
                msg.append("Result: DIRECT (no proxy needed)");
            } else {
                msg.append("Result:\n");
                for (ProxyInfo.ProxyCandidate candidate : proxyInfo.getCandidates()) {
                    if (candidate.isDirect()) {
                        msg.append("  - DIRECT\n");
                    } else {
                        msg.append("  - ").append(candidate.getType())
                           .append(" ").append(candidate.getHost())
                           .append(":").append(candidate.getPort()).append("\n");
                    }
                }
            }

            JOptionPane.showMessageDialog(this, msg.toString(),
                    "Proxy Test - win-proxy-java", JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, 
                    "Error testing proxy:\n" + e.getMessage(),
                    "Proxy Test Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
