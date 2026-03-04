package de.bund.zrb.service;

import de.bund.zrb.ProxyMode;
import de.bund.zrb.config.ProxyConfig;

import java.io.*;
import java.util.Properties;

public class ProxyConfigService {

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
    private static final String KEY_SHOW_HELP_ON_START = "proxy.showHelpOnStart";
    private static final String KEY_GATEWAY_PASSKEY = "proxy.gateway.passkey";

    // neue Keys für getrennte Server/Client-Werte
    private static final String KEY_SERVER_PORT = "proxy.server.port";
    private static final String KEY_SERVER_GATEWAY_PASSKEY = "proxy.server.gateway.passkey";
    private static final String KEY_CLIENT_GATEWAY_PASSKEY = "proxy.client.gateway.passkey";

    // WPAD/PAC Proxy Support (Client Mode)
    private static final String KEY_CLIENT_OUTBOUND_PROXY_ENABLED = "proxy.client.outboundProxy.enabled";
    private static final String KEY_CLIENT_OUTBOUND_PROXY_CACHE_TTL = "proxy.client.outboundProxy.cacheTtlSeconds";
    private static final String KEY_CLIENT_OUTBOUND_PROXY_CONNECT_TIMEOUT = "proxy.client.outboundProxy.connectTimeoutMillis";
    private static final String KEY_CLIENT_OUTBOUND_PROXY_HANDSHAKE_TIMEOUT = "proxy.client.outboundProxy.handshakeTimeoutMillis";
    private static final String KEY_CLIENT_OUTBOUND_PROXY_SCRIPT_PATH = "proxy.client.outboundProxy.scriptPath";

    // Gateway Authentication
    private static final String KEY_GATEWAY_AUTH_MODE = "proxy.gateway.authMode";
    private static final String KEY_GATEWAY_ENCRYPTION_ENABLED = "proxy.gateway.encryption.enabled";
    private static final String KEY_GATEWAY_USERNAME = "proxy.gateway.username";

    // Relay Mode and Remote Gateway
    private static final String KEY_RELAY_MODE_ENABLED = "proxy.relay.enabled";
    private static final String KEY_REMOTE_GATEWAY_HOST = "proxy.remote.gateway.host";

