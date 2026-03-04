package de.bund.zrb.config;

import de.bund.zrb.ProxyMode;

public class ProxyConfig {

    private final int port;
    private final String keystorePath;
    private final boolean mitmEnabled;
    private final boolean rewriteEnabled;
    private final String rewriteModel;
    private final String rewriteTemperature;
    private final boolean gatewayEnabled;
    private final ProxyMode proxyMode;

    private final String clientHost;
    private int clientPort;

    // Gemeinsamer Gateway-Passkey (Bestand), plus getrennte Felder für Server/Client
    private String gatewayPasskey;
    private int serverPort;
    private String serverGatewayPasskey;
    private String clientGatewayPasskey;

    private boolean showHelpOnStart = true;

    // WPAD/PAC Proxy Support (Client Mode)
    private boolean clientOutboundProxyEnabled = false;
    private int clientOutboundProxyCacheTtlSeconds = 300; // 5 minutes
    private int clientOutboundProxyConnectTimeoutMillis = 10000; // 10 seconds
    private int clientOutboundProxyHandshakeTimeoutMillis = 10000; // 10 seconds
    private String clientOutboundProxyScriptPath = ""; // Custom script path (empty = use default)

    // Gateway Authentication Options
    private String gatewayAuthMode = "PASSKEY"; // NONE, PASSKEY, TOKEN
    private boolean gatewayEncryptionEnabled = false;
    private String gatewayUsername = "";  // Optional username

    // Relay Mode: Server bleibt aktiv auch wenn sich zu anderem Server verbunden wird
    private boolean relayModeEnabled = false;
    
    // Remote Gateway Host (leer = nur Server-Mode, nicht-leer = auch Client-Mode)
    private String remoteGatewayHost = "";

    public ProxyConfig(int port,
                       String keystorePath,
                       boolean mitmEnabled,
                       boolean rewriteEnabled,
                       String rewriteModel,
                       String rewriteTemperature,
                       boolean gatewayEnabled,
                       ProxyMode proxyMode,
                       String clientHost,
                       int clientPort) {
        this(port,
                keystorePath,
                mitmEnabled,
                rewriteEnabled,
                rewriteModel,
                rewriteTemperature,
                gatewayEnabled,
                proxyMode,
                clientHost,
                clientPort,
                "passkey1234");
    }

    public ProxyConfig(int port,
                       String keystorePath,
                       boolean mitmEnabled,
                       boolean rewriteEnabled,
                       String rewriteModel,
                       String rewriteTemperature,
                       boolean gatewayEnabled,
                       ProxyMode proxyMode,
                       String clientHost,
                       int clientPort,
                       String gatewayPasskey) {
        this.port = port;
        this.keystorePath = keystorePath;
        this.mitmEnabled = mitmEnabled;
        this.rewriteEnabled = rewriteEnabled;
        this.rewriteModel = rewriteModel;
        this.rewriteTemperature = rewriteTemperature;
        this.gatewayEnabled = gatewayEnabled;
        this.proxyMode = proxyMode != null ? proxyMode : ProxyMode.SERVER;
        this.clientHost = (clientHost == null || clientHost.trim().isEmpty()) ? "127.0.0.1" : clientHost.trim();
        this.clientPort = (clientPort <= 0 || clientPort > 65535) ? 8888 : clientPort;
        this.gatewayPasskey = (gatewayPasskey == null || gatewayPasskey.trim().isEmpty())
                ? "passkey1234"
                : gatewayPasskey.trim();
        // Standard: Server-Port = Proxy-Port
        this.serverPort = this.port;
        // Default: Server/Client-Passkeys leiten sich vom gemeinsamen Passkey ab
        this.serverGatewayPasskey = this.gatewayPasskey;
        this.clientGatewayPasskey = this.gatewayPasskey;
        this.showHelpOnStart = true;
    }

    public int getPort() {
        return port;
    }

    public String getKeystorePath() {
        return keystorePath;
    }

    public boolean isMitmEnabled() {
        return mitmEnabled;
    }

    public boolean isRewriteEnabled() {
        return rewriteEnabled;
    }

    public String getRewriteModel() {
        return rewriteModel;
    }

    public String getRewriteTemperature() {
        return rewriteTemperature;
    }

    public boolean isGatewayEnabled() {
        return gatewayEnabled;
    }

    public ProxyMode getProxyMode() {
        return proxyMode;
    }

    public String getClientHost() {
        return clientHost;
    }

    public int getClientPort() {
        return clientPort;
    }

    public void setClientPort(String value) {
        try {
            int p = Integer.parseInt(value.trim());
            if (p > 0 && p <= 65535) {
                this.clientPort = p;
            }
        } catch (Exception ignored) {
            // ungültige Eingabe wird ignoriert
        }
    }

    public String getGatewayPasskey() {
        return gatewayPasskey;
    }

