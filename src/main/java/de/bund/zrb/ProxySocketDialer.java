package de.bund.zrb;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

/**
 * Outbound socket dialer that routes connections through a system proxy (WPAD/PAC).
 * Supports fallback to multiple proxy candidates and DIRECT connection.
 */
public final class ProxySocketDialer implements OutboundSocketDialer {

    private final SystemProxyResolver proxyResolver;
    private final int connectTimeoutMillis;
    private final int handshakeTimeoutMillis;
    private final int readTimeoutMillis;
    private final MitmTrafficListener trafficListener;

    public ProxySocketDialer(SystemProxyResolver proxyResolver,
                             int connectTimeoutMillis,
                             int handshakeTimeoutMillis,
                             int readTimeoutMillis,
                             MitmTrafficListener trafficListener) {
        this.proxyResolver = proxyResolver;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.handshakeTimeoutMillis = handshakeTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
        this.trafficListener = trafficListener;
    }

    @Override
    public Socket dial(String targetHost, int targetPort) throws IOException {
        // Determine if HTTPS based on port
        boolean https = (targetPort == 443);

        // Resolve proxy for target
        ProxyInfo proxyInfo = proxyResolver.resolveProxy(targetHost, targetPort, https);
        List<ProxyInfo.ProxyCandidate> candidates = proxyInfo.getCandidates();

        log("Dialing " + targetHost + ":" + targetPort + " via proxy chain: " + proxyInfo);

        IOException lastException = null;

        // Try each candidate in order
        for (int i = 0; i < candidates.size(); i++) {
            ProxyInfo.ProxyCandidate candidate = candidates.get(i);

            try {
                if (candidate.isDirect()) {
                    log("Attempting DIRECT connection to " + targetHost + ":" + targetPort);
                    return dialDirect(targetHost, targetPort);
                } else if (candidate.getType() == ProxyInfo.ProxyType.HTTP) {
                    log("Attempting HTTP CONNECT via " + candidate.getHost() + ":" + candidate.getPort() + " to " + targetHost + ":" + targetPort);
                    return dialViaHttpProxy(candidate.getHost(), candidate.getPort(), targetHost, targetPort);
                } else if (candidate.getType() == ProxyInfo.ProxyType.SOCKS) {
                    log("SOCKS proxy not supported yet: " + candidate);
                    throw new IOException("SOCKS proxy not supported: " + candidate);
                } else {
                    throw new IOException("Unknown proxy type: " + candidate.getType());
                }
            } catch (IOException e) {
                lastException = e;
                log("Proxy candidate " + candidate + " failed: " + e.getMessage());
                // Try next candidate
            }
        }

        // All candidates failed
        throw new IOException("All proxy candidates failed for " + targetHost + ":" + targetPort
                + " (tried " + candidates.size() + " candidate(s)). Last error: "
                + (lastException != null ? lastException.getMessage() : "unknown"), lastException);
    }

    private Socket dialDirect(String targetHost, int targetPort) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(targetHost, targetPort), connectTimeoutMillis);
        socket.setSoTimeout(readTimeoutMillis);
        return socket;
    }

    private Socket dialViaHttpProxy(String proxyHost, int proxyPort, String targetHost, int targetPort) throws IOException {
        // Step 1: Connect to proxy
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(proxyHost, proxyPort), connectTimeoutMillis);
            socket.setSoTimeout(handshakeTimeoutMillis);

            // Step 2: Send CONNECT request
            Writer writer = new OutputStreamWriter(socket.getOutputStream(), "UTF-8");
            writer.write("CONNECT " + targetHost + ":" + targetPort + " HTTP/1.1\r\n");
            writer.write("Host: " + targetHost + ":" + targetPort + "\r\n");
            writer.write("Proxy-Connection: Keep-Alive\r\n");
            writer.write("\r\n");
            writer.flush();

            // Step 3: Read response
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            String statusLine = reader.readLine();
            if (statusLine == null) {
                throw new IOException("Proxy " + proxyHost + ":" + proxyPort + " closed connection during CONNECT handshake");
            }

            // Parse status code
            String[] parts = statusLine.split(" ", 3);
            if (parts.length < 2) {
                throw new IOException("Invalid HTTP response from proxy " + proxyHost + ":" + proxyPort + ": " + statusLine);
            }

            int statusCode;
            try {
                statusCode = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                throw new IOException("Invalid HTTP status code from proxy " + proxyHost + ":" + proxyPort + ": " + statusLine);
            }

            // Read headers until empty line
            String headerLine;
            while ((headerLine = reader.readLine()) != null) {
                if (headerLine.trim().isEmpty()) {
                    break;
                }
            }

            // Step 4: Check status code
            if (statusCode != 200) {
                socket.close();
                throw new IOException("Proxy " + proxyHost + ":" + proxyPort + " rejected CONNECT to " + targetHost + ":" + targetPort
                        + " with status " + statusCode + " (" + statusLine + ")");
            }

            // Success - tunnel established, switch to read timeout
            socket.setSoTimeout(readTimeoutMillis);
            log("HTTP CONNECT tunnel established via " + proxyHost + ":" + proxyPort + " to " + targetHost + ":" + targetPort);
            return socket;

        } catch (IOException e) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // ignore secondary failure
            }
            throw e;
        }
    }

    private void log(String msg) {
        if (trafficListener != null) {
            trafficListener.onTraffic("proxy-dialer", msg, false);
        } else {
            System.out.println("[ProxySocketDialer] " + msg);
        }
    }
}

