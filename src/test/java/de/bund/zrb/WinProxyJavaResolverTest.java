package de.bund.zrb;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for WinProxyJavaResolver to verify Windows proxy resolution using win-proxy-java library.
 * These tests only run on Windows.
 */
@EnabledOnOs(OS.WINDOWS)
public class WinProxyJavaResolverTest {

    @Test
    @DisplayName("Test: WinProxyJavaResolver can be created")
    void testResolverCreation() {
        System.out.println("\n--- Test: WinProxyJavaResolver creation ---");
        
        WinProxyJavaResolver resolver = new WinProxyJavaResolver(300, null);
        assertNotNull(resolver, "Resolver should be created");
        
        System.out.println("WinProxyJavaResolver created successfully!");
    }

    @Test
    @DisplayName("Test: WinProxyJavaResolver can resolve proxy for google.com")
    void testResolveProxyForGoogle() throws IOException {
        System.out.println("\n--- Test: Resolve proxy for google.com ---");
        
        WinProxyJavaResolver resolver = new WinProxyJavaResolver(300, null);
        
        // Resolve proxy for google.com:443 (HTTPS)
        ProxyInfo proxyInfo = resolver.resolveProxy("www.google.com", 443, true);
        
        assertNotNull(proxyInfo, "ProxyInfo should not be null");
        assertNotNull(proxyInfo.getCandidates(), "Candidates should not be null");
        assertFalse(proxyInfo.getCandidates().isEmpty(), "Candidates should not be empty");
        
        System.out.println("Resolved proxy for www.google.com:443 -> " + proxyInfo);
        
        // Print each candidate
        for (ProxyInfo.ProxyCandidate candidate : proxyInfo.getCandidates()) {
            if (candidate.isDirect()) {
                System.out.println("  - DIRECT");
            } else {
                System.out.println("  - " + candidate.getType() + " " + candidate.getHost() + ":" + candidate.getPort());
            }
        }
    }

    @Test
    @DisplayName("Test: WinProxyJavaResolver can resolve proxy for HTTP URL")
    void testResolveProxyForHttp() throws IOException {
        System.out.println("\n--- Test: Resolve proxy for HTTP URL ---");
        
        WinProxyJavaResolver resolver = new WinProxyJavaResolver(300, null);
        
        // Resolve proxy for example.com:80 (HTTP)
        ProxyInfo proxyInfo = resolver.resolveProxy("example.com", 80, false);
        
        assertNotNull(proxyInfo, "ProxyInfo should not be null");
        assertNotNull(proxyInfo.getCandidates(), "Candidates should not be null");
        
        System.out.println("Resolved proxy for example.com:80 -> " + proxyInfo);
    }

    @Test
    @DisplayName("Test: WinProxyJavaResolver uses cache")
    void testProxyCaching() throws IOException {
        System.out.println("\n--- Test: Proxy caching ---");
        
        WinProxyJavaResolver resolver = new WinProxyJavaResolver(300, null);
        
        // First call - should resolve
        long start1 = System.currentTimeMillis();
        ProxyInfo proxyInfo1 = resolver.resolveProxy("www.google.com", 443, true);
        long duration1 = System.currentTimeMillis() - start1;
        
        // Second call - should use cache
        long start2 = System.currentTimeMillis();
        ProxyInfo proxyInfo2 = resolver.resolveProxy("www.google.com", 443, true);
        long duration2 = System.currentTimeMillis() - start2;
        
        assertNotNull(proxyInfo1, "First result should not be null");
        assertNotNull(proxyInfo2, "Second result should not be null");
        
        // Cached call should be faster (usually much faster)
        System.out.println("First call duration: " + duration1 + "ms");
        System.out.println("Second call duration (cached): " + duration2 + "ms");
        
        assertTrue(duration2 < duration1 || duration2 < 10, 
                "Cached call should be faster or very quick");
    }

    @Test
    @DisplayName("Test: WinProxyJavaResolver clearCache works")
    void testClearCache() throws IOException {
        System.out.println("\n--- Test: Clear cache ---");
        
        WinProxyJavaResolver resolver = new WinProxyJavaResolver(300, null);
        
        // Populate cache
        resolver.resolveProxy("www.google.com", 443, true);
        
        // Clear cache
        resolver.clearCache();
        
        // Should resolve again (not from cache)
        ProxyInfo proxyInfo = resolver.resolveProxy("www.google.com", 443, true);
        assertNotNull(proxyInfo, "Result after cache clear should not be null");
        
        System.out.println("Cache cleared and re-resolved successfully");
    }

    @Test
    @DisplayName("Test: WinProxyJavaResolver testResolve method")
    void testTestResolveMethod() throws IOException {
        System.out.println("\n--- Test: testResolve method ---");
        
        WinProxyJavaResolver resolver = new WinProxyJavaResolver(300, null);
        
        // Test resolve for a full URL
        ProxyInfo proxyInfo = resolver.testResolve("https://api.openai.com/v1/chat/completions");
        
        assertNotNull(proxyInfo, "ProxyInfo should not be null");
        
        System.out.println("Test resolve for api.openai.com -> " + proxyInfo);
    }

    @Test
    @DisplayName("Test: WinProxyJavaResolver handles localhost")
    void testLocalhostResolution() throws IOException {
        System.out.println("\n--- Test: Localhost resolution ---");
        
        WinProxyJavaResolver resolver = new WinProxyJavaResolver(300, null);
        
        // Localhost should typically be DIRECT (bypassed)
        ProxyInfo proxyInfo = resolver.resolveProxy("localhost", 8080, false);
        
        assertNotNull(proxyInfo, "ProxyInfo should not be null");
        
        System.out.println("Resolved proxy for localhost:8080 -> " + proxyInfo);
        
        // On most systems, localhost is bypassed
        if (proxyInfo.isDirect()) {
            System.out.println("localhost correctly resolved to DIRECT");
        } else {
            System.out.println("Note: localhost resolved to proxy (unusual but possible configuration)");
        }
    }
}
