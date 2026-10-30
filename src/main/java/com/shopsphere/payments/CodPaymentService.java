package com.shopsphere.payments;

import java.math.BigDecimal;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Cash on Delivery: starts PENDING; becomes PAID when every active fulfilment group is delivered (cash collected). */
@Service
public class CodPaymentService implements PaymentService {
    private final JdbcClient jdbc;
    public CodPaymentService(JdbcClient jdbc) { this.jdbc = jdbc; }
    @Override public String method() { return "COD"; }
    @Override public String createFor(long orderId, BigDecimal amount) {
        jdbc.sql("insert into payments (order_id, method, status, amount) values (:o, 'COD', 'PENDING', :a)").param("o", orderId).param("a", amount).update();
        return "PENDING";
    }
}
