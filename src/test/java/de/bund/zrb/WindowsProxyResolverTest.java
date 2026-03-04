package de.bund.zrb;

import de.bund.zrb.service.ProxyConfigService;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for WindowsProxyResolver to verify WPAD/PAC functionality.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class WindowsProxyResolverTest {

    @Test
    @Order(1)
    @DisplayName("Test: WPAD script resource exists")
    void testWpadScriptResourceExists() {
        System.out.println("\n--- Test: WPAD script resource exists ---");
        
        String resourcePath = "/ps/get-proxy-for-url.ps1";
        InputStream in = WindowsProxyResolver.class.getResourceAsStream(resourcePath);
        
        assertNotNull(in, "WPAD script resource should exist at " + resourcePath);
        
        try {
            in.close();
        } catch (Exception ignored) {}
        
        System.out.println("WPAD script resource found at: " + resourcePath);
    }

    @Test
    @Order(2)
    @DisplayName("Test: WindowsProxyResolver can be created")
    void testWindowsProxyResolverCreation() {
        System.out.println("\n--- Test: WindowsProxyResolver creation ---");
        
        // Skip if not Windows
        String os = System.getProperty("os.name");
        if (os == null || !os.toLowerCase().contains("win")) {
            System.out.println("Skipping: Not running on Windows");
            return;
        }
        
        ProxyConfigService configService = new ProxyConfigService();
        File workingDir = configService.getConfigDir();
        
        System.out.println("Working directory: " + workingDir.getAbsolutePath());
        
        WindowsProxyResolver resolver = new WindowsProxyResolver(
                workingDir,
                300, // 5 min cache TTL
                null // no traffic listener
        );
        
        assertNotNull(resolver, "Resolver should be created");
        System.out.println("WindowsProxyResolver created successfully!");
    }

    @Test
    @Order(3)
    @DisplayName("Test: WindowsProxyResolver can resolve proxy for google.com")
    void testProxyResolution() throws Exception {
        System.out.println("\n--- Test: Proxy resolution ---");
        
        // Skip if not Windows
        String os = System.getProperty("os.name");
        if (os == null || !os.toLowerCase().contains("win")) {
            System.out.println("Skipping: Not running on Windows");
            return;
        }
        
        ProxyConfigService configService = new ProxyConfigService();
        File workingDir = configService.getConfigDir();
        
        WindowsProxyResolver resolver = new WindowsProxyResolver(
                workingDir,
                300,
                null
        );
        
        // Resolve proxy for google.com:443 (HTTPS)
        ProxyInfo proxyInfo = resolver.resolveProxy("www.google.com", 443, true);
        
        assertNotNull(proxyInfo, "ProxyInfo should not be null");
        System.out.println("Resolved proxy for www.google.com:443: " + proxyInfo);
        
        // The result could be DIRECT or PROXY depending on system configuration
        assertFalse(proxyInfo.getCandidates().isEmpty(), "Should have at least one candidate");
    }

    @Test
    @Order(4)
    @DisplayName("Test: Script is extracted to working directory")
    void testScriptExtraction() throws Exception {
        System.out.println("\n--- Test: Script extraction ---");
        
        // Skip if not Windows
        String os = System.getProperty("os.name");
        if (os == null || !os.toLowerCase().contains("win")) {
            System.out.println("Skipping: Not running on Windows");
            return;
        }
        
        ProxyConfigService configService = new ProxyConfigService();
        File workingDir = configService.getConfigDir();
        File scriptFile = new File(workingDir, "get-proxy-for-url.ps1");
        
        // Delete existing script to test extraction
        if (scriptFile.exists()) {
            scriptFile.delete();
        }
        assertFalse(scriptFile.exists(), "Script should be deleted before test");
        
        // Create resolver - this should extract the script
        WindowsProxyResolver resolver = new WindowsProxyResolver(
                workingDir,
                300,
                null
        );
        
        // Trigger extraction by resolving a proxy
        try {
            resolver.resolveProxy("test.example.com", 80, false);
        } catch (Exception e) {
            // Script execution might fail, but extraction should work
            System.out.println("Resolution failed (expected if no proxy): " + e.getMessage());
        }
        
        assertTrue(scriptFile.exists(), "Script should be extracted after first use");
        System.out.println("Script extracted to: " + scriptFile.getAbsolutePath());
        System.out.println("Script size: " + scriptFile.length() + " bytes");
    }
}

