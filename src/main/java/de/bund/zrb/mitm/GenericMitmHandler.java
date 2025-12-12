package de.bund.zrb.mitm;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.bund.zrb.TunnelPipeTask;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyStore;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class GenericMitmHandler implements MitmHandler {

    private static final int CONNECT_TIMEOUT_MILLIS = 15000;
    private static final int READ_TIMEOUT_MILLIS = 60000;
    private static final int MAX_HEADER_BYTES = 64 * 1024;
    private static final int MAX_BODY_BYTES = 1_048_576; // 1 MB

    private final SSLContext serverSslContext;
    private final SSLSocketFactory clientSslFactory;
    private final Set<String> mitmHosts;
    private final MitmTrafficListener trafficListener;

    // Rewrite-Konfiguration
    private final boolean rewriteEnabled;
    private final String modelToPatch;          // z.B. "gpt-5-mini"
    private final Double targetTemperature;     // z.B. 1.0; null = Temperatur nicht anfassen

    private final Gson gson = new Gson();

    // Hauptkonstruktor (wird von ProxyControlFrame verwendet)
    public GenericMitmHandler(String keyStorePath,
                              String keyStorePassword,
                              Set<String> mitmHosts,
                              MitmTrafficListener trafficListener,
                              boolean rewriteEnabled,
                              String modelToPatch,
                              Double targetTemperature) {
        try {
            this.serverSslContext = createServerSslContext(keyStorePath, keyStorePassword);
            this.clientSslFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            this.mitmHosts = normalizeHosts(mitmHosts);
            this.trafficListener = trafficListener;
            this.rewriteEnabled = rewriteEnabled;
            this.modelToPatch = modelToPatch != null ? modelToPatch.trim() : null;
            this.targetTemperature = targetTemperature;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize GenericMitmHandler: " + e.getMessage(), e);
        }
    }

    // Convenience-Konstruktor (ohne Rewrite)
    public GenericMitmHandler(String keyStorePath,
                              String keyStorePassword,
                              Set<String> mitmHosts,
                              MitmTrafficListener trafficListener) {
        this(keyStorePath, keyStorePassword, mitmHosts, trafficListener, false, null, null);
    }

    @Override
    public boolean supports(String host, int port) {
        if (host == null || port != 443) return false;
        String h = host.toLowerCase();
        if (mitmHosts.isEmpty()) return true;
        return mitmHosts.contains(h);
    }

    @Override
    public void handleConnect(String host, int port, Socket clientSocket) throws IOException {
        // TLS zum echten Server
        SSLSocket remote = (SSLSocket) clientSslFactory.createSocket();
        remote.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
        remote.setSoTimeout(READ_TIMEOUT_MILLIS);
        remote.startHandshake();
        log("[MITM] Connected TLS to " + host + ":" + port);

        // CONNECT bestätigen
        OutputStream clientOutPlain = clientSocket.getOutputStream();
        clientOutPlain.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes("ISO-8859-1"));
        clientOutPlain.flush();

        // TLS ggü. Client mit unserem Zert
        SSLSocket clientTls = (SSLSocket) serverSslContext
                .getSocketFactory()
                .createSocket(clientSocket, host, port, true);
        clientTls.setUseClientMode(false);
        clientTls.setNeedClientAuth(false);
        clientTls.setSoTimeout(READ_TIMEOUT_MILLIS);
        clientTls.startHandshake();
        log("[MITM] Established TLS with client for " + host + ":" + port);

        // erste Request ggf. patchen
        handleFirstRequest(clientTls, remote);

        // rest tunneln
        startBidirectionalTunnel(clientTls, remote);
    }

    private void handleFirstRequest(SSLSocket clientTls, SSLSocket remote) {
        try {
            InputStream in = clientTls.getInputStream();
            OutputStream out = remote.getOutputStream();

            // Headers lesen
            ByteArrayOutputStream headerBuf = new ByteArrayOutputStream();
            if (!readUntilDoubleCrlf(in, headerBuf, MAX_HEADER_BYTES)) {
                log("[MITM] Failed to read full request headers");
                return;
            }

            byte[] headerBytes = headerBuf.toByteArray();
            String headerStr = new String(headerBytes, "ISO-8859-1");
            logTraffic("client->server headers", headerStr, false);

            int contentLength = parseContentLength(headerStr);

            if (contentLength <= 0 || contentLength > MAX_BODY_BYTES) {
                // nichts zu patchen / zu groß -> direkt weiter
                out.write(headerBytes);
                out.flush();
                if (contentLength > 0) {
                    pipeFixed(in, out, contentLength);
                }
                return;
            }

            // Body lesen
            byte[] bodyBytes = readFixedBytes(in, contentLength);
            if (bodyBytes == null) {
                log("[MITM] Failed to read full request body");
                return;
            }

            String body = new String(bodyBytes, "UTF-8");
            logTraffic("client->server body", body, looksLikeJson(body));

            // nur /v1/chat/completions + Rewrite aktiv + passendes Modell anfassen
            if (!rewriteEnabled || !isChatCompletionsRequest(headerStr)) {
                out.write(headerBytes);
                out.write(bodyBytes);
                out.flush();
                return;
            }

            String patchedBody = patchJsonBodyIfNeeded(body);
            if (patchedBody == null || patchedBody.equals(body)) {
                out.write(headerBytes);
                out.write(bodyBytes);
                out.flush();
                return;
            }

            byte[] patchedBytes = patchedBody.getBytes("UTF-8");
            String fixedHeaders = replaceContentLength(headerStr, patchedBytes.length);

            log("[MITM] Request body modified for model=" + modelToPatch);
            logTraffic("client->server body (modified)", patchedBody, true);

            out.write(fixedHeaders.getBytes("ISO-8859-1"));
            out.write(patchedBytes);
            out.flush();

        } catch (Exception e) {
            log("[MITM] Error in first request handling: " + e.getMessage());
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

    // ---- JSON Patch ----

    private String patchJsonBodyIfNeeded(String body) {
        try {
            if (!looksLikeJson(body) || modelToPatch == null || modelToPatch.isEmpty()) {
                return body;
            }

            JsonElement root = JsonParser.parseString(body);
            if (!root.isJsonObject()) {
                return body;
            }

            JsonObject obj = root.getAsJsonObject();

            if (!obj.has("model")) {
                return body;
            }

            String model = obj.get("model").getAsString();
            if (!modelToPatch.equalsIgnoreCase(model.trim())) {
                return body;
            }

            boolean changed = false;

            if (targetTemperature != null) {
                obj.addProperty("temperature", targetTemperature);
                changed = true;
            }

            if (!changed) {
                return body;
            }

            return gson.toJson(obj);

        } catch (Exception e) {
            log("[MITM] JSON patch failed: " + e.getMessage());
            return body;
        }
    }

    // ---- Helper ----

    private boolean isChatCompletionsRequest(String headers) {
        if (headers == null) return false;
        String lower = headers.toLowerCase();
        int end = lower.indexOf("\r\n");
        String requestLine = (end > 0) ? lower.substring(0, end) : lower;
        return requestLine.startsWith("post /v1/chat/completions ");
    }

    private boolean readUntilDoubleCrlf(InputStream in,
                                        ByteArrayOutputStream buffer,
                                        int maxBytes) throws IOException {
        int state = 0;
        while (buffer.size() < maxBytes) {
            int b = in.read();
            if (b == -1) return false;
            buffer.write(b);

            switch (state) {
                case 0: state = (b == '\r') ? 1 : 0; break;
                case 1: state = (b == '\n') ? 2 : 0; break;
                case 2: state = (b == '\r') ? 3 : 0; break;
                case 3:
                    if (b == '\n') return true;
                    state = 0;
                    break;
            }
        }
        return false;
    }

    private int parseContentLength(String headers) {
        String[] lines = headers.split("\r\n");
        for (String line : lines) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                String name = line.substring(0, colon).trim();
                if ("content-length".equalsIgnoreCase(name)) {
                    String v = line.substring(colon + 1).trim();
                    try {
                        return Integer.parseInt(v);
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
            if (r == -1) return null;
            off += r;
        }
        return data;
    }

    private void pipeFixed(InputStream in, OutputStream out, int length) throws IOException {
        byte[] buf = new byte[8192];
        int remaining = length;
        while (remaining > 0) {
            int r = in.read(buf, 0, Math.min(buf.length, remaining));
            if (r == -1) throw new EOFException("Unexpected EOF");
            out.write(buf, 0, r);
            remaining -= r;
        }
    }

    private boolean looksLikeJson(String text) {
        if (text == null) return false;
        String t = text.trim();
        return (t.startsWith("{") && t.endsWith("}"))
                || (t.startsWith("[") && t.endsWith("]"));
    }

    private String replaceContentLength(String headerBlock, int newLen) {
        int sep = headerBlock.indexOf("\r\n\r\n");
        String headersOnly = (sep >= 0) ? headerBlock.substring(0, sep) : headerBlock;

        String[] lines = headersOnly.split("\r\n");
        StringBuilder sb = new StringBuilder();
        boolean replaced = false;

        for (String line : lines) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                String name = line.substring(0, colon).trim();
                if ("content-length".equalsIgnoreCase(name)) {
                    sb.append("Content-Length: ").append(newLen).append("\r\n");
                    replaced = true;
                    continue;
                }
            }
            sb.append(line).append("\r\n");
        }

        if (!replaced) {
            sb.append("Content-Length: ").append(newLen).append("\r\n");
        }

        sb.append("\r\n");
        return sb.toString();
    }

    private void logTraffic(String direction, String text, boolean isJson) {
        if (trafficListener != null) {
            trafficListener.onTraffic(direction, text, isJson);
        } else {
            if (isJson) {
                log("[MITM][" + direction + "] JSON len=" + (text != null ? text.length() : 0));
            } else {
                log("[MITM][" + direction + "] " + cut(text, 300));
            }
        }
    }

    private String cut(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max) + "...";
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
        if (hosts == null || hosts.isEmpty()) return Collections.emptySet();
        Set<String> out = new HashSet<String>();
        for (String h : hosts) {
            if (h != null) {
                String t = h.trim().toLowerCase();
                if (!t.isEmpty()) out.add(t);
            }
        }
        return out;
    }

    private void closeQuietly(Socket s) {
        if (s == null) return;
        try {
            s.close();
        } catch (IOException ignored) { }
    }
}
