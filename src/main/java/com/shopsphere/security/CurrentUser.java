package com.shopsphere.security;

import com.shopsphere.exception.ApiException;
import org.springframework.security.oauth2.jwt.Jwt;

public final class CurrentUser {
    private CurrentUser() {}
    public static long id(Jwt jwt) {
        if (jwt == null) throw ApiException.unauthorized("UNAUTHENTICATED", "Please sign in.");
        return Long.parseLong(jwt.getSubject());
    }
}
