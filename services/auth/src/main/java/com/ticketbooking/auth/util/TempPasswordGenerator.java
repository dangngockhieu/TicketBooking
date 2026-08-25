package com.ticketbooking.auth.util;

import java.security.SecureRandom;

/**
 * Sinh mật khẩu tạm ngẫu nhiên đủ mạnh cho tài khoản Organizer do Admin tạo
 * (xem AuthServiceImpl#createOrganizer). Không dùng Math.random().
 */
public final class TempPasswordGenerator {

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"; // bỏ I, O dễ nhầm
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz"; // bỏ l, o dễ nhầm
    private static final String DIGITS = "23456789"; // bỏ 0, 1 dễ nhầm
    private static final String SPECIAL = "!@#$%";
    private static final String ALL = UPPER + LOWER + DIGITS + SPECIAL;
    private static final int LENGTH = 12;

    private static final SecureRandom RANDOM = new SecureRandom();

    private TempPasswordGenerator() {
    }

    public static String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        // Đảm bảo có đủ mỗi loại ký tự để không bị validator "mật khẩu yếu" từ chối
        sb.append(UPPER.charAt(RANDOM.nextInt(UPPER.length())));
        sb.append(LOWER.charAt(RANDOM.nextInt(LOWER.length())));
        sb.append(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
        sb.append(SPECIAL.charAt(RANDOM.nextInt(SPECIAL.length())));
        for (int i = sb.length(); i < LENGTH; i++) {
            sb.append(ALL.charAt(RANDOM.nextInt(ALL.length())));
        }
        // Xáo trộn để 4 ký tự bắt buộc không luôn nằm ở đầu
        char[] chars = sb.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }
}
