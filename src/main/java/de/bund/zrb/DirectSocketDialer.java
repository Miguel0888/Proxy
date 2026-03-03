package de.bund.zrb;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Direct outbound socket dialer without proxy.
 */
public final class DirectSocketDialer implements OutboundSocketDialer {

    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    public DirectSocketDialer(int connectTimeoutMillis, int readTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
    }

    @Override
    public Socket dial(String targetHost, int targetPort) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(targetHost, targetPort), connectTimeoutMillis);
        socket.setSoTimeout(readTimeoutMillis);
        return socket;
    }
}

