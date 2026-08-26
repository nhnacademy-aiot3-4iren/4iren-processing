package com.nhnacademy.processing.auth;

public record AuthUser(
        Long id,
        Role role
) {
    public boolean isAdmin() {
        return this.role == Role.ADMIN;
    }
}
