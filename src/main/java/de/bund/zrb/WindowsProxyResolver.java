package de.bund.zrb;

import de.bund.zrb.mitm.MitmTrafficListener;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Windows-specific proxy resolver using PowerShell and .NET WebRequest.
 * Supports WPAD/PAC auto-configuration.
 * 
 * @deprecated Use {@link WinProxyJavaResolver} instead, which uses the win-proxy-java library
 *             and doesn't require external PowerShell scripts.
 */
@Deprecated
public final class WindowsProxyResolver implements SystemProxyResolver {

    private static final String SCRIPT_RESOURCE = "/ps/get-proxy-for-url.ps1";
    private static final String SCRIPT_FILE_NAME = "get-proxy-for-url.ps1";

    private final File workingDir;
    private final long cacheTtlMillis;
    private final ConcurrentHashMap<String, CacheEntry> cache;
    private final MitmTrafficListener trafficListener;
    private final String customScriptPath; // Optional custom script path

    public WindowsProxyResolver(File workingDir, long cacheTtlSeconds, MitmTrafficListener trafficListener) {
        this(workingDir, cacheTtlSeconds, trafficListener, null);
    }

    public WindowsProxyResolver(File workingDir, long cacheTtlSeconds, MitmTrafficListener trafficListener, String customScriptPath) {
        this.workingDir = workingDir;
        this.cacheTtlMillis = TimeUnit.SECONDS.toMillis(cacheTtlSeconds);
        this.cache = new ConcurrentHashMap<String, CacheEntry>();
        this.trafficListener = trafficListener;
        this.customScriptPath = customScriptPath != null && !customScriptPath.trim().isEmpty() ? customScriptPath.trim() : null;
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

        // Resolve via PowerShell
        ProxyInfo info = resolveViaScript(url);

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

    private ProxyInfo resolveViaScript(String url) throws IOException {
        File scriptFile = getScriptFile();

        ProcessBuilder pb = new ProcessBuilder(
                "powershell.exe",
                "-ExecutionPolicy", "Bypass",
                "-NoProfile",
                "-File", scriptFile.getAbsolutePath(),
                "-TestUrl", url
        );
        pb.directory(workingDir);
        pb.redirectErrorStream(true);

        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }

        int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Proxy resolution interrupted for " + url, e);
        }

        // Exit code 0 is success (both for direct and proxy)
        if (exitCode != 0) {
            throw new IOException("Proxy resolution script failed for " + url + " (exit code " + exitCode + "): " + output.toString().trim());
        }

        String result = output.toString().trim();
        return parseProxyResult(result);
    }

    private ProxyInfo parseProxyResult(String result) throws IOException {
        // Empty output = DIRECT
        if (result == null || result.isEmpty()) {
            return ProxyInfo.direct();
        }

        // New format: just "host:port" (no PROXY prefix)
        // Also support legacy format with "PROXY host:port" for compatibility
        List<ProxyInfo.ProxyCandidate> candidates = new ArrayList<ProxyInfo.ProxyCandidate>();

        // Split by semicolon for multiple proxies (legacy PAC format)
        String[] entries = result.split(";");

        for (String entry : entries) {
            entry = entry.trim();
            if (entry.isEmpty()) {
                continue;
            }

            if (entry.equalsIgnoreCase("DIRECT")) {
                candidates.add(ProxyInfo.ProxyCandidate.direct());
            } else if (entry.toUpperCase().startsWith("PROXY ")) {
                // Legacy format: "PROXY host:port"
                String hostPort = entry.substring(6).trim();
                int colonIdx = hostPort.lastIndexOf(':');
                if (colonIdx > 0) {
                    String host = hostPort.substring(0, colonIdx);
                    try {
                        int port = Integer.parseInt(hostPort.substring(colonIdx + 1));
                        candidates.add(ProxyInfo.ProxyCandidate.httpProxy(host, port));
                    } catch (NumberFormatException e) {
                        throw new IOException("Invalid proxy port in: " + entry);
                    }
                } else {
                    throw new IOException("Invalid proxy format (missing port): " + entry);
                }
            } else if (entry.toUpperCase().startsWith("SOCKS ")) {
                // Legacy format: "SOCKS host:port"
                String hostPort = entry.substring(6).trim();
                int colonIdx = hostPort.lastIndexOf(':');
                if (colonIdx > 0) {
                    String host = hostPort.substring(0, colonIdx);
                    try {
                        int port = Integer.parseInt(hostPort.substring(colonIdx + 1));
                        candidates.add(ProxyInfo.ProxyCandidate.socks(host, port));
                    } catch (NumberFormatException e) {
                        throw new IOException("Invalid SOCKS port in: " + entry);
                    }
                } else {
                    throw new IOException("Invalid SOCKS format (missing port): " + entry);
                }
            } else {
                // New simple format: just "host:port" (no prefix)
                int colonIdx = entry.lastIndexOf(':');
                if (colonIdx > 0) {
                    String host = entry.substring(0, colonIdx);
                    try {
                        int port = Integer.parseInt(entry.substring(colonIdx + 1));
                        // Assume HTTP proxy for simple format
                        candidates.add(ProxyInfo.ProxyCandidate.httpProxy(host, port));
                    } catch (NumberFormatException e) {
                        // Not a valid host:port, might be an error message - treat as no proxy
                        log("Warning: Could not parse proxy output: " + entry);
                        return ProxyInfo.direct();
                    }
                } else {
                    // No colon found - might be an error message or hostname without port
                    log("Warning: Unknown proxy output format: " + entry);
                    return ProxyInfo.direct();
                }
            }
        }

        if (candidates.isEmpty()) {
            return ProxyInfo.direct();
        }

        return ProxyInfo.fromCandidates(candidates);
    }

    /**
     * Returns the script file to use. If a custom script path is configured and exists,
     * uses that. Otherwise extracts and uses the default embedded script.
     */
    private File getScriptFile() throws IOException {
        // Check for custom script first
        if (customScriptPath != null) {
            File customScript = new File(customScriptPath);
            if (customScript.exists() && customScript.isFile()) {
                log("Using custom proxy script: " + customScript.getAbsolutePath());
                return customScript;
            } else {
                log("Warning: Custom script not found at " + customScriptPath + ", falling back to default");
            }
        }
        
        // Use default embedded script
        return ensureScriptExtracted();
    }

    private File ensureScriptExtracted() throws IOException {
        File scriptFile = new File(workingDir, SCRIPT_FILE_NAME);
        if (scriptFile.exists()) {
            return scriptFile;
        }

        if (!workingDir.exists() && !workingDir.mkdirs()) {
            throw new IOException("Failed to create working directory: " + workingDir.getAbsolutePath());
        }

        InputStream in = WindowsProxyResolver.class.getResourceAsStream(SCRIPT_RESOURCE);
        if (in == null) {
            throw new IOException("Script resource not found: " + SCRIPT_RESOURCE);
        }

        FileOutputStream out = null;
        try {
            out = new FileOutputStream(scriptFile);
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } finally {
            closeQuietly(in);
            closeQuietly(out);
        }

        log("Extracted proxy resolver script to: " + scriptFile.getAbsolutePath());
        return scriptFile;
    }

    private void log(String msg) {
        if (trafficListener != null) {
            trafficListener.onTraffic("proxy-resolver", msg, false);
        } else {
            System.out.println("[WindowsProxyResolver] " + msg);
        }
    }

    private void closeQuietly(java.io.Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException ignored) {
                // ignore
            }
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

