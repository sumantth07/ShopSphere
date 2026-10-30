package com.shopsphere.sellers;

import com.shopsphere.exception.ApiException;
import com.shopsphere.orders.OrderDtos.Group;
import com.shopsphere.orders.OrderService;
import com.shopsphere.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sellers")
public class SellerController {
    static final String IMG = "^(https?://.+|/uploads/[A-Za-z0-9._-]+)$";
    public record ApplyRequest(@NotBlank @Size(min = 3, max = 120) String storeName, @NotBlank @Size(min = 20, max = 2000, message = "Describe your store in at least 20 characters.") String storeDescription,
                               @Pattern(regexp = "^$|^https?://.+", message = "Logo must be an http(s) URL.") @Size(max = 500) String logoUrl,
                               @NotBlank @Email String contactEmail, @Pattern(regexp = "^$|^[0-9+()\\-\\s]{7,30}$", message = "Enter a valid phone number.") String contactPhone) {}
    public record SellerProfile(long id, String storeName, String storeDescription, String logoUrl, String contactEmail, String contactPhone,
                                String status, String rejectionReason, OffsetDateTime createdAt) {}
    public record ProductRequest(@NotBlank @Size(max = 500) String name, @Size(max = 10000) String description, @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
                                 @NotNull Long categoryId, @Size(max = 200) String brand, @NotBlank @Pattern(regexp = IMG, message = "Main image must be an uploaded image or an http(s) URL.") String imageUrl,
                                 @Size(max = 1, message = "A product can have at most 2 images (main + 1 more).") List<@Pattern(regexp = IMG) String> additionalImages, @NotNull @Min(0) @Max(1_000_000) Integer stockQuantity, Boolean active) {}
    public record StockRequest(@NotNull @Min(0) @Max(1_000_000) Integer stockQuantity) {}
    public record StatusRequest(@NotNull Boolean active) {}
    public record SellerProduct(long id, String name, String brand, String category, BigDecimal price, String imageUrl, int stockQuantity, boolean active,
                                String stockState, BigDecimal rating, Integer reviewCount) {}
    public record Page<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}

    private final JdbcClient jdbc; private final SellerService sellers; private final OrderService orders; private final int lowStock;
    public SellerController(JdbcClient jdbc, SellerService sellers, OrderService orders, @Value("${shopsphere.inventory.low-stock-threshold}") int lowStock) {
        this.jdbc = jdbc; this.sellers = sellers; this.orders = orders; this.lowStock = lowStock;
    }

    // ---------------- application & profile ----------------
    @PostMapping("/applications") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public SellerProfile apply(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ApplyRequest r) {
        long uid = CurrentUser.id(jwt);
        var existing = sellers.find(uid);
        if (existing.isPresent() && !existing.get().status().equals("REJECTED"))
            throw ApiException.conflict("APPLICATION_EXISTS", "You already have a seller application (" + existing.get().status().toLowerCase() + ").");
        if (existing.isPresent()) {   // resubmission after rejection: same row, back to PENDING
            jdbc.sql("update sellers set store_name=:n, store_description=:d, logo_url=:l, contact_email=:e, contact_phone=:p, application_status='PENDING', rejection_reason=null, updated_at=now() where user_id=:u")
                .param("n", r.storeName().trim()).param("d", r.storeDescription().trim()).param("l", blankToNull(r.logoUrl()), Types.VARCHAR).param("e", r.contactEmail().trim())
                .param("p", blankToNull(r.contactPhone()), Types.VARCHAR).param("u", uid).update();
        } else {
            jdbc.sql("insert into sellers (user_id, store_name, store_description, logo_url, contact_email, contact_phone, origin, application_status) values (:u,:n,:d,:l,:e,:p,'REGISTERED','PENDING')")
                .param("u", uid).param("n", r.storeName().trim()).param("d", r.storeDescription().trim()).param("l", blankToNull(r.logoUrl()), Types.VARCHAR)
                .param("e", r.contactEmail().trim()).param("p", blankToNull(r.contactPhone()), Types.VARCHAR).update();
        }
        return profile(uid);
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private SellerProfile profile(long uid) {
        return jdbc.sql("select id, store_name, store_description, logo_url, contact_email, contact_phone, application_status, rejection_reason, created_at from sellers where user_id = :u")
            .param("u", uid).query((rs, n) -> new SellerProfile(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                rs.getString(7), rs.getString(8), rs.getObject(9, OffsetDateTime.class))).optional().orElseThrow(() -> ApiException.notFound("No seller application found."));
    }

    @GetMapping("/me") public SellerProfile me(@AuthenticationPrincipal Jwt jwt) { return profile(CurrentUser.id(jwt)); }

    public record ProfileUpdate(@NotBlank @Size(min = 3, max = 120) String storeName, @NotBlank @Size(max = 2000) String storeDescription,
                                @Pattern(regexp = "^$|^https?://.+") String logoUrl, @NotBlank @Email String contactEmail, String contactPhone) {}

    @PutMapping("/me")
    public SellerProfile update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileUpdate r) {
        long uid = CurrentUser.id(jwt); sellers.requireApproved(uid);
        jdbc.sql("update sellers set store_name=:n, store_description=:d, logo_url=:l, contact_email=:e, contact_phone=:p, updated_at=now() where user_id=:u")
            .param("n", r.storeName().trim()).param("d", r.storeDescription().trim()).param("l", blankToNull(r.logoUrl()), Types.VARCHAR).param("e", r.contactEmail().trim())
            .param("p", blankToNull(r.contactPhone()), Types.VARCHAR).param("u", uid).update();
        return profile(uid);
    }

    // ---------------- products (always scoped to the caller's seller id) ----------------
    @GetMapping("/me/products")
    public Page<SellerProduct> products(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String q, @RequestParam(required = false) String stock,
                                        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        long sid = sellers.requireApproved(CurrentUser.id(jwt));
        int sz = Math.min(Math.max(size, 1), 50), pg = Math.max(page, 0);
        String like = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        String stockFilter = "low".equals(stock) ? " and p.stock_quantity between 1 and :low" : "out".equals(stock) ? " and p.stock_quantity = 0" : "";
        String where = " where p.seller_id = :sid and (cast(:like as text) is null or lower(p.name) like :like or lower(p.brand) like :like)" + stockFilter;
        long total = jdbc.sql("select count(*) from products p" + where).param("sid", sid).param("like", like, Types.VARCHAR).param("low", lowStock).query(Long.class).single();
        List<SellerProduct> rows = jdbc.sql("select p.id, p.name, p.brand, c.name, p.price, p.image_url, p.stock_quantity, p.active, p.rating, p.review_count from products p join categories c on c.id = p.category_id"
                + where + " order by p.id desc limit :lim offset :off")
            .param("sid", sid).param("like", like, Types.VARCHAR).param("low", lowStock).param("lim", sz).param("off", pg * sz)
            .query((rs, n) -> new SellerProduct(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBigDecimal(5), rs.getString(6), rs.getInt(7), rs.getBoolean(8),
                rs.getInt(7) == 0 ? "OUT_OF_STOCK" : rs.getInt(7) <= lowStock ? "LOW" : "OK", rs.getBigDecimal(9), (Integer) rs.getObject(10))).list();
        return new Page<>(rows, pg, sz, total, (int) Math.ceil(total / (double) sz));
    }

    @PostMapping("/me/products") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public Map<String, Long> createProduct(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProductRequest r) {
        long sid = sellers.requireApproved(CurrentUser.id(jwt));
        return Map.of("id", insertProduct(sid, r));
    }

    /** Shared with the admin API. */
    public long insertProduct(long sellerId, ProductRequest r) {
        requireCategory(r.categoryId());
        long id = jdbc.sql("""
            insert into products (name, description, price, category_id, brand, image_url, seller_id, listing_available, stock_quantity, stock_source, active)
            values (:n,:d,:p,:c,:b,:i,:s,true,:st,'SELLER',:a) returning id""")
            .param("n", r.name().trim()).param("d", blankToNull(r.description()), Types.VARCHAR).param("p", r.price()).param("c", r.categoryId())
            .param("b", blankToNull(r.brand()), Types.VARCHAR).param("i", r.imageUrl().trim()).param("s", sellerId).param("st", r.stockQuantity())
            .param("a", r.active() == null || r.active()).query(Long.class).single();
        saveImages(id, r.additionalImages());
        return id;
    }

    private void requireCategory(long id) {
        if (!jdbc.sql("select exists(select 1 from categories where id=:c)").param("c", id).query(Boolean.class).single()) throw ApiException.badRequest("CATEGORY_INVALID", "Unknown category.");
    }
    private void saveImages(long productId, List<String> urls) {
        jdbc.sql("delete from product_images where product_id=:p").param("p", productId).update();
        if (urls == null) return;
        int pos = 0;
        for (String u : urls.stream().distinct().limit(10).toList())
            jdbc.sql("insert into product_images (product_id, url, position) values (:p,:u,:pos) on conflict do nothing").param("p", productId).param("u", u).param("pos", pos++).update();
    }

    public record ProductDetailForEdit(SellerProduct product, String description, List<String> additionalImages, Long categoryId) {}

    @GetMapping("/me/products/{id}")
    public ProductDetailForEdit one(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        long sid = sellers.requireApproved(CurrentUser.id(jwt));
        var p = jdbc.sql("select p.id, p.name, p.brand, c.name, p.price, p.image_url, p.stock_quantity, p.active, p.rating, p.review_count, p.description, p.category_id from products p join categories c on c.id=p.category_id where p.id=:id and p.seller_id=:s")
            .param("id", id).param("s", sid).query((rs, n) -> new ProductDetailForEdit(new SellerProduct(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBigDecimal(5), rs.getString(6),
                rs.getInt(7), rs.getBoolean(8), rs.getInt(7) == 0 ? "OUT_OF_STOCK" : rs.getInt(7) <= lowStock ? "LOW" : "OK", rs.getBigDecimal(9), (Integer) rs.getObject(10)), rs.getString(11), List.of(), rs.getLong(12)))
            .optional().orElseThrow(() -> ApiException.notFound("Product not found."));   // another seller's product is simply "not found"
        List<String> imgs = jdbc.sql("select url from product_images where product_id=:p order by position").param("p", id).query(String.class).list();
        return new ProductDetailForEdit(p.product(), p.description(), imgs, p.categoryId());
    }

    @PutMapping("/me/products/{id}") @Transactional
    public Map<String, Long> updateProduct(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody ProductRequest r) {
        long sid = sellers.requireApproved(CurrentUser.id(jwt));
        requireCategory(r.categoryId());
        int n = jdbc.sql("""
            update products set name=:n, description=:d, price=:p, category_id=:c, brand=:b, image_url=:i, stock_quantity=:st, stock_source='SELLER', active=:a, updated_at=now()
            where id=:id and seller_id=:s""")
            .param("n", r.name().trim()).param("d", blankToNull(r.description()), Types.VARCHAR).param("p", r.price()).param("c", r.categoryId()).param("b", blankToNull(r.brand()), Types.VARCHAR)
            .param("i", r.imageUrl().trim()).param("st", r.stockQuantity()).param("a", r.active() == null || r.active()).param("id", id).param("s", sid).update();
        if (n == 0) throw ApiException.notFound("Product not found.");
        saveImages(id, r.additionalImages());
        return Map.of("id", id);
    }

    @PatchMapping("/me/products/{id}/status")
    public Map<String, Object> setActive(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody StatusRequest r) {
        long sid = sellers.requireApproved(CurrentUser.id(jwt));
        if (jdbc.sql("update products set active=:a, updated_at=now() where id=:id and seller_id=:s").param("a", r.active()).param("id", id).param("s", sid).update() == 0) throw ApiException.notFound("Product not found.");
        return Map.of("id", id, "active", r.active());
    }

    @PatchMapping("/me/products/{id}/stock")
    public Map<String, Object> setStock(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody StockRequest r) {
        long sid = sellers.requireApproved(CurrentUser.id(jwt));
        if (jdbc.sql("update products set stock_quantity=:q, stock_source='SELLER', updated_at=now() where id=:id and seller_id=:s").param("q", r.stockQuantity()).param("id", id).param("s", sid).update() == 0) throw ApiException.notFound("Product not found.");
        return Map.of("id", id, "stockQuantity", r.stockQuantity());
    }

    // ---------------- orders (fulfilment groups of this seller only) ----------------
    public record SellerOrderRow(long groupId, long orderId, String status, java.time.LocalDate estimatedDelivery, OffsetDateTime placedAt, BigDecimal subtotal,
                                 String customerName, String city, String state, int itemCount) {}

    @GetMapping("/me/orders")
    public List<SellerOrderRow> orders(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String status) {
        long sid = sellers.requireApproved(CurrentUser.id(jwt));
        return jdbc.sql("""
            select so.id, so.order_id, so.status, so.estimated_delivery, so.created_at, coalesce((select sum(unit_price*quantity) from order_items i where i.seller_order_id=so.id and i.status='ACTIVE'),0),
                   o.ship_name, o.ship_city, o.ship_state, (select count(*) from order_items i where i.seller_order_id=so.id)
            from seller_orders so join orders o on o.id = so.order_id where so.seller_id = :s and (cast(:st as text) is null or so.status = :st) order by so.created_at desc limit 200""")
            .param("s", sid).param("st", status == null || status.isBlank() ? null : status, Types.VARCHAR)
            .query((rs, n) -> new SellerOrderRow(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getObject(4, java.time.LocalDate.class), rs.getObject(5, OffsetDateTime.class), rs.getBigDecimal(6),
                rs.getString(7), rs.getString(8), rs.getString(9), rs.getInt(10))).list();
    }

    public record SellerOrderDetail(Group group, long orderId, OffsetDateTime placedAt, String shipName, String shipPhone, String shipAddress) {}

    @GetMapping("/me/orders/{groupId}")
    public SellerOrderDetail orderDetail(@AuthenticationPrincipal Jwt jwt, @PathVariable long groupId) {
        long sid = sellers.requireApproved(CurrentUser.id(jwt));
        var o = jdbc.sql("select so.order_id, o.created_at, o.ship_name, o.ship_phone, concat_ws(', ', o.ship_line1, o.ship_line2, o.ship_city, o.ship_state, o.ship_postal, o.ship_country) from seller_orders so join orders o on o.id=so.order_id where so.id=:g and so.seller_id=:s")
            .param("g", groupId).param("s", sid).query((rs, n) -> new Object[]{rs.getLong(1), rs.getObject(2, OffsetDateTime.class), rs.getString(3), rs.getString(4), rs.getString(5)}).optional()
            .orElseThrow(() -> ApiException.notFound("Order not found."));
        Group g = orders.groupsFor("so.id = :id", groupId).get(0);
        return new SellerOrderDetail(g, (Long) o[0], (OffsetDateTime) o[1], (String) o[2], (String) o[3], (String) o[4]);
    }

    /** Moves the seller's group one step forward. A seller can only do this for their own group. */
    @PostMapping("/me/orders/{groupId}/advance")
    public Map<String, String> advance(@AuthenticationPrincipal Jwt jwt, @PathVariable long groupId) {
        long sid = sellers.requireApproved(CurrentUser.id(jwt));
        boolean mine = jdbc.sql("select exists(select 1 from seller_orders where id=:g and seller_id=:s)").param("g", groupId).param("s", sid).query(Boolean.class).single();
        if (!mine) throw ApiException.notFound("Order not found.");
        return Map.of("status", orders.advance(groupId));
    }

    // ---------------- insights ----------------
    @GetMapping("/me/insights")
    public Map<String, Object> insights(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "30") int days) {
        long sid = sellers.requireApproved(CurrentUser.id(jwt));
        int d = days <= 7 ? 7 : days <= 30 ? 30 : 90;
        var m = new java.util.LinkedHashMap<String, Object>();
        m.put("periodDays", d);
        m.put("activeProducts", jdbc.sql("select count(*) from products where seller_id=:s and active").param("s", sid).query(Long.class).single());
        m.put("totalStock", jdbc.sql("select coalesce(sum(stock_quantity),0) from products where seller_id=:s and active").param("s", sid).query(Long.class).single());
        m.put("lowStockProducts", jdbc.sql("select id, name, stock_quantity from products where seller_id=:s and active and stock_quantity between 1 and :l order by stock_quantity limit 10").param("s", sid).param("l", lowStock)
            .query((rs, n) -> Map.of("id", rs.getLong(1), "name", rs.getString(2), "stock", rs.getInt(3))).list());
        m.put("outOfStockProducts", jdbc.sql("select id, name from products where seller_id=:s and active and stock_quantity = 0 order by id limit 10").param("s", sid)
            .query((rs, n) -> Map.of("id", rs.getLong(1), "name", rs.getString(2))).list());
        m.put("lowStockThreshold", lowStock);
        m.put("totalOrders", jdbc.sql("select count(*) from seller_orders where seller_id=:s").param("s", sid).query(Long.class).single());
        m.put("ordersByStatus", jdbc.sql("select status, count(*) from seller_orders where seller_id=:s group by status").param("s", sid)
            .query((rs, n) -> Map.of("status", rs.getString(1), "count", rs.getLong(2))).list());
        // Revenue = unit_price x quantity of ACTIVE items (purchase-time prices) in non-cancelled shipments, excluding shipping. "Delivered revenue" counts only DELIVERED shipments.
        m.put("revenue", jdbc.sql("select coalesce(sum(i.unit_price*i.quantity),0) from order_items i join seller_orders so on so.id=i.seller_order_id where so.seller_id=:s and i.status='ACTIVE'").param("s", sid).query(BigDecimal.class).single());
        m.put("deliveredRevenue", jdbc.sql("select coalesce(sum(i.unit_price*i.quantity),0) from order_items i join seller_orders so on so.id=i.seller_order_id where so.seller_id=:s and i.status='ACTIVE' and so.status='DELIVERED'").param("s", sid).query(BigDecimal.class).single());
        m.put("cancelledItems", jdbc.sql("select count(*), coalesce(sum(i.unit_price*i.quantity),0) from order_items i join seller_orders so on so.id=i.seller_order_id where so.seller_id=:s and i.status='CANCELLED'").param("s", sid)
            .query((rs, n) -> Map.of("count", rs.getLong(1), "value", rs.getBigDecimal(2))).single());
        m.put("trend", jdbc.sql("""
            select to_char(g.day, 'YYYY-MM-DD'), coalesce(x.orders,0), coalesce(x.revenue,0) from generate_series(current_date - (:d - 1), current_date, interval '1 day') as g(day)
            left join (select so.created_at::date as day, count(distinct so.id) as orders, sum(i.unit_price*i.quantity) filter (where i.status='ACTIVE') as revenue
                       from seller_orders so join order_items i on i.seller_order_id = so.id where so.seller_id=:s group by 1) x on x.day = g.day::date order by g.day""")
            .param("s", sid).param("d", d).query((rs, n) -> Map.of("date", rs.getString(1), "orders", rs.getLong(2), "revenue", rs.getBigDecimal(3))).list());
        m.put("bestProducts", jdbc.sql("select i.product_id, i.product_name, sum(i.quantity), sum(i.unit_price*i.quantity) from order_items i join seller_orders so on so.id=i.seller_order_id where so.seller_id=:s and i.status='ACTIVE' group by 1,2 order by 3 desc limit 5").param("s", sid)
            .query((rs, n) -> Map.of("productId", rs.getLong(1), "name", rs.getString(2), "units", rs.getLong(3), "revenue", rs.getBigDecimal(4))).list());
        m.put("recentOrders", orders(jwt, null).stream().limit(5).toList());
        return m;
    }
}
