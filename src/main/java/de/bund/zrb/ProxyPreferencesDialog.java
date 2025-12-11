package de.bund.zrb;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class ProxyPreferencesDialog extends JDialog {

    private final ProxyConfigService configService;

    private JTextField portField;
    private JTextField keystoreField;
    private JCheckBox mitmCheckBox;
    private JCheckBox gatewayCheckBox;
    private JCheckBox rewriteCheckBox;
    private JTextField rewriteModelField;
    private JTextField rewriteTemperatureField;

    public ProxyPreferencesDialog(Frame owner, ProxyConfigService configService) {
        super(owner, "Preferences", true);
        this.configService = configService;

        initComponents();
        layoutComponents();
        loadFromConfig();
        pack();
    }

    private void initComponents() {
        portField = new JTextField(6);
        keystoreField = new JTextField(30);
        mitmCheckBox = new JCheckBox("Enable MITM for api.openai.com");
        gatewayCheckBox = new JCheckBox("Route via gateway");

        rewriteCheckBox = new JCheckBox("Rewrite model/temperature for /v1/chat/completions");
        rewriteModelField = new JTextField("gpt-5-mini", 16);
        rewriteTemperatureField = new JTextField("1.0", 4);

        mitmCheckBox.addActionListener(e -> updateRewriteControls());
        rewriteCheckBox.addActionListener(e -> updateRewriteControls());
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

        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 1;
        form.add(new JLabel("Port:"), gc);
        gc.gridx = 1;
        form.add(portField, gc);

        gc.gridx = 0; row++; gc.gridy = row; gc.gridwidth = 1;
        form.add(new JLabel("Keystore (.jks):"), gc);
        gc.gridx = 1;
        form.add(keystoreField, gc);
        JButton browse = new JButton("Browse...");
        browse.addActionListener(e -> chooseKeystore());
        gc.gridx = 2;
        form.add(browse, gc);

        gc.gridx = 0; row++; gc.gridy = row; gc.gridwidth = 3;
        form.add(mitmCheckBox, gc);

        gc.gridx = 0; row++; gc.gridy = row; gc.gridwidth = 3;
        JPanel rewritePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        rewritePanel.add(rewriteCheckBox);
        rewritePanel.add(new JLabel("Model:"));
        rewritePanel.add(rewriteModelField);
        rewritePanel.add(new JLabel("→ Temperature:"));
        rewritePanel.add(rewriteTemperatureField);
        form.add(rewritePanel, gc);

        gc.gridx = 0; row++; gc.gridy = row; gc.gridwidth = 3;
        form.add(gatewayCheckBox, gc);

        content.add(form, BorderLayout.CENTER);

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
        portField.setText(String.valueOf(cfg.getPort()));
        keystoreField.setText(cfg.getKeystorePath());
        mitmCheckBox.setSelected(cfg.isMitmEnabled());
        rewriteCheckBox.setSelected(cfg.isRewriteEnabled());
        rewriteModelField.setText(cfg.getRewriteModel());
        rewriteTemperatureField.setText(cfg.getRewriteTemperature());
        gatewayCheckBox.setSelected(cfg.isGatewayEnabled());
        updateRewriteControls();
    }

    private void onOk() {
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Port must be a number zwischen 1 und 65535.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (port <= 0 || port > 65535) {
            JOptionPane.showMessageDialog(this, "Port must be a number zwischen 1 und 65535.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        boolean mitmEnabled = mitmCheckBox.isSelected();
        String keystore = keystoreField.getText().trim();
        if (mitmEnabled && keystore.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Keystore path must not be empty when MITM is enabled.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        boolean rewriteEnabled = mitmEnabled && rewriteCheckBox.isSelected();
        String model = rewriteModelField.getText().trim();
        String temp = rewriteTemperatureField.getText().trim();

        if (rewriteEnabled) {
            if (model.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Model name must not be empty when rewrite is enabled.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (temp.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Temperature must not be empty when rewrite is enabled.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            try {
                Double.parseDouble(temp);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Temperature must be a valid decimal number.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        // Host/Port für Client-Mode kommen hier (noch) nicht aus dem Dialog; Defaults verwenden
        String clientHost = "127.0.0.1";
        int clientPort = port > 0 ? port : 8888;

        ProxyConfig cfg = new ProxyConfig(
                port,
                keystore,
                mitmEnabled,
                rewriteEnabled,
                model,
                temp,
                gatewayCheckBox.isSelected(),
                ProxyMode.SERVER, // Dialog selbst schaltet den Mode noch nicht
                clientHost,
                clientPort
        );

        try {
            configService.saveConfig(cfg);
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to save config: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateRewriteControls() {
        boolean mitm = mitmCheckBox.isSelected();
        rewriteCheckBox.setEnabled(mitm);
        boolean rewrite = mitm && rewriteCheckBox.isSelected();
        rewriteModelField.setEnabled(rewrite);
        rewriteTemperatureField.setEnabled(rewrite);
    }

    private void chooseKeystore() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select myproxy.jks");
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            keystoreField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }
}
