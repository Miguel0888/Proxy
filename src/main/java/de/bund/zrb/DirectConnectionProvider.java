package de.bund.zrb;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Open outbound connections directly without a gateway.
 */
public final class DirectConnectionProvider implements OutboundConnectionProvider {

    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    public DirectConnectionProvider(int connectTimeoutMillis, int readTimeoutMillis) {
        if (connectTimeoutMillis <= 0) {
            throw new IllegalArgumentException("connectTimeoutMillis must be > 0");
        }
        if (readTimeoutMillis <= 0) {
            throw new IllegalArgumentException("readTimeoutMillis must be > 0");
        }
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
    }

    @Override
    public Socket openConnectTunnel(String host, int port) throws IOException {
        return openSocket(host, port);
    }

    @Override
    public Socket openHttpConnection(String host, int port) throws IOException {
        return openSocket(host, port);
    }

    private Socket openSocket(String host, int port) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), connectTimeoutMillis);
        socket.setSoTimeout(readTimeoutMillis);
        return socket;
    }
}
