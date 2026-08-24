package com.nhnacademy.processing.auth;

public record AuthUser(
        Long id,
        String role
) {
    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
