package com.project.subscription.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class SecretEncryptor {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private final SecretKeySpec keySpec;

    public SecretEncryptor(@Value("${webhook.master-key}") String masterKeyB64) {
        byte[] keyBytes = Base64.getDecoder().decode(masterKeyB64);
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
    }

    public EncryptedSecret encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes());

            return new EncryptedSecret(
                Base64.getEncoder().encodeToString(encrypted),
                Base64.getEncoder().encodeToString(iv)
            );
        } catch (Exception e) {
            throw new RuntimeException("密钥加密失败", e);
        }
    }

    public String decrypt(String encryptedB64, String ivB64) {
        try {
            byte[] encrypted = Base64.getDecoder().decode(encryptedB64);
            byte[] iv = Base64.getDecoder().decode(ivB64);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(encrypted));
        } catch (Exception e) {
            throw new RuntimeException("密钥解密失败", e);
        }
    }

    public record EncryptedSecret(String encrypted, String iv) {}
}
