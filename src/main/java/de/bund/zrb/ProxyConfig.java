package de.bund.zrb;

public class ProxyConfig {

    private final int port;
    private final String keystorePath;
    private final boolean mitmEnabled;
    private final boolean rewriteEnabled;
    private final String rewriteModel;
    private final String rewriteTemperature;
    private final boolean gatewayEnabled;

    public ProxyConfig(int port,
                       String keystorePath,
                       boolean mitmEnabled,
                       boolean rewriteEnabled,
                       String rewriteModel,
                       String rewriteTemperature,
                       boolean gatewayEnabled) {
        this.port = port;
        this.keystorePath = keystorePath;
        this.mitmEnabled = mitmEnabled;
        this.rewriteEnabled = rewriteEnabled;
        this.rewriteModel = rewriteModel;
        this.rewriteTemperature = rewriteTemperature;
        this.gatewayEnabled = gatewayEnabled;
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
}
