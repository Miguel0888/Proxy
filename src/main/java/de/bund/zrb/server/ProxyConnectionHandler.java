package de.bund.zrb.server;

import de.bund.zrb.*;
import de.bund.zrb.common.ProxyView;
import de.bund.zrb.mitm.MitmHandler;
import de.bund.zrb.server.gateway.GatewaySessionManager;
import de.bund.zrb.server.gateway.SocketGatewaySession;

import java.io.*;
import java.net.Socket;

public class ProxyConnectionHandler {

    private static final int CONNECT_TIMEOUT_MILLIS = 15000;
    private static final int READ_TIMEOUT_MILLIS = 60000;

    private final MitmHandler mitmHandler;
    private final OutboundConnectionProvider outboundConnectionProvider;
    private final GatewaySessionManager gatewaySessionManager;
    private final String expectedPasskey;
    private final ProxyView view;

    public ProxyConnectionHandler() {
        this(null, createDefaultConnectionProvider(), null, null, null);
    }

    public ProxyConnectionHandler(MitmHandler mitmHandler) {
        this(mitmHandler, createDefaultConnectionProvider(), null, null, null);
    }

    public ProxyConnectionHandler(MitmHandler mitmHandler,
                                  OutboundConnectionProvider outboundConnectionProvider) {
        this(mitmHandler, outboundConnectionProvider, null, null, null);
    }

    public ProxyConnectionHandler(MitmHandler mitmHandler,
                                  OutboundConnectionProvider outboundConnectionProvider,
                                  GatewaySessionManager gatewaySessionManager,
                                  String expectedPasskey,
                                  ProxyView view) {
        this.mitmHandler = mitmHandler;
        this.outboundConnectionProvider = outboundConnectionProvider;
        this.gatewaySessionManager = gatewaySessionManager;
        this.expectedPasskey = expectedPasskey != null ? expectedPasskey.trim() : "";
        this.view = view;
    }

    private static OutboundConnectionProvider createDefaultConnectionProvider() {
        return new DirectConnectionProvider(CONNECT_TIMEOUT_MILLIS, READ_TIMEOUT_MILLIS);
    }

    public void handle(Socket clientSocket) throws IOException {
        try {
            clientSocket.setSoTimeout(READ_TIMEOUT_MILLIS);

            InputStream clientIn = clientSocket.getInputStream();
            OutputStream clientOut = clientSocket.getOutputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientIn, "ISO-8859-1"));

            System.out.println("[Proxy] Handling new connection from " + clientSocket.getRemoteSocketAddress());

            String firstLine = reader.readLine();
            if (firstLine == null || firstLine.isEmpty()) {
                System.out.println("[Proxy] Empty first line, close connection");
                return;
            }

            System.out.println("[Proxy] First line from " + clientSocket.getRemoteSocketAddress() + ": '" + firstLine + "'");

            // Ein-Port-Gateway: HELLO-Handshake direkt hier erkennen
            if (firstLine.startsWith("HELLO") && gatewaySessionManager != null) {
                handleGatewayHello(firstLine, clientSocket, reader);
                return;
            }

            String requestLine = firstLine;

            String headerLine;
            StringBuilder rawHeaders = new StringBuilder();
            while ((headerLine = reader.readLine()) != null && headerLine.length() > 0) {
                rawHeaders.append(headerLine).append("\r\n");
            }

            System.out.println("[Proxy] Request line: " + requestLine);

            String[] parts = requestLine.split(" ");
            if (parts.length < 3) {
                System.out.println("[Proxy] Invalid request line");
                writeBadRequest(clientOut);
                return;
            }

            String method = parts[0];
            String target = parts[1];
            String httpVersion = parts[2];

