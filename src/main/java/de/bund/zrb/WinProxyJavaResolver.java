package de.bund.zrb;

import com.aresstack.winproxy.PacUrlSource;
import com.aresstack.winproxy.ProxyResult;
import com.aresstack.winproxy.WindowsProxyResolver;
import de.bund.zrb.config.ProxyConfig;
import de.bund.zrb.mitm.MitmTrafficListener;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Windows-specific proxy resolver using win-proxy-java library.
 * Supports all library modes:
 * - AUTO: Full Windows system proxy detection (WPAD/PAC/static/bypass)
 * - STATIC: Manual proxy host:port with optional bypass list
 * - PAC_URL: Evaluate a specific PAC URL
 */
public final class WinProxyJavaResolver implements SystemProxyResolver {

    /** Proxy resolution mode */
    public enum ResolveMode {
        /** Automatic: use full Windows system proxy (WPAD + PAC + static + bypass) */
        AUTO,
        /** Manual: fixed proxy host:port with optional bypass list */
        STATIC,
        /** PAC URL: evaluate a specific PAC URL for each target */
        PAC_URL
    }

    private final long cacheTtlMillis;
    private final ConcurrentHashMap<String, CacheEntry> cache;
    private final MitmTrafficListener trafficListener;

    // Configuration
    private final ResolveMode mode;
    private final String staticProxyHost;
    private final int staticProxyPort;
    private final String pacUrl;
    private final String bypassList;
    private final PacUrlSource pacSource;

    /**
     * Creates a WinProxyJavaResolver with AUTO mode (simplest constructor).
     */
    public WinProxyJavaResolver(long cacheTtlSeconds, MitmTrafficListener trafficListener) {
        this(ResolveMode.AUTO, null, 0, null, null, null, cacheTtlSeconds, trafficListener);
    }

    /**
     * Creates a WinProxyJavaResolver from ProxyConfig settings.
     */
    public WinProxyJavaResolver(ProxyConfig config, MitmTrafficListener trafficListener) {
        this(
                parseMode(config.getClientOutboundProxyMode()),
                config.getClientOutboundProxyHost(),
                config.getClientOutboundProxyPort(),
                config.getClientOutboundProxyPacUrl(),
                config.getClientOutboundProxyBypassList(),
                config.getClientOutboundProxyPacSource(),
                config.getClientOutboundProxyCacheTtlSeconds(),
                trafficListener
        );
    }

    /**
     * Full constructor with all settings.
     */
    public WinProxyJavaResolver(ResolveMode mode,
                                String staticProxyHost,
                                int staticProxyPort,
                                String pacUrl,
                                String bypassList,
                                String pacSourceStr,
                                long cacheTtlSeconds,
                                MitmTrafficListener trafficListener) {
        this.mode = mode != null ? mode : ResolveMode.AUTO;
        this.staticProxyHost = staticProxyHost != null ? staticProxyHost.trim() : "";
        this.staticProxyPort = staticProxyPort > 0 ? staticProxyPort : 8080;
        this.pacUrl = pacUrl != null ? pacUrl.trim() : "";
        this.bypassList = bypassList != null ? bypassList.trim() : "";
        this.pacSource = parsePacSource(pacSourceStr);
        this.cacheTtlMillis = TimeUnit.SECONDS.toMillis(cacheTtlSeconds);
        this.cache = new ConcurrentHashMap<String, CacheEntry>();
        this.trafficListener = trafficListener;
    }

    @Override
    public ProxyInfo resolveProxy(String targetHost, int targetPort, boolean https) throws IOException {
        String url = buildUrl(targetHost, targetPort, https);

        // Check cache
        CacheEntry cached = cache.get(url);
        if (cached != null && !cached.isExpired()) {
            log("Proxy cache hit for " + url + ": " + cached.proxyInfo);
            return cached.proxyInfo;
        }

        ProxyInfo info;
        switch (mode) {
            case STATIC:
                info = resolveStatic(url);
                break;
            case PAC_URL:
                info = resolvePacUrl(url);
                break;
            case AUTO:
            default:
                info = resolveAuto(url);
                break;
        }

        // Cache result
        cache.put(url, new CacheEntry(info, System.currentTimeMillis() + cacheTtlMillis));
        log("Resolved proxy for " + url + " [" + mode + "]: " + info);
        return info;
    }

