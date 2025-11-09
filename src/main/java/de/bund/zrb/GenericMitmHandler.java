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

    private final SSLContext serverSslContext;       // Talk TLS to client (our cert)
    private final SSLSocketFactory clientSslFactory; // Talk TLS to real server
    private final Set<String> mitmHosts;             // lower-case hostnames; empty = all

    public GenericMitmHandler(String keyStorePath, String keyStorePassword) {
        this(keyStorePath, keyStorePassword, Collections.<String>emptySet());
    }

    public GenericMitmHandler(String keyStorePath,
                              String keyStorePassword,
                              Set<String> mitmHosts) {
        try {
            this.serverSslContext = createServerSslContext(keyStorePath, keyStorePassword);
            this.clientSslFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            this.mitmHosts = normalizeHosts(mitmHosts);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize GenericMitmHandler: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean supports(String host, int port) {
        if (host == null || port != 443) {
            return false;
        }
        String h = host.toLowerCase();
        // If no hosts configured: MITM all 443
        if (mitmHosts.isEmpty()) {
            return true;
        }
        return mitmHosts.contains(h);
    }

    @Override
    public void handleConnect(String host, int port, Socket clientSocket) throws IOException {
        // 1) Establish TLS to real server
        SSLSocket remote = (SSLSocket) clientSslFactory.createSocket();
        remote.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
        remote.setSoTimeout(READ_TIMEOUT_MILLIS);
        remote.startHandshake();
        System.out.println("[MITM] Connected TLS to " + host + ":" + port);

        // 2) Acknowledge CONNECT to client (still plain HTTP)
        OutputStream clientOutPlain = clientSocket.getOutputStream();
        clientOutPlain.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes("ISO-8859-1"));
        clientOutPlain.flush();

        // 3) Upgrade client side to TLS using our keystore cert
        SSLSocket clientTls = (SSLSocket) serverSslContext
                .getSocketFactory()
                .createSocket(clientSocket, host, port, true);
        clientTls.setUseClientMode(false);
        clientTls.setNeedClientAuth(false);
        clientTls.setSoTimeout(READ_TIMEOUT_MILLIS);
        clientTls.startHandshake();
        System.out.println("[MITM] Established TLS with client for " + host + ":" + port);

        // 4) Optional: log first HTTP request line
        logFirstHttpRequestLine(clientTls);

        // 5) Start bidirectional piping using existing tunnel task
        Thread t1 = new Thread(new TunnelPipeTask(clientTls, remote));
        Thread t2 = new Thread(new TunnelPipeTask(remote, clientTls));
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

    private void logFirstHttpRequestLine(SSLSocket clientTls) {
        try {
            clientTls.setSoTimeout(READ_TIMEOUT_MILLIS);
            clientTls.getInputStream().mark(8192);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(clientTls.getInputStream(), "UTF-8"));
            String line = reader.readLine();
            if (line != null) {
                System.out.println("[MITM] HTTP: " + line);
            }
            // For production: implement buffered forwarding to avoid data loss.
        } catch (IOException e) {
            System.out.println("[MITM] Could not read first HTTP line: " + e.getMessage());
        }
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
            if (h != null && h.trim().length() > 0) {
                result.add(h.trim().toLowerCase());
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
