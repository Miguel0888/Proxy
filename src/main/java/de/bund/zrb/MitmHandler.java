package de.bund.zrb;

import java.io.IOException;
import java.net.Socket;

public interface MitmHandler {

    // Decide if this handler wants to intercept this host:port
    boolean supports(String host, int port);

    // Perform full MITM handling for this CONNECT target.
    // Implement method as blocking: return only when tunnel is finished.
    void handleConnect(String host, int port, Socket clientSocket) throws IOException;
}
