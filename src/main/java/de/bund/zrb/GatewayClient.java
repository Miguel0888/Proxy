package de.bund.zrb;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Simple gateway client that connects from machine B to server A
 * and registers as a gateway.
 */
public class GatewayClient {

    private final String serverHost;
    private final int serverPort;
    private final String gatewayId;

    private static final int CONNECT_TIMEOUT_MILLIS = 15000;
    private static final int READ_TIMEOUT_MILLIS = 60000;

    public GatewayClient(String serverHost, int serverPort, String gatewayId) {
        if (serverHost == null || serverHost.trim().length() == 0) {
            throw new IllegalArgumentException("serverHost must not be empty");
        }
        if (serverPort <= 0 || serverPort > 65535) {
            throw new IllegalArgumentException("serverPort must be between 1 and 65535");
        }
        if (gatewayId == null || gatewayId.trim().length() == 0) {
            throw new IllegalArgumentException("gatewayId must not be empty");
        }
        this.serverHost = serverHost.trim();
        this.serverPort = serverPort;
        this.gatewayId = gatewayId.trim();
    }

    /**
     * Run client loop.
     */
    public void run() {
        while (true) {
            Socket socket = null;
            try {
                socket = connectToServer();
                System.out.println("[GatewayClient] Connected to " + serverHost + ":" + serverPort);

                performHello(socket);
                System.out.println("[GatewayClient] Sent HELLO for gatewayId=" + gatewayId);

                readCommandLoop(socket);
            } catch (IOException e) {
                System.err.println("[GatewayClient] Connection error: " + e.getMessage());
            } finally {
                closeQuietly(socket);
            }

            // Simple reconnect strategy
            try {
                System.out.println("[GatewayClient] Reconnect in 3 seconds ...");
                Thread.sleep(3000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private Socket connectToServer() throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(serverHost, serverPort), CONNECT_TIMEOUT_MILLIS);
        socket.setSoTimeout(READ_TIMEOUT_MILLIS);
        return socket;
    }

    private void performHello(Socket socket) throws IOException {
        OutputStream out = socket.getOutputStream();
        String line = "HELLO " + gatewayId + "\r\n";
        out.write(line.getBytes("ISO-8859-1"));
        out.flush();
    }

    private void readCommandLoop(Socket socket) throws IOException {
        InputStream in = socket.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, "ISO-8859-1"));

        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.length() == 0) {
                continue;
            }
            System.out.println("[GatewayClient] Command from server: " + line);

            // TODO: Implement actual commands later
            // Example design:
            // - OPEN-CONNECT host port
            // - OPEN-HTTP host port
            // For now just log the command.
        }
        System.out.println("[GatewayClient] Server closed connection");
    }

    private void closeQuietly(Socket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
            // Ignore
        }
    }
}