    public boolean isShowHelpOnStart() {
        return showHelpOnStart;
    }

    public void setShowHelpOnStart(boolean showHelpOnStart) {
        this.showHelpOnStart = showHelpOnStart;
    }

    // --- neue Getter/Setter für Server/Client-spezifische Ports und Passkeys ---

    public int getServerPort() {
        return serverPort > 0 ? serverPort : port;
    }

    public void setServerPort(String value) {
        try {
            int p = Integer.parseInt(value.trim());
            if (p > 0 && p <= 65535) {
                this.serverPort = p;
            }
        } catch (Exception ignored) {
            // ungültige Eingabe wird ignoriert
        }
    }

    public String getServerGatewayPasskey() {
        return (serverGatewayPasskey == null || serverGatewayPasskey.isEmpty())
                ? gatewayPasskey
                : serverGatewayPasskey;
    }

    public void setServerGatewayPasskey(String value) {
        this.serverGatewayPasskey = value != null ? value.trim() : "";
    }

    public String getClientGatewayPasskey() {
        return (clientGatewayPasskey == null || clientGatewayPasskey.isEmpty())
                ? gatewayPasskey
                : clientGatewayPasskey;
    }

    public void setClientGatewayPasskey(String value) {
        this.clientGatewayPasskey = value != null ? value.trim() : "";
    }

    // --- WPAD/PAC Proxy Support (Client Mode) ---

    public boolean isClientOutboundProxyEnabled() {
        return clientOutboundProxyEnabled;
    }

    public void setClientOutboundProxyEnabled(boolean enabled) {
        this.clientOutboundProxyEnabled = enabled;
    }

    public int getClientOutboundProxyCacheTtlSeconds() {
        return clientOutboundProxyCacheTtlSeconds;
    }

    public void setClientOutboundProxyCacheTtlSeconds(int seconds) {
        if (seconds > 0) {
            this.clientOutboundProxyCacheTtlSeconds = seconds;
        }
    }

    public int getClientOutboundProxyConnectTimeoutMillis() {
        return clientOutboundProxyConnectTimeoutMillis;
    }

    public void setClientOutboundProxyConnectTimeoutMillis(int millis) {
        if (millis > 0) {
            this.clientOutboundProxyConnectTimeoutMillis = millis;
        }
    }

    public int getClientOutboundProxyHandshakeTimeoutMillis() {
        return clientOutboundProxyHandshakeTimeoutMillis;
    }

    public void setClientOutboundProxyHandshakeTimeoutMillis(int millis) {
        if (millis > 0) {
            this.clientOutboundProxyHandshakeTimeoutMillis = millis;
        }
    }

    public String getClientOutboundProxyScriptPath() {
        return clientOutboundProxyScriptPath != null ? clientOutboundProxyScriptPath : "";
    }

    public void setClientOutboundProxyScriptPath(String path) {
        this.clientOutboundProxyScriptPath = path != null ? path.trim() : "";
    }

    // --- Gateway Authentication ---

    public String getGatewayAuthMode() {
        return gatewayAuthMode != null ? gatewayAuthMode : "PASSKEY";
    }

    public void setGatewayAuthMode(String mode) {
        if (mode != null && (mode.equals("NONE") || mode.equals("PASSKEY") || mode.equals("TOKEN"))) {
            this.gatewayAuthMode = mode;
        }
    }

    public boolean isGatewayEncryptionEnabled() {
        return gatewayEncryptionEnabled;
    }

    public void setGatewayEncryptionEnabled(boolean enabled) {
        this.gatewayEncryptionEnabled = enabled;
    }

    public String getGatewayUsername() {
        return gatewayUsername != null ? gatewayUsername : "";
    }

    public void setGatewayUsername(String username) {
        this.gatewayUsername = username != null ? username.trim() : "";
    }

    // --- Relay Mode ---

    public boolean isRelayModeEnabled() {
        return relayModeEnabled;
    }

    public void setRelayModeEnabled(boolean enabled) {
        this.relayModeEnabled = enabled;
    }

    // --- Remote Gateway Host ---

    public String getRemoteGatewayHost() {
        return remoteGatewayHost != null ? remoteGatewayHost.trim() : "";
    }

    public void setRemoteGatewayHost(String host) {
        this.remoteGatewayHost = host != null ? host.trim() : "";
    }

    /**
     * Determines if this proxy should act as a client (connect to remote gateway).
     * True if remoteGatewayHost is not empty.
     */
    public boolean shouldConnectToRemote() {
        return remoteGatewayHost != null && !remoteGatewayHost.trim().isEmpty();
    }

    /**
     * Determines if local server should be active.
     * True if relayModeEnabled OR no remote gateway is configured.
     */
    public boolean shouldRunLocalServer() {
        return relayModeEnabled || !shouldConnectToRemote();
    }
}
