package de.bund.zrb;

import java.io.*;
import java.util.Properties;

class ProxyConfigService {

    private static final String CONFIG_DIR = ".proxy";
    private static final String CONFIG_FILE = "proxy.properties";

    private static final String KEY_PORT = "proxy.port";
    private static final String KEY_KEYSTORE_PATH = "proxy.keystore.path";
    private static final String KEY_MITM_ENABLED = "proxy.mitm.enabled";

    private static final String KEY_REWRITE_ENABLED = "proxy.model.rewrite.enabled";
    private static final String KEY_REWRITE_MODEL = "proxy.model.rewrite.name";
    private static final String KEY_REWRITE_TEMPERATURE = "proxy.model.rewrite.temperature";

    private static final String KEY_GATEWAY_ENABLED = "proxy.gateway.enabled";
    private static final String KEY_PROXY_MODE = "proxy.mode"; // values: SERVER or CLIENT
    private static final String KEY_CLIENT_HOST = "proxy.client.host";
    private static final String KEY_CLIENT_PORT = "proxy.client.port";

    ProxyConfig loadConfig() {
        File file = getConfigFile();
        if (!file.exists()) {
            return defaultConfig();
        }

        Properties props = new Properties();
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            props.load(in);

            int port = Integer.parseInt(props.getProperty(KEY_PORT, "8888"));
            String ks = props.getProperty(KEY_KEYSTORE_PATH, defaultKeystorePath());
            boolean mitm = Boolean.parseBoolean(props.getProperty(KEY_MITM_ENABLED, "false"));

            boolean rewriteEnabled = Boolean.parseBoolean(props.getProperty(KEY_REWRITE_ENABLED, "false"));
            String rewriteModel = props.getProperty(KEY_REWRITE_MODEL, "gpt-5-mini");
            String rewriteTemp = props.getProperty(KEY_REWRITE_TEMPERATURE, "1.0");

            boolean gatewayEnabled = Boolean.parseBoolean(props.getProperty(KEY_GATEWAY_ENABLED, "false"));

            String modeValue = props.getProperty(KEY_PROXY_MODE, "SERVER");
            ProxyMode proxyMode;
            try {
                proxyMode = ProxyMode.valueOf(modeValue.toUpperCase());
            } catch (IllegalArgumentException e) {
                proxyMode = ProxyMode.SERVER;
            }

            String clientHost = props.getProperty(KEY_CLIENT_HOST, "127.0.0.1");
            int clientPort;
            try {
                clientPort = Integer.parseInt(props.getProperty(KEY_CLIENT_PORT, "8888"));
            } catch (NumberFormatException e) {
                clientPort = 8888;
            }

            return new ProxyConfig(port, ks, mitm, rewriteEnabled, rewriteModel, rewriteTemp,
                    gatewayEnabled, proxyMode, clientHost, clientPort);
        } catch (IOException | NumberFormatException e) {
            return defaultConfig();
        } finally {
            closeQuietly(in);
        }
    }

    void saveConfig(ProxyConfig config) throws IOException {
        File dir = getConfigDir();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Could not create config directory: " + dir.getAbsolutePath());
        }

        Properties props = new Properties();
        props.setProperty(KEY_PORT, String.valueOf(config.getPort()));
        props.setProperty(KEY_KEYSTORE_PATH, config.getKeystorePath() == null ? "" : config.getKeystorePath());
        props.setProperty(KEY_MITM_ENABLED, String.valueOf(config.isMitmEnabled()));
        props.setProperty(KEY_REWRITE_ENABLED, String.valueOf(config.isRewriteEnabled()));
        props.setProperty(KEY_REWRITE_MODEL, config.getRewriteModel() == null ? "" : config.getRewriteModel());
        props.setProperty(KEY_REWRITE_TEMPERATURE, config.getRewriteTemperature() == null ? "" : config.getRewriteTemperature());
        props.setProperty(KEY_GATEWAY_ENABLED, String.valueOf(config.isGatewayEnabled()));
        props.setProperty(KEY_PROXY_MODE, config.getProxyMode().name());
        props.setProperty(KEY_CLIENT_HOST, config.getClientHost());
        props.setProperty(KEY_CLIENT_PORT, String.valueOf(config.getClientPort()));

        File file = getConfigFile();
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            props.store(out, "Local proxy configuration");
        } finally {
            closeQuietly(out);
        }
    }

    File getConfigDir() {
        String home = System.getProperty("user.home");
        return new File(home, CONFIG_DIR);
    }

    File getConfigFile() {
        return new File(getConfigDir(), CONFIG_FILE);
    }

    String defaultKeystorePath() {
        return new File(getConfigDir(), "myproxy.jks").getAbsolutePath();
    }

    private ProxyConfig defaultConfig() {
        return new ProxyConfig(8888, defaultKeystorePath(), false, false,
                "gpt-5-mini", "1.0", false, ProxyMode.SERVER,
                "127.0.0.1", 8888);
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
}
