package de.bund.zrb;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;

public class ProxyControlFrame extends JFrame implements ProxyView {

    private JTextField portField;
    private JTextField keystoreField;
    private JCheckBox mitmCheckBox;
    private JCheckBox gatewayCheckBox;

    // Rewrite config (nur intern, UI-Einstellungen kommen aus dem Preferences-Dialog)
    private JCheckBox rewriteCheckBox;
    private JTextField rewriteModelField;
    private JTextField rewriteTemperatureField;

    private JLabel statusLabel;
    private JLabel publicIpLabel;
    private JLabel clientInfoLabel;
    private JLabel clientStatusDotLabel;

    private JMenuBar menuBar;
    private JToolBar toolBar;

    private JButton startStopButton;
    private JToggleButton modeToggleButton;
    private JTextPane trafficPane;

    private JTextField clientHostField;
    private JTextField clientPortField;

    // Neuer Gateway-Passkey (für Server/Client-Gateway-Handshake)
    private JTextField gatewayPasskeyField;

    private JButton publicIpCopyButton;
    private JButton clientHostPasteButton;

    private final ProxyConfigService configService = new ProxyConfigService();
    private final PublicIpService publicIpService = new PublicIpService();
    private final MitmSetupService mitmSetupService = new MitmSetupService();
    private final CaInstallService caInstallService = new CaInstallService();
    private final ProxyController controller = new ProxyController(this, configService, mitmSetupService, caInstallService);

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public ProxyControlFrame() {
        super("Local Proxy Control");

        initComponents();
        layoutComponents();
        initActions();
        initPublicIpStatus();
        loadConfig(); // lädt auch clientHost/clientPort in die Toolbar
        showHelpDialogIfNeeded();
        updateStatus();
        updateRewriteControls();

        // WICHTIG: ab hier sind Host/Port ausschließlich aus der Toolbar maßgeblich
        if (modeToggleButton.isSelected()) {
            startClientMode();
        }
    }

    private void initComponents() {
        portField = new JTextField(6);
        keystoreField = new JTextField(30);
        mitmCheckBox = new JCheckBox("Enable MITM for api.openai.com");
        gatewayCheckBox = new JCheckBox("Route via gateway");

        rewriteCheckBox = new JCheckBox("Rewrite model/temperature for /v1/chat/completions");
        rewriteModelField = new JTextField("gpt-5-mini", 16);
        rewriteTemperatureField = new JTextField("1.0", 4);

        statusLabel = new JLabel("Status: stopped");
        statusLabel.setForeground(Color.RED);

        publicIpLabel = new JLabel("Public IP: resolving...");
        clientInfoLabel = new JLabel("No client connected");
        clientInfoLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        clientInfoLabel.setForeground(Color.BLACK);

        clientStatusDotLabel = new JLabel("\u2022");
        clientStatusDotLabel.setForeground(Color.RED);

        startStopButton = new JButton("Start proxy");
        modeToggleButton = new JToggleButton("Server mode");
        modeToggleButton.setFocusable(false);

        trafficPane = new JTextPane();
        trafficPane.setContentType("text/html");
        trafficPane.setEditable(false);
        trafficPane.setText("<html><body style='font-family:monospace;font-size:11px;'></body></html>");

        // Kleine Symbol-Buttons über gemeinsame Factory-Methode erzeugen,
        // um sicherzustellen, dass Margin/Insets wirklich 0 sind und genug Platz da ist.
        publicIpCopyButton = createSmallIconButton("\u29C9", "Copy public IP to clipboard");
        clientHostPasteButton = createSmallIconButton("\u29C9", "Paste host from clipboard");

        clientHostField = new JTextField("127.0.0.1", 12);
        clientPortField = new JTextField("8888", 5);

        gatewayPasskeyField = new JTextField("passkey1234", 10);

        initMenuBar();
        initToolBar();
    }

    private void initMenuBar() {
        menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");
        JMenuItem preferencesItem = new JMenuItem("Preferences...");
        preferencesItem.addActionListener(e -> openPreferencesDialog());
        fileMenu.add(preferencesItem);

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> dispose());
        fileMenu.add(exitItem);

        JMenu mitmMenu = new JMenu("MITM");
        JMenuItem generateKeystoreItem = new JMenuItem("Generate MITM keystore");
        generateKeystoreItem.addActionListener(e -> runCertSetup());
        JMenuItem installCaItem = new JMenuItem("Install CA into trust store");
        installCaItem.addActionListener(e -> runInstallCa());
        mitmMenu.add(generateKeystoreItem);
        mitmMenu.add(installCaItem);

