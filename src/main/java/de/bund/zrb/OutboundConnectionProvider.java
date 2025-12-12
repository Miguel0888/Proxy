package de.bund.zrb;

import java.io.IOException;
import java.net.Socket;

interface OutboundConnectionProvider {

    Socket openConnectTunnel(String host, int port) throws IOException;

    Socket openHttpConnection(String host, int port) throws IOException;
}
