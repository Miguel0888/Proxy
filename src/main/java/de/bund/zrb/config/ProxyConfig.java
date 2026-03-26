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

    // WPAD/PAC Proxy Support (Client Mode) - now using win-proxy-java library (no scripts needed)
    private boolean clientOutboundProxyEnabled = false;
    private String clientOutboundProxyMode = "REGISTRY"; // WINDOWS_PAC, REGISTRY, PAC_URL, MANUAL
    private String clientOutboundProxyHost = "";      // manual proxy host (MANUAL mode)
    private int clientOutboundProxyPort = 8080;       // manual proxy port (MANUAL mode)
    private String clientOutboundProxyPacUrl = "";     // custom PAC URL (PAC_URL mode)
    private boolean clientOutboundProxyPacUrlFromScript = true; // if true, pacUrl is a PowerShell script whose output is the PAC URL
    private String clientOutboundProxyBypassList = ""; // bypass list (semicolon-separated, e.g. "localhost;127.0.0.1;*.local")
    private boolean clientOutboundProxyNoProxyLocal = true; // never proxy local targets
    private String clientOutboundProxyPacScript = "";  // PowerShell PAC/WPAD script (WINDOWS_PAC mode)
    private String clientOutboundProxyTestUrl = "https://plugins.gradle.org/m2/"; // test URL for proxy test
    private String clientOutboundProxyPacSource = "REGISTRY"; // PAC URL source: DIRECT, REGISTRY, POWERSHELL
    private int clientOutboundProxyCacheTtlSeconds = 300; // 5 minutes
    private int clientOutboundProxyConnectTimeoutMillis = 10000; // 10 seconds
    private int clientOutboundProxyHandshakeTimeoutMillis = 10000; // 10 seconds

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

    public String getClientOutboundProxyMode() {
        return clientOutboundProxyMode;
    }

    public void setClientOutboundProxyMode(String mode) {
        if (mode != null && (mode.equals("WINDOWS_PAC") || mode.equals("REGISTRY") || mode.equals("PAC_URL") || mode.equals("MANUAL"))) {
            this.clientOutboundProxyMode = mode;
        }
    }

    public String getClientOutboundProxyHost() {
        return clientOutboundProxyHost;
    }

    public void setClientOutboundProxyHost(String host) {
        this.clientOutboundProxyHost = host != null ? host.trim() : "";
    }

    public int getClientOutboundProxyPort() {
        return clientOutboundProxyPort;
    }

    public void setClientOutboundProxyPort(int port) {
        if (port > 0 && port <= 65535) {
            this.clientOutboundProxyPort = port;
        }
    }

    public String getClientOutboundProxyPacUrl() {
        return clientOutboundProxyPacUrl;
    }

    public void setClientOutboundProxyPacUrl(String url) {
        this.clientOutboundProxyPacUrl = url != null ? url.trim() : "";
    }

    public boolean isClientOutboundProxyPacUrlFromScript() {
        return clientOutboundProxyPacUrlFromScript;
    }

    public void setClientOutboundProxyPacUrlFromScript(boolean fromScript) {
        this.clientOutboundProxyPacUrlFromScript = fromScript;
    }

    public String getClientOutboundProxyBypassList() {
        return clientOutboundProxyBypassList;
    }

    public void setClientOutboundProxyBypassList(String list) {
        this.clientOutboundProxyBypassList = list != null ? list.trim() : "";
    }

    public String getClientOutboundProxyPacSource() {
        return clientOutboundProxyPacSource;
    }

    public void setClientOutboundProxyPacSource(String source) {
        this.clientOutboundProxyPacSource = source != null ? source.trim() : "";
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

    public boolean isClientOutboundProxyNoProxyLocal() {
        return clientOutboundProxyNoProxyLocal;
    }

    public void setClientOutboundProxyNoProxyLocal(boolean noProxyLocal) {
        this.clientOutboundProxyNoProxyLocal = noProxyLocal;
    }

    public String getClientOutboundProxyPacScript() {
        return clientOutboundProxyPacScript;
    }

    public void setClientOutboundProxyPacScript(String script) {
        this.clientOutboundProxyPacScript = script != null ? script : "";
    }

    public String getClientOutboundProxyTestUrl() {
        return clientOutboundProxyTestUrl;
    }

    public void setClientOutboundProxyTestUrl(String url) {
        this.clientOutboundProxyTestUrl = url != null ? url.trim() : "";
    }

    /**
     * @deprecated Script path is no longer used - win-proxy-java library doesn't need external scripts.
     * This method is kept for backwards compatibility when loading old configurations.
     */
    @Deprecated
    public String getClientOutboundProxyScriptPath() {
        return "";
    }

    /**
     * @deprecated Script path is no longer used - win-proxy-java library doesn't need external scripts.
     * This method is kept for backwards compatibility when loading old configurations.
     */
    @Deprecated
    public void setClientOutboundProxyScriptPath(String path) {
        // Ignored - no longer used
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
