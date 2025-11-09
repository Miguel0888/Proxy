package de.bund.zrb;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyStore;

public class MitmConnectionHandler {

    private static final int CONNECT_TIMEOUT_MILLIS = 15000;
    private static final int READ_TIMEOUT_MILLIS = 60000;

    private final SSLContext serverSslContext;
    private final SSLSocketFactory clientSslFactory;
    private final String mitmHostName;

    public MitmConnectionHandler(String keyStorePath, String keyStorePassword, String mitmHostName) {
        try {
            this.serverSslContext = createServerSslContext(keyStorePath, keyStorePassword);
            this.clientSslFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            this.mitmHostName = mitmHostName.toLowerCase();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to init SSL contexts", e);
        }
    }

    public void handle(Socket clientSocket) throws IOException {
        clientSocket.setSoTimeout(READ_TIMEOUT_MILLIS);

        InputStream in = clientSocket.getInputStream();
        OutputStream out = clientSocket.getOutputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, "ISO-8859-1"));

        String requestLine = reader.readLine();
        if (requestLine == null || requestLine.length() == 0) {
            return;
        }

        String header;
        while ((header = reader.readLine()) != null && header.length() > 0) {
            // Ignore headers of CONNECT request
        }

        System.out.println("[MITM] Request line: " + requestLine);

        String[] parts = requestLine.split(" ");
        if (parts.length < 3) {
            writeBadRequest(out);
            return;
        }

        String method = parts[0];
        String target = parts[1];

        if (!"CONNECT".equalsIgnoreCase(method)) {
            // For non-CONNECT requests just reject or implement plain HTTP if needed
            System.out.println("[MITM] Non-CONNECT request, returning 400");
            writeBadRequest(out);
            return;
        }

        // Expect host:port
        String lowerTarget = target.toLowerCase();

        if (lowerTarget.startsWith(mitmHostName + ":")) {
            System.out.println("[MITM] MITM for " + target);
            handleMitm(clientSocket, target);
        } else {
            System.out.println("[MITM] Tunnel only for " + target);
            handleTunnel(clientSocket, target);
        }
    }

    private void handleMitm(Socket clientSocket, String target) throws IOException {
        String[] hostPort = target.split(":");
        String host = hostPort[0];
        int port = parsePort(hostPort[1], 443);

        // Connect to OpenAI via TLS
        SSLSocket serverSocket = (SSLSocket) clientSslFactory.createSocket();
        serverSocket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
        serverSocket.setSoTimeout(READ_TIMEOUT_MILLIS);
        serverSocket.startHandshake();
        System.out.println("[MITM] Connected TLS to " + host + ":" + port);

        // Acknowledge CONNECT to client (plain)
        OutputStream clientOutPlain = clientSocket.getOutputStream();
        clientOutPlain.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes("ISO-8859-1"));
        clientOutPlain.flush();

        // Upgrade client side to TLS using our MITM cert
        SSLSocket clientSslSocket = (SSLSocket) serverSslContext
                .getSocketFactory()
                .createSocket(clientSocket, 0, 0, true);
        clientSslSocket.setUseClientMode(false);
        clientSslSocket.setNeedClientAuth(false);
        clientSslSocket.setSoTimeout(READ_TIMEOUT_MILLIS);
        clientSslSocket.startHandshake();
        System.out.println("[MITM] Established TLS with client for " + host + ":" + port);

        // Optional: log first request line to OpenAI
        logFirstHttpRequestLine(clientSslSocket);

        // Now pipe in both directions
        startBidirectionalPipe(clientSslSocket, serverSocket);
    }

    private void handleTunnel(Socket clientSocket, String target) throws IOException {
        String[] hostPort = target.split(":");
        if (hostPort.length != 2) {
            writeBadRequest(clientSocket.getOutputStream());
            return;
        }

        String host = hostPort[0];
        int port = parsePort(hostPort[1], 443);

        Socket remote = new Socket();
        remote.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
        remote.setSoTimeout(READ_TIMEOUT_MILLIS);

        OutputStream clientOut = clientSocket.getOutputStream();
        clientOut.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes("ISO-8859-1"));
        clientOut.flush();

        startBidirectionalPipe(clientSocket, remote);
    }

    private void startBidirectionalPipe(Socket a, Socket b) {
        Thread t1 = new Thread(new SocketPipeTask(a, b));
        Thread t2 = new Thread(new SocketPipeTask(b, a));
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
            closeQuietly(a);
            closeQuietly(b);
        }
    }

    private void logFirstHttpRequestLine(SSLSocket clientSslSocket) {
        try {
            clientSslSocket.setSoTimeout(READ_TIMEOUT_MILLIS);
            InputStream in = clientSslSocket.getInputStream();
            in.mark(8192);

            BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line = reader.readLine();
            if (line != null) {
                System.out.println("[MITM] OpenAI HTTP: " + line);
            }
            // Note: Für echte Nutzung eigenen Buffered-Pump implementieren, um nichts zu verlieren.
        } catch (IOException e) {
            System.out.println("[MITM] Could not read first HTTP line: " + e.getMessage());
        }
    }

    private SSLContext createServerSslContext(String keyStorePath, String keyStorePassword) throws Exception {
        KeyStore ks = KeyStore.getInstance("JKS");
        InputStream ksIn = new FileInputStream(keyStorePath);
        try {
            ks.load(ksIn, keyStorePassword.toCharArray());
        } finally {
            ksIn.close();
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, keyStorePassword.toCharArray());

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);
        return ctx;
    }

    private void writeBadRequest(OutputStream out) throws IOException {
        String msg = "HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\n\r\n";
        out.write(msg.getBytes("ISO-8859-1"));
        out.flush();
    }

    private int parsePort(String value, int defaultPort) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultPort;
        }
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
