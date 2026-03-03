package de.bund.zrb.auth;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Handles authentication and optional encryption for gateway connections.
 * 
 * Authentication modes:
 * - NONE: No authentication (open gateway)
 * - PASSKEY: Simple shared passkey (legacy, not recommended)
 * - TOKEN: Challenge-response with HMAC-SHA256 (recommended)
 * 
 * Encryption modes:
 * - NONE: Plain text (for local/trusted networks)
 * - AES: AES-256-CBC encryption with PBKDF2 key derivation
 */
public class GatewayAuthenticator {

    public enum AuthMode {
        NONE,
        PASSKEY,
        TOKEN
    }

    public enum EncryptionMode {
        NONE,
        AES
    }

    private static final int PBKDF2_ITERATIONS = 10000;
    private static final int KEY_LENGTH = 256;
    private static final int IV_LENGTH = 16;
    private static final int SALT_LENGTH = 16;

    private final AuthMode authMode;
    private final EncryptionMode encryptionMode;
    private final String sharedSecret;
    private final SecureRandom random;

    private byte[] sessionKey;
    private byte[] sessionIv;

    public GatewayAuthenticator(AuthMode authMode, EncryptionMode encryptionMode, String sharedSecret) {
        this.authMode = authMode;
        this.encryptionMode = encryptionMode;
        this.sharedSecret = sharedSecret != null ? sharedSecret : "";
        this.random = new SecureRandom();
    }

    /**
     * Create authenticator with default settings (TOKEN auth, no encryption).
     */
    public static GatewayAuthenticator withToken(String secret) {
        return new GatewayAuthenticator(AuthMode.TOKEN, EncryptionMode.NONE, secret);
    }

    /**
     * Create authenticator with TOKEN auth and AES encryption.
     */
    public static GatewayAuthenticator withTokenAndEncryption(String secret) {
        return new GatewayAuthenticator(AuthMode.TOKEN, EncryptionMode.AES, secret);
    }

    /**
     * Create authenticator for legacy passkey mode (backward compatible).
     */
    public static GatewayAuthenticator withPasskey(String passkey) {
        return new GatewayAuthenticator(AuthMode.PASSKEY, EncryptionMode.NONE, passkey);
    }

    /**
     * Create authenticator with no authentication (open gateway).
     */
    public static GatewayAuthenticator noAuth() {
        return new GatewayAuthenticator(AuthMode.NONE, EncryptionMode.NONE, null);
    }

    // --- Server-side methods ---

    /**
     * Generate a challenge for TOKEN authentication.
     * @return Base64-encoded challenge
     */
    public String generateChallenge() {
        byte[] challenge = new byte[32];
        random.nextBytes(challenge);
        return Base64.getEncoder().encodeToString(challenge);
    }

    /**
     * Verify client's response to a challenge.
     * @param challenge The original challenge sent to client
     * @param response The client's response
     * @return true if authentication successful
     */
    public boolean verifyResponse(String challenge, String response) {
        if (authMode == AuthMode.NONE) {
            return true;
        }
        
        if (authMode == AuthMode.PASSKEY) {
            // Legacy mode: response is just the passkey
            return sharedSecret.equals(response);
        }

        // TOKEN mode: response is HMAC-SHA256(challenge, secret)
        try {
            String expected = computeHmac(challenge, sharedSecret);
            return constantTimeEquals(expected, response);
        } catch (Exception e) {
            return false;
        }
    }

    // --- Client-side methods ---

    /**
     * Compute response to a challenge (for TOKEN auth).
     * @param challenge The challenge from server
     * @return Response to send to server
     */
    public String computeResponse(String challenge) {
        if (authMode == AuthMode.NONE) {
            return "";
        }
        
        if (authMode == AuthMode.PASSKEY) {
            return sharedSecret;
        }

        // TOKEN mode: compute HMAC-SHA256(challenge, secret)
        try {
            return computeHmac(challenge, sharedSecret);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute response", e);
        }
    }

    // --- Encryption methods ---

    /**
     * Initialize encryption session with derived key.
     * Call this after successful authentication.
     */
    public void initEncryptionSession() throws Exception {
        if (encryptionMode == EncryptionMode.NONE) {
            return;
        }

        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);

        sessionKey = deriveKey(sharedSecret, salt);
        sessionIv = new byte[IV_LENGTH];
        random.nextBytes(sessionIv);
    }

    /**
     * Encrypt data for transmission.
     */
    public byte[] encrypt(byte[] data) throws Exception {
        if (encryptionMode == EncryptionMode.NONE) {
            return data;
        }

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(sessionKey, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(sessionIv);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        return cipher.doFinal(data);
    }

    /**
     * Decrypt received data.
     */
    public byte[] decrypt(byte[] encryptedData) throws Exception {
        if (encryptionMode == EncryptionMode.NONE) {
            return encryptedData;
        }

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(sessionKey, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(sessionIv);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        return cipher.doFinal(encryptedData);
    }

    // --- Helper methods ---

    private String computeHmac(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKey);
        byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hmacBytes);
    }

    private byte[] deriveKey(String password, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH);
        SecretKey secretKey = factory.generateSecret(spec);
        return secretKey.getEncoded();
    }

    /**
     * Constant-time comparison to prevent timing attacks.
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        
        if (aBytes.length != bBytes.length) {
            return false;
        }
        
        int result = 0;
        for (int i = 0; i < aBytes.length; i++) {
            result |= aBytes[i] ^ bBytes[i];
        }
        return result == 0;
    }

    // --- Getters ---

    public AuthMode getAuthMode() {
        return authMode;
    }

    public EncryptionMode getEncryptionMode() {
        return encryptionMode;
    }

    public boolean isEncryptionEnabled() {
        return encryptionMode != EncryptionMode.NONE;
    }

    public boolean isAuthEnabled() {
        return authMode != AuthMode.NONE;
    }
}

