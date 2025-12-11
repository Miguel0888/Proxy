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

    private volatile boolean running;
    private ServerSocket serverSocket;
    private Thread thread;

    GatewayServer(int port, GatewaySessionManager sessionManager, MitmTrafficListener trafficListener) {
        this.port = port;
        this.sessionManager = sessionManager;
        this.trafficListener = trafficListener;
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
                }
                log("GatewayServer stopped on port " + port);
            }
        }, "gateway-server");
        thread.setDaemon(true);
        thread.start();
    }

    private void handleGatewayClient(Socket socket) {
        Thread t = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"))) {
                String hello = reader.readLine();
                if (hello == null || !hello.startsWith("HELLO ")) {
                    log("Invalid HELLO from " + socket.getRemoteSocketAddress());
                    socket.close();
                    return;
                }
                String id = hello.substring("HELLO ".length()).trim();
                String remote = String.valueOf(socket.getRemoteSocketAddress());
                log("Gateway client connected: " + remote + " id=" + id);

                SocketGatewaySession session = new SocketGatewaySession(id, remote, socket, sessionManager, trafficListener, reader);
                sessionManager.setActiveSession(session);
                // Session.run() blocks until connection closes
                session.run();
            } catch (IOException e) {
                log("Gateway client error: " + e.getMessage());
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
