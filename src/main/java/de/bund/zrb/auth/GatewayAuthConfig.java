package de.bund.zrb.auth;

/**
 * Configuration for gateway authentication and encryption.
 */
public class GatewayAuthConfig {

    private GatewayAuthenticator.AuthMode authMode;
    private GatewayAuthenticator.EncryptionMode encryptionMode;
    private String sharedSecret;
    private String username;  // Optional username for future OAuth support

    public GatewayAuthConfig() {
        // Defaults: TOKEN auth without encryption
        this.authMode = GatewayAuthenticator.AuthMode.TOKEN;
        this.encryptionMode = GatewayAuthenticator.EncryptionMode.NONE;
        this.sharedSecret = "";
        this.username = "";
    }

    /**
     * Create config for legacy passkey mode (backward compatible).
     */
    public static GatewayAuthConfig legacyPasskey(String passkey) {
        GatewayAuthConfig config = new GatewayAuthConfig();
        config.setAuthMode(GatewayAuthenticator.AuthMode.PASSKEY);
        config.setEncryptionMode(GatewayAuthenticator.EncryptionMode.NONE);
        config.setSharedSecret(passkey);
        return config;
    }

    /**
     * Create config with TOKEN auth and optional encryption.
     */
    public static GatewayAuthConfig tokenAuth(String secret, boolean encrypted) {
        GatewayAuthConfig config = new GatewayAuthConfig();
        config.setAuthMode(GatewayAuthenticator.AuthMode.TOKEN);
        config.setEncryptionMode(encrypted ? GatewayAuthenticator.EncryptionMode.AES : GatewayAuthenticator.EncryptionMode.NONE);
        config.setSharedSecret(secret);
        return config;
    }

    /**
     * Create authenticator from this config.
     */
    public GatewayAuthenticator createAuthenticator() {
        return new GatewayAuthenticator(authMode, encryptionMode, sharedSecret);
    }

    // --- Getters and Setters ---

    public GatewayAuthenticator.AuthMode getAuthMode() {
        return authMode;
    }

    public void setAuthMode(GatewayAuthenticator.AuthMode authMode) {
        this.authMode = authMode;
    }

    public GatewayAuthenticator.EncryptionMode getEncryptionMode() {
        return encryptionMode;
    }

    public void setEncryptionMode(GatewayAuthenticator.EncryptionMode encryptionMode) {
        this.encryptionMode = encryptionMode;
    }

    public String getSharedSecret() {
        return sharedSecret;
    }

    public void setSharedSecret(String sharedSecret) {
        this.sharedSecret = sharedSecret != null ? sharedSecret : "";
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username != null ? username : "";
    }

    public boolean isEncrypted() {
        return encryptionMode == GatewayAuthenticator.EncryptionMode.AES;
    }

    public boolean isAuthRequired() {
        return authMode != GatewayAuthenticator.AuthMode.NONE && !sharedSecret.isEmpty();
    }
}

