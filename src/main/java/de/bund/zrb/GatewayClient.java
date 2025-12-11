package de.bund.zrb;

import java.io.*;
import java.net.Socket;

// GatewayClient im CLIENT-Mode: verbindet sich zum Server und dient als Tunnel-Endpunkt.
class GatewayClient {

    private final String host;
    private final int port;
    private final MitmTrafficListener trafficListener;
    private final ProxyView view;

    GatewayClient(String host,
                  int port,
                  String ignoredId, // frühere ID, jetzt ungenutzt
                  MitmTrafficListener trafficListener,
                  ProxyView view) {
        this.host = host;
        this.port = port;
        this.trafficListener = trafficListener;
        this.view = view;
    }

    void run() throws IOException {
        log("GatewayClient connecting to " + host + ":" + port);
        try (Socket socket = new Socket(host, port)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            Writer writer = new OutputStreamWriter(socket.getOutputStream(), "UTF-8");

            String passkey = (view != null) ? view.getGatewayPasskey() : "";
            passkey = passkey != null ? passkey.trim() : "";

            // HELLO nur mit Passkey (keine ID mehr)
            String hello = passkey.isEmpty()
                    ? "HELLO\r\n"
                    : ("HELLO " + passkey + "\r\n");
            writer.write(hello);
            writer.flush();

            // Auf einfache Bestätigung vom Server warten
            String ack = reader.readLine();
            if (ack == null || !"OK".equalsIgnoreCase(ack.trim())) {
                log("GatewayClient: HELLO rejected (server replied: " + ack + ")");
                if (view != null) {
                    view.updateGatewayClientStatus("Gateway HELLO rejected", false);
                }
                return;
            }

            if (view != null) {
                view.updateGatewayClientStatus("Gateway client connected to " + host + ":" + port, true);
            }

            // Ab hier: bestehendes Protokoll zum Server (CONNECT/HTTP-Kommandos)
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
