package de.bund.zrb;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;

/**
 * GatewaySession implementation for a single TCP control connection to a gateway client.
 *
 * Protocol (server side = this class, client side = GatewayClient):
 * - Client connects and sends:  HELLO [passkey]   (handled before this session is created)
 * - This session is created with a reader positioned after the HELLO line.
 * - For each tunnel:
 *   - Server sends:   CONNECT <host> <port>   or   HTTP <host> <port>
 *   - Client replies: OK or ERROR
 *   - If OK, both sides start piping raw bytes over the same socket.
 *
 * Important design decision:
 * Read from the control socket in exactly one place (run-loop) to avoid races with
 * other threads. openConnectTunnel/openHttpConnection only write commands and then
 * wait for the reader thread to report the confirmation.
 */
class SocketGatewaySession implements GatewaySession {

    private final String id;
    private final String remoteAddress;
    private final Socket controlSocket;
    private final GatewaySessionManager sessionManager;
    private final MitmTrafficListener trafficListener;
    private final BufferedReader reader; // Positioned after HELLO
    private final ProxyView view;

    private volatile boolean alive = true;
    private volatile boolean busy = false;

    // Synchronization for tunnel confirmation between run() and openTunnel(...)
    private final Object tunnelLock = new Object();
    private String lastTunnelResponse;     // e.g. "OK" or "ERROR" or error text
    private boolean waitingForResponse;

    SocketGatewaySession(String id,
                         String remoteAddress,
                         Socket controlSocket,
                         GatewaySessionManager sessionManager,
                         MitmTrafficListener trafficListener,
                         BufferedReader reader,
                         ProxyView view) {
        this.id = id;
        this.remoteAddress = remoteAddress;
        this.controlSocket = controlSocket;
        this.sessionManager = sessionManager;
        this.trafficListener = trafficListener;
        this.reader = reader;
        this.view = view;
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

    /**
     * Send tunnel command to gateway client and wait for confirmation from run-loop.
     */
    private Socket openTunnel(String type, String host, int port) throws IOException {
        if (!isAlive()) {
            throw new IOException("Gateway session not alive");
        }
        if (busy) {
            throw new IOException("Gateway session already busy with another tunnel");
        }
        busy = true;

        try {
            OutputStream out = controlSocket.getOutputStream();
            Writer writer = new OutputStreamWriter(out, "UTF-8");

            String command = type + " " + host + " " + port + "\r\n";
            log("Send tunnel command to gateway " + id + ": " + command.trim());
            writer.write(command);
            writer.flush();

            String response = waitForTunnelResponse();
            if (response == null) {
                throw new IOException("Gateway did not confirm tunnel: control connection closed");
            }
            if (!"OK".equalsIgnoreCase(response.trim())) {
                throw new IOException("Gateway did not confirm tunnel: " + response);
            }

            log("Tunnel established via gateway " + id + " for " + host + ":" + port);
            // Use the same socket as remote endpoint; ProxyConnectionHandler will pipe data.
            return controlSocket;
        } finally {
            // Current design supports one tunnel per session at a time.
            busy = false;
        }
    }

    /**
     * Wait until run() has read the next line from the gateway client and reported it.
     */
    private String waitForTunnelResponse() throws IOException {
        synchronized (tunnelLock) {
            if (!isAlive()) {
                return null;
            }

            lastTunnelResponse = null;
            waitingForResponse = true;

            long deadline = System.currentTimeMillis() + 15000L; // 15 seconds timeout
            long now = System.currentTimeMillis();
            while (waitingForResponse && now < deadline && isAlive()) {
                long remaining = deadline - now;
                if (remaining <= 0L) {
                    break;
                }
                try {
                    tunnelLock.wait(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for tunnel confirmation", e);
                }
                now = System.currentTimeMillis();
            }

            waitingForResponse = false;
            return lastTunnelResponse;
        }
    }

    @Override
    public void close() throws IOException {
        alive = false;
        sessionManager.clearActiveSession(this);
        controlSocket.close();
    }

    /**
     * Run loop for this gateway session. This method is called from the ProxyConnectionHandler
     * thread that accepted the HELLO connection and blocks until the gateway disconnects or
     * the session is closed.
     *
     * Responsibilities:
     * - Read all textual control messages from the gateway client.
     * - Deliver tunnel confirmation lines back to openTunnel(...) via tunnelLock.
     * - Close session on EOF or IO error.
     */
    void run() {
        // Update server UI: gateway client is connected
        if (view != null) {
            view.updateGatewayClientStatus("Gateway client connected: " + remoteAddress, true);
        }
        try {
            while (isAlive()) {
                String line;
                try {
                    line = reader.readLine();
                } catch (IOException e) {
                    log("Gateway session read error: " + e.getMessage());
                    break;
                }

                if (line == null) {
                    log("Gateway session closed by remote: " + id + " @ " + remoteAddress);
                    break;
                }

                line = line.trim();
                if (line.length() == 0) {
                    continue;
                }

                handleControlLine(line);
            }
        } finally {
            alive = false;
            sessionManager.clearActiveSession(this);
            try {
                controlSocket.close();
            } catch (IOException ignored) {
                // Ignore secondary failure on close
            }
            if (view != null) {
                view.updateGatewayClientStatus("No client connected", false);
            }
            log("Gateway session closed: " + id + " @ " + remoteAddress);
        }
    }

    /**
     * Handle a single control line received from gateway client.
     */
    private void handleControlLine(String line) {
        synchronized (tunnelLock) {
            if (waitingForResponse) {
                // Treat any line while waiting as response to last tunnel command.
                lastTunnelResponse = line;
                waitingForResponse = false;
                tunnelLock.notifyAll();
                return;
            }
        }

        // Currently the protocol does not define unsolicited control messages.
        // Log unexpected lines for diagnostics.
        log("Unexpected control message from gateway " + id + ": '" + line + "'");
    }

    private void log(String msg) {
        if (trafficListener != null) {
            trafficListener.onTraffic("info", msg, false);
        } else {
            System.out.println("[GatewaySession] " + msg);
        }
    }
}
