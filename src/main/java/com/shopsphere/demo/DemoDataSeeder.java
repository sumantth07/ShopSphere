package com.shopsphere.demo;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.shopsphere.orders.DeliveryEstimator;
import com.shopsphere.orders.FulfillmentFlow;

/**
 * DEMO DATA. Runs only with the "demo" profile (SPRING_PROFILES_ACTIVE=demo) and never with "prod".
 * Repeatable: does nothing if the demo customers already exist. Everything it creates is flagged (orders.is_demo, products.is_demo)
 * and demo accounts use the documented password below, so keep this profile OFF in any public deployment.
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    public static final String DEMO_PASSWORD = "Demo@12345";
    private final JdbcClient jdbc; private final PasswordEncoder encoder; private final Environment env;

    public DemoDataSeeder(JdbcClient jdbc, PasswordEncoder encoder, Environment env) { this.jdbc = jdbc; this.encoder = encoder; this.env = env; }

    @Override @Transactional
    public void run(ApplicationArguments args) {
        if (env.acceptsProfiles(Profiles.of("prod"))) throw new IllegalStateException("The demo profile must not run together with prod.");
        if (jdbc.sql("select exists(select 1 from users where email = 'demo.customer1@shopsphere.demo')").query(Boolean.class).single()) { log.info("Demo data already present."); return; }
        long productCount = jdbc.sql("select count(*) from products").query(Long.class).single();
        if (productCount < 100) { log.warn("Demo seeding skipped: import the product catalog first (docs/CSV_IMPORT_GUIDE.md)."); return; }

        String hash = encoder.encode(DEMO_PASSWORD);
        long[] customers = new long[3];
        String[] names = {"Asha Rao", "Vikram Shah", "Meera Iyer"};
        for (int i = 0; i < 3; i++)
            customers[i] = jdbc.sql("insert into users (full_name, email, password_hash, email_verified) values (:n,:e,:p,true) returning id").param("n", names[i]).param("e", "demo.customer" + (i + 1) + "@shopsphere.demo").param("p", hash).query(Long.class).single();
        long[] sellerIds = new long[3];
        String[][] stores = {{"Northwind Home & Kitchen", "demo.seller1@shopsphere.demo"}, {"Bluebird Electronics", "demo.seller2@shopsphere.demo"}, {"GreenLeaf Essentials", "demo.seller3@shopsphere.demo"}};
        for (int i = 0; i < 3; i++) {
            long uid = jdbc.sql("insert into users (full_name, email, password_hash, email_verified) values (:n,:e,:p,true) returning id").param("n", "Demo Seller " + (i + 1)).param("e", stores[i][1]).param("p", hash).query(Long.class).single();
            sellerIds[i] = jdbc.sql("insert into sellers (user_id, store_name, store_description, contact_email, origin, application_status) values (:u,:n,:d,:e,'REGISTERED','APPROVED') returning id")
                .param("u", uid).param("n", stores[i][0]).param("d", "Demonstration store seeded for the ShopSphere portfolio project.").param("e", stores[i][1]).query(Long.class).single();
        }
        // Give each demo seller 40 products (by id) and a spread of stock levels: ok, low, out of stock.
        Random rnd = new Random(42);
        List<Long> ids = jdbc.sql("select id from products where listing_available order by id limit 120").query(Long.class).list();
        for (int i = 0; i < ids.size(); i++) {
            int stock = i % 10 == 0 ? 0 : i % 7 == 0 ? 2 + rnd.nextInt(6) : 15 + rnd.nextInt(80);
            jdbc.sql("update products set seller_id = :s, stock_quantity = :q, stock_source = 'DEMO_SEEDED', is_demo = true where id = :id").param("s", sellerIds[i % 3]).param("q", stock).param("id", ids.get(i)).update();
        }
        jdbc.sql("insert into addresses (user_id, label, full_name, phone, line1, city, state, postal_code, country, is_default) select id, 'Home', full_name, '+91 90000 00000', '12 Demo Street', 'Hyderabad', 'Telangana', '500001', 'India', true from users where id in (:ids)")
            .param("ids", Arrays.stream(customers).boxed().toList()).update();

        // Demo orders: each mixes sellers, backdated 1-60 days, with histories matching the status reached.
        List<Long> sellable = jdbc.sql("select id from products where is_demo and stock_quantity > 20 order by id").query(Long.class).list();
        String[] targets = {"DELIVERED", "DELIVERED", "SHIPPED", "OUT_FOR_DELIVERY", "PROCESSING", "CONFIRMED", "PLACED", "DELIVERED", "PACKED", "SHIPPED", "DELIVERED", "PROCESSING"};
        for (int o = 0; o < 12; o++) {
            long cust = customers[o % 3];
            int daysAgo = 2 + rnd.nextInt(58);
            OffsetDateTime placed = OffsetDateTime.now().minusDays(daysAgo).withHour(10).withMinute(0).withSecond(0).withNano(0);
            List<Long> picks = new ArrayList<>();
            while (picks.size() < 3) { long p = sellable.get(rnd.nextInt(sellable.size())); if (!picks.contains(p)) picks.add(p); }
            record P(long id, String name, String img, BigDecimal price, long seller) {}
            List<P> ps = new ArrayList<>();
            for (long pid : picks)
                ps.add(jdbc.sql("select id, name, image_url, price, seller_id from products where id = :id").param("id", pid)
                    .query((rs, n) -> new P(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4), rs.getLong(5))).single());
            BigDecimal subtotal = ps.stream().map(p -> p.price().multiply(BigDecimal.valueOf(1 + (p.id() % 2)))).reduce(BigDecimal.ZERO, BigDecimal::add);
            long orderId = jdbc.sql("""
                insert into orders (user_id, ship_name, ship_phone, ship_line1, ship_city, ship_state, ship_postal, ship_country, subtotal, total, is_demo, created_at, updated_at)
                values (:u,'Demo Customer','+91 90000 00000','12 Demo Street','Hyderabad','Telangana','500001','India',:s,:s,true,:t,:t) returning id""").param("u", cust).param("s", subtotal).param("t", placed).query(Long.class).single();
            Map<Long, List<P>> bySeller = new LinkedHashMap<>();
            ps.forEach(p -> bySeller.computeIfAbsent(p.seller(), k -> new ArrayList<>()).add(p));
            int g = 0;
            for (var e : bySeller.entrySet()) {
                String target = targets[(o + g++) % targets.length];
                boolean cancelled = o == 5 && g == 1;     // one demo shipment is cancelled
                LocalDate eta = DeliveryEstimator.estimate(placed.toLocalDate(), e.getKey(), 3, 5);
                BigDecimal gsub = e.getValue().stream().map(p -> p.price().multiply(BigDecimal.valueOf(1 + (p.id() % 2)))).reduce(BigDecimal.ZERO, BigDecimal::add);
                String finalStatus = cancelled ? "CANCELLED" : target;
                long so = jdbc.sql("insert into seller_orders (order_id, seller_id, status, estimated_delivery, subtotal, created_at, updated_at, cancelled_at, cancel_reason) values (:o,:s,:st,:eta,:sub,:t,:t,:ca,:cr) returning id")
                    .param("o", orderId).param("s", e.getKey()).param("st", finalStatus).param("eta", eta).param("sub", gsub).param("t", placed)
                    .param("ca", cancelled ? placed.plusHours(5) : null, Types.TIMESTAMP_WITH_TIMEZONE).param("cr", cancelled ? "Ordered by mistake" : null, Types.VARCHAR).query(Long.class).single();
                int step = 0;
                for (String st : FulfillmentFlow.STEPS) {
                    jdbc.sql("insert into order_status_history (seller_order_id, status, note, created_at) values (:so,:st,:n,:t)").param("so", so).param("st", st)
                        .param("n", step == 0 ? "Order placed" : "Status updated to " + st.toLowerCase().replace('_', ' ')).param("t", placed.plusHours(step * 20L)).update();
                    if (cancelled && step == 1) break;
                    if (st.equals(target) && !cancelled) break;
                    step++;
                }
                if (cancelled) jdbc.sql("insert into order_status_history (seller_order_id, status, note, created_at) values (:so,'CANCELLED','Cancelled: Ordered by mistake',:t)").param("so", so).param("t", placed.plusHours(5)).update();
                for (P p : e.getValue()) {
                    int qty = 1 + (int) (p.id() % 2);
                    jdbc.sql("insert into order_items (order_id, seller_order_id, product_id, product_name, image_url, unit_price, quantity, status, cancelled_at, cancel_reason, stock_restored) values (:o,:so,:p,:n,:i,:pr,:q,:st,:ca,:cr,:sr)")
                        .param("o", orderId).param("so", so).param("p", p.id()).param("n", p.name()).param("i", p.img()).param("pr", p.price()).param("q", qty)
                        .param("st", cancelled ? "CANCELLED" : "ACTIVE").param("ca", cancelled ? placed.plusHours(5) : null, Types.TIMESTAMP_WITH_TIMEZONE)
                        .param("cr", cancelled ? "Ordered by mistake" : null, Types.VARCHAR).param("sr", cancelled).update();
                    if (!cancelled) jdbc.sql("update products set stock_quantity = greatest(stock_quantity - :q, 0) where id = :id").param("q", qty).param("id", p.id()).update();
                }
            }
            jdbc.sql("insert into payments (order_id, method, status, amount, created_at) values (:o,'COD','PENDING',:a,:t)").param("o", orderId).param("a", subtotal).param("t", placed).update();
        }
        // derive parent order + payment status from the groups using the same rules as the live app
        jdbc.sql("select id from orders where is_demo").query(Long.class).list().forEach(this::refresh);
        log.info("Demo data created. Demo logins: demo.customer1..3@shopsphere.demo and demo.seller1..3@shopsphere.demo, password {}", DEMO_PASSWORD);
    }

    private void refresh(long orderId) {
        List<String> groups = jdbc.sql("select status from seller_orders where order_id = :o").param("o", orderId).query(String.class).list();
        boolean anyCancelledItem = jdbc.sql("select exists(select 1 from order_items where order_id=:o and status='CANCELLED')").param("o", orderId).query(Boolean.class).single();
        List<String> live = groups.stream().filter(s -> !s.equals("CANCELLED")).toList();
        String status = live.isEmpty() ? "CANCELLED" : live.stream().allMatch(s -> s.equals("DELIVERED")) ? "DELIVERED" : anyCancelledItem ? "PARTIALLY_CANCELLED" : live.stream().allMatch(s -> s.equals("PLACED")) ? "PLACED" : "IN_PROGRESS";
        jdbc.sql("update orders set status=:s where id=:o").param("s", status).param("o", orderId).update();
        jdbc.sql("update payments set status=:p, amount=coalesce((select sum(unit_price*quantity) from order_items where order_id=:o and status='ACTIVE'),0) where order_id=:o")
            .param("p", status.equals("CANCELLED") ? "CANCELLED" : status.equals("DELIVERED") ? "PAID" : "PENDING").param("o", orderId).update();
    }
}
