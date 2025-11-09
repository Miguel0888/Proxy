package de.bund.zrb;

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
    private static final int MAX_HEADER_BYTES = 32 * 1024;
    private static final int MAX_BODY_BYTES = 1024 * 1024; // 1 MB für Analyse/Rewrite

    private final SSLContext serverSslContext;
    private final SSLSocketFactory clientSslFactory;
    private final Set<String> mitmHosts;
    private final MitmTrafficListener trafficListener;

    public GenericMitmHandler(String keyStorePath,
                              String keyStorePassword,
                              Set<String> mitmHosts,
                              MitmTrafficListener trafficListener) {
        try {
            this.serverSslContext = createServerSslContext(keyStorePath, keyStorePassword);
            this.clientSslFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            this.mitmHosts = normalizeHosts(mitmHosts);
            this.trafficListener = trafficListener;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize GenericMitmHandler: " + e.getMessage(), e);
        }
    }

    public GenericMitmHandler(String keyStorePath, String keyStorePassword) {
        this(keyStorePath, keyStorePassword, Collections.<String>emptySet(), null);
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
        // 1) TLS zum echten Server
        SSLSocket remote = (SSLSocket) clientSslFactory.createSocket();
        remote.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
        remote.setSoTimeout(READ_TIMEOUT_MILLIS);
        remote.startHandshake();
        log("[MITM] Connected TLS to " + host + ":" + port);

        // 2) CONNECT zum Client bestätigen
        OutputStream clientOutPlain = clientSocket.getOutputStream();
        clientOutPlain.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes("ISO-8859-1"));
        clientOutPlain.flush();

        // 3) TLS ggü. Client mit unserem Zert
        SSLSocket clientTls = (SSLSocket) serverSslContext
                .getSocketFactory()
                .createSocket(clientSocket, host, port, true);
        clientTls.setUseClientMode(false);
        clientTls.setNeedClientAuth(false);
        clientTls.setSoTimeout(READ_TIMEOUT_MILLIS);
        clientTls.startHandshake();
        log("[MITM] Established TLS with client for " + host + ":" + port);

        // 4) Erste Anfrage vom Client gezielt lesen, ggf. rewriten und weiterleiten
        boolean initialHandled = handleInitialClientRequestWithOptionalRewrite(clientTls, remote);

        // 5) Danach restlichen Traffic einfach tunneln
        if (initialHandled) {
            startBidirectionalTunnel(clientTls, remote);
        } else {
            // Falls Analyse fehlschlug: sicherheitshalber auch einfach tunneln
            startBidirectionalTunnel(clientTls, remote);
        }
    }

    // Lese genau eine HTTP-Request vom Client, rewritte bei Bedarf und schicke sie an remote.
    private boolean handleInitialClientRequestWithOptionalRewrite(SSLSocket clientTls,
                                                                  SSLSocket remote) {
        try {
            InputStream in = clientTls.getInputStream();
            OutputStream out = remote.getOutputStream();

            // 1) Headers lesen
            ByteArrayOutputStream headerBuffer = new ByteArrayOutputStream();
            if (!readUntilDoubleCrlf(in, headerBuffer, MAX_HEADER_BYTES)) {
                log("[MITM] Failed to read request headers");
                return false;
            }
            String headers = headerBuffer.toString("UTF-8");

            // 2) Content-Length ermitteln (nur dann können wir sicher Body lesen)
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

            // 3) Logging in UI
            logTraffic("client->server headers", headers, false);
            if (hasBody) {
                boolean isJson = looksLikeJson(body);
                logTraffic("client->server body", body, isJson);
            }

            // 4) Optionaler Rewrite nur für OpenAI chat.completions + gpt-5-mini
            String newBody = body;
            String newHeaders = headers;

            if (hasBody && isChatCompletionsRequest(headers)) {
                String rewritten = rewriteOpenAiJsonIfNeeded(body);
                if (!rewritten.equals(body)) {
                    newBody = rewritten;
                    byte[] nb = newBody.getBytes("UTF-8");
                    newHeaders = replaceContentLength(headers, nb.length);
                    log("[MITM] Modified OpenAI request body and Content-Length");
                    // Logging der modifizierten Variante
                    logTraffic("client->server body (modified)", newBody, looksLikeJson(newBody));
                }
            }

            // 5) Zusammensetzen und an remote schicken
            ByteArrayOutputStream forward = new ByteArrayOutputStream();
            forward.write(newHeaders.getBytes("UTF-8"));
            forward.write("\r\n\r\n".getBytes("UTF-8"));
            if (hasBody) {
                forward.write(newBody.getBytes("UTF-8"));
            }

            byte[] outBytes = forward.toByteArray();
            out.write(outBytes);
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

    // Lies Bytes bis "\r\n\r\n" oder Limit erreicht
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

            // State-Maschine für \r\n\r\n
            switch (state) {
                case 0: state = (b == '\r') ? 1 : 0; break;
                case 1: state = (b == '\n') ? 2 : 0; break;
                case 2: state = (b == '\r') ? 3 : 0; break;
                case 3:
                    if (b == '\n') {
                        return true; // Ende Header
                    } else {
                        state = 0;
                    }
                    break;
                default: state = 0;
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
        String lower = headers.toLowerCase();
        // Sehr simple Prüfung auf Request-Line
        return lower.startsWith("post /v1/chat/completions ")
                || lower.contains("\npost /v1/chat/completions ");
    }

    private String rewriteOpenAiJsonIfNeeded(String body) {
        try {
            if (!looksLikeJson(body)) {
                return body;
            }

            com.google.gson.Gson gson = new com.google.gson.GsonBuilder().create();
            com.google.gson.JsonElement el = gson.fromJson(body, com.google.gson.JsonElement.class);
            if (!el.isJsonObject()) {
                return body;
            }

            com.google.gson.JsonObject obj = el.getAsJsonObject();

            if (!obj.has("model")) {
                return body;
            }
            String model = obj.get("model").getAsString();
            String m = model.toLowerCase();
            if (!m.startsWith("gpt-5-mini")) {
                return body;
            }

            if (obj.has("temperature")) {
                try {
                    double t = obj.get("temperature").getAsDouble();
                    if (t == 0.0d) {
                        obj.addProperty("temperature", 1.0d);
                        log("[MITM] Rewrote temperature 0.0 -> 1.0 for " + model);
                    }
                } catch (Exception ignored) {
                    // Ignore non-numeric
                }
            }

            return gson.toJson(obj);
        } catch (Exception e) {
            log("[MITM] Failed to parse/modify JSON: " + e.getMessage());
            return body;
        }
    }

    private String replaceContentLength(String headers, int newLength) {
        String[] lines = headers.split("\r\n");
        boolean replaced = false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
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
}
