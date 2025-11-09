package de.bund.zrb;

import java.io.IOException;
import java.net.Socket;

public interface MitmHandler {

    /**
     * Decide if this handler should intercept the given host and port.
     */
    boolean supports(String host, int port);

    /**
     * Handle CONNECT request with MITM (terminate client TLS, connect to remote, forward).
     * Implementations must fully process the tunnel and return only when done.
     */
    void handleConnect(String host, int port, Socket clientSocket) throws IOException;
}
