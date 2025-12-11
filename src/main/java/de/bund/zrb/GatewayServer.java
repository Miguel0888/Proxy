package de.bund.zrb;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

class GatewayServer {

    private final int port;
    private final GatewaySessionManager sessionManager;
    private final MitmTrafficListener trafficListener;
    private final ProxyView view;

    private volatile boolean running;
    private ServerSocket serverSocket;
    private Thread thread;

    GatewayServer(int port,
                  GatewaySessionManager sessionManager,
                  MitmTrafficListener trafficListener,
                  ProxyView view) {
        this.port = port;
        this.sessionManager = sessionManager;
        this.trafficListener = trafficListener;
        this.view = view;
    }

    synchronized void start() throws IOException {
        if (running) {
            return;
        }
        serverSocket = new ServerSocket(port);
        running = true;

        thread = new Thread(() -> {
            log("GatewayServer listening on port " + port);
            try {
                while (running) {
                    Socket socket = serverSocket.accept();
                    handleGatewayClient(socket);
                }
            } catch (IOException e) {
                if (running) {
                    log("GatewayServer error: " + e.getMessage());
                }
            } finally {
                try {
                    serverSocket.close();
                } catch (IOException ignored) {
                    // Ignore
                }
                log("GatewayServer stopped on port " + port);
                // When server stops, show no client connected
                if (view != null) {
                    view.updateGatewayClientStatus("No client connected", false);
                }
            }
        }, "gateway-server");
        thread.setDaemon(true);
        thread.start();
    }

    private void handleGatewayClient(Socket socket) {
        Thread t = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), "UTF-8"))) {

                String hello = reader.readLine();
                if (hello == null || !hello.startsWith("HELLO ")) {
                    log("Invalid HELLO from " + socket.getRemoteSocketAddress());
                    try {
                        socket.close();
                    } catch (IOException ignored) {
                        // Ignore
                    }
                    return;
                }

                String id = hello.substring("HELLO ".length()).trim();
                String remote = String.valueOf(socket.getRemoteSocketAddress());
                log("Gateway client connected: " + remote + " id=" + id);

                if (view != null) {
                    view.updateGatewayClientStatus(
                            "Gateway client connected: " + remote + " id=" + id,
                            true
                    );
                }

                SocketGatewaySession session = new SocketGatewaySession(
                        id,
                        remote,
                        socket,
                        sessionManager,
                        trafficListener,
                        reader
                );

                sessionManager.setActiveSession(session);

                // Block until connection closes; afterwards clear state
                session.run();

                if (view != null) {
                    view.updateGatewayClientStatus("No client connected", false);
                }

            } catch (IOException e) {
                log("Gateway client error: " + e.getMessage());
                if (view != null) {
                    view.updateGatewayClientStatus("No client connected", false);
                }
            }
        }, "gateway-client-handler");
        t.setDaemon(true);
        t.start();
    }

    synchronized void stop() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
                // Ignore
            }
        }
        if (thread != null) {
            try {
                thread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void log(String msg) {
        if (trafficListener != null) {
            trafficListener.onTraffic("info", msg, false);
        } else {
            System.out.println("[GatewayServer] " + msg);
        }
    }
}
