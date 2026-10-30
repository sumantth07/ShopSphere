package com.shopsphere.auth;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class OtpHashTest {
    @Test void codesAreSixDigits() { for (int i = 0; i < 200; i++) assertTrue(OtpService.newCode().matches("\\d{6}")); }
    @Test void tokensAreRandomAndHashedDeterministically() {
        String a = OtpService.newToken(), b = OtpService.newToken();
        assertNotEquals(a, b);
        assertEquals(64, a.length());
        assertEquals(OtpService.hashToken(a), OtpService.hashToken(a));
        assertNotEquals(a, OtpService.hashToken(a));   // the stored value is never the raw token
    }
    @Test void passwordPolicy() {
        assertTrue("Passw0rdX".matches(AuthDtos.PASSWORD_RULE));
        assertFalse("short1".matches(AuthDtos.PASSWORD_RULE));
        assertFalse("nodigitshere".matches(AuthDtos.PASSWORD_RULE));
        assertFalse("12345678".matches(AuthDtos.PASSWORD_RULE));
    }
}
