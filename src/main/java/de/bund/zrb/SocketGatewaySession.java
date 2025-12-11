package de.bund.zrb;

import java.io.*;
import java.net.Socket;

/**
 * Simple GatewaySession for protocol:
 * - first line from client: HELLO <id> (handled by GatewayServer)
 * - server sends: CONNECT <host> <port> (one tunnel per session)
 * - client replies: OK
 * - afterwards raw byte tunnel over the same socket.
 */
class SocketGatewaySession implements GatewaySession {

    private final String id;
    private final String remoteAddress;
    private final Socket controlSocket;
    private final GatewaySessionManager sessionManager;
    private final MitmTrafficListener trafficListener;
    private final BufferedReader reader; // already positioned after HELLO

    private volatile boolean alive = true;
    private volatile boolean busy = false;

    SocketGatewaySession(String id,
                         String remoteAddress,
                         Socket controlSocket,
                         GatewaySessionManager sessionManager,
                         MitmTrafficListener trafficListener,
                         BufferedReader reader) {
        this.id = id;
        this.remoteAddress = remoteAddress;
        this.controlSocket = controlSocket;
        this.sessionManager = sessionManager;
        this.trafficListener = trafficListener;
        this.reader = reader;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getRemoteAddress() {
        return remoteAddress;
    }

    @Override
    public boolean isAlive() {
        return alive && !controlSocket.isClosed();
    }

    @Override
    public synchronized Socket openConnectTunnel(String host, int port) throws IOException {
        return openTunnel("CONNECT", host, port);
    }

    @Override
    public synchronized Socket openHttpConnection(String host, int port) throws IOException {
        return openTunnel("HTTP", host, port);
    }

    private Socket openTunnel(String type, String host, int port) throws IOException {
        if (!isAlive()) {
            throw new IOException("Gateway session not alive");
        }
        if (busy) {
            throw new IOException("Gateway session already busy with another tunnel");
        }
        busy = true;

        OutputStream out = controlSocket.getOutputStream();
        Writer writer = new OutputStreamWriter(out, "UTF-8");
        writer.write(type + " " + host + " " + port + "\r\n");
        writer.flush();

        String line = reader.readLine();
        if (line == null || !"OK".equals(line.trim())) {
            busy = false;
            throw new IOException("Gateway did not confirm tunnel: " + line);
        }

        log("Tunnel established via gateway " + id + " for " + host + ":" + port);
        // Use the same socket as remote endpoint; ProxyConnectionHandler will pipe data.
        return controlSocket;
    }

    @Override
    public void close() throws IOException {
        alive = false;
        sessionManager.clearActiveSession(this);
        controlSocket.close();
    }

    /**
     * Run loop: blocks until control socket is closed. For this simple protocol there
     * is no additional control traffic after CONNECT/OK, so we just wait.
     */
    void run() {
        try {
            controlSocket.getInputStream().read(); // block until closed
        } catch (IOException ignored) {
        } finally {
            alive = false;
            sessionManager.clearActiveSession(this);
            try {
                controlSocket.close();
            } catch (IOException ignored) {
            }
            log("Gateway session closed: " + id + " @ " + remoteAddress);
        }
    }

    private void log(String msg) {
        if (trafficListener != null) {
            trafficListener.onTraffic("info", msg, false);
        } else {
            System.out.println("[GatewaySession] " + msg);
        }
    }
}

