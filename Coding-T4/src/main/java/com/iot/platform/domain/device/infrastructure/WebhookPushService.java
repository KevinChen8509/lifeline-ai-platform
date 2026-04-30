package com.iot.platform.domain.device.infrastructure;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Webhook 推送服务。
 * 构建自建信封格式、HMAC-SHA256 签名、AES-256-GCM 加密。
 */
@Component
public class WebhookPushService {

    private static final SecureRandom RNG = new SecureRandom();

    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128; // bits

    /**
     * 构建推送信封（明文模式）。
     *
     * @param type    消息类型
     * @param payload 消息体
     * @param secret  签名密钥
     * @return 完整信封 Map
     */
    public Map<String, Object> buildEnvelope(String type, Map<String, Object> payload, String secret) {
        String id = generateEventId();
        long timestamp = System.currentTimeMillis();
        String payloadJson = toJson(payload);

        String signingString = timestamp + "\n" + id + "\n" + payloadJson;
        String signature = sign(signingString, secret);

        return Map.of(
            "version", "1.0",
            "id", id,
            "timestamp", timestamp,
            "type", type,
            "signature", signature,
            "payload", payloadJson
        );
    }

    /**
     * 构建加密推送信封（AES-256-GCM 模式）。
     * payload 字段为 Base64(IV[12] + ciphertext + authTag[16])，信封其余字段不变。
     */
    public Map<String, Object> buildEncryptedEnvelope(String type, Map<String, Object> payload,
                                                        String hmacSecret, String aesKeyBase64) {
        String id = generateEventId();
        long timestamp = System.currentTimeMillis();
        String payloadJson = toJson(payload);

        String encryptedPayload = encryptPayload(payloadJson, aesKeyBase64);

        // 签名仍基于原始 payload JSON（客户端先验签，再解密）
        String signingString = timestamp + "\n" + id + "\n" + payloadJson;
        String signature = sign(signingString, hmacSecret);

        return Map.of(
            "version", "1.0",
            "id", id,
            "timestamp", timestamp,
            "type", type,
            "signature", signature,
            "payload", encryptedPayload,
            "encrypted", true
        );
    }

    /**
     * AES-256-GCM 加密。
     * @param plaintext     明文 JSON
     * @param aesKeyBase64  Base64 编码的 256 位密钥
     * @return Base64(IV[12] + ciphertext + authTag[16])
     */
    public String encryptPayload(String plaintext, String aesKeyBase64) {
        try {
            byte[] keyBytes = java.util.Base64.getDecoder().decode(aesKeyBase64);
            byte[] iv = new byte[GCM_IV_LENGTH];
            RNG.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(keyBytes, "AES"),
                new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buf = ByteBuffer.allocate(iv.length + ciphertext.length);
            buf.put(iv);
            buf.put(ciphertext);
            return java.util.Base64.getEncoder().encodeToString(buf.array());
        } catch (Exception e) {
            throw new RuntimeException("AES-256-GCM encryption failed", e);
        }
    }

    /**
     * AES-256-GCM 解密。
     * @param encrypted     Base64(IV[12] + ciphertext + authTag[16])
     * @param aesKeyBase64  Base64 编码的 256 位密钥
     * @return 解密后的明文 JSON
     */
    public String decryptPayload(String encrypted, String aesKeyBase64) {
        try {
            byte[] keyBytes = java.util.Base64.getDecoder().decode(aesKeyBase64);
            byte[] combined = java.util.Base64.getDecoder().decode(encrypted);

            ByteBuffer buf = ByteBuffer.wrap(combined);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buf.get(iv);
            byte[] ciphertext = new byte[buf.remaining()];
            buf.get(ciphertext);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(keyBytes, "AES"),
                new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] plain = cipher.doFinal(ciphertext);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES-256-GCM decryption failed", e);
        }
    }

    /**
     * HMAC-SHA256 签名。
     *
     * @param data   签名字符串
     * @param secret 密钥
     * @return 签名值 "hmac_sha256=v1,{hex}"
     */
    public String sign(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return "hmac_sha256=v1," + HexFormat.of().formatHex(hmac);
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 signing failed", e);
        }
    }

    /**
     * 验证签名。
     */
    public boolean verify(String data, String secret, String expectedSignature) {
        String actual = sign(data, secret);
        return timeSafeEquals(actual, expectedSignature);
    }

    private String generateEventId() {
        byte[] nonce = new byte[4];
        RNG.nextBytes(nonce);
        return "evt_" + System.currentTimeMillis() + "_" + HexFormat.of().formatHex(nonce);
    }

    private String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : new TreeMap<>(map).entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(e.getKey()).append("\":");
            sb.append(toJsonValue(e.getValue()));
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private String toJsonValue(Object v) {
        if (v == null) return "null";
        if (v instanceof String) return "\"" + escapeJson((String) v) + "\"";
        if (v instanceof Number) return v.toString();
        if (v instanceof Boolean) return v.toString();
        if (v instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) v;
            return toJson(m);
        }
        if (v instanceof List) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : (List<?>) v) {
                if (!first) sb.append(",");
                sb.append(toJsonValue(item));
                first = false;
            }
            sb.append("]");
            return sb.toString();
        }
        if (v instanceof Map.Entry) {
            Map.Entry<?, ?> entry = (Map.Entry<?, ?>) v;
            return "{\"" + entry.getKey() + "\":" + toJsonValue(entry.getValue()) + "}";
        }
        return "\"" + escapeJson(v.toString()) + "\"";
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private boolean timeSafeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