    public ProxyConfig loadConfig() {
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

            String gatewayPasskey = props.getProperty(KEY_GATEWAY_PASSKEY, "passkey1234");

            ProxyConfig cfg = new ProxyConfig(port, ks, mitm, rewriteEnabled, rewriteModel, rewriteTemp,
                    gatewayEnabled, proxyMode, clientHost, clientPort, gatewayPasskey);

            // neue Server/Client-spezifische Werte aus Properties lesen
            String serverPortRaw = props.getProperty(KEY_SERVER_PORT);
            if (serverPortRaw != null) {
                cfg.setServerPort(serverPortRaw);
            }
            String serverGw = props.getProperty(KEY_SERVER_GATEWAY_PASSKEY);
            if (serverGw != null) {
                cfg.setServerGatewayPasskey(serverGw);
            }
            String clientGw = props.getProperty(KEY_CLIENT_GATEWAY_PASSKEY);
            if (clientGw != null) {
                cfg.setClientGatewayPasskey(clientGw);
            }

            // Help-Flag aus Properties lesen
            String showHelpRaw = props.getProperty(KEY_SHOW_HELP_ON_START);
            if (showHelpRaw == null) {
                cfg.setShowHelpOnStart(true);
            } else {
                cfg.setShowHelpOnStart(Boolean.parseBoolean(showHelpRaw));
            }

            // WPAD/PAC Proxy Support laden
            String outboundProxyEnabledRaw = props.getProperty(KEY_CLIENT_OUTBOUND_PROXY_ENABLED);
            if (outboundProxyEnabledRaw != null) {
                cfg.setClientOutboundProxyEnabled(Boolean.parseBoolean(outboundProxyEnabledRaw));
            }
            String cacheTtlRaw = props.getProperty(KEY_CLIENT_OUTBOUND_PROXY_CACHE_TTL);
            if (cacheTtlRaw != null) {
                try {
                    cfg.setClientOutboundProxyCacheTtlSeconds(Integer.parseInt(cacheTtlRaw));
                } catch (NumberFormatException ignored) {
                    // ignore
                }
            }
            String connectTimeoutRaw = props.getProperty(KEY_CLIENT_OUTBOUND_PROXY_CONNECT_TIMEOUT);
            if (connectTimeoutRaw != null) {
                try {
                    cfg.setClientOutboundProxyConnectTimeoutMillis(Integer.parseInt(connectTimeoutRaw));
                } catch (NumberFormatException ignored) {
                    // ignore
                }
            }
            String handshakeTimeoutRaw = props.getProperty(KEY_CLIENT_OUTBOUND_PROXY_HANDSHAKE_TIMEOUT);
            if (handshakeTimeoutRaw != null) {
                try {
                    cfg.setClientOutboundProxyHandshakeTimeoutMillis(Integer.parseInt(handshakeTimeoutRaw));
                } catch (NumberFormatException ignored) {
                    // ignore
                }
            }
            String scriptPathRaw = props.getProperty(KEY_CLIENT_OUTBOUND_PROXY_SCRIPT_PATH);
            if (scriptPathRaw != null) {
                cfg.setClientOutboundProxyScriptPath(scriptPathRaw);
            }

            // Gateway Authentication laden
            String authModeRaw = props.getProperty(KEY_GATEWAY_AUTH_MODE);
            if (authModeRaw != null) {
                cfg.setGatewayAuthMode(authModeRaw);
            }
            String encryptionEnabledRaw = props.getProperty(KEY_GATEWAY_ENCRYPTION_ENABLED);
            if (encryptionEnabledRaw != null) {
                cfg.setGatewayEncryptionEnabled(Boolean.parseBoolean(encryptionEnabledRaw));
            }
            String usernameRaw = props.getProperty(KEY_GATEWAY_USERNAME);
            if (usernameRaw != null) {
                cfg.setGatewayUsername(usernameRaw);
            }

            // Relay Mode and Remote Gateway laden
            String relayModeRaw = props.getProperty(KEY_RELAY_MODE_ENABLED);
            if (relayModeRaw != null) {
                cfg.setRelayModeEnabled(Boolean.parseBoolean(relayModeRaw));
            }
            String remoteGatewayHostRaw = props.getProperty(KEY_REMOTE_GATEWAY_HOST);
            if (remoteGatewayHostRaw != null) {
                cfg.setRemoteGatewayHost(remoteGatewayHostRaw);
            }

            return cfg;
        } catch (IOException | NumberFormatException e) {
            return defaultConfig();
        } finally {
            closeQuietly(in);
        }
    }

    public void saveConfig(ProxyConfig config) throws IOException {
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
        props.setProperty(KEY_SHOW_HELP_ON_START, String.valueOf(config.isShowHelpOnStart()));
        props.setProperty(KEY_GATEWAY_PASSKEY, config.getGatewayPasskey());

        // neue Server/Client-spezifische Werte speichern
        props.setProperty(KEY_SERVER_PORT, String.valueOf(config.getServerPort()));
        props.setProperty(KEY_SERVER_GATEWAY_PASSKEY, config.getServerGatewayPasskey());
        props.setProperty(KEY_CLIENT_GATEWAY_PASSKEY, config.getClientGatewayPasskey());

        // WPAD/PAC Proxy Support speichern
        props.setProperty(KEY_CLIENT_OUTBOUND_PROXY_ENABLED, String.valueOf(config.isClientOutboundProxyEnabled()));
        props.setProperty(KEY_CLIENT_OUTBOUND_PROXY_CACHE_TTL, String.valueOf(config.getClientOutboundProxyCacheTtlSeconds()));
        props.setProperty(KEY_CLIENT_OUTBOUND_PROXY_CONNECT_TIMEOUT, String.valueOf(config.getClientOutboundProxyConnectTimeoutMillis()));
        props.setProperty(KEY_CLIENT_OUTBOUND_PROXY_HANDSHAKE_TIMEOUT, String.valueOf(config.getClientOutboundProxyHandshakeTimeoutMillis()));
        props.setProperty(KEY_CLIENT_OUTBOUND_PROXY_SCRIPT_PATH, config.getClientOutboundProxyScriptPath());

        // Gateway Authentication speichern
        props.setProperty(KEY_GATEWAY_AUTH_MODE, config.getGatewayAuthMode());
        props.setProperty(KEY_GATEWAY_ENCRYPTION_ENABLED, String.valueOf(config.isGatewayEncryptionEnabled()));
        props.setProperty(KEY_GATEWAY_USERNAME, config.getGatewayUsername());

        // Relay Mode and Remote Gateway speichern
        props.setProperty(KEY_RELAY_MODE_ENABLED, String.valueOf(config.isRelayModeEnabled()));
        props.setProperty(KEY_REMOTE_GATEWAY_HOST, config.getRemoteGatewayHost());

        File file = getConfigFile();
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            props.store(out, "Local proxy configuration");
        } finally {
            closeQuietly(out);
        }
    }

    public File getConfigDir() {
        String home = System.getProperty("user.home");
        return new File(home, CONFIG_DIR);
    }

    public File getConfigFile() {
        return new File(getConfigDir(), CONFIG_FILE);
    }

    String defaultKeystorePath() {
        return new File(getConfigDir(), "myproxy.jks").getAbsolutePath();
    }

    private ProxyConfig defaultConfig() {
        ProxyConfig cfg = new ProxyConfig(8888, defaultKeystorePath(), false, false,
                "gpt-5-mini", "1.0", false, ProxyMode.SERVER,
                "127.0.0.1", 8888, "passkey1234");
        cfg.setShowHelpOnStart(true);
        return cfg;
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