        JMenu proxyMenu = new JMenu("Proxy");
        JMenuItem startStopItem = new JMenuItem("Start/Stop proxy");
        startStopItem.addActionListener(e -> toggleProxy());
        proxyMenu.add(startStopItem);

        menuBar.add(fileMenu);
        menuBar.add(mitmMenu);
        menuBar.add(proxyMenu);

        setJMenuBar(menuBar);
    }

    private void initToolBar() {
        toolBar = new JToolBar();
        toolBar.setFloatable(false);

        toolBar.add(modeToggleButton);
        toolBar.addSeparator();
        toolBar.add(new JLabel("Target:"));
        toolBar.add(clientHostField);
        toolBar.add(clientHostPasteButton);
        toolBar.add(new JLabel(":"));
        toolBar.add(clientPortField);
        toolBar.add(new JLabel("  Passkey:"));
        toolBar.add(gatewayPasskeyField);
        toolBar.addSeparator();
        toolBar.add(startStopButton);
        // no other buttons in the toolbar; MITM actions are triggered via menu only
    }

    private void layoutComponents() {
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(new EmptyBorder(10, 10, 10, 10));
        setContentPane(content);

        JPanel north = new JPanel(new BorderLayout());
        north.add(toolBar, BorderLayout.NORTH);
        content.add(north, BorderLayout.NORTH);
        content.add(new JScrollPane(trafficPane), BorderLayout.CENTER);

        JPanel statusBar = new JPanel(new BorderLayout(8, 0));
        statusBar.setBorder(new EmptyBorder(4, 0, 0, 0));
        JPanel publicIpPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        publicIpPanel.add(publicIpLabel);
        publicIpPanel.add(publicIpCopyButton);
        statusBar.add(publicIpPanel, BorderLayout.WEST);

        statusBar.add(statusLabel, BorderLayout.CENTER);

        JPanel clientStatusPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        clientStatusPanel.add(clientInfoLabel);
        clientStatusPanel.add(clientStatusDotLabel);
        statusBar.add(clientStatusPanel, BorderLayout.EAST);

        content.add(statusBar, BorderLayout.SOUTH);
    }

    private void initActions() {
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1000, 650);
        setLocationRelativeTo(null);

        startStopButton.addActionListener(e -> toggleProxy());
        modeToggleButton.addActionListener(e -> {
            boolean nowClientMode = modeToggleButton.isSelected();
            updateModeToggleText();

            // Laufende Instanz stoppen
            controller.stopProxy();
            updateGatewayClientStatus("No client connected", false);
            updateStatus();

            // Aktuelle Config laden
            ProxyConfig cfg = configService.loadConfig();

            if (nowClientMode) {
                // Letzte SERVER-Werte vor dem Umschalten speichern
                cfg.setServerPort(clientPortField.getText());
                cfg.setServerGatewayPasskey(gatewayPasskeyField.getText());
                try {
                    configService.saveConfig(cfg);
                } catch (IOException ignored) {
                    // wenn Speichern fehlschlägt, bleiben alte Werte erhalten
                }

                // CLIENT-Mode: Toolbar mit Client-Werten aus Config befüllen
                clientHostField.setText(cfg.getClientHost());
                clientPortField.setText(String.valueOf(cfg.getClientPort()));
                gatewayPasskeyField.setText(cfg.getClientGatewayPasskey());
            } else {
                // Letzte CLIENT-Werte vor dem Umschalten speichern
                cfg.setClientPort(clientPortField.getText());
                cfg.setClientGatewayPasskey(gatewayPasskeyField.getText());
                try {
                    configService.saveConfig(cfg);
                } catch (IOException ignored) {
                    // wenn Speichern fehlschlägt, bleiben alte Werte erhalten
                }

                // SERVER-Mode: Port & Passkey aus Config holen
                portField.setText(String.valueOf(cfg.getPort()));
                clientPortField.setText(String.valueOf(cfg.getServerPort()));
                gatewayPasskeyField.setText(cfg.getServerGatewayPasskey());
                gatewayCheckBox.setSelected(cfg.isGatewayEnabled());
            }
        });

        mitmCheckBox.addActionListener(e -> updateRewriteControls());
        rewriteCheckBox.addActionListener(e -> updateRewriteControls());
        publicIpCopyButton.addActionListener(e -> {
            copyPublicIpToClipboard();
            blinkLabel(publicIpLabel);
        });

        clientHostPasteButton.addActionListener(e -> pasteHostFromClipboard());

        // Host/Port-Änderungen in der Toolbar sofort in die Config schreiben
        clientHostField.addActionListener(e -> saveConfig());
        clientPortField.addActionListener(e -> saveConfig());
        clientHostField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                saveConfig();
            }
        });
        clientPortField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                saveConfig();
            }
        });
    }

    private void initPublicIpStatus() {
        publicIpLabel.setText("Public IP: resolving...");
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                final String ip = publicIpService.resolvePublicIp();
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        publicIpLabel.setText("Public IP: " + ip);
                    }
                });
            }
        }, "public-ip-resolver");
        worker.setDaemon(true);
        worker.start();
    }

    private void copyPublicIpToClipboard() {
        String text = publicIpLabel.getText();
        if (text == null || text.isEmpty()) {
            return;
        }
        // Erwartetes Format: "Public IP: <ip>" – wir extrahieren die IP nach dem Doppelpunkt
        String ip = text;
        int idx = text.indexOf(':');
        if (idx >= 0 && idx + 1 < text.length()) {
            ip = text.substring(idx + 1).trim();
        }
        if (ip.isEmpty() || ip.equals("resolving...")) {
            return;
        }
        try {
            java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(ip);
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        } catch (Exception ignored) {
            // kein Dialog
        }
    }

    private void pasteHostFromClipboard() {
        try {
            java.awt.datatransfer.Clipboard cb = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
            java.awt.datatransfer.Transferable t = cb.getContents(null);
            if (t != null && t.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor)) {
                String data = (String) t.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor);
                if (data != null) {
                    data = data.trim();
                    if (!data.isEmpty()) {
                        clientHostField.setText(data);
                        blinkComponent(clientHostField);
                        saveConfig();
                    }
                }
            }
        } catch (Exception ignored) {
            // still silent
        }
    }

    private void blinkLabel(final JLabel label) {
        blinkComponent(label);
    }

    private void blinkComponent(final JComponent comp) {
        final Color original = comp.getForeground();
        final Color blinkColor = Color.WHITE; // neutrales Weiß statt Grün
        comp.setForeground(blinkColor);
        javax.swing.Timer timer = new javax.swing.Timer(200, e -> comp.setForeground(original));
        timer.setRepeats(false);
        timer.start();
    }

    private void loadConfig() {
        ProxyConfig cfg = configService.loadConfig();
        portField.setText(String.valueOf(cfg.getPort()));
        keystoreField.setText(cfg.getKeystorePath());
        mitmCheckBox.setSelected(cfg.isMitmEnabled());
        rewriteCheckBox.setSelected(cfg.isRewriteEnabled());
        rewriteModelField.setText(cfg.getRewriteModel());
        rewriteTemperatureField.setText(cfg.getRewriteTemperature());
        gatewayCheckBox.setSelected(cfg.isGatewayEnabled());

        ProxyMode mode = cfg.getProxyMode();
        boolean clientMode = (mode == ProxyMode.CLIENT);
        modeToggleButton.setSelected(clientMode);
        updateModeToggleText();

        // Host/Port EINMALIG aus Config in Toolbar laden
        clientHostField.setText(cfg.getClientHost());
        clientPortField.setText(String.valueOf(cfg.getClientPort()));
        gatewayPasskeyField.setText(cfg.getGatewayPasskey());

        updateRewriteControls();
        updateStartButtonEnabledState();

        clientInfoLabel.setText("No client connected");
        clientInfoLabel.setForeground(Color.BLACK);
        clientStatusDotLabel.setForeground(Color.RED);
    }

    private boolean saveConfig() {
        int port = readPortFromField();
        if (port <= 0) {
            showError("Port must be a number between 1 und 65535.");
            return false;
        }

        String clientHost = clientHostField.getText().trim();
        int clientPort;
        try {
            clientPort = Integer.parseInt(clientPortField.getText().trim());
        } catch (NumberFormatException e) {
            clientPort = 8888;
        }

        ProxyMode mode = modeToggleButton.isSelected() ? ProxyMode.CLIENT : ProxyMode.SERVER;

        // Load existing config to preserve flags like showHelpOnStart
        ProxyConfig oldCfg = configService.loadConfig();

        ProxyConfig cfg = new ProxyConfig(
                port,
                keystoreField.getText().trim(),
                mitmCheckBox.isSelected(),
                rewriteCheckBox.isSelected(),
                rewriteModelField.getText().trim(),
                rewriteTemperatureField.getText().trim(),
                gatewayCheckBox.isSelected(),
                mode,
                clientHost,
                clientPort,
                gatewayPasskeyField.getText().trim()
        );

        // Preserve "do not show help" flag
        cfg.setShowHelpOnStart(oldCfg.isShowHelpOnStart());

        try {
            configService.saveConfig(cfg);
            return true;
        } catch (IOException e) {
            showError("Failed to save configuration: " + e.getMessage());
            return false;
        }
    }

    private void toggleProxy() {
        // Start/Stop nur im SERVER-Mode erlaubt
        if (modeToggleButton.isSelected()) {
            return;
        }

        if (controller.isProxyRunning()) {
            controller.stopProxy();
            updateGatewayClientStatus("No client connected", false);
            updateStatus();
        } else {
            startProxy();
        }
    }

    private void startProxy() {
        try {
            // SERVER-Mode: immer Port & Passkey aus Config verwenden, nicht direkt aus der Toolbar
            ProxyConfig cfg = configService.loadConfig();
            int port = cfg.getPort();
            if (port <= 0 || port > 65535) {
                showError("Port must be a number between 1 and 65535.");
                return;
            }

            controller.startProxy(cfg, (direction, text, isJson) -> appendTraffic(direction, text, isJson));

            updateGatewayClientStatus("No client connected", false);
            updateStatus();
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        } catch (IOException e) {
            showError("Failed to start proxy: " + e.getMessage());
        }
    }

    private void startClientMode() {
        try {
            // CLIENT-Mode: ausschließlich Toolbar-Werte verwenden (alter Pfad)
            int port = readPortFromField();
            if (port <= 0) {
                showError("Port must be a number between 1 and 65535.");
                return;
            }

            String clientHost = clientHostField.getText().trim();
            int clientPort;
            try {
                clientPort = Integer.parseInt(clientPortField.getText().trim());
            } catch (NumberFormatException e) {
                showError("Client port must be a number between 1 and 65535.");
                return;
            }
            if (clientPort <= 0 || clientPort > 65535) {
                showError("Client port must be a number zwischen 1 und 65535.");
                return;
            }

            ProxyConfig cfg = new ProxyConfig(
                    port,
                    keystoreField.getText().trim(),
                    mitmCheckBox.isSelected(),
                    rewriteCheckBox.isSelected(),
                    rewriteModelField.getText().trim(),
                    rewriteTemperatureField.getText().trim(),
                    gatewayCheckBox.isSelected(),
                    ProxyMode.CLIENT,
                    clientHost,
                    clientPort,
                    gatewayPasskeyField.getText().trim()
            );

            controller.stopProxy();
            controller.startProxy(cfg, (direction, text, isJson) -> appendTraffic(direction, text, isJson));

            updateGatewayClientStatus("Connecting to " + clientHost + ":" + clientPort, false);
            updateStatus();
        } catch (Exception e) {
            showError("Failed to start client mode: " + e.getMessage());
        }
    }

    @Override
    public void updateGatewayClientStatus(String text, boolean connected) {
        SwingUtilities.invokeLater(() -> {
            clientInfoLabel.setText(text);
            clientInfoLabel.setForeground(Color.BLACK);
            clientStatusDotLabel.setForeground(connected ? new Color(0, 160, 0) : Color.RED);
        });
    }

    private void updateModeToggleText() {
        if (modeToggleButton.isSelected()) {
            modeToggleButton.setText("Client mode");
        } else {
            modeToggleButton.setText("Server mode");
        }
        updateStartButtonEnabledState();
    }

    private void updateStartButtonEnabledState() {
        // Start/Stop-Button nur im Server-Mode aktiv
        boolean serverMode = !modeToggleButton.isSelected();
        startStopButton.setEnabled(serverMode);
    }

    private boolean isProxyRunning() {
        return controller.isProxyRunning();
    }

    private void stopProxy() {
        controller.stopProxy();
        updateGatewayClientStatus("No client connected", false);
        updateStatus();
    }

    private void updateStatus() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (isProxyRunning()) {
                    statusLabel.setText("Status: running");
                    statusLabel.setForeground(new Color(0, 128, 0));
                    startStopButton.setText("Stop proxy");
                } else {
                    statusLabel.setText("Status: stopped");
                    statusLabel.setForeground(Color.RED);
                    startStopButton.setText("Start proxy");
                }
                // Text "Use as HTTP proxy: ..." wird nicht mehr angezeigt
            }
        });
    }

    private void updateRewriteControls() {
        boolean mitm = mitmCheckBox.isSelected();

        rewriteCheckBox.setEnabled(mitm);

        boolean rewriteEnabled = mitm && rewriteCheckBox.isSelected();
        rewriteModelField.setEnabled(rewriteEnabled);
        rewriteTemperatureField.setEnabled(rewriteEnabled);
    }

    private void appendTraffic(final String direction, final String text, final boolean isJson) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                StringBuilder html = new StringBuilder();
                String current = trafficPane.getText();
                int bodyIndex = current.indexOf("<body");
                if (bodyIndex >= 0) {
                    int start = current.indexOf(">", bodyIndex);
                    int end = current.lastIndexOf("</body>");
                    if (start >= 0 && end > start) {
                        html.append(current, 0, start + 1);
                        html.append(current, start + 1, end);
                    } else {
                        html.append("<html><body style='font-family:monospace;font-size:11px;'>");
                    }
                } else {
                    html.append("<html><body style='font-family:monospace;font-size:11px;'>");
                }

                html.append("<div style='margin-bottom:4px;'>");
                html.append("<span style='color:#888;'>[")
                        .append(escapeHtml(direction))
                        .append("]</span> ");

                String content = text;
                if (isJson) {
                    try {
                        content = gson.toJson(gson.fromJson(text, Object.class));
                    } catch (Exception ignored) {
                        // Keep original text if parsing fails
                    }
                }
                html.append("<pre style='display:inline;'>")
                        .append(escapeHtml(content))
                        .append("</pre>");
                html.append("</div>");

                html.append("</body></html>");

                trafficPane.setText(html.toString());
                trafficPane.setCaretPosition(trafficPane.getDocument().getLength());
            }
        });
    }

    private String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '&':
                    sb.append("&amp;");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    private void chooseKeystore() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select myproxy.jks");

        String current = keystoreField.getText().trim();
        if (!current.isEmpty()) {
            File f = new File(current);
            if (f.getParentFile() != null && f.getParentFile().exists()) {
                chooser.setCurrentDirectory(f.getParentFile());
            }
        }

        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            keystoreField.setText(chooser.getSelectedFile().getAbsolutePath());
            updateStatus();
        }
    }

    private int readPortFromField() {
        try {
            int port = Integer.parseInt(portField.getText().trim());
            if (port <= 0 || port > 65535) {
                return -1;
            }
            return port;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // --- MITM keystore generation (PS scripts) ---

    private void runCertSetup() {
        int choice = JOptionPane.showConfirmDialog(
                this,
                "This will generate a local CA certificate and MITM keystore (myproxy.jks)\n" +
                        "in your user directory under .proxy.\n\n" +
                        "Use this only for local debugging. The generated CA can be used to\n" +
                        "intercept HTTPS traffic to api.openai.com via this proxy.",
                "Generate MITM keystore?",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (choice != JOptionPane.OK_OPTION) {
            appendTraffic("setup", "MITM keystore generation cancelled by user.", false);
            return;
        }

        Thread worker = new Thread(() -> {
            appendTraffic("setup", "Start MITM keystore generation ...", false);

            if (!isWindowsOs()) {
                showError("PowerShell-based certificate setup is only implemented for Windows.");
                appendTraffic("setup", "Abort: non-Windows OS detected.", false);
                return;
            }

            try {
                MitmSetupResult result = mitmSetupService.runMitmSetup();
                appendTraffic("setup", result.log, false);
                if (result.success && result.keystoreFile != null) {
                    keystoreField.setText(result.keystoreFile.getAbsolutePath());
                    mitmCheckBox.setSelected(true);
                    saveConfig();
                    updateRewriteControls();
                    appendTraffic("setup", "myproxy.jks detected and MITM enabled in UI.", false);
                } else {
                    showError("MITM setup failed. See setup log.");
                }
            } catch (IOException e) {
                showError("Error during certificate setup: " + e.getMessage());
                appendTraffic("setup", "Exception: " + e.getMessage(), false);
            }
        }, "mitm-setup");
        worker.setDaemon(true);
        worker.start();
    }

    // --- CA install into system trust store (separate, explicit) ---

    private void runInstallCa() {
        if (!isWindowsOs()) {
            showError("Automatic CA installation is currently only implemented for Windows.");
            return;
        }

        java.io.File caFile = caInstallService.getCaFile();
        if (!caFile.exists()) {
            showError("CA certificate not found: " + caFile.getAbsolutePath() +
                    "\nRun 'Generate MITM keystore' first.");
            return;
        }

        int choice = JOptionPane.showConfirmDialog(
                this,
                "This will import the local MITM CA certificate into the Windows\n" +
                        "\"Trusted Root Certification Authorities\" store.\n\n" +
                        "Effects:\n" +
                        "- Certificates issued by this CA (e.g. for api.openai.com via this proxy)\n" +
                        "  will be trusted by your system.\n" +
                        "- This is powerful and must only be used for local debugging on your machine.\n" +
                        "- Remove the CA from the trust store if you no longer need it.\n\n" +
                        "You may need to run this application with administrative privileges.\n\n" +
                        "Do you understand and want to proceed?",
                "Install CA into Windows trust store?",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (choice != JOptionPane.YES_OPTION) {
            appendTraffic("setup", "User declined CA trust-store installation.", false);
            return;
        }

        Thread worker = new Thread(() -> {
            appendTraffic("setup", "Start CA installation into Windows Root store ...", false);
            try {
                CaInstallResult result = caInstallService.installCa(caFile);
                appendTraffic("setup", "certutil exit code: " + result.exitCode, false);
                appendTraffic("setup", "certutil output:\n" + result.log, false);
                if (result.success) {
                    appendTraffic("setup", "CA successfully installed into Windows Root store.", false);
                    JOptionPane.showMessageDialog(
                            ProxyControlFrame.this,
                            "CA successfully installed into Windows Root store.",
                            "Success",
                            JOptionPane.INFORMATION_MESSAGE
                    );
                } else {
                    showError("certutil failed. See setup log. Run as Administrator?");
                }
            } catch (IOException e) {
                showError("Failed to run certutil: " + e.getMessage());
                appendTraffic("setup", "certutil error: " + e.getMessage(), false);
            }
        }, "ca-install");
        worker.setDaemon(true);
        worker.start();
    }


    // --- shared helpers for scripts ---

    private File extractResourceIfMissing(String resourcePath, File target) throws IOException {
        if (target.exists()) {
            return target;
        }

        InputStream in = ProxyControlFrame.class.getResourceAsStream(resourcePath);
        if (in == null) {
            throw new IOException("Resource not found: " + resourcePath);
        }

        appendTraffic("setup", "Extract " + resourcePath + " -> " + target.getAbsolutePath(), false);

        FileOutputStream out = null;
        try {
            out = new FileOutputStream(target);
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } finally {
            closeQuietly(in);
            closeQuietly(out);
        }

        return target;
    }

    private int runPowerShellScript(File workingDir, File scriptFile) throws IOException {
        if (!scriptFile.exists()) {
            throw new IOException("Script not found: " + scriptFile.getAbsolutePath());
        }

        ProcessBuilder pb = new ProcessBuilder(
                "powershell.exe",
                "-ExecutionPolicy", "Bypass",
                "-File", scriptFile.getAbsolutePath()
        );
        pb.directory(workingDir);
        pb.redirectErrorStream(true);

        Process process = pb.start();

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), "UTF-8")
        );

        String line;
        StringBuilder output = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }

        int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Script execution interrupted: " + scriptFile.getName(), e);
        }

        appendTraffic("setup", "Output from " + scriptFile.getName() + ":\n" + output.toString(), false);
        return exitCode;
    }

    private boolean isWindowsOs() {
        String os = System.getProperty("os.name");
        return os != null && os.toLowerCase().contains("win");
    }

    private void showError(final String message) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                JOptionPane.showMessageDialog(
                        ProxyControlFrame.this,
                        message,
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });
    }

    private void closeQuietly(Closeable c) {
        if (c == null) {
            return;
        }
        try {
            c.close();
        } catch (IOException ignored) {
            // Ignore
        }
    }

    private void openPreferencesDialog() {
        ProxyPreferencesDialog dialog = new ProxyPreferencesDialog(this, configService);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);

        // nach dem Schließen des Dialogs Config neu laden und Status aktualisieren
        loadConfig();
        updateStatus();
        updateRewriteControls();
    }

    private void showHelpDialogIfNeeded() {
        ProxyConfig cfg = configService.loadConfig();
        if (!cfg.isShowHelpOnStart()) {
            return;
        }

        JCheckBox dontShowAgain = new JCheckBox("Diesen Hinweis nicht mehr anzeigen");

        String message = "<html>" +
                "<h3>Hinweis zu Server- und Client-Mode</h3>" +
                "<p><b>Server-Mode:</b><br>" +
                "- Läuft typischerweise auf der Maschine, auf der IntelliJ läuft.<br>" +
                "- Startet den lokalen HTTP-Proxy (Port aus der Toolbar).<br>" +
                "- Wenn in den Einstellungen die Option <b>Gateway</b> aktiviert ist,<br>" +
                "  wartet der Server zusätzlich auf eine Gateway-Client-Verbindung<br>" +
                "  und routet Anfragen über diesen Client.</p>" +
                "<p><b>Client-Mode:</b><br>" +
                "- Läuft auf der Maschine hinter NAT, die sich zum Server verbindet.<br>" +
                "- Nimmt in der Toolbar unter <b>Target</b> die öffentliche IP/den Host<br>" +
                "  und Port des Servers entgegen und baut von dort die Verbindung auf.<br>" +
                "- Es läuft KEIN lokaler HTTP-Proxy, sondern nur der Gateway-Client.</p>" +
                "<p><b>Wichtig:</b><br>" +
                "- Wenn Du Gateway-Routing verwenden willst, aktiviere in den<br>" +
                "  <b>Einstellungen / Preferences</b> im Server-Mode die Checkbox<br>" +
                "  <b>Route via gateway</b> (Gateway-Mode).<br>" +
                "- Im Client-Mode musst Du keinen Gateway-Haken setzen; dort zählt<br>" +
                "  nur die <b>Target</b>-Einstellung (IP/Port des Servers).</p>" +
                "</html>";

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(new JLabel(message), BorderLayout.CENTER);
        panel.add(dontShowAgain, BorderLayout.SOUTH);

        JOptionPane.showMessageDialog(
                this,
                panel,
                "Hinweis zu Server/Client-Mode und Gateway",
                JOptionPane.INFORMATION_MESSAGE
        );

        if (dontShowAgain.isSelected()) {
            cfg.setShowHelpOnStart(false);
            try {
                configService.saveConfig(cfg);
            } catch (IOException ignored) {
                // wenn Speichern fehlschlägt, erscheint der Dialog eben erneut
            }
        }
    }

    @Override
    public int getServerPort() {
        try {
            return Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public String getServerGatewayPasskey() {
        return gatewayPasskeyField != null ? gatewayPasskeyField.getText().trim() : "passkey1234";
    }

    @Override
    public String getClientTargetHost() {
        return clientHostField.getText().trim();
    }

    @Override
    public int getClientTargetPort() {
        try {
            return Integer.parseInt(clientPortField.getText().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public String getClientGatewayPasskey() {
        return gatewayPasskeyField != null ? gatewayPasskeyField.getText().trim() : "passkey1234";
    }

    /**
     * Erzeugt einen sehr kleinen, flachen Button für Symbol-Text (z.B. Copy/Paste).
     * Wichtig: Insets/Margins werden strikt auf 0 gesetzt, Font leicht verkleinert
     * und eine etwas größere Kachelgröße gewählt, um Darstellungsfehler wie ".."
     * auf manchen Systemen zu vermeiden.
     */
    private JButton createSmallIconButton(String text, String tooltip) {
        JButton button = new JButton(text);
        button.setToolTipText(tooltip);

        // Fokusrahmen und Hintergrund deaktivieren – wie bisher
        button.setFocusable(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);

        // Schrift minimal verkleinern, damit das Symbol sicher in die Box passt
        Font f = button.getFont();
        if (f != null) {
            button.setFont(f.deriveFont(Math.max(8f, f.getSize2D() - 1f)));
        }

        // WICHTIG: Keine zusätzlichen Abstände zulassen
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        // Etwas großzügiger als vorher, damit der Glyph nicht abgeschnitten wird
        Dimension size = new Dimension(20, 20);
        button.setPreferredSize(size);
        button.setMinimumSize(size);
        button.setMaximumSize(size);

        return button;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                ProxyControlFrame frame = new ProxyControlFrame();
                frame.setVisible(true);
            }
        });
    }
}
