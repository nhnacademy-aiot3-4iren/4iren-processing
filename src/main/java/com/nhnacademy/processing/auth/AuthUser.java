package com.nhnacademy.processing.auth;

public record AuthUser(
        Long id,
        Role role
) {
    public boolean hasAdminPrivilege() {
        return this.role == Role.ADMIN || this.role == Role.OWNER;
    }

    public boolean isAdmin() {
        return hasAdminPrivilege();
    }
}