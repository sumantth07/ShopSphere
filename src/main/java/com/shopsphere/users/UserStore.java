package com.shopsphere.users;

import java.sql.Types;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class UserStore {
    public record UserRow(long id, String fullName, String email, boolean emailVerified, String role, String status, String passwordHash) {}

    private final JdbcClient jdbc;
    public UserStore(JdbcClient jdbc) { this.jdbc = jdbc; }

    public static String normalizeEmail(String email) { return email == null ? null : email.trim().toLowerCase(); }

    private static UserRow map(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new UserRow(rs.getLong("id"), rs.getString("full_name"), rs.getString("email"), rs.getBoolean("email_verified"),
            rs.getString("role"), rs.getString("status"), rs.getString("password_hash"));
    }

    public Optional<UserRow> findByEmail(String email) {
        return jdbc.sql("select * from users where email = :e").param("e", normalizeEmail(email)).query(UserStore::map).optional();
    }
    public Optional<UserRow> findById(long id) {
        return jdbc.sql("select * from users where id = :id").param("id", id).query(UserStore::map).optional();
    }
    public long create(String fullName, String email, String passwordHash, boolean verified) {
        return jdbc.sql("insert into users (full_name, email, password_hash, email_verified) values (:n, :e, :p, :v) returning id")
            .param("n", fullName).param("e", normalizeEmail(email)).param("p", passwordHash, Types.VARCHAR).param("v", verified)
            .query(Long.class).single();
    }
    public void updateRegistration(long id, String fullName, String passwordHash) {
        jdbc.sql("update users set full_name = :n, password_hash = :p, updated_at = now() where id = :id")
            .param("n", fullName).param("p", passwordHash).param("id", id).update();
    }
    public void markVerified(long id) { jdbc.sql("update users set email_verified = true, updated_at = now() where id = :id").param("id", id).update(); }
    public void setPassword(long id, String hash) { jdbc.sql("update users set password_hash = :p, updated_at = now() where id = :id").param("p", hash).param("id", id).update(); }
    public void clearPassword(long id) { jdbc.sql("update users set password_hash = null, updated_at = now() where id = :id").param("id", id).update(); }
    public void touchLogin(long id) { jdbc.sql("update users set last_login_at = now() where id = :id").param("id", id).update(); }
    public void updateName(long id, String name) { jdbc.sql("update users set full_name = :n, updated_at = now() where id = :id").param("n", name).param("id", id).update(); }
    public boolean adminExists() { return jdbc.sql("select exists(select 1 from users where role = 'ADMIN')").query(Boolean.class).single(); }
    public long createAdmin(String name, String email, String hash) {
        return jdbc.sql("insert into users (full_name, email, password_hash, email_verified, role) values (:n,:e,:p,true,'ADMIN') returning id")
            .param("n", name).param("e", normalizeEmail(email)).param("p", hash).query(Long.class).single();
    }
}
