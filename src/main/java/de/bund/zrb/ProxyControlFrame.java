package de.bund.zrb;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.util.Properties;

public class ProxyControlFrame extends JFrame {

    private static final String CONFIG_DIR = ".proxy";
    private static final String CONFIG_FILE = "proxy.properties";

    private static final String KEY_PORT = "proxy.port";
    private static final String KEY_KEYSTORE_PATH = "proxy.keystore.path";

    private JTextField portField;
    private JTextField keystoreField;
    private JLabel statusLabel;
    private JLabel urlLabel;
    private JButton startStopButton;
    private JButton applyButton;

    private Thread proxyThread;
    private volatile boolean proxyRunning;

    public ProxyControlFrame() {
        super("Local Proxy Control");
        initComponents();
        layoutComponents();
        initActions();
        loadConfig();
        autoStartProxy();
    }

    private void initComponents() {
        portField = new JTextField(6);
        keystoreField = new JTextField(30);

        statusLabel = new JLabel("Status: stopped");
        statusLabel.setForeground(Color.RED);

        urlLabel = new JLabel("Proxy: 127.0.0.1");
        startStopButton = new JButton("Stop proxy");
        applyButton = new JButton("Apply settings");
    }

    private void layoutComponents() {
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(new EmptyBorder(10, 10, 10, 10));
        setContentPane(content);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 4, 4, 4);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        gc.gridx = 0;
        gc.gridy = row;
        form.add(new JLabel("Port:"), gc);

        gc.gridx = 1;
        form.add(portField, gc);

        row++;
        gc.gridx = 0;
        gc.gridy = row;
        form.add(new JLabel("Keystore (.jks):"), gc);

        gc.gridx = 1;
        form.add(keystoreField, gc);

        JButton browseButton = new JButton("Browse...");
        gc.gridx = 2;
        form.add(browseButton, gc);

        row++;
        gc.gridx = 0;
        gc.gridy = row;
        gc.gridwidth = 3;
        form.add(statusLabel, gc);

        row++;
        gc.gridy = row;
        form.add(urlLabel, gc);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(applyButton);
        buttons.add(startStopButton);

        content.add(form, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);

        // Browse keystore
        browseButton.addActionListener(e -> chooseKeystore());
    }

    private void initActions() {
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(600, 220);
        setLocationRelativeTo(null);

        startStopButton.addActionListener(e -> toggleProxy());
        applyButton.addActionListener(e -> applySettings());
    }

    private void autoStartProxy() {
        // Start proxy automatically on UI startup
        startProxy();
    }

    private void toggleProxy() {
        if (proxyRunning) {
            stopProxy();
        } else {
            startProxy();
        }
    }

    private void startProxy() {
        if (proxyRunning) {
            return;
        }

        int port = readPortFromField();
        if (port <= 0) {
            showError("Port must be a number between 1 and 65535.");
            return;
        }

        String keystorePath = keystoreField.getText().trim();
        if (keystorePath.isEmpty()) {
            showError("Keystore path must not be empty.");
            return;
        }

        File ksFile = new File(keystorePath);
        if (!ksFile.exists()) {
            showError("Keystore not found at: " + ksFile.getAbsolutePath());
            return;
        }

        proxyThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    proxyRunning = true;
                    updateStatus();
                    // Use existing MITM implementation
                    MitmProxyServer server =
                            new MitmProxyServer(port, keystorePath, "changeit", "api.openai.com");
                    server.start(); // blocks; when it returns, proxy is finished
                } catch (IOException e) {
                    showError("Failed to start proxy: " + e.getMessage());
                } finally {
                    proxyRunning = false;
                    updateStatus();
                }
            }
        }, "proxy-main");
        proxyThread.setDaemon(true);
        proxyThread.start();
    }

    private void stopProxy() {
        // Current MitmProxyServer implementation has no explicit stop hook.
        // For now, try to interrupt thread; for a clean stop, add a stop() to MitmProxyServer.
        if (proxyThread != null && proxyThread.isAlive()) {
            proxyThread.interrupt();
        }
        proxyRunning = false;
        updateStatus();
    }

    private void applySettings() {
        if (!saveConfig()) {
            return;
        }
        if (proxyRunning) {
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
                if (proxyRunning) {
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
            // Defaults
            portField.setText("8888");
            keystoreField.setText(defaultKeystorePath());
            updateStatus();
            return;
        }

        Properties props = new Properties();
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            props.load(in);

            String port = props.getProperty(KEY_PORT, "8888");
            String ks = props.getProperty(KEY_KEYSTORE_PATH, defaultKeystorePath());

            portField.setText(port);
            keystoreField.setText(ks);
        } catch (IOException e) {
            showError("Failed to load config: " + e.getMessage());
            portField.setText("8888");
            keystoreField.setText(defaultKeystorePath());
        } finally {
            closeQuietly(in);
        }
        updateStatus();
    }

    private boolean saveConfig() {
        int port = readPortFromField();
        if (port <= 0) {
            showError("Port must be a number between 1 and 65535.");
            return false;
        }

        String keystorePath = keystoreField.getText().trim();
        if (keystorePath.isEmpty()) {
            showError("Keystore path must not be empty.");
            return false;
        }

        File dir = getConfigDir();
        if (!dir.exists() && !dir.mkdirs()) {
            showError("Could not create config directory: " + dir.getAbsolutePath());
            return false;
        }

        Properties props = new Properties();
        props.setProperty(KEY_PORT, String.valueOf(port));
        props.setProperty(KEY_KEYSTORE_PATH, keystorePath);

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

    private File getConfigFileStatic() {
        return getConfigFile();
    }

    private static File getConfigFile() {
        String home = System.getProperty("user.home");
        File dir = new File(home, CONFIG_DIR);
        return new File(dir, CONFIG_FILE);
    }

    private void showError(String message) {
        // Use Swing thread to show message box
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
