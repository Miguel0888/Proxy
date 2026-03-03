package de.bund.zrb;

import java.io.IOException;
import java.net.Socket;

/**
 * Abstraction for creating outbound socket connections.
 * Implementations can connect directly or via a proxy.
 */
public interface OutboundSocketDialer {

    /**
     * Open a socket connection to the given target host and port.
     *
     * @param targetHost target hostname
     * @param targetPort target port
     * @return connected socket
     * @throws IOException if connection fails
     */
    Socket dial(String targetHost, int targetPort) throws IOException;
}