    // ---- AUTO mode: full Windows system proxy resolution ----

    private ProxyInfo resolveAuto(String url) throws IOException {
        try {
            ProxyResult result;
            if (!bypassList.isEmpty()) {
                // Use resolve with custom PAC source and bypass
                result = WindowsProxyResolver.resolve(url, pacSource, bypassList);
            } else {
                // Use simple resolve (uses all system defaults)
                result = WindowsProxyResolver.resolve(url);
            }
            return convertResult(result, url, "auto");
        } catch (Exception e) {
            log("win-proxy-java AUTO error for " + url + ": " + e.getMessage());
            throw new IOException("Proxy resolution (AUTO) failed for " + url + ": " + e.getMessage(), e);
        }
    }

    // ---- STATIC mode: manual proxy with optional bypass ----

    private ProxyInfo resolveStatic(String url) throws IOException {
        if (staticProxyHost.isEmpty()) {
            log("STATIC mode but no proxy host configured -> DIRECT");
            return ProxyInfo.direct();
        }

        // Check bypass list
        if (!bypassList.isEmpty()) {
            try {
                if (WindowsProxyResolver.isBypassed(url, bypassList)) {
                    log("STATIC: URL " + url + " matches bypass list -> DIRECT");
                    return ProxyInfo.direct();
                }
            } catch (Exception e) {
                log("STATIC: bypass check error: " + e.getMessage());
                // Continue with proxy
            }
        }

        log("STATIC: using proxy " + staticProxyHost + ":" + staticProxyPort + " for " + url);
        List<ProxyInfo.ProxyCandidate> candidates = new ArrayList<ProxyInfo.ProxyCandidate>();
        candidates.add(ProxyInfo.ProxyCandidate.httpProxy(staticProxyHost, staticProxyPort));
        candidates.add(ProxyInfo.ProxyCandidate.direct()); // fallback
        return ProxyInfo.fromCandidates(candidates);
    }

    // ---- PAC_URL mode: evaluate specific PAC URL ----

    private ProxyInfo resolvePacUrl(String url) throws IOException {
        if (pacUrl.isEmpty()) {
            log("PAC_URL mode but no PAC URL configured -> falling back to AUTO");
            return resolveAuto(url);
        }

        try {
            ProxyResult result = WindowsProxyResolver.evaluatePac(url, pacUrl);
            return convertResult(result, url, "pac-url");
        } catch (Exception e) {
            log("win-proxy-java PAC_URL error for " + url + ": " + e.getMessage());
            throw new IOException("Proxy resolution (PAC_URL) failed for " + url + ": " + e.getMessage(), e);
        }
    }

    // ---- Shared helpers ----

    private ProxyInfo convertResult(ProxyResult result, String url, String source) {
        if (result == null) {
            log("win-proxy-java returned null for " + url + " [" + source + "] -> DIRECT");
            return ProxyInfo.direct();
        }

        List<ProxyInfo.ProxyCandidate> candidates = new ArrayList<ProxyInfo.ProxyCandidate>();

        if (result.isDirect()) {
            log("win-proxy-java: DIRECT for " + url + " [" + source + "] (" + result.getReason() + ")");
            candidates.add(ProxyInfo.ProxyCandidate.direct());
        } else {
            String host = result.getHost();
            int port = result.getPort();
            log("win-proxy-java: proxy " + host + ":" + port + " for " + url + " [" + source + "] (" + result.getReason() + ")");
            candidates.add(ProxyInfo.ProxyCandidate.httpProxy(host, port));
            candidates.add(ProxyInfo.ProxyCandidate.direct()); // fallback
        }

        return ProxyInfo.fromCandidates(candidates);
    }

    private String buildUrl(String host, int port, boolean https) {
        String scheme = https ? "https" : "http";
        int defaultPort = https ? 443 : 80;
        if (port == defaultPort) {
            return scheme + "://" + host + "/";
        }
        return scheme + "://" + host + ":" + port + "/";
    }

