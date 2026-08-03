package com.datafabric.dataservice.governance;

/**
 * 字段脱敏工具 - 静态方法，无状态
 *
 *   phone: 13800000001 → 138****0001
 *   idCard: 110101199001011234 → 110101********1234
 */
public final class MaskingUtil {

    private MaskingUtil() {}

    /** 138****0001 */
    public static String phone(String s) {
        if (s == null || s.length() < 8) return s;
        return s.substring(0, 3) + "****" + s.substring(s.length() - 4);
    }

    /** 110101********1234 */
    public static String idCard(String s) {
        if (s == null || s.length() < 11) return s;
        return s.substring(0, 6) + "********" + s.substring(s.length() - 4);
    }
}