            if ("CONNECT".equalsIgnoreCase(method)) {
                String[] hostPort = target.split(":");
                if (hostPort.length == 2) {
                    String host = hostPort[0];
                    int port = parsePort(hostPort[1], 443);

                    if (mitmHandler != null && mitmHandler.supports(host, port)) {
                        System.out.println("[Proxy] MITM handler for " + host + ":" + port);
                        mitmHandler.handleConnect(host, port, clientSocket);
                        return;
                    }
                }

                // Fallback: normal CONNECT (tunnel)
                handleConnect(target, clientSocket);
                return;
            } else {
                System.out.println("[Proxy] HTTP " + method + " " + target);
                handleHttpRequest(method, target, httpVersion, rawHeaders.toString(), clientSocket);
            }
        } finally {
            closeQuietly(clientSocket);
        }
    }

    private void handleGatewayHello(String helloLine,
                                    Socket socket,
                                    BufferedReader reader) throws IOException {
        String rest = helloLine.substring("HELLO".length()).trim();
        String passkey = rest.isEmpty() ? null : rest.split(" ")[0];

        System.out.println("[Proxy] HELLO from " + socket.getRemoteSocketAddress() + " with passkey='" + passkey + "'");
        System.out.println("[Proxy] Expected passkey on server is '" + expectedPasskey + "'");

        if (!expectedPasskey.isEmpty()) {
            if (passkey == null || !expectedPasskey.equals(passkey)) {
                System.out.println("[Proxy] Gateway client rejected: invalid passkey from " + socket.getRemoteSocketAddress());
                closeQuietly(socket);
                if (view != null) {
                    view.updateGatewayClientStatus("Gateway HELLO rejected", false);
                }
                return;
            }
        }

        System.out.println("[Proxy] Gateway client accepted from " + socket.getRemoteSocketAddress());

        // HELLO akzeptiert -> explizit OK an den Client senden
        Writer writer = new OutputStreamWriter(socket.getOutputStream(), "ISO-8859-1");
        writer.write("OK\r\n");
        writer.flush();
        System.out.println("[Proxy] Sent OK to gateway client " + socket.getRemoteSocketAddress());

        if (gatewaySessionManager != null) {
            SocketGatewaySession session = new SocketGatewaySession(
                    "gateway-client",
                    String.valueOf(socket.getRemoteSocketAddress()),
                    socket,
                    gatewaySessionManager,
                    null,
                    reader,
                    view
            );
            gatewaySessionManager.setActiveSession(session);
            session.run();
            gatewaySessionManager.clearActiveSession(session);
        }
    }

    private void handleConnect(String target, Socket clientSocket) throws IOException {
        String[] hostPort = target.split(":");
        if (hostPort.length != 2) {
            System.out.println("[Proxy] Invalid CONNECT target: " + target);
            writeBadRequest(clientSocket.getOutputStream());
            return;
        }

        String host = hostPort[0];
        int port = parsePort(hostPort[1], 443);

        Socket remoteSocket = null;
        try {
            System.out.println("[Proxy] Opening tunnel to " + host + ":" + port);
            try {
                remoteSocket = outboundConnectionProvider.openConnectTunnel(host, port);
            } catch (IOException e) {
                String msg = e.getMessage();
                if (msg != null && (msg.contains("No active gateway session available")
                        || msg.contains("Gateway did not confirm tunnel"))) {
                    System.out.println("[Proxy] Reject CONNECT because " + msg);
                    writeServiceUnavailable(clientSocket.getOutputStream());
                    return;
                }
                throw e;
            }

            OutputStream clientOut = clientSocket.getOutputStream();
            clientOut.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes("ISO-8859-1"));
            clientOut.flush();
            System.out.println("[Proxy] Tunnel established " + host + ":" + port);

            startTunnelBlocking(clientSocket, remoteSocket);
            System.out.println("[Proxy] Tunnel closed " + host + ":" + port);
        } finally {
            closeQuietly(remoteSocket);
        }
    }

    private void handleHttpRequest(String method,
                                   String target,
                                   String httpVersion,
                                   String rawHeaders,
                                   Socket clientSocket) throws IOException {

        String host;
        int port;
        String path = target;

        if (target.startsWith("http://") || target.startsWith("https://")) {
            boolean https = target.startsWith("https://");
            int schemeEnd = target.indexOf("://") + 3;
            int pathIndex = target.indexOf('/', schemeEnd);
            String hostPart = pathIndex > 0 ? target.substring(schemeEnd, pathIndex) : target.substring(schemeEnd);

            int colonIndex = hostPart.indexOf(':');
            if (colonIndex > 0) {
                host = hostPart.substring(0, colonIndex);
                port = parsePort(hostPart.substring(colonIndex + 1), https ? 443 : 80);
            } else {
                host = hostPart;
                port = https ? 443 : 80;
            }

            path = pathIndex > 0 ? target.substring(pathIndex) : "/";
        } else {
            host = extractHostFromHeaders(rawHeaders);
            port = 80;
        }

        if (host == null) {
            System.out.println("[Proxy] Missing Host header for: " + target);
            writeBadRequest(clientSocket.getOutputStream());
            return;
        }

        System.out.println("[Proxy] Forward " + method + " " + host + ":" + port + path);

        Socket remoteSocket = null;
        try {
            try {
                remoteSocket = outboundConnectionProvider.openHttpConnection(host, port);
            } catch (IOException e) {
                String msg = e.getMessage();
                if (msg != null && msg.contains("No active gateway session available")) {
                    System.out.println("[Proxy] Reject HTTP request because no active gateway session is available");
                    writeServiceUnavailable(clientSocket.getOutputStream());
                    return;
                }
                throw e;
            }

            OutputStream remoteOut = remoteSocket.getOutputStream();
            InputStream remoteIn = remoteSocket.getInputStream();
            OutputStream clientOut = clientSocket.getOutputStream();

            String requestLine = method + " " + path + " " + httpVersion + "\r\n";
            remoteOut.write(requestLine.getBytes("ISO-8859-1"));
            remoteOut.write(rawHeaders.getBytes("ISO-8859-1"));
            remoteOut.write("\r\n".getBytes("ISO-8859-1"));
            remoteOut.flush();

            pipe(remoteIn, clientOut);

            System.out.println("[Proxy] Completed " + method + " " + host + ":" + port + path);
        } finally {
            closeQuietly(remoteSocket);
        }
    }

    private void startTunnelBlocking(Socket clientSocket, Socket remoteSocket) {
        TunnelPipeTask clientToRemote = new TunnelPipeTask(clientSocket, remoteSocket);
        TunnelPipeTask remoteToClient = new TunnelPipeTask(remoteSocket, clientSocket);

        Thread t1 = new Thread(clientToRemote);
        Thread t2 = new Thread(remoteToClient);

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

    private void pipe(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
            out.flush();
        }
    }

    private String extractHostFromHeaders(String rawHeaders) {
        String[] lines = rawHeaders.split("\r\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.toLowerCase().startsWith("host:")) {
                String value = line.substring(5).trim();
                if (value.length() == 0) {
                    continue;
                }
                int colonIndex = value.indexOf(':');
                if (colonIndex > 0) {
                    return value.substring(0, colonIndex);
                }
                return value;
            }
        }
        return null;
    }

    private int parsePort(String value, int defaultPort) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultPort;
        }
    }

    private void writeBadRequest(OutputStream out) throws IOException {
        String response = "HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\n\r\n";
        out.write(response.getBytes("ISO-8859-1"));
        out.flush();
    }

    private void writeServiceUnavailable(OutputStream out) throws IOException {
        String body = "No active gateway client connected";
        String response = "HTTP/1.1 503 Service Unavailable\r\n" +
                "Content-Type: text/plain; charset=ISO-8859-1\r\n" +
                "Content-Length: " + body.length() + "\r\n\r\n" +
                body;
        out.write(response.getBytes("ISO-8859-1"));
        out.flush();
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