    /**
     * Test method to resolve proxy for a URL - useful for UI testing.
     */
    public ProxyInfo testResolve(String url) throws IOException {
        try {
            String host;
            int port;
            boolean https;
            java.net.URI uri = java.net.URI.create(url);
            host = uri.getHost();
            https = "https".equalsIgnoreCase(uri.getScheme());
            port = uri.getPort();
            if (port == -1) {
                port = https ? 443 : 80;
            }
            // Bypass cache for tests
            switch (mode) {
                case STATIC: return resolveStatic(url);
                case PAC_URL: return resolvePacUrl(url);
                default: return resolveAuto(url);
            }
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid URL: " + url, e);
        }
    }

    /**
     * Reads current Windows proxy diagnostic info.
     * Useful for the "Diagnose" button in UI.
     */
    public static String getDiagnosticInfo() {
        StringBuilder sb = new StringBuilder();
        try {
            sb.append("=== Windows Proxy Diagnose ===\n\n");

            // WPAD Auto-detect
            boolean wpadEnabled = WindowsProxyResolver.isWpadAutoDetectEnabled();
            sb.append("WPAD Auto-Detect: ").append(wpadEnabled ? "AKTIVIERT" : "DEAKTIVIERT").append("\n");

            // Connection flags
            int flags = WindowsProxyResolver.readConnectionFlags();
            sb.append("Connection Flags: ").append(flags).append("\n");
            sb.append("  Bit 1 (Manual Proxy):  ").append((flags & 0x02) != 0 ? "JA" : "NEIN").append("\n");
            sb.append("  Bit 2 (PAC Script):    ").append((flags & 0x04) != 0 ? "JA" : "NEIN").append("\n");
            sb.append("  Bit 3 (WPAD):          ").append((flags & 0x08) != 0 ? "JA" : "NEIN").append("\n");

            // Registry values
            String proxyServer = WindowsProxyResolver.readRegistryValueFromAllHives("ProxyServer");
            sb.append("\nProxy Server (Registry): ").append(proxyServer != null ? proxyServer : "(nicht gesetzt)").append("\n");

            String proxyOverride = WindowsProxyResolver.readRegistryValueFromAllHives("ProxyOverride");
            sb.append("Proxy Bypass (Registry): ").append(proxyOverride != null ? proxyOverride : "(nicht gesetzt)").append("\n");

            String autoConfigUrl = WindowsProxyResolver.readRegistryValueFromAllHives("AutoConfigURL");
            sb.append("PAC URL (Registry):      ").append(autoConfigUrl != null ? autoConfigUrl : "(nicht gesetzt)").append("\n");

        } catch (Exception e) {
            sb.append("\nFehler beim Lesen der Diagnose: ").append(e.getMessage()).append("\n");
        }
        return sb.toString();
    }

    /** Clear the proxy cache. */
    public void clearCache() {
        cache.clear();
        log("Proxy cache cleared");
    }

    public ResolveMode getMode() {
        return mode;
    }

    private void log(String msg) {
        if (trafficListener != null) {
            trafficListener.onTraffic("proxy-resolver", msg, false);
        } else {
            System.out.println("[WinProxyJavaResolver] " + msg);
        }
    }

    private static ResolveMode parseMode(String raw) {
        if (raw == null) return ResolveMode.AUTO;
        String upper = raw.trim().toUpperCase();
        // New mode names (matching MainframeMate Settings > Proxy)
        if ("WINDOWS_PAC".equals(upper)) return ResolveMode.AUTO;
        if ("REGISTRY".equals(upper)) return ResolveMode.AUTO;
        if ("MANUAL".equals(upper)) return ResolveMode.STATIC;
        // Original mode names (backward compatibility)
        if ("STATIC".equals(upper)) return ResolveMode.STATIC;
        if ("PAC_URL".equals(upper)) return ResolveMode.PAC_URL;
        return ResolveMode.AUTO;
    }

    private static PacUrlSource parsePacSource(String raw) {
        if (raw == null || raw.trim().isEmpty()) return PacUrlSource.REGISTRY;
        String upper = raw.trim().toUpperCase();
        if ("DIRECT".equals(upper)) return PacUrlSource.DIRECT;
        if ("POWERSHELL".equals(upper)) return PacUrlSource.POWERSHELL;
        return PacUrlSource.REGISTRY;
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
