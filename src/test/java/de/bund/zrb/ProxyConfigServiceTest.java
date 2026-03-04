package de.bund.zrb;

import de.bund.zrb.config.ProxyConfig;
import de.bund.zrb.service.ProxyConfigService;
import org.junit.jupiter.api.*;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for ProxyConfigService to verify settings persistence.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ProxyConfigServiceTest {

    private ProxyConfigService configService;
    private File testConfigDir;

    @BeforeEach
    void setUp() {
        configService = new ProxyConfigService();
        testConfigDir = configService.getConfigDir();
    }

    @Test
    @Order(1)
    @DisplayName("Test: WPAD setting is persisted correctly")
    void testWpadSettingPersistence() throws Exception {
        System.out.println("\n--- Test: WPAD persistence ---");
        
        // Load current config
        ProxyConfig cfg = configService.loadConfig();
        boolean originalWpadEnabled = cfg.isClientOutboundProxyEnabled();
        System.out.println("Original WPAD enabled: " + originalWpadEnabled);
        
        // Toggle WPAD setting
        cfg.setClientOutboundProxyEnabled(!originalWpadEnabled);
        configService.saveConfig(cfg);
        System.out.println("Saved WPAD enabled: " + cfg.isClientOutboundProxyEnabled());
        
        // Reload and verify
        ProxyConfig reloaded = configService.loadConfig();
        System.out.println("Reloaded WPAD enabled: " + reloaded.isClientOutboundProxyEnabled());
        
        assertEquals(!originalWpadEnabled, reloaded.isClientOutboundProxyEnabled(), 
                "WPAD setting should be persisted");
        
        // Restore original setting
        reloaded.setClientOutboundProxyEnabled(originalWpadEnabled);
        configService.saveConfig(reloaded);
        System.out.println("Restored WPAD enabled: " + originalWpadEnabled);
    }

    @Test
    @Order(2)
    @DisplayName("Test: Relay mode setting is persisted correctly")
    void testRelayModeSettingPersistence() throws Exception {
        System.out.println("\n--- Test: Relay mode settings persistence ---");
        
        ProxyConfig cfg = configService.loadConfig();
        boolean originalRelayMode = cfg.isRelayModeEnabled();
        System.out.println("Original Relay mode: " + originalRelayMode);
        
        // Toggle setting
        cfg.setRelayModeEnabled(!originalRelayMode);
        configService.saveConfig(cfg);
        
        // Reload and verify
        ProxyConfig reloaded = configService.loadConfig();
        assertEquals(!originalRelayMode, reloaded.isRelayModeEnabled());
        System.out.println("Relay mode persisted correctly!");
        
        // Restore
        reloaded.setRelayModeEnabled(originalRelayMode);
        configService.saveConfig(reloaded);
    }

    @Test
    @Order(3)
    @DisplayName("Test: All WPAD parameters are persisted")
    void testAllWpadParametersPersistence() throws Exception {
        System.out.println("\n--- Test: All WPAD parameters ---");
        
        ProxyConfig cfg = configService.loadConfig();
        
        // Set specific values
        cfg.setClientOutboundProxyEnabled(true);
        cfg.setClientOutboundProxyCacheTtlSeconds(600);
        cfg.setClientOutboundProxyConnectTimeoutMillis(15000);
        cfg.setClientOutboundProxyHandshakeTimeoutMillis(20000);
        configService.saveConfig(cfg);
        
        // Reload and verify
        ProxyConfig reloaded = configService.loadConfig();
        assertTrue(reloaded.isClientOutboundProxyEnabled());
        assertEquals(600, reloaded.getClientOutboundProxyCacheTtlSeconds());
        assertEquals(15000, reloaded.getClientOutboundProxyConnectTimeoutMillis());
        assertEquals(20000, reloaded.getClientOutboundProxyHandshakeTimeoutMillis());
        
        System.out.println("All WPAD parameters persisted correctly!");
        
        // Restore defaults
        reloaded.setClientOutboundProxyEnabled(false);
        reloaded.setClientOutboundProxyCacheTtlSeconds(300);
        reloaded.setClientOutboundProxyConnectTimeoutMillis(10000);
        reloaded.setClientOutboundProxyHandshakeTimeoutMillis(10000);
        configService.saveConfig(reloaded);
    }

    @Test
    @Order(4)
    @DisplayName("Test: Config file exists after save")
    void testConfigFileExists() throws Exception {
        System.out.println("\n--- Test: Config file exists ---");
        
        ProxyConfig cfg = configService.loadConfig();
        configService.saveConfig(cfg);
        
        File configFile = configService.getConfigFile();
        assertTrue(configFile.exists(), "Config file should exist after save");
        System.out.println("Config file location: " + configFile.getAbsolutePath());
        System.out.println("Config file size: " + configFile.length() + " bytes");
    }
}

