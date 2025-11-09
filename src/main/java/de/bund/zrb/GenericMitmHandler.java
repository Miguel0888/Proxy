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
    private static final int MAX_CAPTURE_BYTES = 65536;

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

    // Convenience: MITM for all 443
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
        // 1) TLS zu echtem Ziel
        SSLSocket remote = (SSLSocket) clientSslFactory.createSocket();
        remote.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
        remote.setSoTimeout(READ_TIMEOUT_MILLIS);
        remote.startHandshake();
        log("[MITM] Connected TLS to " + host + ":" + port);

        // 2) CONNECT bestätigen
        OutputStream clientOutPlain = clientSocket.getOutputStream();
        clientOutPlain.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes("ISO-8859-1"));
        clientOutPlain.flush();

        // 3) TLS ggü. Client mit unserem Zertifikat
        SSLSocket clientTls = (SSLSocket) serverSslContext
                .getSocketFactory()
                .createSocket(clientSocket, host, port, true);
        clientTls.setUseClientMode(false);
        clientTls.setNeedClientAuth(false);
        clientTls.setSoTimeout(READ_TIMEOUT_MILLIS);
        clientTls.startHandshake();
        log("[MITM] Established TLS with client for " + host + ":" + port);

        // 4) Daten in beide Richtungen leiten + mitschneiden
        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                pipeWithCapture(clientTls, remote, "client->server");
            }
        }, "mitm-c2s");

        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                pipeWithCapture(remote, clientTls, "server->client");
            }
        }, "mitm-s2c");

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

    private void pipeWithCapture(Socket sourceSocket,
                                 Socket targetSocket,
                                 String direction) {
        ByteArrayOutputStream capture = (trafficListener != null) ? new ByteArrayOutputStream() : null;

        try {
            InputStream in = sourceSocket.getInputStream();
            OutputStream out = targetSocket.getOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                if (capture != null && capture.size() < MAX_CAPTURE_BYTES) {
                    capture.write(buffer, 0, read);
                }
                out.write(buffer, 0, read);
                out.flush();
            }
        } catch (IOException ignored) {
            // Connection closed, stop piping
        } finally {
            if (capture != null && capture.size() > 0) {
                try {
                    String text = new String(capture.toByteArray(), "UTF-8");
                    boolean isJson = looksLikeJson(text);
                    logTraffic(direction, text, isJson);
                } catch (Exception e) {
                    log("[MITM] Failed to decode captured traffic: " + e.getMessage());
                }
            }
        }
    }

    private boolean looksLikeJson(String text) {
        if (text == null) {
            return false;
        }
        String trimmed = text.trim();
        // Very simple heuristic
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
