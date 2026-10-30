package com.shopsphere.wishlist;

import com.shopsphere.exception.ApiException;
import com.shopsphere.products.ProductCard;
import com.shopsphere.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wishlist")
public class WishlistController {
    public record AddRequest(@NotNull Long productId) {}
    private final JdbcClient jdbc;
    public WishlistController(JdbcClient jdbc) { this.jdbc = jdbc; }

    @GetMapping
    public List<ProductCard> list(@AuthenticationPrincipal Jwt jwt) {
        return jdbc.sql("select " + ProductCard.COLS + ProductCard.FROM + " join wishlist_items w on w.product_id = p.id where w.user_id = :u order by w.created_at desc")
            .param("u", CurrentUser.id(jwt)).query(ProductCard::map).list();
    }

    @GetMapping("/ids")
    public List<Long> ids(@AuthenticationPrincipal Jwt jwt) {
        return jdbc.sql("select product_id from wishlist_items where user_id = :u").param("u", CurrentUser.id(jwt)).query(Long.class).list();
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public void add(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddRequest r) {
        boolean exists = jdbc.sql("select exists(select 1 from products where id=:p and active)").param("p", r.productId()).query(Boolean.class).single();
        if (!exists) throw ApiException.notFound("Product not found.");
        jdbc.sql("insert into wishlist_items (user_id, product_id) values (:u, :p) on conflict do nothing").param("u", CurrentUser.id(jwt)).param("p", r.productId()).update();
    }

    @DeleteMapping("/items/{productId}")
    public void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable long productId) {
        jdbc.sql("delete from wishlist_items where user_id=:u and product_id=:p").param("u", CurrentUser.id(jwt)).param("p", productId).update();
    }
}
