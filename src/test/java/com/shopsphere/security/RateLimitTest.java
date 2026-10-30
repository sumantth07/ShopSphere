package com.shopsphere.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RateLimitTest {
    @Test void blocksAfterLimitAndRecoversAfterWindow() {
        RateLimitFilter f = new RateLimitFilter();
        long t = 1_000_000;
        for (int i = 0; i < 10; i++) assertTrue(f.allow("ip|/api/auth/login", 10, t + i));
        assertFalse(f.allow("ip|/api/auth/login", 10, t + 20));
        assertTrue(f.allow("ip|/api/auth/login", 10, t + 61_000));
        assertTrue(f.allow("other|/api/auth/login", 10, t + 20));
    }
    @Test void loginIsStricterThanOtherAuthRoutes() {
        assertEquals(10, RateLimitFilter.limitFor("/api/auth/login", "POST"));
        assertEquals(20, RateLimitFilter.limitFor("/api/auth/verify-email", "POST"));
        assertEquals(0, RateLimitFilter.limitFor("/api/products", "GET"));
    }
}
