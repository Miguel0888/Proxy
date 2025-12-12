package de.bund.zrb;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class DirectConnectionProvider implements OutboundConnectionProvider {

    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    public DirectConnectionProvider(int connectTimeoutMillis, int readTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
    }

    public Socket openConnectTunnel(String host, int port) throws IOException {
        return openSocket(host, port);
    }

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
