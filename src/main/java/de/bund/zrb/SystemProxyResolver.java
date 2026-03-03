package de.bund.zrb;

import java.io.IOException;

/**
 * Resolves the effective proxy for a given target URL/host using system proxy settings (WPAD/PAC).
 */
public interface SystemProxyResolver {

    /**
     * Resolve proxy information for the given target.
     *
     * @param targetHost target hostname
     * @param targetPort target port
     * @param https      true if HTTPS (port 443), false otherwise
     * @return ProxyInfo containing proxy candidates (may be DIRECT)
     * @throws IOException if proxy resolution fails
     */
    ProxyInfo resolveProxy(String targetHost, int targetPort, boolean https) throws IOException;
}

