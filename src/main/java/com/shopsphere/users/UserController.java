package com.shopsphere.users;

import com.shopsphere.auth.AuthDtos.UserDto;
import com.shopsphere.auth.AuthService;
import com.shopsphere.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
public class UserController {
    public record UpdateProfile(@NotBlank @Size(max = 120) String fullName) {}
    public record Notification(long id, String type, String message, boolean read, OffsetDateTime createdAt) {}

    private final AuthService auth; private final UserStore users; private final JdbcClient jdbc;
    public UserController(AuthService auth, UserStore users, JdbcClient jdbc) { this.auth = auth; this.users = users; this.jdbc = jdbc; }

    @GetMapping public UserDto me(@AuthenticationPrincipal Jwt jwt) { return auth.userDto(CurrentUser.id(jwt)); }

    @PutMapping public UserDto update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfile r) {
        long id = CurrentUser.id(jwt); users.updateName(id, r.fullName().trim()); return auth.userDto(id);
    }

    @GetMapping("/notifications")
    public List<Notification> notifications(@AuthenticationPrincipal Jwt jwt) {
        return jdbc.sql("select id, type, message, read_at is not null, created_at from notifications where user_id = :u order by created_at desc limit 30")
            .param("u", CurrentUser.id(jwt)).query((rs, n) -> new Notification(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBoolean(4), rs.getObject(5, OffsetDateTime.class))).list();
    }
}
