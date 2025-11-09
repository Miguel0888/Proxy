package de.bund.zrb;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class LocalProxyServer {

    private final int listenPort;
    private final ProxyConnectionHandler connectionHandler;

    private volatile boolean running;
    private ServerSocket serverSocket;
    private Thread acceptThread;

    public LocalProxyServer(int listenPort, MitmHandler mitmHandler) {
        this.listenPort = listenPort;
        this.connectionHandler = new ProxyConnectionHandler(mitmHandler);
    }

    public synchronized void start() throws IOException {
        if (running) {
            return;
        }

        serverSocket = new ServerSocket(listenPort);
        running = true;

        acceptThread = new Thread(new Runnable() {
            @Override
            public void run() {
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
        Thread t = new Thread(new ClientConnectionTask(clientSocket, connectionHandler), "proxy-client");
        t.setDaemon(true);
        t.start();
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
