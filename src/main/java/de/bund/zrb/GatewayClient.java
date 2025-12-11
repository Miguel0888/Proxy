package de.bund.zrb;

import java.io.*;
import java.net.Socket;

// Implemented GatewayClient for CLIENT mode: connects to server, sends HELLO <id>, then handles CONNECT/HTTP commands by opening local sockets and tunneling bytes.
class GatewayClient {

    private final String host;
    private final int port;
    private final String gatewayId;
    private final MitmTrafficListener trafficListener;
    private final ProxyView view;

    GatewayClient(String host,
                  int port,
                  String gatewayId,
                  MitmTrafficListener trafficListener,
                  ProxyView view) {
        this.host = host;
        this.port = port;
        this.gatewayId = gatewayId;
        this.trafficListener = trafficListener;
        this.view = view;
    }

    void run() throws IOException {
        log("GatewayClient connecting to " + host + ":" + port + " as " + gatewayId);
        try (Socket socket = new Socket(host, port)) {
            if (view != null) {
                view.updateGatewayClientStatus("Gateway client connected: " + host + ":" + port + " id=" + gatewayId, true);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            Writer writer = new OutputStreamWriter(socket.getOutputStream(), "UTF-8");

            writer.write("HELLO " + gatewayId + "\r\n");
            writer.flush();

            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    log("GatewayClient: server closed connection");
                    break;
                }
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                String[] parts = line.split(" ");
                if (parts.length != 3) {
                    log("GatewayClient: invalid command: " + line);
                    break;
                }

                String cmd = parts[0];
                String targetHost = parts[1];
                int targetPort;
                try {
                    targetPort = Integer.parseInt(parts[2]);
                } catch (NumberFormatException e) {
                    log("GatewayClient: invalid port in command: " + line);
                    break;
                }

                if (!"CONNECT".equals(cmd) && !"HTTP".equals(cmd)) {
                    log("GatewayClient: unknown command: " + cmd);
                    break;
                }

                handleConnect(socket, writer, targetHost, targetPort);
                // For simple protocol: after one tunnel, we stop and let client reconnect
                break;
            }
        } finally {
            if (view != null) {
                view.updateGatewayClientStatus("No client connected", false);
            }
        }
    }

    private void handleConnect(Socket controlSocket,
                               Writer writer,
                               String targetHost,
                               int targetPort) throws IOException {
        log("GatewayClient: opening local connection to " + targetHost + ":" + targetPort);
        try (Socket target = new Socket(targetHost, targetPort)) {
            writer.write("OK\r\n");
            writer.flush();

            // Pipe bytes between controlSocket and target
            Thread t1 = new Thread(new SocketPipeTask(controlSocket, target), "gw-client-to-target");
            Thread t2 = new Thread(new SocketPipeTask(target, controlSocket), "gw-target-to-client");
            t1.setDaemon(true);
            t2.setDaemon(true);
            t1.start();
            t2.start();

            try {
                t1.join();
                t2.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void log(String msg) {
        if (trafficListener != null) {
            trafficListener.onTraffic("info", msg, false);
        } else {
            System.out.println("[GatewayClient] " + msg);
        }
    }
}
