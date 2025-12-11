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
    private final int clientPort;

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
}
