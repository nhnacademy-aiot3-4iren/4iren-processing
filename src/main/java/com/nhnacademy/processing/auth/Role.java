package com.nhnacademy.processing.auth;

public enum Role {
    ADMIN,
    NORMAL;

    public static Role fromString(String value) {
        if(value == null) {
            return null;
        }
        try {
            return Role.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
