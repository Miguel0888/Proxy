package de.bund.zrb;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyStore;

public class OpenAiMitmHandler implements MitmHandler {

    private static final int CONNECT_TIMEOUT_MILLIS = 15000;
    private static final int READ_TIMEOUT_MILLIS = 60000;

    private final String targetHost;
    private final SSLContext serverSslContext;      // used to talk TLS with client
    private final SSLSocketFactory clientSslFactory; // used to talk TLS with real OpenAI

    public OpenAiMitmHandler(String targetHost,
                             String keyStorePath,
                             String keyStorePassword) {
        try {
            this.targetHost = targetHost.toLowerCase();
            this.serverSslContext = createServerSslContext(keyStorePath, keyStorePassword);
            this.clientSslFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize MITM handler: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean supports(String host, int port) {
        if (host == null) {
            return false;
        }
        String h = host.toLowerCase();
        return h.equals(targetHost) && port == 443;
    }

    @Override
    public void handleConnect(String host, int port, Socket clientSocket) throws IOException {
        // Connect TLS to real OpenAI
        SSLSocket remote = (SSLSocket) clientSslFactory.createSocket();
        remote.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
        remote.setSoTimeout(READ_TIMEOUT_MILLIS);
        remote.startHandshake();
        System.out.println("[MITM] Connected TLS to " + host + ":" + port);

        // Acknowledge CONNECT to client (plain)
        OutputStream clientOutPlain = clientSocket.getOutputStream();
        clientOutPlain.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes("ISO-8859-1"));
        clientOutPlain.flush();

        // Upgrade client side to TLS using our certificate from keystore
        SSLSocket clientTls = (SSLSocket) serverSslContext
                .getSocketFactory()
                .createSocket(clientSocket, 0, 0, true);
        clientTls.setUseClientMode(false);
        clientTls.setNeedClientAuth(false);
        clientTls.setSoTimeout(READ_TIMEOUT_MILLIS);
        clientTls.startHandshake();
        System.out.println("[MITM] Established TLS with client for " + host + ":" + port);

        // Optional: log first HTTP request line
        logFirstHttpRequestLine(clientTls);

        // Start bidirectional piping
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
            InputStream in = clientTls.getInputStream();
            in.mark(8192);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line = reader.readLine();
            if (line != null) {
                System.out.println("[MITM] OpenAI HTTP: " + line);
            }
            // For real use: implement buffered pump to not drop bytes.
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
