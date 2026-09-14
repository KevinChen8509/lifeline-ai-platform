package com.datafabric.dataservice.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * production-audit Blocker 2 修复：服务级 key 的生成 / 哈希 / 形态判定，集中一处。
 *
 * 存储与序列化只允许哈希（SHA-256 hex，64 位十六进制）；明文仅在
 * 发布 / 轮换的返回值里出现一次（KeyedServiceView），校验时对入参先哈希再恒时比较。
 *
 * 旧注册表行（W6-B~W6-D 落库）存的是明文 sk-w6-… —— loadAll 时按
 * {@link #isPlaintextFormat} 识别并自愈迁移为哈希（见 JdbcRegistryStore.fromRow）。
 */
final class ServiceKeys {

    /** 明文形态：sk-w6- + 32 位小写十六进制（生成即此形态，用于识别存量明文行） */
    private static final Pattern PLAINTEXT = Pattern.compile("^sk-w6-[0-9a-f]{32}$");

    private static final SecureRandom RANDOM = new SecureRandom();

    private ServiceKeys() {
    }

    /** 生成明文 key（仅发布/轮换时调用一次，随响应一次性返回） */
    static String generate() {
        byte[] buf = new byte[16];
        RANDOM.nextBytes(buf);
        return "sk-w6-" + HexFormat.of().formatHex(buf);
    }

    /** SHA-256 哈希（小写 hex，64 位）——存储与比较的统一形态 */
    static String sha256Hex(String plaintext) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 缺少 SHA-256（不可能）", e);
        }
    }

    /** 是否明文形态（存量行迁移判定；哈希是 64 hex 无前缀，不会误判） */
    static boolean isPlaintextFormat(String value) {
        return value != null && PLAINTEXT.matcher(value).matches();
    }

    /** 日志脱敏用：末 4 位 */
    static String last4(String key) {
        return key.length() <= 4 ? "****" : key.substring(key.length() - 4);
    }
}
