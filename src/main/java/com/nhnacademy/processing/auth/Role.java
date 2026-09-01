package com.nhnacademy.processing.auth;

public enum Role {
    ADMIN,
    OWNER,
    NORMAL;

    public static Role from(String roleStr) {
        if (roleStr == null || roleStr.isBlank()) {
            throw new IllegalArgumentException("유효하지 않은 Role 값입니다.");
        }
        String cleanRole = roleStr.startsWith("ROLE_") ? roleStr.substring(5) : roleStr;
        try {
            return Role.valueOf(cleanRole.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 Role 값입니다.");
        }
    }

    public static Role fromString(String roleStr) {
        return from(roleStr);
    }

    public boolean isManagerOrAdmin() {
        return this == ADMIN || this == OWNER;
    }
}