package com.shopsphere.cart;

import com.shopsphere.exception.ApiException;
import com.shopsphere.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Server-side cart. Totals are always recomputed here from current prices; the client never sends a price. */
@RestController
@RequestMapping("/api/cart")
public class CartController {
    public record AddItem(@NotNull Long productId, @Min(1) @Max(20) int quantity) {}
    public record SetQuantity(@Min(1) @Max(20) int quantity) {}
    public record CartLine(long id, long productId, String name, String imageUrl, String brand, BigDecimal unitPrice, int quantity,
                           BigDecimal lineTotal, int stockQuantity, boolean available, String issue, String sellerName) {}
    public record CartView(List<CartLine> items, int itemCount, BigDecimal subtotal, boolean hasIssues) {}

    private final JdbcClient jdbc;
    public CartController(JdbcClient jdbc) { this.jdbc = jdbc; }

    static CartView view(JdbcClient jdbc, long uid) {
        List<CartLine> lines = jdbc.sql("""
            select ci.id, p.id, p.name, p.image_url, p.brand, p.price, ci.quantity, p.stock_quantity,
                   (p.active and p.listing_available and p.stock_quantity > 0) as available, coalesce(s.store_name, p.imported_seller_name), p.active
            from cart_items ci join products p on p.id = ci.product_id left join sellers s on s.id = p.seller_id
            where ci.user_id = :u order by ci.created_at desc""").param("u", uid).query((rs, n) -> {
                int qty = rs.getInt(7), stock = rs.getInt(8); boolean avail = rs.getBoolean(9);
                String issue = !avail ? "This item is currently unavailable." : qty > stock ? "Only " + stock + " left in stock." : null;
                BigDecimal price = rs.getBigDecimal(6);
                return new CartLine(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5), price, qty,
                    price.multiply(BigDecimal.valueOf(qty)), stock, avail, issue, rs.getString(10));
            }).list();
        BigDecimal subtotal = lines.stream().filter(l -> l.issue() == null).map(CartLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartView(lines, lines.stream().mapToInt(CartLine::quantity).sum(), subtotal, lines.stream().anyMatch(l -> l.issue() != null));
    }

    @GetMapping public CartView get(@AuthenticationPrincipal Jwt jwt) { return view(jdbc, CurrentUser.id(jwt)); }

    @PostMapping("/items") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public CartView add(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddItem r) {
        long uid = CurrentUser.id(jwt);
        var p = jdbc.sql("select stock_quantity, active and listing_available from products where id = :id").param("id", r.productId())
            .query((rs, n) -> new Object[]{rs.getInt(1), rs.getBoolean(2)}).optional().orElseThrow(() -> ApiException.notFound("Product not found."));
        int stock = (Integer) p[0];
        if (!(Boolean) p[1] || stock <= 0) throw ApiException.conflict("OUT_OF_STOCK", "This product is out of stock.");
        int existing = jdbc.sql("select coalesce(sum(quantity),0) from cart_items where user_id=:u and product_id=:p").param("u", uid).param("p", r.productId()).query(Integer.class).single();
        if (existing + r.quantity() > stock) throw ApiException.conflict("INSUFFICIENT_STOCK", "Only " + stock + " in stock" + (existing > 0 ? " (you already have " + existing + " in your cart)." : "."));
        if (existing + r.quantity() > 20) throw ApiException.badRequest("MAX_QUANTITY", "You can buy at most 20 of one item.");
        jdbc.sql("insert into cart_items (user_id, product_id, quantity) values (:u,:p,:q) on conflict (user_id, product_id) do update set quantity = cart_items.quantity + :q")
            .param("u", uid).param("p", r.productId()).param("q", r.quantity()).update();
        return view(jdbc, uid);
    }

    @PatchMapping("/items/{id}") @Transactional
    public CartView set(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody SetQuantity r) {
        long uid = CurrentUser.id(jwt);
        Integer stock = jdbc.sql("select p.stock_quantity from cart_items ci join products p on p.id=ci.product_id where ci.id=:id and ci.user_id=:u")
            .param("id", id).param("u", uid).query(Integer.class).optional().orElseThrow(() -> ApiException.notFound("Cart item not found."));
        if (r.quantity() > stock) throw ApiException.conflict("INSUFFICIENT_STOCK", "Only " + stock + " in stock.");
        jdbc.sql("update cart_items set quantity=:q where id=:id and user_id=:u").param("q", r.quantity()).param("id", id).param("u", uid).update();
        return view(jdbc, uid);
    }

    @DeleteMapping("/items/{id}")
    public CartView remove(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        long uid = CurrentUser.id(jwt);
        jdbc.sql("delete from cart_items where id=:id and user_id=:u").param("id", id).param("u", uid).update();
        return view(jdbc, uid);
    }
}
