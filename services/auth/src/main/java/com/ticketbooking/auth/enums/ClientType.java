package com.ticketbooking.auth.enums;

public enum ClientType {
    WEB,
    MOBILE;

    public static ClientType fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return WEB;
        }
        try {
            return ClientType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return WEB;
        }
    }
}
