package de.bund.zrb;

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
}
