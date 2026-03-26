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
    
    // Client Outbound Proxy (matching MainframeMate Settings > Proxy)
    private JCheckBox clientOutboundProxyCheckBox;
    private JComboBox<String> proxyModeBox;           // WINDOWS_PAC, REGISTRY, PAC_URL, MANUAL
    private JLabel proxyHostLabel;
    private JTextField proxyHostField;
    private JLabel proxyPortLabel;
    private JSpinner proxyPortSpinner;
    private JCheckBox proxyNoProxyLocalBox;
    private JTextArea proxyPacScriptArea;
    private JScrollPane pacScrollPane;
    private JLabel pacSectionLabel;
    private JLabel pacUrlLabel;
    private JTextField pacUrlField;
    private JCheckBox pacUrlFromScriptBox;
    private JLabel proxyTestUrlLabel;
    private JTextField proxyTestUrlField;
    private JButton proxyTestButton;
    private JButton resetScriptButton;
    private JSpinner cacheTtlSpinner;
    private JSpinner connectTimeoutSpinner;
    private JSpinner handshakeTimeoutSpinner;

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
        
        // Client Outbound Proxy (matching MainframeMate Settings > Proxy)
        clientOutboundProxyCheckBox = new JCheckBox("Proxy für ausgehende Verbindungen verwenden");
        proxyModeBox = new JComboBox<String>(new String[]{"WINDOWS_PAC", "REGISTRY", "PAC_URL", "MANUAL"});
        proxyModeBox.setToolTipText("<html>" +
                "<b>WINDOWS_PAC</b> — PowerShell PAC/WPAD-Script (anpassbar).<br>" +
                "<b>REGISTRY</b> — Proxy aus der Windows Registry (kein PowerShell nötig).<br>" +
                "<b>PAC_URL</b> — PAC-Datei von einer expliziten URL laden und auswerten.<br>" +
                "<b>MANUAL</b> — Fester Proxy-Host und -Port." +
                "</html>");
        proxyHostLabel = new JLabel("Proxy Host:");
        proxyHostField = new JTextField(24);
        proxyPortLabel = new JLabel("Proxy Port:");
        proxyPortSpinner = new JSpinner(new SpinnerNumberModel(8080, 1, 65535, 1));
        proxyNoProxyLocalBox = new JCheckBox("Lokale Ziele niemals über Proxy");
        proxyNoProxyLocalBox.setSelected(true);

        pacUrlFromScriptBox = new JCheckBox("URL per PowerShell-Script beziehen");
        pacUrlLabel = new JLabel("PAC-URL:");
        pacUrlField = new JTextField(40);
        
        pacSectionLabel = new JLabel("PAC / WPAD Script");
        pacSectionLabel.setFont(pacSectionLabel.getFont().deriveFont(Font.BOLD, pacSectionLabel.getFont().getSize2D() + 1f));
        proxyPacScriptArea = new JTextArea(12, 60);
        proxyPacScriptArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        pacScrollPane = new JScrollPane(proxyPacScriptArea);

        resetScriptButton = new JButton("Standard-Script laden");
        resetScriptButton.setToolTipText("Setzt das PAC/WPAD-Script auf die Werkseinstellung zurück");

        proxyTestUrlLabel = new JLabel("Test-URL:");
        proxyTestUrlField = new JTextField("https://plugins.gradle.org/m2/", 30);
        proxyTestButton = new JButton("Testen");

        cacheTtlSpinner = new JSpinner(new SpinnerNumberModel(300, 1, 86400, 10));
        connectTimeoutSpinner = new JSpinner(new SpinnerNumberModel(10000, 500, 120000, 500));
        handshakeTimeoutSpinner = new JSpinner(new SpinnerNumberModel(10000, 500, 120000, 500));

        // Listeners
        mitmCheckBox.addActionListener(e -> updateControls());
        rewriteCheckBox.addActionListener(e -> updateControls());
        clientOutboundProxyCheckBox.addActionListener(e -> updateControls());
        proxyModeBox.addActionListener(e -> updateControls());
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

        // === Client Outbound Proxy Panel (matching MainframeMate Settings > Proxy) ===
        JPanel outboundPanel = new JPanel(new GridBagLayout());
        outboundPanel.setBorder(new TitledBorder("Ausgehender Proxy (Outbound)"));
        gc = new GridBagConstraints();
        gc.insets = new Insets(3, 4, 3, 4);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;

        row = 0;
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 4;
        JLabel infoLabel = new JLabel("<html><i>Proxy-Konfiguration für ausgehende Verbindungen.</i></html>");
        infoLabel.setForeground(Color.GRAY);
        outboundPanel.add(infoLabel, gc);

        row++;
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 4;
        outboundPanel.add(clientOutboundProxyCheckBox, gc);

        // Proxy mode
        row++;
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 1; gc.weightx = 0;
        outboundPanel.add(new JLabel("Proxy-Modus:"), gc);
        gc.gridx = 1; gc.gridwidth = 3; gc.weightx = 1.0;
        outboundPanel.add(proxyModeBox, gc);

        // MANUAL-only: Host / Port
        row++;
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 1; gc.weightx = 0;
        outboundPanel.add(proxyHostLabel, gc);
        gc.gridx = 1; gc.gridwidth = 3; gc.weightx = 1.0;
        outboundPanel.add(proxyHostField, gc);

        row++;
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 1; gc.weightx = 0;
        outboundPanel.add(proxyPortLabel, gc);
        gc.gridx = 1; gc.gridwidth = 1; gc.weightx = 0;
        outboundPanel.add(proxyPortSpinner, gc);

        row++;
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 4; gc.weightx = 1.0;
        outboundPanel.add(proxyNoProxyLocalBox, gc);

        // PAC_URL-only: Explicit PAC URL
        row++;
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 4;
        outboundPanel.add(pacUrlFromScriptBox, gc);

        row++;
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 1; gc.weightx = 0;
        outboundPanel.add(pacUrlLabel, gc);
        gc.gridx = 1; gc.gridwidth = 3; gc.weightx = 1.0;
        outboundPanel.add(pacUrlField, gc);

        // Separator before PAC script
        row++;
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 4;
        outboundPanel.add(new JSeparator(), gc);

        // PAC/WPAD Script section — Label + Default-Button nebeneinander
        row++;
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 4;
        JPanel pacHeaderPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        pacHeaderPanel.add(pacSectionLabel);
        pacHeaderPanel.add(resetScriptButton);
        outboundPanel.add(pacHeaderPanel, gc);

        row++;
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 4; gc.weightx = 1.0;
        gc.fill = GridBagConstraints.BOTH; gc.weighty = 1.0;
        outboundPanel.add(pacScrollPane, gc);
        gc.fill = GridBagConstraints.HORIZONTAL; gc.weighty = 0;

        // Separator
        row++;
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 4;
        outboundPanel.add(new JSeparator(), gc);

        // Cache TTL
        row++;
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 1; gc.weightx = 0;
        outboundPanel.add(new JLabel("Cache TTL (Sek.):"), gc);
        gc.gridx = 1; gc.weightx = 0;
        outboundPanel.add(cacheTtlSpinner, gc);
        gc.gridx = 2; gc.gridwidth = 2; gc.weightx = 1.0;
        outboundPanel.add(new JLabel("<html><small>Dauer für gecachte Proxy-Auflösungen</small></html>"), gc);

        // Connect Timeout
        row++;
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 1; gc.weightx = 0;
        outboundPanel.add(new JLabel("Connect Timeout (ms):"), gc);
        gc.gridx = 1; gc.weightx = 0;
        outboundPanel.add(connectTimeoutSpinner, gc);

        // Handshake Timeout
        row++;
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 1; gc.weightx = 0;
        outboundPanel.add(new JLabel("Handshake Timeout (ms):"), gc);
        gc.gridx = 1; gc.weightx = 0;
        outboundPanel.add(handshakeTimeoutSpinner, gc);

        // Separator
        row++;
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 4;
        outboundPanel.add(new JSeparator(), gc);

        // Test URL + Test button + Diagnose button
        row++;
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 1; gc.weightx = 0;
        outboundPanel.add(proxyTestUrlLabel, gc);
        gc.gridx = 1; gc.gridwidth = 1; gc.weightx = 1.0;
        outboundPanel.add(proxyTestUrlField, gc);
        JPanel testButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        proxyTestButton.addActionListener(e -> testProxyResolution());
        testButtonsPanel.add(proxyTestButton);
        JButton diagnoseButton = new JButton("Diagnose");
        diagnoseButton.addActionListener(e -> showDiagnostics());
        testButtonsPanel.add(diagnoseButton);
        gc.gridx = 2; gc.gridwidth = 2; gc.weightx = 0;
        outboundPanel.add(testButtonsPanel, gc);

        // Wire mode switch and reset
        proxyModeBox.addActionListener(e -> updateModeVisibility());
        resetScriptButton.addActionListener(e -> {
            String current = proxyPacScriptArea.getText();
            if (current != null && !current.trim().isEmpty()) {
                int answer = JOptionPane.showConfirmDialog(this,
                        "Das aktuelle Script wird durch das Standard-Script ersetzt.\nFortfahren?",
                        "Standard-Script laden", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
                if (answer != JOptionPane.OK_OPTION) {
                    return;
                }
            }
            proxyPacScriptArea.setText(getDefaultPacScript());
            proxyPacScriptArea.setCaretPosition(0);
        });
        pacUrlFromScriptBox.addActionListener(e -> updatePacUrlHint());

        mainPanel.add(outboundPanel);

        // Scroll-Wrapper falls Dialog zu groß wird
        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        content.add(scrollPane, BorderLayout.CENTER);

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
        
        // Client Outbound Proxy - ALL settings
        clientOutboundProxyCheckBox.setSelected(cfg.isClientOutboundProxyEnabled());
        String proxyMode = cfg.getClientOutboundProxyMode();
        proxyModeBox.setSelectedItem(proxyMode != null ? proxyMode : "REGISTRY");
        proxyHostField.setText(cfg.getClientOutboundProxyHost());
        proxyPortSpinner.setValue(cfg.getClientOutboundProxyPort());
        proxyNoProxyLocalBox.setSelected(cfg.isClientOutboundProxyNoProxyLocal());
        pacUrlField.setText(cfg.getClientOutboundProxyPacUrl());
        pacUrlFromScriptBox.setSelected(cfg.isClientOutboundProxyPacUrlFromScript());
        
        String pacScript = cfg.getClientOutboundProxyPacScript();
        proxyPacScriptArea.setText(pacScript != null && !pacScript.trim().isEmpty() ? pacScript : getDefaultPacScript());
        proxyPacScriptArea.setCaretPosition(0);
        
        String testUrl = cfg.getClientOutboundProxyTestUrl();
        proxyTestUrlField.setText(testUrl != null && !testUrl.isEmpty() ? testUrl : "https://plugins.gradle.org/m2/");
        
        cacheTtlSpinner.setValue(cfg.getClientOutboundProxyCacheTtlSeconds());
        connectTimeoutSpinner.setValue(cfg.getClientOutboundProxyConnectTimeoutMillis());
        handshakeTimeoutSpinner.setValue(cfg.getClientOutboundProxyHandshakeTimeoutMillis());
        
        updateControls();
        updateModeVisibility();
        updatePacUrlHint();
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

        // Proxy validation
        boolean outboundEnabled = clientOutboundProxyCheckBox.isSelected();
        String selectedMode = (String) proxyModeBox.getSelectedItem();
        if (outboundEnabled && "MANUAL".equals(selectedMode)) {
            String proxyHost = proxyHostField.getText().trim();
            if (proxyHost.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Proxy Host darf nicht leer sein im MANUAL-Modus.",
                        "Validation Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
        if (outboundEnabled && "PAC_URL".equals(selectedMode)) {
            String pacUrl = pacUrlField.getText().trim();
            if (pacUrl.isEmpty()) {
                JOptionPane.showMessageDialog(this, "PAC URL darf nicht leer sein im PAC_URL-Modus.",
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
        
        // WPAD/PAC - ALLE Settings aus UI übernehmen
        cfg.setClientOutboundProxyEnabled(clientOutboundProxyCheckBox.isSelected());
        cfg.setClientOutboundProxyMode((String) proxyModeBox.getSelectedItem());
        cfg.setClientOutboundProxyHost(proxyHostField.getText().trim());
        cfg.setClientOutboundProxyPort(((Number) proxyPortSpinner.getValue()).intValue());
        cfg.setClientOutboundProxyPacUrl(pacUrlField.getText().trim());
        cfg.setClientOutboundProxyPacUrlFromScript(pacUrlFromScriptBox.isSelected());
        cfg.setClientOutboundProxyBypassList(oldCfg.getClientOutboundProxyBypassList());
        cfg.setClientOutboundProxyNoProxyLocal(proxyNoProxyLocalBox.isSelected());
        cfg.setClientOutboundProxyPacScript(proxyPacScriptArea.getText());
        cfg.setClientOutboundProxyTestUrl(proxyTestUrlField.getText().trim());
        cfg.setClientOutboundProxyPacSource(oldCfg.getClientOutboundProxyPacSource());
        cfg.setClientOutboundProxyCacheTtlSeconds(((Number) cacheTtlSpinner.getValue()).intValue());
        cfg.setClientOutboundProxyConnectTimeoutMillis(((Number) connectTimeoutSpinner.getValue()).intValue());
        cfg.setClientOutboundProxyHandshakeTimeoutMillis(((Number) handshakeTimeoutSpinner.getValue()).intValue());
        
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
        
        // Proxy controls enable/disable based on master checkbox
        boolean proxyEnabled = clientOutboundProxyCheckBox.isSelected();
        proxyModeBox.setEnabled(proxyEnabled);
        cacheTtlSpinner.setEnabled(proxyEnabled);
        connectTimeoutSpinner.setEnabled(proxyEnabled);
        handshakeTimeoutSpinner.setEnabled(proxyEnabled);
        
        if (proxyEnabled) {
            updateModeVisibility();
        } else {
            // Disable all mode-specific controls
            proxyHostLabel.setEnabled(false);
            proxyHostField.setEnabled(false);
            proxyPortLabel.setEnabled(false);
            proxyPortSpinner.setEnabled(false);
            proxyNoProxyLocalBox.setEnabled(false);
            pacUrlLabel.setEnabled(false);
            pacUrlField.setEnabled(false);
            pacUrlFromScriptBox.setEnabled(false);
            pacSectionLabel.setEnabled(false);
            proxyPacScriptArea.setEnabled(false);
            proxyPacScriptArea.setEditable(false);
            resetScriptButton.setEnabled(false);
            proxyTestUrlLabel.setEnabled(false);
            proxyTestUrlField.setEnabled(false);
            proxyTestButton.setEnabled(false);
        }
    }

    /**
     * Enables/disables fields depending on the selected proxy mode.
     * Matches MainframeMate Settings > Proxy behavior:
     * <ul>
     *   <li><b>WINDOWS_PAC</b>: PAC script + Test enabled, Host/Port/PAC-URL disabled</li>
     *   <li><b>REGISTRY</b>: Test-URL + Test enabled, PAC script + Host/Port/PAC-URL disabled</li>
     *   <li><b>PAC_URL</b>: PAC-URL + Test enabled, PAC script + Host/Port disabled</li>
     *   <li><b>MANUAL</b>: Host/Port enabled, PAC script + Test + PAC-URL disabled</li>
     * </ul>
     */
    private void updateModeVisibility() {
        String mode = java.util.Objects.toString(proxyModeBox.getSelectedItem(), "REGISTRY");
        boolean isPac = "WINDOWS_PAC".equals(mode);
        boolean isRegistry = "REGISTRY".equals(mode);
        boolean isPacUrl = "PAC_URL".equals(mode);
        boolean isManual = "MANUAL".equals(mode);

        // MANUAL fields — only in MANUAL mode
        proxyHostLabel.setEnabled(isManual);
        proxyHostField.setEnabled(isManual);
        proxyPortLabel.setEnabled(isManual);
        proxyPortSpinner.setEnabled(isManual);

        // No-proxy-local — always available
        proxyNoProxyLocalBox.setEnabled(true);

        // Explicit PAC URL / Script — only in PAC_URL mode
        pacUrlLabel.setEnabled(isPacUrl);
        pacUrlField.setEnabled(isPacUrl);
        pacUrlFromScriptBox.setEnabled(isPacUrl);

        // PAC/WPAD script — only in WINDOWS_PAC mode
        pacSectionLabel.setEnabled(isPac);
        proxyPacScriptArea.setEnabled(isPac);
        proxyPacScriptArea.setEditable(isPac);
        resetScriptButton.setEnabled(isPac);

        // Test-URL + Test-Button — for WINDOWS_PAC, REGISTRY, and PAC_URL
        boolean testable = isPac || isRegistry || isPacUrl;
        proxyTestUrlLabel.setEnabled(testable);
        proxyTestUrlField.setEnabled(testable);
        proxyTestButton.setEnabled(testable);
    }

    /** Updates label and tooltip of the PAC URL field depending on script mode. */
    private void updatePacUrlHint() {
        if (pacUrlFromScriptBox.isSelected()) {
            pacUrlLabel.setText("PAC-URL Script:");
            pacUrlField.setToolTipText("PowerShell-Befehl, dessen Ausgabe die PAC-URL ist.");
        } else {
            pacUrlLabel.setText("PAC-URL:");
            pacUrlField.setToolTipText("Vollständige URL zur PAC-Datei.");
        }
    }

    /** Returns a default PAC/WPAD PowerShell script. */
    private String getDefaultPacScript() {
        return "param(\n" +
                "    [string]$TestUrl = \"https://plugins.gradle.org/m2/\",\n" +
                "    [switch]$DebugEnabled\n" +
                ")\n\n" +
                "function Write-DebugLine([string]$msg) {\n" +
                "    if ($DebugEnabled) { Write-Host $msg }\n" +
                "}\n\n" +
                "$uri = [Uri]$TestUrl\n\n" +
                "$proxy = [System.Net.WebRequest]::GetSystemWebProxy()\n" +
                "$proxy.Credentials = [System.Net.CredentialCache]::DefaultNetworkCredentials\n\n" +
                "if ($proxy.IsBypassed($uri)) {\n" +
                "    Write-DebugLine (\"[DEBUG] DIRECT for {0}\" -f $TestUrl)\n" +
                "    exit 0\n" +
                "}\n\n" +
                "$proxyUri = $proxy.GetProxy($uri)\n\n" +
                "if (-not $proxyUri -or $proxyUri.AbsoluteUri -eq $uri.AbsoluteUri) {\n" +
                "    Write-DebugLine (\"[DEBUG] DIRECT for {0}\" -f $TestUrl)\n" +
                "    exit 0\n" +
                "}\n\n" +
                "Write-DebugLine (\"[DEBUG] Proxy for {0} -> {1}\" -f $TestUrl, $proxyUri.AbsoluteUri)\n" +
                "Write-Output (\"{0}:{1}\" -f $proxyUri.Host, $proxyUri.Port)\n" +
                "exit 0\n";
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
     * Tests proxy resolution using the win-proxy-java library with current dialog settings.
     */
    private void testProxyResolution() {
        String testUrl = proxyTestUrlField.getText().trim();
        if (testUrl.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Bitte Test-URL eingeben.", "Proxy Test", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String os = System.getProperty("os.name");
        if (os == null || !os.toLowerCase().contains("win")) {
            JOptionPane.showMessageDialog(this, 
                    "Windows Systemproxy-Erkennung nur unter Windows verfügbar.",
                    "Proxy Test", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            // Create resolver with CURRENT dialog settings (not saved config)
            String selectedMode = (String) proxyModeBox.getSelectedItem();
            WinProxyJavaResolver.ResolveMode resolveMode = WinProxyJavaResolver.ResolveMode.AUTO;
            if ("MANUAL".equals(selectedMode)) resolveMode = WinProxyJavaResolver.ResolveMode.STATIC;
            else if ("PAC_URL".equals(selectedMode)) resolveMode = WinProxyJavaResolver.ResolveMode.PAC_URL;
            // WINDOWS_PAC and REGISTRY both use AUTO mode

            WinProxyJavaResolver resolver = new WinProxyJavaResolver(
                    resolveMode,
                    proxyHostField.getText().trim(),
                    ((Number) proxyPortSpinner.getValue()).intValue(),
                    pacUrlField.getText().trim(),
                    "", // bypass list
                    "REGISTRY".equals(selectedMode) ? "REGISTRY" : "POWERSHELL",
                    300,
                    null
            );

            ProxyInfo proxyInfo = resolver.testResolve(testUrl);

            // Build result message
            StringBuilder msg = new StringBuilder();
            msg.append("Proxy-Auflösung für: ").append(testUrl).append("\n");
            msg.append("Modus: ").append(selectedMode).append("\n\n");
            
            if (proxyInfo.isDirect()) {
                msg.append("Ergebnis: DIRECT (kein Proxy nötig)");
            } else {
                msg.append("Ergebnis:\n");
                for (ProxyInfo.ProxyCandidate candidate : proxyInfo.getCandidates()) {
                    if (candidate.isDirect()) {
                        msg.append("  \u2022 DIRECT (Fallback)\n");
                    } else {
                        msg.append("  \u2022 ").append(candidate.getType())
                           .append(" ").append(candidate.getHost())
                           .append(":").append(candidate.getPort()).append("\n");
                    }
                }
            }

            JOptionPane.showMessageDialog(this, msg.toString(),
                    "Proxy Test - " + selectedMode, JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, 
                    "Fehler beim Proxy-Test:\n" + e.getMessage(),
                    "Proxy Test Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Shows Windows proxy diagnostic info from the library.
     */
    private void showDiagnostics() {
        String os = System.getProperty("os.name");
        if (os == null || !os.toLowerCase().contains("win")) {
            JOptionPane.showMessageDialog(this, 
                    "Windows Proxy-Diagnose nur unter Windows verfügbar.",
                    "Diagnose", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            String diagnostics = WinProxyJavaResolver.getDiagnosticInfo();
            
            JTextArea textArea = new JTextArea(diagnostics);
            textArea.setEditable(false);
            textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            JScrollPane scroll = new JScrollPane(textArea);
            scroll.setPreferredSize(new Dimension(500, 350));
            
            JOptionPane.showMessageDialog(this, scroll,
                    "Windows Proxy Diagnose", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, 
                    "Fehler beim Lesen der Diagnose:\n" + e.getMessage(),
                    "Diagnose Fehler", JOptionPane.ERROR_MESSAGE);
        }
    }
}
