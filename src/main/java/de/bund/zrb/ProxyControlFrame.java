package de.bund.zrb;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.*;
import java.util.Collections;

public class ProxyControlFrame extends JFrame implements ProxyView {

    private static final String CONFIG_DIR = ".proxy";
    private static final String CONFIG_FILE = "proxy.properties";

    private static final String KEY_PORT = "proxy.port";
    private static final String KEY_KEYSTORE_PATH = "proxy.keystore.path";
    private static final String KEY_MITM_ENABLED = "proxy.mitm.enabled";

    private static final String KEY_REWRITE_ENABLED = "proxy.model.rewrite.enabled";
    private static final String KEY_REWRITE_MODEL = "proxy.model.rewrite.name";
    private static final String KEY_REWRITE_TEMPERATURE = "proxy.model.rewrite.temperature";

    private static final String KEY_GATEWAY_ENABLED = "proxy.gateway.enabled";

    // Resources inside the JAR (place scripts under src/main/resources/ps)
    private static final String RESOURCE_CREATE_CA = "/ps/create-ca.ps1";
    private static final String RESOURCE_OPENAI_CERT = "/ps/create-openai-cert.ps1";

    // Expected CA filename created by your scripts in ~/.proxy
    private static final String CA_CERT_FILE_NAME = "myproxy-ca.crt";

    private JTextField portField;
    private JTextField keystoreField;
    private JCheckBox mitmCheckBox;

    private JCheckBox gatewayCheckBox;

    // Rewrite config UI
    private JCheckBox rewriteCheckBox;
    private JTextField rewriteModelField;
    private JTextField rewriteTemperatureField;

    private JLabel statusLabel;
    private JLabel urlLabel;
    private JLabel publicIpLabel;
    private JLabel clientInfoLabel;

