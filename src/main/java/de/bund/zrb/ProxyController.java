package de.bund.zrb;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

class ProxyController {

    private final ProxyView view;
    private final ProxyConfigService configService;
    private final MitmSetupService mitmSetupService;
    private final CaInstallService caInstallService;

    private final GatewaySessionManager gatewaySessionManager = new GatewaySessionManager();

    private LocalProxyServer server;

    ProxyController(ProxyView view,
                    ProxyConfigService configService,
                    MitmSetupService mitmSetupService,
                    CaInstallService caInstallService) {
        if (view == null) {
            throw new IllegalArgumentException("view must not be null");
        }
        if (configService == null) {
            throw new IllegalArgumentException("configService must not be null");
        }
        if (mitmSetupService == null) {
            throw new IllegalArgumentException("mitmSetupService must not be null");
        }
        if (caInstallService == null) {
            throw new IllegalArgumentException("caInstallService must not be null");
        }
        this.view = view;
        this.configService = configService;
        this.mitmSetupService = mitmSetupService;
        this.caInstallService = caInstallService;
    }

    synchronized void startProxy(ProxyConfig config,
                                 MitmTrafficListener trafficListener) throws IOException {
        if (server != null && server.isRunning()) {
            return;
        }

        if (config == null) {
            throw new IllegalArgumentException("config must not be null.");
        }

        int port = config.getPort();
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535.");
        }

        MitmHandler mitmHandler = createMitmHandler(config, trafficListener);

        OutboundConnectionProvider outboundProvider;
        if (config.isGatewayEnabled()) {
            outboundProvider = new GatewayConnectionProvider(gatewaySessionManager);
            if (trafficListener != null) {
                trafficListener.onTraffic(
                        "info",
                        "Starting proxy in GATEWAY mode (waiting for gateway client connection)",
                        false
                );
            }
        } else {
            outboundProvider = new DirectConnectionProvider(15000, 60000);
            if (trafficListener != null) {
                trafficListener.onTraffic(
                        "info",
                        "Starting proxy in DIRECT mode",
                        false
                );
            }
        }

        server = new LocalProxyServer(port, mitmHandler, outboundProvider);
        server.start();
    }

    synchronized void stopProxy() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    synchronized boolean isProxyRunning() {
        return server != null && server.isRunning();
    }

    GatewaySessionManager getGatewaySessionManager() {
        return gatewaySessionManager;
    }

    MitmSetupResult runMitmSetup() throws IOException {
        return mitmSetupService.runMitmSetup();
    }

    File getCaFile() {
        return caInstallService.getCaFile();
    }

    CaInstallResult installCa(File caFile) throws IOException {
        return caInstallService.installCa(caFile);
    }

    private MitmHandler createMitmHandler(ProxyConfig config,
                                          MitmTrafficListener trafficListener) throws IOException {
        if (!config.isMitmEnabled()) {
            return null;
        }

        String keystorePath = config.getKeystorePath();
        if (keystorePath == null || keystorePath.trim().length() == 0) {
            throw new IllegalArgumentException("Keystore path must not be empty when MITM is enabled.");
        }

        File ksFile = new File(keystorePath.trim());
        if (!ksFile.exists()) {
            throw new IllegalArgumentException("Keystore not found at: " + ksFile.getAbsolutePath());
        }

        boolean rewriteEnabled = config.isRewriteEnabled();
        String rewriteModel = null;
        Double rewriteTemperature = null;

        if (rewriteEnabled) {
            rewriteModel = config.getRewriteModel();
            if (rewriteModel == null || rewriteModel.trim().length() == 0) {
                throw new IllegalArgumentException("Model name must not be empty when rewrite is enabled.");
            }

            String tempText = config.getRewriteTemperature();
            if (tempText == null || tempText.trim().length() == 0) {
                throw new IllegalArgumentException("Temperature must not be empty when rewrite is enabled.");
            }

            try {
                rewriteTemperature = Double.valueOf(tempText.trim());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Temperature must be a valid decimal number.");
            }
        }

        try {
            GenericMitmHandler handler = new GenericMitmHandler(
                    ksFile.getAbsolutePath(),
                    "changeit",
                    Collections.singleton("api.openai.com"),
                    trafficListener,
                    rewriteEnabled,
                    rewriteModel,
                    rewriteTemperature
            );

            if (trafficListener != null) {
                String info;
                if (!rewriteEnabled) {
                    info = "MITM enabled for api.openai.com without model/temperature rewrite.";
                } else {
                    StringBuilder sb = new StringBuilder();
                    sb.append("MITM enabled for api.openai.com with rewrite: model == ")
                            .append(rewriteModel);
                    if (rewriteTemperature != null) {
                        sb.append(", temperature -> ").append(rewriteTemperature);
                    }
                    info = sb.toString();
                }
                trafficListener.onTraffic("info", info, false);
            }

            return handler;
        } catch (IllegalStateException e) {
            throw new IllegalArgumentException("Failed to initialize MITM: " + e.getMessage(), e);
        }
    }
}
