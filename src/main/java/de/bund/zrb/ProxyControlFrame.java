package de.bund.zrb;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.util.Collections;
import java.util.Properties;

public class ProxyControlFrame extends JFrame {

    private static final String CONFIG_DIR = ".proxy";
    private static final String CONFIG_FILE = "proxy.properties";

    private static final String KEY_PORT = "proxy.port";
    private static final String KEY_KEYSTORE_PATH = "proxy.keystore.path";
    private static final String KEY_MITM_ENABLED = "proxy.mitm.enabled";

    private JTextField portField;
    private JTextField keystoreField;
    private JCheckBox mitmCheckBox;
    private JLabel statusLabel;
    private JLabel urlLabel;
    private JButton startStopButton;
    private JButton applyButton;
    private JTextPane trafficPane;

    private LocalProxyServer server;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public ProxyControlFrame() {
        super("Local Proxy Control");

        initComponents();
        layoutComponents();
        initActions();
        loadConfig();
        updateStatus();
    }

    private void initComponents() {
        portField = new JTextField(6);
        keystoreField = new JTextField(30);
        mitmCheckBox = new JCheckBox("Enable MITM for api.openai.com");

        statusLabel = new JLabel("Status: stopped");
        statusLabel.setForeground(Color.RED);

        urlLabel = new JLabel("Use as HTTP proxy: 127.0.0.1:<port>");

        startStopButton = new JButton("Start proxy");
        applyButton = new JButton("Apply settings");

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
        top.add(new JLabel("Port:"), gc);

        gc.gridx = 1;
        top.add(portField, gc);

        // Keystore
        row++;
        gc.gridx = 0;
        gc.gridy = row;
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
        buttons.add(applyButton);
        buttons.add(startStopButton);

        JPanel north = new JPanel(new BorderLayout());
        north.add(top, BorderLayout.CENTER);
        north.add(buttons, BorderLayout.SOUTH);

        content.add(north, BorderLayout.NORTH);
        content.add(new JScrollPane(trafficPane), BorderLayout.CENTER);

        browseButton.addActionListener(e -> chooseKeystore());
    }

    private void initActions() {
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);

        startStopButton.addActionListener(e -> toggleProxy());
        applyButton.addActionListener(e -> applySettings());
    }

    private void toggleProxy() {
        if (isProxyRunning()) {
            stopProxy();
        } else {
            startProxy();
        }
    }

    private void startProxy() {
        if (isProxyRunning()) {
            return;
        }

        int port = readPortFromField();
        if (port <= 0) {
            showError("Port must be a number between 1 and 65535.");
            return;
        }

        boolean mitmEnabled = mitmCheckBox.isSelected();
        String keystorePath = keystoreField.getText().trim();

        MitmHandler mitmHandler = null;

        if (mitmEnabled) {
            if (keystorePath.isEmpty()) {
                showError("Keystore path must not be empty when MITM is enabled.");
                return;
            }
            File ksFile = new File(keystorePath);
            if (!ksFile.exists()) {
                showError("Keystore not found at: " + ksFile.getAbsolutePath());
                return;
            }

            MitmTrafficListener listener = new MitmTrafficListener() {
                @Override
                public void onTraffic(String direction, String text, boolean isJson) {
                    appendTraffic(direction, text, isJson);
                }
            };

            try {
                mitmHandler = new GenericMitmHandler(
                        ksFile.getAbsolutePath(),
                        "changeit", // keep consistent with your PS scripts
                        Collections.singleton("api.openai.com"),
                        listener
                );
                appendTraffic("info", "MITM enabled for api.openai.com", false);
            } catch (IllegalStateException e) {
                showError("Failed to initialize MITM: " + e.getMessage());
                return;
            }
        } else {
            appendTraffic("info", "Starting proxy without MITM", false);
        }

        server = new LocalProxyServer(port, mitmHandler);
        try {
            server.start();
        } catch (IOException e) {
            server = null;
            showError("Failed to start proxy: " + e.getMessage());
            return;
        }

        updateStatus();
    }

    private void stopProxy() {
        if (server != null) {
            server.stop();
            server = null;
        }
        appendTraffic("info", "Proxy stopped", false);
        updateStatus();
    }

    private boolean isProxyRunning() {
        return server != null && server.isRunning();
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

    private void appendTraffic(String direction, String text, boolean isJson) {
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
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '&': sb.append("&amp;"); break;
                default: sb.append(c);
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

    private void loadConfig() {
        File file = getConfigFile();
        if (!file.exists()) {
            portField.setText("8888");
            keystoreField.setText(defaultKeystorePath());
            mitmCheckBox.setSelected(false);
            return;
        }

        Properties props = new Properties();
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            props.load(in);

            String port = props.getProperty(KEY_PORT, "8888");
            String ks = props.getProperty(KEY_KEYSTORE_PATH, defaultKeystorePath());
            String mitm = props.getProperty(KEY_MITM_ENABLED, "false");

            portField.setText(port);
            keystoreField.setText(ks);
            mitmCheckBox.setSelected(Boolean.parseBoolean(mitm));
        } catch (IOException e) {
            showError("Failed to load config: " + e.getMessage());
            portField.setText("8888");
            keystoreField.setText(defaultKeystorePath());
            mitmCheckBox.setSelected(false);
        } finally {
            closeQuietly(in);
        }
    }

    private boolean saveConfig() {
        int port = readPortFromField();
        if (port <= 0) {
            showError("Port must be a number between 1 und 65535.");
            return false;
        }

        String keystorePath = keystoreField.getText().trim();
        boolean mitmEnabled = mitmCheckBox.isSelected();

        File dir = getConfigDir();
        if (!dir.exists() && !dir.mkdirs()) {
            showError("Could not create config directory: " + dir.getAbsolutePath());
            return false;
        }

        Properties props = new Properties();
        props.setProperty(KEY_PORT, String.valueOf(port));
        props.setProperty(KEY_KEYSTORE_PATH, keystorePath);
        props.setProperty(KEY_MITM_ENABLED, String.valueOf(mitmEnabled));

        File file = getConfigFile();
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            props.store(out, "Local proxy configuration");
        } catch (IOException e) {
            showError("Failed to save config: " + e.getMessage());
            return false;
        } finally {
            closeQuietly(out);
        }
        return true;
    }

    private File getConfigDir() {
        String home = System.getProperty("user.home");
        return new File(home, CONFIG_DIR);
    }

    private File getConfigFile() {
        return new File(getConfigDir(), CONFIG_FILE);
    }

    private String defaultKeystorePath() {
        return new File(getConfigDir(), "myproxy.jks").getAbsolutePath();
    }

    private void showError(final String message) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                JOptionPane.showMessageDialog(ProxyControlFrame.this, message, "Error", JOptionPane.ERROR_MESSAGE);
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
