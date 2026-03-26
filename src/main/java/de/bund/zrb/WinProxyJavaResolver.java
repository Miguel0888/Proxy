package de.bund.zrb;

import com.aresstack.winproxy.ProxyResult;
import com.aresstack.winproxy.WindowsProxyResolver;
import de.bund.zrb.mitm.MitmTrafficListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Windows-specific proxy resolver using win-proxy-java library.
 * Replaces the PowerShell-based WindowsProxyResolver with a pure Java implementation.
 * Supports WPAD/PAC auto-configuration without external scripts.
 */
public final class WinProxyJavaResolver implements SystemProxyResolver {

    private final long cacheTtlMillis;
    private final ConcurrentHashMap<String, CacheEntry> cache;
    private final MitmTrafficListener trafficListener;

    /**
     * Creates a new WinProxyJavaResolver.
     *
     * @param cacheTtlSeconds Cache TTL in seconds for proxy resolution results
     * @param trafficListener Optional listener for logging/traffic events
     */
    public WinProxyJavaResolver(long cacheTtlSeconds, MitmTrafficListener trafficListener) {
        this.cacheTtlMillis = TimeUnit.SECONDS.toMillis(cacheTtlSeconds);
        this.cache = new ConcurrentHashMap<String, CacheEntry>();
        this.trafficListener = trafficListener;
    }

    @Override
    public ProxyInfo resolveProxy(String targetHost, int targetPort, boolean https) throws IOException {
        String url = buildUrl(targetHost, targetPort, https);
        String cacheKey = url;

        // Check cache
        CacheEntry cached = cache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log("Proxy cache hit for " + url + ": " + cached.proxyInfo);
            return cached.proxyInfo;
        }

        // Resolve via win-proxy-java library
        ProxyInfo info = resolveViaLibrary(url);

        // Cache result
        cache.put(cacheKey, new CacheEntry(info, System.currentTimeMillis() + cacheTtlMillis));

        log("Resolved proxy for " + url + ": " + info);
        return info;
    }

    private String buildUrl(String host, int port, boolean https) {
        String scheme = https ? "https" : "http";
        int defaultPort = https ? 443 : 80;
        if (port == defaultPort) {
            return scheme + "://" + host + "/";
        }
        return scheme + "://" + host + ":" + port + "/";
    }

    private ProxyInfo resolveViaLibrary(String url) throws IOException {
        try {
            // Use win-proxy-java to resolve proxy for this URL
            ProxyResult result = WindowsProxyResolver.resolve(url);
            
            if (result == null) {
                log("win-proxy-java returned null for " + url + " -> DIRECT");
                return ProxyInfo.direct();
            }

            List<ProxyInfo.ProxyCandidate> candidates = new ArrayList<ProxyInfo.ProxyCandidate>();
            
            if (result.isDirect()) {
                log("win-proxy-java returned DIRECT for " + url + " (" + result.getReason() + ")");
                candidates.add(ProxyInfo.ProxyCandidate.direct());
            } else {
                String host = result.getHost();
                int port = result.getPort();
                String reason = result.getReason();
                
                log("win-proxy-java returned proxy " + host + ":" + port + " for " + url + " (" + reason + ")");
                candidates.add(ProxyInfo.ProxyCandidate.httpProxy(host, port));
                // Add DIRECT as fallback if proxy fails
                candidates.add(ProxyInfo.ProxyCandidate.direct());
            }

            return ProxyInfo.fromCandidates(candidates);

        } catch (IllegalArgumentException e) {
            // Invalid URL
            throw new IOException("Invalid URL for proxy resolution: " + url, e);
        } catch (Exception e) {
            // Any other error from the library
            log("win-proxy-java error for " + url + ": " + e.getMessage() + " -> falling back to DIRECT");
            // On error, fall back to direct connection
            return ProxyInfo.direct();
        }
    }

    /**
     * Test method to resolve proxy for a URL - useful for UI testing.
     *
     * @param url The URL to test
     * @return ProxyInfo with resolved proxy information
     * @throws IOException if resolution fails
     */
    public ProxyInfo testResolve(String url) throws IOException {
        return resolveViaLibrary(url);
    }

    /**
     * Clear the proxy cache.
     */
    public void clearCache() {
        cache.clear();
        log("Proxy cache cleared");
    }

    private void log(String msg) {
        if (trafficListener != null) {
            trafficListener.onTraffic("proxy-resolver", msg, false);
        } else {
            System.out.println("[WinProxyJavaResolver] " + msg);
        }
    }

    private static class CacheEntry {
        final ProxyInfo proxyInfo;
        final long expiresAt;

        CacheEntry(ProxyInfo proxyInfo, long expiresAt) {
            this.proxyInfo = proxyInfo;
            this.expiresAt = expiresAt;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
