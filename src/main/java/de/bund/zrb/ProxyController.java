package de.bund.zrb;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

class ProxyController {

    private final ProxyView view;
    private final ProxyConfigService configService;
    private final MitmSetupService mitmSetupService;
    private final CaInstallService caInstallService;

    private LocalProxyServer server;

    ProxyController(ProxyView view,
                    ProxyConfigService configService,
                    MitmSetupService mitmSetupService,
                    CaInstallService caInstallService) {
        this.view = view;
        this.configService = configService;
        this.mitmSetupService = mitmSetupService;
        this.caInstallService = caInstallService;
    }

    boolean isProxyRunning() {
        return server != null && server.isRunning();
    }

    void stopProxy() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    void startProxy(ProxyConfig config, MitmTrafficListener listener) throws IOException {
        if (isProxyRunning()) {
            return;
        }

        int port = config.getPort();
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be a number between 1 and 65535.");
        }

        MitmHandler mitmHandler = null;
        if (config.isMitmEnabled()) {
            String keystorePath = config.getKeystorePath();
            if (keystorePath == null || keystorePath.trim().isEmpty()) {
                throw new IllegalArgumentException("Keystore path must not be empty when MITM is enabled.");
            }

            File ksFile = new File(keystorePath.trim());
            if (!ksFile.exists()) {
                throw new IllegalArgumentException("Keystore not found at: " + ksFile.getAbsolutePath());
            }

            boolean rewriteEnabled = config.isRewriteEnabled();
            String rewriteModel = config.getRewriteModel();
            Double rewriteTemperature = null;
            String temp = config.getRewriteTemperature();
            if (rewriteEnabled) {
                if (rewriteModel == null || rewriteModel.trim().isEmpty()) {
                    throw new IllegalArgumentException("Model name must not be empty when rewrite is enabled.");
                }
                if (temp == null || temp.trim().isEmpty()) {
                    throw new IllegalArgumentException("Temperature must not be empty when rewrite is enabled.");
                }
                try {
                    rewriteTemperature = Double.valueOf(temp.trim());
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("Temperature must be a valid decimal number.");
                }
            }

            mitmHandler = new GenericMitmHandler(
                    ksFile.getAbsolutePath(),
                    "changeit",
                    Collections.singleton("api.openai.com"),
                    listener,
                    rewriteEnabled,
                    rewriteModel,
                    rewriteTemperature
            );
        }

        server = new LocalProxyServer(port, mitmHandler);
        server.start();
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
}

