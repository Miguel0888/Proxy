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
    private JComboBox<String> proxyModeBox;           // AUTO, STATIC, PAC_URL
    private JTextField staticProxyHostField;
    private JSpinner staticProxyPortSpinner;
    private JTextField pacUrlField;
    private JTextField bypassListField;
    private JTextField staticBypassListField;          // separate bypass field for STATIC card
    private JComboBox<String> pacSourceBox;            // REGISTRY, POWERSHELL, DIRECT
    private JSpinner cacheTtlSpinner;
    private JSpinner connectTimeoutSpinner;
    private JSpinner handshakeTimeoutSpinner;
    private JTextField clientOutboundProxyTestUrlField;

    // Card layout for mode-specific panels
    private JPanel modeCardsPanel;
    private CardLayout modeCardLayout;

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
        
        // Client Outbound Proxy (win-proxy-java)
        clientOutboundProxyCheckBox = new JCheckBox("Windows Systemproxy für ausgehende Verbindungen verwenden");
        proxyModeBox = new JComboBox<String>(new String[]{"AUTO", "STATIC", "PAC_URL"});
        staticProxyHostField = new JTextField(20);
        staticProxyPortSpinner = new JSpinner(new SpinnerNumberModel(8080, 1, 65535, 1));
        pacUrlField = new JTextField(30);
        bypassListField = new JTextField(30);
        staticBypassListField = new JTextField(30);
        pacSourceBox = new JComboBox<String>(new String[]{"REGISTRY", "POWERSHELL", "DIRECT"});
        cacheTtlSpinner = new JSpinner(new SpinnerNumberModel(300, 1, 86400, 10));
        connectTimeoutSpinner = new JSpinner(new SpinnerNumberModel(10000, 500, 120000, 500));
        handshakeTimeoutSpinner = new JSpinner(new SpinnerNumberModel(10000, 500, 120000, 500));
        clientOutboundProxyTestUrlField = new JTextField("https://www.google.com/", 30);

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

        // === Client Outbound Panel (FULL win-proxy-java settings) ===
        JPanel outboundPanel = new JPanel(new GridBagLayout());
        outboundPanel.setBorder(new TitledBorder("Client Outbound Proxy (win-proxy-java)"));
        gc = new GridBagConstraints();
        gc.insets = new Insets(3, 4, 3, 4);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;

        row = 0;
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 4;
        outboundPanel.add(clientOutboundProxyCheckBox, gc);

        // Proxy mode
        row++;
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 1; gc.weightx = 0;
        outboundPanel.add(new JLabel("Proxy-Modus:"), gc);
        gc.gridx = 1; gc.gridwidth = 1; gc.weightx = 0;
        outboundPanel.add(proxyModeBox, gc);
        gc.gridx = 2; gc.gridwidth = 2; gc.weightx = 1.0;
        JLabel modeHintLabel = new JLabel("<html><small><i>AUTO=Windows System, STATIC=Manuell, PAC_URL=PAC auswerten</i></small></html>");
        outboundPanel.add(modeHintLabel, gc);

        // -- Mode-specific cards --
        modeCardLayout = new CardLayout();
        modeCardsPanel = new JPanel(modeCardLayout);

        // AUTO card (PAC source + bypass)
        JPanel autoCard = new JPanel(new GridBagLayout());
        GridBagConstraints ac = new GridBagConstraints();
        ac.insets = new Insets(2, 4, 2, 4);
        ac.anchor = GridBagConstraints.WEST;
        ac.fill = GridBagConstraints.HORIZONTAL;
        ac.gridy = 0; ac.gridx = 0; ac.weightx = 0;
        autoCard.add(new JLabel("PAC-Quelle:"), ac);
        ac.gridx = 1; ac.weightx = 1.0;
        autoCard.add(pacSourceBox, ac);
        ac.gridy = 1; ac.gridx = 0; ac.weightx = 0;
        autoCard.add(new JLabel("Bypass-Liste:"), ac);
        ac.gridx = 1; ac.weightx = 1.0;
        autoCard.add(bypassListField, ac);

        // STATIC card (host + port)
        JPanel staticCard = new JPanel(new GridBagLayout());
        GridBagConstraints sc = new GridBagConstraints();
        sc.insets = new Insets(2, 4, 2, 4);
        sc.anchor = GridBagConstraints.WEST;
        sc.fill = GridBagConstraints.HORIZONTAL;
        sc.gridy = 0; sc.gridx = 0; sc.weightx = 0;
        staticCard.add(new JLabel("Proxy Host:"), sc);
        sc.gridx = 1; sc.weightx = 1.0;
        staticCard.add(staticProxyHostField, sc);
        sc.gridx = 2; sc.weightx = 0;
        staticCard.add(new JLabel("Port:"), sc);
        sc.gridx = 3; sc.weightx = 0;
        staticCard.add(staticProxyPortSpinner, sc);
        sc.gridy = 1; sc.gridx = 0; sc.weightx = 0;
        staticCard.add(new JLabel("Bypass-Liste:"), sc);
        sc.gridx = 1; sc.gridwidth = 3; sc.weightx = 1.0;
        staticCard.add(staticBypassListField, sc);

        // PAC_URL card
        JPanel pacCard = new JPanel(new GridBagLayout());
        GridBagConstraints pc = new GridBagConstraints();
        pc.insets = new Insets(2, 4, 2, 4);
        pc.anchor = GridBagConstraints.WEST;
        pc.fill = GridBagConstraints.HORIZONTAL;
        pc.gridy = 0; pc.gridx = 0; pc.weightx = 0;
        pacCard.add(new JLabel("PAC URL:"), pc);
        pc.gridx = 1; pc.weightx = 1.0;
        pacCard.add(pacUrlField, pc);

        modeCardsPanel.add(autoCard, "AUTO");
        modeCardsPanel.add(staticCard, "STATIC");
        modeCardsPanel.add(pacCard, "PAC_URL");

        row++;
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 4; gc.weightx = 1.0;
        outboundPanel.add(modeCardsPanel, gc);

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
        outboundPanel.add(new JLabel("Test-URL:"), gc);
        gc.gridx = 1; gc.gridwidth = 1; gc.weightx = 1.0;
        outboundPanel.add(clientOutboundProxyTestUrlField, gc);
        JPanel testButtonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton testButton = new JButton("Testen");
        testButton.addActionListener(e -> testProxyResolution());
        testButtonsPanel.add(testButton);
        JButton diagnoseButton = new JButton("Diagnose");
        diagnoseButton.addActionListener(e -> showDiagnostics());
        testButtonsPanel.add(diagnoseButton);
        gc.gridx = 2; gc.gridwidth = 2; gc.weightx = 0;
        outboundPanel.add(testButtonsPanel, gc);

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
        proxyModeBox.setSelectedItem(proxyMode != null ? proxyMode : "AUTO");
        staticProxyHostField.setText(cfg.getClientOutboundProxyHost());
        staticProxyPortSpinner.setValue(cfg.getClientOutboundProxyPort());
        pacUrlField.setText(cfg.getClientOutboundProxyPacUrl());
        bypassListField.setText(cfg.getClientOutboundProxyBypassList());
        staticBypassListField.setText(cfg.getClientOutboundProxyBypassList());
        String pacSource = cfg.getClientOutboundProxyPacSource();
        pacSourceBox.setSelectedItem(pacSource != null && !pacSource.isEmpty() ? pacSource : "REGISTRY");
        cacheTtlSpinner.setValue(cfg.getClientOutboundProxyCacheTtlSeconds());
        connectTimeoutSpinner.setValue(cfg.getClientOutboundProxyConnectTimeoutMillis());
        handshakeTimeoutSpinner.setValue(cfg.getClientOutboundProxyHandshakeTimeoutMillis());
        
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

        // Proxy validation
        boolean outboundEnabled = clientOutboundProxyCheckBox.isSelected();
        String selectedMode = (String) proxyModeBox.getSelectedItem();
        if (outboundEnabled && "STATIC".equals(selectedMode)) {
            String proxyHost = staticProxyHostField.getText().trim();
            if (proxyHost.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Proxy Host darf nicht leer sein im STATIC-Modus.",
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
        cfg.setClientOutboundProxyHost(staticProxyHostField.getText().trim());
        cfg.setClientOutboundProxyPort(((Number) staticProxyPortSpinner.getValue()).intValue());
        cfg.setClientOutboundProxyPacUrl(pacUrlField.getText().trim());
        // Bypass-Liste: aus dem aktiven Feld je nach Modus
        String activeMode = (String) proxyModeBox.getSelectedItem();
        String bypassValue = "STATIC".equals(activeMode)
                ? staticBypassListField.getText().trim()
                : bypassListField.getText().trim();
        cfg.setClientOutboundProxyBypassList(bypassValue);
        cfg.setClientOutboundProxyPacSource((String) pacSourceBox.getSelectedItem());
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
        
        // Proxy controls enable/disable
        boolean proxyEnabled = clientOutboundProxyCheckBox.isSelected();
        proxyModeBox.setEnabled(proxyEnabled);
        staticProxyHostField.setEnabled(proxyEnabled);
        staticProxyPortSpinner.setEnabled(proxyEnabled);
        pacUrlField.setEnabled(proxyEnabled);
        bypassListField.setEnabled(proxyEnabled);
        staticBypassListField.setEnabled(proxyEnabled);
        pacSourceBox.setEnabled(proxyEnabled);
        cacheTtlSpinner.setEnabled(proxyEnabled);
        connectTimeoutSpinner.setEnabled(proxyEnabled);
        handshakeTimeoutSpinner.setEnabled(proxyEnabled);
        clientOutboundProxyTestUrlField.setEnabled(proxyEnabled);

        // Show correct card for selected mode
        if (proxyEnabled) {
            String selectedMode = (String) proxyModeBox.getSelectedItem();
            if (selectedMode != null && modeCardLayout != null) {
                modeCardLayout.show(modeCardsPanel, selectedMode);
            }
        }
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
        String testUrl = clientOutboundProxyTestUrlField.getText().trim();
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
            if ("STATIC".equals(selectedMode)) resolveMode = WinProxyJavaResolver.ResolveMode.STATIC;
            else if ("PAC_URL".equals(selectedMode)) resolveMode = WinProxyJavaResolver.ResolveMode.PAC_URL;

            WinProxyJavaResolver resolver = new WinProxyJavaResolver(
                    resolveMode,
                    staticProxyHostField.getText().trim(),
                    ((Number) staticProxyPortSpinner.getValue()).intValue(),
                    pacUrlField.getText().trim(),
                    "STATIC".equals(selectedMode) ? staticBypassListField.getText().trim() : bypassListField.getText().trim(),
                    (String) pacSourceBox.getSelectedItem(),
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
