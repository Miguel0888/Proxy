package de.bund.zrb;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyStore;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class GenericMitmHandler implements MitmHandler {

    private static final int CONNECT_TIMEOUT_MILLIS = 15000;
    private static final int READ_TIMEOUT_MILLIS = 60000;
    private static final int MAX_HEADER_BYTES = 32 * 1024;
    private static final int MAX_BODY_BYTES = 1024 * 1024; // 1 MB

    private final SSLContext serverSslContext;
    private final SSLSocketFactory clientSslFactory;
    private final Set<String> mitmHosts;
    private final MitmTrafficListener trafficListener;

    private final boolean rewriteEnabled;
    private final String targetModel;
    private final Double targetTemperature;

    public GenericMitmHandler(String keyStorePath,
                              String keyStorePassword,
                              Set<String> mitmHosts,
                              MitmTrafficListener trafficListener,
                              boolean rewriteEnabled,
                              String targetModel,
                              Double targetTemperature) {
        try {
            this.serverSslContext = createServerSslContext(keyStorePath, keyStorePassword);
            this.clientSslFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            this.mitmHosts = normalizeHosts(mitmHosts);
            this.trafficListener = trafficListener;

            this.rewriteEnabled = rewriteEnabled;
            if (targetModel != null) {
                String trimmed = targetModel.trim();
                this.targetModel = trimmed.length() > 0 ? trimmed : null;
            } else {
                this.targetModel = null;
            }
            this.targetTemperature = targetTemperature;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize GenericMitmHandler: " + e.getMessage(), e);
        }
    }

    // Backwards-compatible convenience constructors
    public GenericMitmHandler(String keyStorePath,
                              String keyStorePassword,
                              Set<String> mitmHosts,
                              MitmTrafficListener trafficListener) {
        this(keyStorePath, keyStorePassword, mitmHosts, trafficListener, false, null, null);
    }

    public GenericMitmHandler(String keyStorePath, String keyStorePassword) {
        this(keyStorePath, keyStorePassword, Collections.<String>emptySet(), null, false, null, null);
    }

    @Override
    public boolean supports(String host, int port) {
        if (host == null || port != 443) {
            return false;
        }
        String h = host.toLowerCase();
        if (mitmHosts.isEmpty()) {
            return true;
        }
        return mitmHosts.contains(h);
    }

    @Override
    public void handleConnect(String host, int port, Socket clientSocket) throws IOException {
        // 1) TLS to real server
        SSLSocket remote = (SSLSocket) clientSslFactory.createSocket();
        remote.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
        remote.setSoTimeout(READ_TIMEOUT_MILLIS);
        remote.startHandshake();
        log("[MITM] Connected TLS to " + host + ":" + port);

        // 2) Confirm CONNECT to client
        OutputStream clientOutPlain = clientSocket.getOutputStream();
        clientOutPlain.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes("ISO-8859-1"));
        clientOutPlain.flush();

        // 3) TLS towards client with our cert
        SSLSocket clientTls = (SSLSocket) serverSslContext
                .getSocketFactory()
                .createSocket(clientSocket, host, port, true);
        clientTls.setUseClientMode(false);
        clientTls.setNeedClientAuth(false);
        clientTls.setSoTimeout(READ_TIMEOUT_MILLIS);
        clientTls.startHandshake();
        log("[MITM] Established TLS with client for " + host + ":" + port);

        // 4) Handle first request (optional rewrite) then tunnel
        boolean initialHandled = handleInitialClientRequestWithOptionalRewrite(clientTls, remote);

        // 5) Tunnel rest of traffic
        if (initialHandled) {
            startBidirectionalTunnel(clientTls, remote);
        } else {
            startBidirectionalTunnel(clientTls, remote);
        }
    }

    // Read one HTTP request, optionally rewrite JSON, forward to remote.
    private boolean handleInitialClientRequestWithOptionalRewrite(SSLSocket clientTls,
                                                                  SSLSocket remote) {
        try {
            InputStream in = clientTls.getInputStream();
            OutputStream out = remote.getOutputStream();

            // 1) Read headers (incl. request line)
            ByteArrayOutputStream headerBuffer = new ByteArrayOutputStream();
            if (!readUntilDoubleCrlf(in, headerBuffer, MAX_HEADER_BYTES)) {
                log("[MITM] Failed to read request headers");
                return false;
            }

            byte[] headerBytes = headerBuffer.toByteArray();
            if (headerBytes.length < 4) {
                log("[MITM] Header too short");
                return false;
            }

            int headerLen = headerBytes.length - 4; // strip trailing \r\n\r\n
            String headers = new String(headerBytes, 0, headerLen, "UTF-8");

            int contentLength = parseContentLength(headers);
            boolean hasBody = contentLength > 0 && contentLength <= MAX_BODY_BYTES;

            String body = "";
            if (hasBody) {
                byte[] bodyBytes = readFixedBytes(in, contentLength);
                if (bodyBytes == null) {
                    log("[MITM] Failed to read full request body");
                    return false;
                }
                body = new String(bodyBytes, "UTF-8");
            }

            // Logging original
            logTraffic("client->server headers", headers, false);
            if (hasBody) {
                logTraffic("client->server body", body, looksLikeJson(body));
            }

            String headersToSend = headers;
            String bodyToSend = body;

            // 2) Optional rewrite (only if enabled & conditions match)
            if (rewriteEnabled && hasBody && isChatCompletionsRequest(headers) && looksLikeJson(body)) {
                ModifiedRequest modified = modifyRequest(headers, body);
                if (modified.modified) {
                    headersToSend = modified.headers;
                    bodyToSend = modified.body;
                    logTraffic("client->server body (modified)",
                            bodyToSend, looksLikeJson(bodyToSend));
                }
            }

            // 3) Forward
            ByteArrayOutputStream forward = new ByteArrayOutputStream();
            forward.write(headersToSend.getBytes("UTF-8"));
            forward.write("\r\n\r\n".getBytes("UTF-8"));
            if (hasBody && bodyToSend != null && bodyToSend.length() > 0) {
                forward.write(bodyToSend.getBytes("UTF-8"));
            }

            out.write(forward.toByteArray());
            out.flush();

            return true;
        } catch (Exception e) {
            log("[MITM] Error during initial request handling: " + e.getMessage());
            return false;
        }
    }

    private void startBidirectionalTunnel(Socket clientTls, Socket remote) {
        Thread t1 = new Thread(new TunnelPipeTask(clientTls, remote), "mitm-c2s");
        Thread t2 = new Thread(new TunnelPipeTask(remote, clientTls), "mitm-s2c");
        t1.setDaemon(true);
        t2.setDaemon(true);
        t1.start();
        t2.start();
        try {
            t1.join();
            t2.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            closeQuietly(clientTls);
            closeQuietly(remote);
        }
    }

    // Read bytes until "\r\n\r\n" or limit.
    private boolean readUntilDoubleCrlf(InputStream in,
                                        ByteArrayOutputStream buffer,
                                        int maxBytes) throws IOException {
        int state = 0;
        while (buffer.size() < maxBytes) {
            int b = in.read();
            if (b == -1) {
                return false;
            }
            buffer.write(b);

            switch (state) {
                case 0:
                    state = (b == '\r') ? 1 : 0;
                    break;
                case 1:
                    state = (b == '\n') ? 2 : 0;
                    break;
                case 2:
                    state = (b == '\r') ? 3 : 0;
                    break;
                case 3:
                    if (b == '\n') {
                        return true;
                    } else {
                        state = 0;
                    }
                    break;
                default:
                    state = 0;
            }
        }
        return false;
    }

    private int parseContentLength(String headers) {
        String[] lines = headers.split("\r\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int colon = line.indexOf(':');
            if (colon > 0) {
                String name = line.substring(0, colon).trim();
                if ("content-length".equalsIgnoreCase(name)) {
                    String value = line.substring(colon + 1).trim();
                    try {
                        return Integer.parseInt(value);
                    } catch (NumberFormatException ignored) {
                        return -1;
                    }
                }
            }
        }
        return -1;
    }

    private byte[] readFixedBytes(InputStream in, int length) throws IOException {
        byte[] data = new byte[length];
        int off = 0;
        while (off < length) {
            int r = in.read(data, off, length - off);
            if (r == -1) {
                return null;
            }
            off += r;
        }
        return data;
    }

    private boolean isChatCompletionsRequest(String headers) {
        if (headers == null) {
            return false;
        }
        String lower = headers.toLowerCase();
        return lower.startsWith("post /v1/chat/completions ")
                || lower.contains("\npost /v1/chat/completions ");
    }

    // Core rewrite logic based on current configuration.
    private ModifiedRequest modifyRequest(String headers, String body) {
        boolean modified = false;

        try {
            com.google.gson.Gson gson = new com.google.gson.GsonBuilder().create();
            com.google.gson.JsonElement root = gson.fromJson(body, com.google.gson.JsonElement.class);
            if (root == null || !root.isJsonObject()) {
                return new ModifiedRequest(headers, body, false);
            }

            com.google.gson.JsonObject obj = root.getAsJsonObject();

            // Rewrite model if configured
            if (targetModel != null && obj.has("model")) {
                String originalModel = obj.get("model").getAsString();
                if (!targetModel.equals(originalModel)) {
                    obj.addProperty("model", targetModel);
                    modified = true;
                    log("[MITM] Rewrote model: " + originalModel + " -> " + targetModel);
                }
            }

            // Rewrite temperature if configured
            if (targetTemperature != null) {
                double desired = targetTemperature.doubleValue();
                boolean change = true;

                if (obj.has("temperature")) {
                    try {
                        double existing = obj.get("temperature").getAsDouble();
                        if (Double.compare(existing, desired) == 0) {
                            change = false;
                        }
                    } catch (Exception ignored) {
                        // Ignore malformed value, will overwrite below
                    }
                }

                if (change) {
                    obj.addProperty("temperature", desired);
                    modified = true;
                    log("[MITM] Set temperature -> " + desired);
                }
            }

            if (!modified) {
                return new ModifiedRequest(headers, body, false);
            }

            String newBody = gson.toJson(obj);
            int newLen = newBody.getBytes("UTF-8").length;
            String newHeaders = replaceContentLength(headers, newLen);

            return new ModifiedRequest(newHeaders, newBody, true);

        } catch (Exception e) {
            log("[MITM] Failed to modify JSON: " + e.getMessage());
            return new ModifiedRequest(headers, body, false);
        }
    }

    private String replaceContentLength(String headers, int newLength) {
        String[] lines = headers.split("\r\n");
        StringBuilder sb = new StringBuilder();
        boolean replaced = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.length() == 0) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon > 0) {
                String name = line.substring(0, colon).trim();
                if ("content-length".equalsIgnoreCase(name)) {
                    sb.append("Content-Length: ").append(newLength).append("\r\n");
                    replaced = true;
                    continue;
                }
            }
            sb.append(line).append("\r\n");
        }

        if (!replaced) {
            sb.append("Content-Length: ").append(newLength).append("\r\n");
        }

        return sb.toString();
    }

    private boolean looksLikeJson(String text) {
        if (text == null) {
            return false;
        }
        String trimmed = text.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }

    private void logTraffic(String direction, String text, boolean isJson) {
        if (trafficListener != null) {
            trafficListener.onTraffic(direction, text, isJson);
        } else {
            if (isJson) {
                log("[MITM][" + direction + "] JSON length=" + text.length());
            } else {
                log("[MITM][" + direction + "] " + cut(text, 300));
            }
        }
    }

    private String cut(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "...";
    }

    private void log(String msg) {
        System.out.println(msg);
    }

    private SSLContext createServerSslContext(String keyStorePath, String keyStorePassword) throws Exception {
        KeyStore ks = KeyStore.getInstance("JKS");
        InputStream in = new FileInputStream(keyStorePath);
        try {
            ks.load(in, keyStorePassword.toCharArray());
        } finally {
            in.close();
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, keyStorePassword.toCharArray());

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);
        return ctx;
    }

    private Set<String> normalizeHosts(Set<String> hosts) {
        if (hosts == null || hosts.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> result = new HashSet<String>();
        for (String h : hosts) {
            if (h != null) {
                String trimmed = h.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed.toLowerCase());
                }
            }
        }
        return result;
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

    // Simple holder for modified request
    private static final class ModifiedRequest {
        final String headers;
        final String body;
        final boolean modified;

        ModifiedRequest(String headers, String body, boolean modified) {
            this.headers = headers;
            this.body = body;
            this.modified = modified;
        }
    }
}
