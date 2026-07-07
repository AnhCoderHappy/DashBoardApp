package com.mdata.backend.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

@Component
public class CryptoUtil {

    private final String encryptionKey;
    private final boolean mockPlatforms;

    public CryptoUtil(
            @Value("${ENCRYPTION_KEY:your-secure-32-byte-hex-encryption-key-for-tokens}") String encryptionKey,
            @Value("${MOCK_PLATFORMS:false}") String mockPlatforms
    ) {
        this.encryptionKey = encryptionKey;
        this.mockPlatforms = "true".equalsIgnoreCase(mockPlatforms);
    }

    private SecretKeySpec getSecretKeySpec() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = digest.digest(encryptionKey.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(keyBytes, "AES");
    }

    public String encryptSecret(String text) {
        try {
            byte[] iv = new byte[12]; // 12 bytes IV for GCM
            new SecureRandom().nextBytes(iv);

            SecretKeySpec keySpec = getSecretKeySpec();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(128, iv); // 128 bit tag length
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec);

            byte[] encryptedBytes = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));

            // In Java doFinal appends the auth tag at the end of cipher text
            // We need to split them to match Node.js format: iv_hex:tag_hex:ciphertext_hex
            int tagLengthBytes = 16;
            int cipherTextLength = encryptedBytes.length - tagLengthBytes;

            byte[] cipherText = new byte[cipherTextLength];
            byte[] tag = new byte[tagLengthBytes];

            System.arraycopy(encryptedBytes, 0, cipherText, 0, cipherTextLength);
            System.arraycopy(encryptedBytes, cipherTextLength, tag, 0, tagLengthBytes);

            HexFormat hex = HexFormat.of();
            return hex.formatHex(iv) + ":" + hex.formatHex(tag) + ":" + hex.formatHex(cipherText);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt secret", e);
        }
    }

    public String decryptSecret(String encryptedData) {
        try {
            if (encryptedData == null) return null;

            String[] parts = encryptedData.split(":");
            if (parts.length != 3) {
                if ("mock-token".equals(encryptedData) || "mock-refresh-token".equals(encryptedData) || mockPlatforms) {
                    return encryptedData;
                }
                throw new IllegalArgumentException("Invalid encrypted data format");
            }

            HexFormat hex = HexFormat.of();
            byte[] iv = hex.parseHex(parts[0]);
            byte[] tag = hex.parseHex(parts[1]);
            byte[] cipherText = hex.parseHex(parts[2]);

            // Combine cipher text and tag for Java Cipher
            byte[] encryptedBytes = new byte[cipherText.length + tag.length];
            System.arraycopy(cipherText, 0, encryptedBytes, 0, cipherText.length);
            System.arraycopy(tag, 0, encryptedBytes, cipherText.length, tag.length);

            SecretKeySpec keySpec = getSecretKeySpec();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);

            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            if (mockPlatforms) {
                return encryptedData;
            }
            throw new RuntimeException("Failed to decrypt secret: " + e.getMessage(), e);
        }
    }
}
