package com.shopsphere.auth;

import jakarta.validation.constraints.*;

public final class AuthDtos {
    private AuthDtos() {}
    public static final String PASSWORD_RULE = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";
    public static final String PASSWORD_MSG = "Password must be 8-72 characters and include a letter and a number.";

    public record RegisterRequest(@NotBlank @Size(max = 120) String fullName, @NotBlank @Email @Size(max = 254) String email,
                                  @NotBlank @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MSG) String password,
                                  @NotBlank String confirmPassword) {}
    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
    public record EmailRequest(@NotBlank @Email String email) {}
    public record VerifyRequest(@NotBlank @Email String email, @NotBlank @Pattern(regexp = "^\\d{6}$", message = "Enter the 6-digit code.") String code) {}
    public record ResetRequest(@NotBlank @Email String email, @NotBlank @Pattern(regexp = "^\\d{6}$", message = "Enter the 6-digit code.") String code,
                               @NotBlank @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MSG) String newPassword) {}
    public record ConfirmLinkRequest(@NotBlank String token, @NotBlank @Pattern(regexp = "^\\d{6}$", message = "Enter the 6-digit code.") String code) {}
    public record ChangePasswordRequest(String currentPassword, @NotBlank @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MSG) String newPassword) {}

    public record UserDto(long id, String fullName, String email, boolean emailVerified, String role,
                          boolean hasPassword, boolean googleLinked, String sellerStatus) {}
    public record AuthResponse(String accessToken, long expiresIn, UserDto user) {}
    public record MessageResponse(String message) {}
}
