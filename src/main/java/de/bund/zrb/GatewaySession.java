package de.bund.zrb;

import java.io.IOException;
import java.net.Socket;

public interface GatewaySession {

    /**
     * Return stable identifier of this gateway client.
     */
    String getId();

    /**
     * Return remote address information (for UI/logging).
     */
    String getRemoteAddress();

    /**
     * Check if underlying connection to gateway client is still alive.
     */
    boolean isAlive();

    /**
     * Open tunnel for HTTPS CONNECT requests via gateway client.
     */
    Socket openConnectTunnel(String host, int port) throws IOException;

    /**
     * Open HTTP connection via gateway client.
     */
    Socket openHttpConnection(String host, int port) throws IOException;

    /**
     * Close gateway session and free all resources.
     */
    void close() throws IOException;
}
