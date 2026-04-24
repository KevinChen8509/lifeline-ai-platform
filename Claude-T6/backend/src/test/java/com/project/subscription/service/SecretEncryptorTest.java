package com.project.subscription.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class SecretEncryptorTest {

    private SecretEncryptor encryptor;

    @BeforeEach
    void setUp() {
        // Generate a valid 256-bit AES key for testing
        byte[] key = new byte[32];
        new java.security.SecureRandom().nextBytes(key);
        String masterKeyB64 = Base64.getEncoder().encodeToString(key);
        encryptor = new SecretEncryptor(masterKeyB64);
    }

    @Test
    void encryptDecrypt_shouldRoundTrip() {
        String plaintext = "whsec_abcdef1234567890";

        SecretEncryptor.EncryptedSecret encrypted = encryptor.encrypt(plaintext);
        assertNotNull(encrypted.encrypted());
        assertNotNull(encrypted.iv());
        assertNotEquals(plaintext, encrypted.encrypted());

        String decrypted = encryptor.decrypt(encrypted.encrypted(), encrypted.iv());
        assertEquals(plaintext, decrypted);
    }

    @Test
    void encrypt_shouldProduceDifferentCiphertextEachTime() {
        String plaintext = "whsec_same_input";

        SecretEncryptor.EncryptedSecret e1 = encryptor.encrypt(plaintext);
        SecretEncryptor.EncryptedSecret e2 = encryptor.encrypt(plaintext);

        assertNotEquals(e1.encrypted(), e2.encrypted(), "Different IVs should produce different ciphertext");
        assertNotEquals(e1.iv(), e2.iv(), "Each encryption should use a different IV");
    }

    @Test
    void decrypt_shouldFailWithWrongIv() {
        String plaintext = "whsec_test";
        SecretEncryptor.EncryptedSecret encrypted = encryptor.encrypt(plaintext);

        assertThrows(RuntimeException.class, () ->
            encryptor.decrypt(encrypted.encrypted(), Base64.getEncoder().encodeToString(new byte[12]))
        );
    }
}