    private JButton startStopButton;
    private JButton applyButton;
    private JButton setupCertButton;
    private JButton installCaButton;
    private JTextPane trafficPane;

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
        loadConfig();
        updateStatus();
        updateRewriteControls();
    }

    private void initComponents() {
        portField = new JTextField(6);
        keystoreField = new JTextField(30);
        mitmCheckBox = new JCheckBox("Enable MITM for api.openai.com");

        gatewayCheckBox = new JCheckBox("Route via gateway");

        // Default rewrite: disabled, but sensible vorbelegung
        rewriteCheckBox = new JCheckBox("Rewrite model/temperature for /v1/chat/completions");
        rewriteModelField = new JTextField("gpt-5-mini", 16);
        rewriteTemperatureField = new JTextField("1.0", 4);

        statusLabel = new JLabel("Status: stopped");
        statusLabel.setForeground(Color.RED);

        urlLabel = new JLabel("Use as HTTP proxy: 127.0.0.1:<port>");

        publicIpLabel = new JLabel("Public IP: resolving...");
        clientInfoLabel = new JLabel("No client connected");
        clientInfoLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        startStopButton = new JButton("Start proxy");
        applyButton = new JButton("Apply settings");
        setupCertButton = new JButton("Generate MITM keystore");
        installCaButton = new JButton("Install CA into system trust store");

        trafficPane = new JTextPane();
        trafficPane.setContentType("text/html");
        trafficPane.setEditable(false);
        trafficPane.setText("<html><body style='font-family:monospace;font-size:11px;'></body></html>");
    }

    private void layoutComponents() {
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(new EmptyBorder(10, 10, 10, 10));
        setContentPane(content);

        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 4, 4, 4);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // Port
        gc.gridx = 0;
        gc.gridy = row;
        gc.gridwidth = 1;
        top.add(new JLabel("Port:"), gc);

        gc.gridx = 1;
        top.add(portField, gc);

        gc.gridx = 2;
        top.add(gatewayCheckBox, gc);

        // Keystore
        row++;
        gc.gridx = 0;
        gc.gridy = row;
        gc.gridwidth = 1;
        top.add(new JLabel("Keystore (.jks):"), gc);

        gc.gridx = 1;
        top.add(keystoreField, gc);

        JButton browseButton = new JButton("Browse...");
        gc.gridx = 2;
        top.add(browseButton, gc);

        // MITM checkbox
        row++;
        gc.gridx = 0;
        gc.gridy = row;
        gc.gridwidth = 3;
        top.add(mitmCheckBox, gc);

        // Rewrite row
        row++;
        gc.gridx = 0;
        gc.gridy = row;
        gc.gridwidth = 3;
        JPanel rewritePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        rewritePanel.add(rewriteCheckBox);
        rewritePanel.add(new JLabel("Model:"));
        rewritePanel.add(rewriteModelField);
        rewritePanel.add(new JLabel("→ Temperature:"));
        rewritePanel.add(rewriteTemperatureField);
        top.add(rewritePanel, gc);

        // Status
        row++;
        gc.gridx = 0;
        gc.gridy = row;
        gc.gridwidth = 3;
        top.add(statusLabel, gc);

        // URL
        row++;
        gc.gridx = 0;
        gc.gridy = row;
        gc.gridwidth = 3;
        top.add(urlLabel, gc);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(setupCertButton);
        buttons.add(installCaButton);
        buttons.add(applyButton);
        buttons.add(startStopButton);

        JPanel north = new JPanel(new BorderLayout());
        north.add(top, BorderLayout.CENTER);
        north.add(buttons, BorderLayout.SOUTH);

        content.add(north, BorderLayout.NORTH);
        content.add(new JScrollPane(trafficPane), BorderLayout.CENTER);

        JPanel statusBar = new JPanel(new BorderLayout(8, 0));
        statusBar.setBorder(new EmptyBorder(4, 0, 0, 0));
        statusBar.add(publicIpLabel, BorderLayout.WEST);
        statusBar.add(clientInfoLabel, BorderLayout.EAST);
        content.add(statusBar, BorderLayout.SOUTH);

        browseButton.addActionListener(e -> chooseKeystore());
    }

    private void initActions() {
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1000, 650);
        setLocationRelativeTo(null);

        startStopButton.addActionListener(e -> toggleProxy());
        applyButton.addActionListener(e -> applySettings());
        setupCertButton.addActionListener(e -> runCertSetup());
        installCaButton.addActionListener(e -> runInstallCa());

        mitmCheckBox.addActionListener(e -> updateRewriteControls());
        rewriteCheckBox.addActionListener(e -> updateRewriteControls());
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

    private void loadConfig() {
        ProxyConfig cfg = configService.loadConfig();
        portField.setText(String.valueOf(cfg.getPort()));
        keystoreField.setText(cfg.getKeystorePath());
        mitmCheckBox.setSelected(cfg.isMitmEnabled());
        rewriteCheckBox.setSelected(cfg.isRewriteEnabled());
        rewriteModelField.setText(cfg.getRewriteModel());
        rewriteTemperatureField.setText(cfg.getRewriteTemperature());
        gatewayCheckBox.setSelected(cfg.isGatewayEnabled());
        updateRewriteControls();
    }

    private boolean saveConfig() {
        int port = readPortFromField();
        if (port <= 0) {
            showError("Port must be a number between 1 und 65535.");
            return false;
        }

        ProxyConfig cfg = new ProxyConfig(
                port,
                keystoreField.getText().trim(),
                mitmCheckBox.isSelected(),
                rewriteCheckBox.isSelected(),
                rewriteModelField.getText().trim(),
                rewriteTemperatureField.getText().trim(),
                gatewayCheckBox.isSelected()
        );

        try {
            configService.saveConfig(cfg);
            return true;
        } catch (IOException e) {
            showError("Failed to save config: " + e.getMessage());
            return false;
        }
    }

    private void toggleProxy() {
        if (controller.isProxyRunning()) {
            controller.stopProxy();
            appendTraffic("info", "Proxy stopped", false);
            clientInfoLabel.setText("Proxy stopped");
            updateStatus();
        } else {
            startProxy();
        }
    }

    private void startProxy() {
        try {
            int port = readPortFromField();
            if (port <= 0) {
                showError("Port must be a number between 1 and 65535.");
                return;
            }

            ProxyConfig cfg = new ProxyConfig(
                    port,
                    keystoreField.getText().trim(),
                    mitmCheckBox.isSelected(),
                    rewriteCheckBox.isSelected(),
                    rewriteModelField.getText().trim(),
                    rewriteTemperatureField.getText().trim(),
                    gatewayCheckBox.isSelected()
            );

            controller.startProxy(cfg, new MitmTrafficListener() {
                @Override
                public void onTraffic(String direction, String text, boolean isJson) {
                    appendTraffic(direction, text, isJson);
                }
            });

            clientInfoLabel.setText("No client connected");
            updateStatus();
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        } catch (IOException e) {
            showError("Failed to start proxy: " + e.getMessage());
        }
    }

    private boolean isProxyRunning() {
        return controller.isProxyRunning();
    }

    private void stopProxy() {
        controller.stopProxy();
        appendTraffic("info", "Proxy stopped", false);
        clientInfoLabel.setText("Proxy stopped");
        updateStatus();
    }

    private void applySettings() {
        if (!saveConfig()) {
            return;
        }
        if (isProxyRunning()) {
            stopProxy();
            startProxy();
        } else {
            updateStatus();
        }
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

                int port = readPortFromField();
                if (port > 0) {
                    urlLabel.setText("Use as HTTP proxy: 127.0.0.1:" + port);
                } else {
                    urlLabel.setText("Use as HTTP proxy: 127.0.0.1:<port>");
                }
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
