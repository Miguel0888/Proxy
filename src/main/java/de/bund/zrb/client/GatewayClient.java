package de.bund.zrb.client;

import de.bund.zrb.mitm.MitmTrafficListener;
import de.bund.zrb.common.ProxyView;

import java.io.*;
import java.net.Socket;

// GatewayClient im CLIENT-Mode: verbindet sich zum Server und dient als Tunnel-Endpunkt.
public class GatewayClient {

    private final String host;
    private final int port;
    private final MitmTrafficListener trafficListener;
    private final ProxyView view;

    // Einfaches Flag, ob aktuell ein Tunnel aktiv ist
    private volatile boolean connected;

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

    public void run() throws IOException {
        log("GatewayClient connecting to " + host + ":" + port);
        try (Socket socket = new Socket(host, port)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            Writer writer = new OutputStreamWriter(socket.getOutputStream(), "UTF-8");

            String passkey = (view != null) ? view.getClientGatewayPasskey() : "";
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

            log("GatewayClient: HELLO accepted (server replied: " + ack + ")");

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

                // Einfache Ein-Thread-Lösung: in diesem Thread synchron tunneln.
                handleConnectSingleThread(socket, targetHost, targetPort);
                break;
            }
        } finally {
            connected = false;
            if (view != null) {
                view.updateGatewayClientStatus("No client connected", false);
            }
        }
    }

    /**
     * Einfache Ein-Thread-Variante: Wir beenden das Protokoll, öffnen eine lokale Verbindung
     * und pumpen dann im aktuellen Thread abwechselnd von controlSocket->target und target->controlSocket.
     *
     * Dadurch verwenden wir keinen zweiten Thread und vermeiden die Race-Condition auf demselben Socket.
     */
    private void handleConnectSingleThread(Socket controlSocket,
                                           String targetHost,
                                           int targetPort) throws IOException {
        log("GatewayClient: opening local connection to " + targetHost + ":" + targetPort);

        connected = true;
        if (view != null) {
            view.updateGatewayClientStatus("Gateway tunnel connected to " + targetHost + ":" + targetPort, true);
        }

        try (Socket target = new Socket(targetHost, targetPort)) {
            InputStream controlIn = controlSocket.getInputStream();
            OutputStream controlOut = controlSocket.getOutputStream();
            InputStream targetIn = target.getInputStream();
            OutputStream targetOut = target.getOutputStream();

            // Dem Server signalisieren, dass der Tunnel bereit ist.
            OutputStreamWriter protoWriter = new OutputStreamWriter(controlOut, "UTF-8");
            protoWriter.write("OK\r\n");
            protoWriter.flush();

            byte[] buf = new byte[8192];

            // Solange einer der beiden Sockets offen ist, versuchen wir zu pumpen.
            while (!controlSocket.isClosed() && !target.isClosed()) {
                boolean progressed = false;

                // 1) Daten vom Client (controlSocket) zum Ziel pumpen, falls etwas anliegt
                if (controlIn.available() > 0) {
                    int read = controlIn.read(buf);
                    if (read == -1) {
                        break; // Client hat geschlossen
                    }
                    targetOut.write(buf, 0, read);
                    targetOut.flush();
                    progressed = true;
                }

                // 2) Daten vom Ziel zurück zum Client pumpen
                if (targetIn.available() > 0) {
                    int read = targetIn.read(buf);
                    if (read == -1) {
                        break; // Ziel hat geschlossen
                    }
                    controlOut.write(buf, 0, read);
                    controlOut.flush();
                    progressed = true;
                }

                // Wenn in dieser Iteration nichts passiert ist, kurz schlafen,
                // damit wir nicht busy-loopen.
                if (!progressed) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } catch (IOException e) {
            log("GatewayClient: failed to open local connection to " + targetHost + ":" + targetPort
                    + " -> " + e.getMessage());
            // Versuchen, dem Server einen Fehler zu signalisieren (best effort)
            try {
                Writer w = new OutputStreamWriter(controlSocket.getOutputStream(), "UTF-8");
                w.write("ERROR\r\n");
                w.flush();
            } catch (IOException ignored) {
                // ignore secondary failure
            }
            throw e;
        } finally {
            connected = false;
            if (view != null) {
                view.updateGatewayClientStatus("Gateway tunnel closed", false);
            }
        }
    }

    boolean isConnected() {
        return connected;
    }

    private void log(String msg) {
        if (trafficListener != null) {
            trafficListener.onTraffic("info", msg, false);
        } else {
            System.out.println("[GatewayClient] " + msg);
        }
    }
}
