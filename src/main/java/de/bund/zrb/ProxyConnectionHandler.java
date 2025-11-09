package de.bund.zrb;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;

public class ProxyConnectionHandler {

    private static final int CONNECT_TIMEOUT_MILLIS = 15000;
    private static final int READ_TIMEOUT_MILLIS = 60000;

    public void handle(Socket clientSocket) throws IOException {
        clientSocket.setSoTimeout(READ_TIMEOUT_MILLIS);

        InputStream clientIn = clientSocket.getInputStream();
        OutputStream clientOut = clientSocket.getOutputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(clientIn, "ISO-8859-1"));

        String requestLine = reader.readLine();
        if (requestLine == null || requestLine.isEmpty()) {
            System.out.println("[Proxy] Empty request line, close connection");
            return;
        }

        String headerLine;
        StringBuilder rawHeaders = new StringBuilder();
        while ((headerLine = reader.readLine()) != null && headerLine.length() > 0) {
            rawHeaders.append(headerLine).append("\r\n");
        }

        System.out.println("[Proxy] Request line: " + requestLine);

        String[] parts = requestLine.split(" ");
        if (parts.length < 3) {
            writeBadRequest(clientOut);
            System.out.println("[Proxy] Invalid request line");
            return;
        }

        String method = parts[0];
        String target = parts[1];
        String httpVersion = parts[2];

        if ("CONNECT".equalsIgnoreCase(method)) {
            System.out.println("[Proxy] CONNECT to " + target);
            handleConnect(target, clientIn, clientOut);
        } else {
            System.out.println("[Proxy] HTTP " + method + " " + target);
            handleHttpRequest(method, target, httpVersion, rawHeaders.toString(), reader, clientOut);
        }
    }

    private void handleConnect(String target, InputStream clientIn, OutputStream clientOut) throws IOException {
        String[] hostPort = target.split(":");
        if (hostPort.length != 2) {
            writeBadRequest(clientOut);
            System.out.println("[Proxy] Invalid CONNECT target: " + target);
            return;
        }

        String host = hostPort[0];
        int port = parsePort(hostPort[1], 443);

        Socket remoteSocket = new Socket();
        System.out.println("[Proxy] Opening tunnel to " + host + ":" + port);
        remoteSocket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
        remoteSocket.setSoTimeout(READ_TIMEOUT_MILLIS);

        OutputStream remoteOut = remoteSocket.getOutputStream();
        InputStream remoteIn = remoteSocket.getInputStream();

        // Signal successful tunnel
        clientOut.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes("ISO-8859-1"));
        clientOut.flush();
        System.out.println("[Proxy] Tunnel established " + host + ":" + port);

        startTunnel(clientIn, clientOut, remoteIn, remoteOut);
    }

    private void handleHttpRequest(String method,
                                   String target,
                                   String httpVersion,
                                   String rawHeaders,
                                   BufferedReader clientReader,
                                   OutputStream clientOut) throws IOException {
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
            writeBadRequest(clientOut);
            System.out.println("[Proxy] Missing Host header for: " + target);
            return;
        }

        System.out.println("[Proxy] Forward " + method + " " + host + ":" + port + path);

        Socket remoteSocket = new Socket();
        remoteSocket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
        remoteSocket.setSoTimeout(READ_TIMEOUT_MILLIS);

        OutputStream remoteOut = remoteSocket.getOutputStream();
        InputStream remoteIn = remoteSocket.getInputStream();

        String requestLine = method + " " + path + " " + httpVersion + "\r\n";
        remoteOut.write(requestLine.getBytes("ISO-8859-1"));
        remoteOut.write(rawHeaders.getBytes("ISO-8859-1"));
        remoteOut.write("\r\n".getBytes("ISO-8859-1"));
        remoteOut.flush();

        pipe(remoteIn, clientOut);

        closeQuietly(remoteSocket);
        System.out.println("[Proxy] Completed " + method + " " + host + ":" + port + path);
    }

    private void startTunnel(final InputStream clientIn,
                             final OutputStream clientOut,
                             final InputStream remoteIn,
                             final OutputStream remoteOut) {

        Thread clientToRemote = new Thread(new TunnelPipeTask(clientIn, remoteOut));
        Thread remoteToClient = new Thread(new TunnelPipeTask(remoteIn, clientOut));

        clientToRemote.setDaemon(true);
        remoteToClient.setDaemon(true);

        clientToRemote.start();
        remoteToClient.start();
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
