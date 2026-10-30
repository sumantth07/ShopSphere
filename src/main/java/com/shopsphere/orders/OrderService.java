package com.shopsphere.orders;

import com.shopsphere.exception.ApiException;
import com.shopsphere.orders.OrderDtos.*;
import com.shopsphere.payments.PaymentService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private final JdbcClient jdbc;
    private final Map<String, PaymentService> payments;
    private final int baseDays, spreadDays;
    private final BigDecimal shippingPerSeller;

    public OrderService(JdbcClient jdbc, List<PaymentService> paymentServices, @Value("${shopsphere.delivery.base-days}") int baseDays,
                        @Value("${shopsphere.delivery.seller-spread-days}") int spreadDays, @Value("${shopsphere.shipping.flat-fee-per-seller}") BigDecimal ship) {
        this.jdbc = jdbc;
        this.payments = paymentServices.stream().collect(Collectors.toMap(PaymentService::method, p -> p));
        this.baseDays = baseDays; this.spreadDays = spreadDays; this.shippingPerSeller = ship;
    }

    private record CartRow(long productId, String name, String image, BigDecimal price, int qty, long sellerId) {}

    // ------------------------------------------------------------------ place order
    /** All-or-nothing. Any failure (e.g. stock) rolls everything back and leaves the cart untouched. */
    @Transactional
    public long place(long userId, PlaceOrder req) {
        PaymentService pay = payments.get(req.paymentMethod());
        if (pay == null) throw ApiException.badRequest("PAYMENT_METHOD_UNSUPPORTED", "Unsupported payment method. Available: " + payments.keySet());
        var addr = jdbc.sql("select full_name, phone, line1, line2, city, state, postal_code, country from addresses where id = :a and user_id = :u")
            .param("a", req.addressId()).param("u", userId)
            .query((rs, n) -> new ShipTo(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8)))
            .optional().orElseThrow(() -> ApiException.badRequest("ADDRESS_REQUIRED", "Choose a valid delivery address."));

        List<CartRow> cart = jdbc.sql("""
            select p.id, p.name, p.image_url, p.price, ci.quantity, p.seller_id from cart_items ci join products p on p.id = ci.product_id
            where ci.user_id = :u order by p.id""").param("u", userId)
            .query((rs, n) -> new CartRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4), rs.getInt(5), rs.getLong(6))).list();
        if (cart.isEmpty()) throw ApiException.badRequest("CART_EMPTY", "Your cart is empty.");

        // Atomic, race-safe stock decrement: the WHERE clause is the guard, so two buyers can never both take the last unit.
        // Rows are processed in product-id order to avoid deadlocks between concurrent checkouts.
        for (CartRow c : cart) {
            int updated = jdbc.sql("""
                update products set stock_quantity = stock_quantity - :q, updated_at = now()
                where id = :id and active and listing_available and stock_quantity >= :q""").param("q", c.qty()).param("id", c.productId()).update();
            if (updated == 0) throw ApiException.conflict("INSUFFICIENT_STOCK", "\"" + shorten(c.name()) + "\" is no longer available in the requested quantity. Update your cart and try again.");
        }

        Map<Long, List<CartRow>> bySeller = cart.stream().collect(Collectors.groupingBy(CartRow::sellerId, LinkedHashMap::new, Collectors.toList()));
        BigDecimal subtotal = cart.stream().map(c -> c.price().multiply(BigDecimal.valueOf(c.qty()))).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal shipping = shippingPerSeller.multiply(BigDecimal.valueOf(bySeller.size()));
        BigDecimal total = subtotal.add(shipping);

        long orderId = jdbc.sql("""
            insert into orders (user_id, ship_name, ship_phone, ship_line1, ship_line2, ship_city, ship_state, ship_postal, ship_country, subtotal, shipping_total, total)
            values (:u,:n,:ph,:l1,:l2,:c,:s,:pc,:co,:sub,:sh,:t) returning id""")
            .param("u", userId).param("n", addr.name()).param("ph", addr.phone()).param("l1", addr.line1()).param("l2", addr.line2(), java.sql.Types.VARCHAR)
            .param("c", addr.city()).param("s", addr.state()).param("pc", addr.postalCode()).param("co", addr.country())
            .param("sub", subtotal).param("sh", shipping).param("t", total).query(Long.class).single();

        LocalDate today = LocalDate.now();
        for (var e : bySeller.entrySet()) {
            BigDecimal groupSub = e.getValue().stream().map(c -> c.price().multiply(BigDecimal.valueOf(c.qty()))).reduce(BigDecimal.ZERO, BigDecimal::add);
            long soId = jdbc.sql("insert into seller_orders (order_id, seller_id, estimated_delivery, subtotal) values (:o,:s,:d,:sub) returning id")
                .param("o", orderId).param("s", e.getKey()).param("d", DeliveryEstimator.estimate(today, e.getKey(), baseDays, spreadDays)).param("sub", groupSub)
                .query(Long.class).single();
            jdbc.sql("insert into order_status_history (seller_order_id, status, note) values (:so, 'PLACED', 'Order placed')").param("so", soId).update();
            for (CartRow c : e.getValue())
                jdbc.sql("insert into order_items (order_id, seller_order_id, product_id, product_name, image_url, unit_price, quantity) values (:o,:so,:p,:n,:i,:pr,:q)")
                    .param("o", orderId).param("so", soId).param("p", c.productId()).param("n", c.name()).param("i", c.image(), java.sql.Types.VARCHAR)
                    .param("pr", c.price()).param("q", c.qty()).update();
        }
        pay.createFor(orderId, total);
        jdbc.sql("delete from cart_items where user_id = :u").param("u", userId).update();
        return orderId;
    }

    private static String shorten(String s) { return s.length() > 60 ? s.substring(0, 57) + "…" : s; }

    // ------------------------------------------------------------------ reads
    public List<OrderSummary> list(long userId) {
        return jdbc.sql("""
            select o.id, o.status, o.created_at, o.total, o.is_demo, pay.method, pay.status,
                   (select count(*) from order_items i where i.order_id = o.id) as items,
                   (select count(*) from seller_orders s where s.order_id = o.id) as sellers,
                   (select i.image_url from order_items i where i.order_id = o.id order by i.id limit 1) as img
            from orders o left join payments pay on pay.order_id = o.id where o.user_id = :u order by o.created_at desc limit 100""")
            .param("u", userId).query((rs, n) -> new OrderSummary(rs.getLong(1), rs.getString(2), rs.getObject(3, OffsetDateTime.class), rs.getBigDecimal(4),
                rs.getInt(8), rs.getInt(9), rs.getString(6), rs.getString(7), rs.getBoolean(5), rs.getString(10))).list();
    }

    /** Ownership is part of the query: another user's order simply does not exist for you. */
    public Order get(long userId, long orderId) {
        var base = jdbc.sql("""
            select o.id, o.status, o.created_at, o.subtotal, o.shipping_total, o.total, o.is_demo, pay.method, pay.status,
                   o.ship_name, o.ship_phone, o.ship_line1, o.ship_line2, o.ship_city, o.ship_state, o.ship_postal, o.ship_country
            from orders o left join payments pay on pay.order_id = o.id where o.id = :o and o.user_id = :u""").param("o", orderId).param("u", userId)
            .query((rs, n) -> new Object[]{rs.getLong(1), rs.getString(2), rs.getObject(3, OffsetDateTime.class), rs.getBigDecimal(4), rs.getBigDecimal(5),
                rs.getBigDecimal(6), rs.getBoolean(7), rs.getString(8), rs.getString(9),
                new ShipTo(rs.getString(10), rs.getString(11), rs.getString(12), rs.getString(13), rs.getString(14), rs.getString(15), rs.getString(16), rs.getString(17))}).optional()
            .orElseThrow(() -> ApiException.notFound("Order not found."));
        return new Order((Long) base[0], (String) base[1], (OffsetDateTime) base[2], (BigDecimal) base[3], (BigDecimal) base[4], (BigDecimal) base[5],
            (String) base[7], (String) base[8], (Boolean) base[6], (ShipTo) base[9], groupsFor("so.order_id = :id", orderId));
    }

    /** Fulfilment groups (with items and history) for a filter on seller_orders aliased "so". */
    public List<Group> groupsFor(String where, Object id) {
        record G(long id, long sellerId, String sellerName, String status, LocalDate eta, BigDecimal subtotal, String reason) {}
        List<G> gs = jdbc.sql("select so.id, so.seller_id, s.store_name, so.status, so.estimated_delivery, so.subtotal, so.cancel_reason from seller_orders so join sellers s on s.id = so.seller_id where "
                + where + " order by so.id").param("id", id)
            .query((rs, n) -> new G(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getObject(5, LocalDate.class), rs.getBigDecimal(6), rs.getString(7))).list();
        if (gs.isEmpty()) return List.of();
        List<Long> ids = gs.stream().map(G::id).toList();
        Map<Long, List<Item>> items = new HashMap<>();
        Map<Long, String> statusById = gs.stream().collect(Collectors.toMap(G::id, G::status));
        jdbc.sql("select id, seller_order_id, product_id, product_name, image_url, unit_price, quantity, status, cancel_reason from order_items where seller_order_id in (:ids) order by id")
            .param("ids", ids).query((rs, n) -> {
                long so = rs.getLong(2); String st = rs.getString(8);
                items.computeIfAbsent(so, k -> new ArrayList<>()).add(new Item(rs.getLong(1), rs.getLong(3), rs.getString(4), rs.getString(5), rs.getBigDecimal(6), rs.getInt(7), st, rs.getString(9),
                    "ACTIVE".equals(st) && FulfillmentFlow.cancellable(statusById.get(so))));
                return 0; }).list();
        Map<Long, List<HistoryEntry>> hist = new HashMap<>();
        jdbc.sql("select seller_order_id, status, note, created_at from order_status_history where seller_order_id in (:ids) order by created_at, id").param("ids", ids)
            .query((rs, n) -> { hist.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(new HistoryEntry(rs.getString(2), rs.getString(3), rs.getObject(4, OffsetDateTime.class))); return 0; }).list();
        return gs.stream().map(g -> new Group(g.id(), g.sellerId(), g.sellerName(), g.status(), g.eta(), g.subtotal(), FulfillmentFlow.cancellable(g.status()), g.reason(),
            items.getOrDefault(g.id(), List.of()), hist.getOrDefault(g.id(), List.of()))).toList();
    }

    // ------------------------------------------------------------------ cancellation
    public enum Scope { ORDER, GROUP, ITEM }

    /** Cancels eligible items. Stock is restored exactly once per item (guarded by the stock_restored flag in the same UPDATE). */
    @Transactional
    public int cancel(long userId, long orderId, Scope scope, Long targetId, String reason) {
        jdbc.sql("select id from orders where id = :o and user_id = :u for update").param("o", orderId).param("u", userId).query(Long.class).optional()
            .orElseThrow(() -> ApiException.notFound("Order not found."));
        String filter = switch (scope) {
            case ORDER -> "";
            case GROUP -> " and i.seller_order_id = " + Long.parseLong(String.valueOf(targetId));
            case ITEM -> " and i.id = " + Long.parseLong(String.valueOf(targetId));
        };
        record Row(long itemId, long productId, int qty, long soId, String soStatus) {}
        List<Row> rows = jdbc.sql("select i.id, i.product_id, i.quantity, i.seller_order_id, so.status from order_items i join seller_orders so on so.id = i.seller_order_id "
                + "where i.order_id = :o and i.status = 'ACTIVE'" + filter + " order by i.id for update of i").param("o", orderId)
            .query((rs, n) -> new Row(rs.getLong(1), rs.getLong(2), rs.getInt(3), rs.getLong(4), rs.getString(5))).list();
        if (rows.isEmpty()) throw ApiException.conflict("NOTHING_TO_CANCEL", "There is nothing left to cancel here.");
        List<Row> eligible = rows.stream().filter(r -> FulfillmentFlow.cancellable(r.soStatus())).toList();
        if (eligible.isEmpty()) throw ApiException.conflict("NOT_CANCELLABLE", "This can no longer be cancelled because it has already shipped or been delivered.");
        if (scope != Scope.ORDER && eligible.size() != rows.size()) throw ApiException.conflict("NOT_CANCELLABLE", "This can no longer be cancelled because it has already shipped.");

        int cancelled = 0;
        Set<Long> touched = new LinkedHashSet<>();
        for (Row r : eligible) {
            int flagged = jdbc.sql("update order_items set status = 'CANCELLED', cancelled_at = now(), cancel_reason = :r, stock_restored = true where id = :id and status = 'ACTIVE' and stock_restored = false")
                .param("r", reason.trim()).param("id", r.itemId()).update();
            if (flagged == 1) {   // only the transaction that flips the flag restores stock, so it can never happen twice
                jdbc.sql("update products set stock_quantity = stock_quantity + :q, updated_at = now() where id = :p").param("q", r.qty()).param("p", r.productId()).update();
                cancelled++; touched.add(r.soId());
            }
        }
        for (long so : touched) {
            boolean anyActive = jdbc.sql("select exists(select 1 from order_items where seller_order_id = :so and status = 'ACTIVE')").param("so", so).query(Boolean.class).single();
            if (!anyActive) {
                jdbc.sql("update seller_orders set status = 'CANCELLED', cancelled_at = now(), cancel_reason = :r, updated_at = now() where id = :so").param("r", reason.trim()).param("so", so).update();
                jdbc.sql("insert into order_status_history (seller_order_id, status, note) values (:so, 'CANCELLED', :n)").param("so", so).param("n", "Cancelled: " + reason.trim()).update();
            } else {
                jdbc.sql("insert into order_status_history (seller_order_id, status, note) values (:so, (select status from seller_orders where id = :so), 'An item in this shipment was cancelled')").param("so", so).update();
            }
        }
        refreshOrder(orderId);
        return cancelled;
    }

    // ------------------------------------------------------------------ fulfilment (seller/admin)
    /** Advances a fulfilment group by exactly one legal step. Callers must have authorised the actor already. */
    @Transactional
    public String advance(long sellerOrderId) {
        var row = jdbc.sql("select order_id, status from seller_orders where id = :id for update").param("id", sellerOrderId)
            .query((rs, n) -> new Object[]{rs.getLong(1), rs.getString(2)}).optional().orElseThrow(() -> ApiException.notFound("Fulfilment group not found."));
        String next = FulfillmentFlow.next((String) row[1]);
        if (next == null) throw ApiException.conflict("NO_NEXT_STATUS", "This shipment is already " + ((String) row[1]).toLowerCase().replace('_', ' ') + ".");
        jdbc.sql("update seller_orders set status = :s, updated_at = now() where id = :id").param("s", next).param("id", sellerOrderId).update();
        jdbc.sql("insert into order_status_history (seller_order_id, status, note) values (:id, :s, :n)").param("id", sellerOrderId).param("s", next)
            .param("n", "Status updated to " + next.toLowerCase().replace('_', ' ')).update();
        refreshOrder((Long) row[0]);
        return next;
    }

    /** Recomputes the parent order status and payment from its groups. */
    public void refreshOrder(long orderId) {
        List<String> groups = jdbc.sql("select status from seller_orders where order_id = :o").param("o", orderId).query(String.class).list();
        boolean anyCancelledItem = jdbc.sql("select exists(select 1 from order_items where order_id = :o and status = 'CANCELLED')").param("o", orderId).query(Boolean.class).single();
        List<String> live = groups.stream().filter(s -> !s.equals("CANCELLED")).toList();
        String status;
        if (live.isEmpty()) status = "CANCELLED";
        else if (live.stream().allMatch(s -> s.equals("DELIVERED"))) status = "DELIVERED";
        else if (anyCancelledItem) status = "PARTIALLY_CANCELLED";
        else if (live.stream().allMatch(s -> s.equals("PLACED"))) status = "PLACED";
        else status = "IN_PROGRESS";
        jdbc.sql("update orders set status = :s, updated_at = now() where id = :o").param("s", status).param("o", orderId).update();
        // payment amount follows what is still active; PAID only once everything remaining is delivered (cash collected)
        jdbc.sql("""
            update payments set amount = coalesce((select sum(unit_price * quantity) from order_items where order_id = :o and status = 'ACTIVE'), 0)
                + (select shipping_total from orders where id = :o) * (case when :s = 'CANCELLED' then 0 else 1 end), updated_at = now() where order_id = :o""")
            .param("o", orderId).param("s", status).update();
        String pay = status.equals("CANCELLED") ? "CANCELLED" : status.equals("DELIVERED") ? "PAID" : "PENDING";
        jdbc.sql("update payments set status = :p, updated_at = now() where order_id = :o and status <> 'FAILED'").param("p", pay).param("o", orderId).update();
    }
}
