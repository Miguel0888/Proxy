package de.bund.zrb;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class LocalProxyServer {

    private final int listenPort;
    private final MitmHandler mitmHandler;
    private final OutboundConnectionProvider outboundConnectionProvider;
    private final GatewaySessionManager gatewaySessionManager;
    private final String gatewayPasskey;
    private final ProxyView view;

    private volatile boolean running;
    private ServerSocket serverSocket;
    private Thread acceptThread;

    public LocalProxyServer(int listenPort, MitmHandler mitmHandler) {
        this(listenPort, mitmHandler, new DirectConnectionProvider(15000, 60000), null, null, null);
    }

    public LocalProxyServer(int listenPort,
                            MitmHandler mitmHandler,
                            OutboundConnectionProvider outboundConnectionProvider) {
        this(listenPort, mitmHandler, outboundConnectionProvider, null, null, null);
    }

    public LocalProxyServer(int listenPort,
                            MitmHandler mitmHandler,
                            OutboundConnectionProvider outboundConnectionProvider,
                            GatewaySessionManager gatewaySessionManager,
                            String gatewayPasskey,
                            ProxyView view) {
        if (listenPort <= 0 || listenPort > 65535) {
            throw new IllegalArgumentException("listenPort must be between 1 and 65535");
        }
        if (outboundConnectionProvider == null) {
            throw new IllegalArgumentException("outboundConnectionProvider must not be null");
        }
        this.listenPort = listenPort;
        this.mitmHandler = mitmHandler;
        this.outboundConnectionProvider = outboundConnectionProvider;
        this.gatewaySessionManager = gatewaySessionManager;
        this.gatewayPasskey = gatewayPasskey;
        this.view = view;
    }

    public synchronized void start() throws IOException {
        if (running) {
            return;
        }

        serverSocket = new ServerSocket(listenPort);
        running = true;

        acceptThread = new Thread(() -> {
            System.out.println("[Proxy] Listening on port " + listenPort);
            try {
                while (running) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        System.out.println("[Proxy] Incoming connection from " + clientSocket.getRemoteSocketAddress());
                        handleClientAsync(clientSocket);
                    } catch (IOException e) {
                        if (running) {
                            System.err.println("[Proxy] Accept failed: " + e.getMessage());
                        }
                    }
                }
            } finally {
                closeServerSocket();
                System.out.println("[Proxy] Stopped listening on port " + listenPort);
            }
        }, "proxy-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        closeServerSocket();
        if (acceptThread != null) {
            try {
                acceptThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public boolean isRunning() {
        return running;
    }

    private void handleClientAsync(final Socket clientSocket) {
        Thread t = new Thread(
                new ClientConnectionTask(clientSocket, createConnectionHandler()),
                "proxy-client"
        );
        t.setDaemon(true);
        t.start();
    }

    private ProxyConnectionHandler createConnectionHandler() {
        return new ProxyConnectionHandler(mitmHandler, outboundConnectionProvider, gatewaySessionManager, gatewayPasskey, view);
    }

    private void closeServerSocket() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
                // Ignore
            } finally {
                serverSocket = null;
            }
        }
    }
}
