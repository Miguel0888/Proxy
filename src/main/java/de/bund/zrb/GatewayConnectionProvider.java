package de.bund.zrb;

import java.io.IOException;
import java.net.Socket;

public class GatewayConnectionProvider implements OutboundConnectionProvider {

    private final GatewaySessionManager sessionManager;

    public GatewayConnectionProvider(GatewaySessionManager sessionManager) {
        if (sessionManager == null) {
            throw new IllegalArgumentException("sessionManager must not be null");
        }
        this.sessionManager = sessionManager;
    }

    public Socket openConnectTunnel(String host, int port) throws IOException {
        GatewaySession session = sessionManager.getActiveSessionOrThrow();
        return session.openConnectTunnel(host, port);
    }

    public Socket openHttpConnection(String host, int port) throws IOException {
        GatewaySession session = sessionManager.getActiveSessionOrThrow();
        return session.openHttpConnection(host, port);
    }
}
