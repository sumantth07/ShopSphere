package com.shopsphere.sellers;

import com.shopsphere.exception.ApiException;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Resolves "the seller acting right now" from the authenticated user: never from a request field. */
@Service
public class SellerService {
    private final JdbcClient jdbc;
    public SellerService(JdbcClient jdbc) { this.jdbc = jdbc; }

    public record SellerRef(long id, String status) {}

    public Optional<SellerRef> find(long userId) {
        return jdbc.sql("select id, application_status from sellers where user_id = :u").param("u", userId).query((rs, n) -> new SellerRef(rs.getLong(1), rs.getString(2))).optional();
    }

    /** The seller id for an APPROVED seller; otherwise 403. Suspended or pending sellers cannot manage anything. */
    public long requireApproved(long userId) {
        SellerRef s = find(userId).orElseThrow(() -> ApiException.forbidden("NOT_A_SELLER", "You do not have a seller account."));
        if (!"APPROVED".equals(s.status())) throw ApiException.forbidden("SELLER_NOT_APPROVED", "Your seller account is " + s.status().toLowerCase() + ".");
        return s.id();
    }
}
