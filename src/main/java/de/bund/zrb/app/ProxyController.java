package de.bund.zrb.app;

import de.bund.zrb.*;
import de.bund.zrb.client.GatewayClient;
import de.bund.zrb.common.ProxyView;
import de.bund.zrb.config.ProxyConfig;
import de.bund.zrb.mitm.*;
import de.bund.zrb.server.LocalProxyServer;
import de.bund.zrb.server.gateway.GatewaySessionManager;
import de.bund.zrb.service.CaInstallService;
import de.bund.zrb.service.MitmSetupService;
import de.bund.zrb.service.ProxyConfigService;

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
    private Thread clientThread;
    private volatile boolean clientRunning;

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

        // Neue vereinfachte Logik:
        // - remoteHost leer = nur Server-Mode
        // - remoteHost gesetzt = Client-Mode (und Server wenn relayMode aktiv)
        String remoteHost = view.getClientTargetHost();
        int remotePort = view.getClientTargetPort();
        boolean hasRemoteTarget = remoteHost != null && !remoteHost.trim().isEmpty();
        boolean shouldRunServer = !hasRemoteTarget || config.isRelayModeEnabled();
        boolean shouldRunClient = hasRemoteTarget;

        // Server starten wenn nötig
        if (shouldRunServer) {
            int port = view.getServerPort();
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
                            "Starting local proxy server on port " + port + " (gateway mode enabled)",
                            false
                    );
                }
            } else {
                outboundProvider = new DirectConnectionProvider(15000, 60000);
                if (trafficListener != null) {
                    trafficListener.onTraffic(
                            "info",
                            "Starting local proxy server on port " + port + " (direct mode)",
                            false
                    );
                }
            }

            String gatewayPasskey = view.getServerGatewayPasskey();
            GatewaySessionManager gsm = config.isGatewayEnabled() ? gatewaySessionManager : null;
            GatewayGate gatewayGate = new GatewayGate(config.isGatewayEnabled());

            server = new LocalProxyServer(port, mitmHandler, outboundProvider, gsm, gatewayPasskey, view, gatewayGate);
            server.start();
        }

        // Client starten wenn Remote-Ziel konfiguriert
        if (shouldRunClient) {
            if (trafficListener != null) {
                trafficListener.onTraffic(
                        "info",
                        "Connecting to remote gateway: " + remoteHost + ":" + remotePort,
                        false
                );
            }

            String gatewayId = "client";
            OutboundSocketDialer outboundDialer = createOutboundDialer(config, trafficListener);

            clientRunning = true;
            clientThread = new Thread(() -> {
                while (clientRunning) {
                    // Remote-Ziel erneut von View holen (könnte sich geändert haben)
                    String currentRemoteHost = view.getClientTargetHost();
                    int currentRemotePort = view.getClientTargetPort();
                    
                    // Leere IP = Client stoppen
                    if (currentRemoteHost == null || currentRemoteHost.trim().isEmpty()) {
                        if (trafficListener != null) {
                            trafficListener.onTraffic("info", "Remote host cleared, stopping client connection", false);
                        }
                        view.updateGatewayClientStatus("No remote host configured", false);
                        break;
                    }
                    
                    if (currentRemotePort <= 0 || currentRemotePort > 65535) {
                        view.updateGatewayClientStatus("Invalid port: " + currentRemotePort, false);
                        try {
                            Thread.sleep(2000L);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        continue;
                    }

                    view.updateGatewayClientStatus("Connecting to " + currentRemoteHost + ":" + currentRemotePort, false);

                    try {
                        GatewayClient client = new GatewayClient(currentRemoteHost, currentRemotePort, gatewayId, trafficListener, view, outboundDialer);
                        client.run();
                    } catch (IOException e) {
                        if (trafficListener != null) {
                            trafficListener.onTraffic("info", "GatewayClient error: " + e.getMessage(), false);
                        }
                        view.updateGatewayClientStatus("Connection failed to " + currentRemoteHost + ":" + currentRemotePort, false);
                    }

                    if (!clientRunning) {
                        break;
                    }

                    try {
                        Thread.sleep(5000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }, "gateway-client-loop");
            clientThread.setDaemon(true);
            clientThread.start();
        }
    }

    synchronized void stopProxy() {
        if (server != null) {
            server.stop();
            server = null;
        }
        clientRunning = false;
        if (clientThread != null) {
            clientThread.interrupt();
            try {
                clientThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            clientThread = null;
        }
        view.updateGatewayClientStatus("No client connected", false);
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

    private OutboundSocketDialer createOutboundDialer(ProxyConfig config,
                                                      MitmTrafficListener trafficListener) {
        if (!config.isClientOutboundProxyEnabled()) {
            // Direct connection without proxy
            int connectTimeout = config.getClientOutboundProxyConnectTimeoutMillis();
            int readTimeout = 30000; // 30 seconds read timeout
            if (trafficListener != null) {
                trafficListener.onTraffic("info", "Client outbound: DIRECT connection (no proxy)", false);
            }
            return new DirectSocketDialer(connectTimeout, readTimeout);
        }

        // WPAD/PAC-based proxy resolution
        if (trafficListener != null) {
            trafficListener.onTraffic("info", "Client outbound: using Windows system proxy (WPAD/PAC)", false);
        }

        // Check if Windows
        String os = System.getProperty("os.name");
        if (os == null || !os.toLowerCase().contains("win")) {
            if (trafficListener != null) {
                trafficListener.onTraffic("warn", "Windows system proxy resolver requested but OS is not Windows. Falling back to DIRECT.", false);
            }
            return new DirectSocketDialer(config.getClientOutboundProxyConnectTimeoutMillis(), 30000);
        }

        // Create Windows resolver with error handling
        try {
            File workingDir = configService.getConfigDir();
            if (!workingDir.exists()) {
                workingDir.mkdirs();
            }
            
            String customScriptPath = config.getClientOutboundProxyScriptPath();
            WindowsProxyResolver resolver = new WindowsProxyResolver(
                    workingDir,
                    config.getClientOutboundProxyCacheTtlSeconds(),
                    trafficListener,
                    customScriptPath.isEmpty() ? null : customScriptPath
            );

            // Create proxy-aware dialer
            if (trafficListener != null) {
                if (customScriptPath.isEmpty()) {
                    trafficListener.onTraffic("info", "WPAD/PAC proxy resolver initialized with default script", false);
                } else {
                    trafficListener.onTraffic("info", "WPAD/PAC proxy resolver initialized with custom script: " + customScriptPath, false);
                }
            }
            
            return new ProxySocketDialer(
                    resolver,
                    config.getClientOutboundProxyConnectTimeoutMillis(),
                    config.getClientOutboundProxyHandshakeTimeoutMillis(),
                    30000, // read timeout
                    trafficListener
            );
        } catch (Exception e) {
            // Fallback to direct connection on any error
            if (trafficListener != null) {
                trafficListener.onTraffic("error", "Failed to initialize WPAD/PAC resolver: " + e.getMessage() + ". Falling back to DIRECT.", false);
            }
            System.err.println("[ProxyController] Failed to initialize WPAD/PAC resolver: " + e.getMessage());
            e.printStackTrace();
            return new DirectSocketDialer(config.getClientOutboundProxyConnectTimeoutMillis(), 30000);
        }
    }
}
