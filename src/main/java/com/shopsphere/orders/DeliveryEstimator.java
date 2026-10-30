package com.shopsphere.orders;

import java.time.LocalDate;

/**
 * DEMO delivery rule (documented, configurable): estimate = order date + baseDays + (sellerId mod spreadDays).
 * Deterministic per seller, so two sellers in one order get different dates and a refresh never changes them.
 * The result is stored in seller_orders.estimated_delivery at order time.
 */
public final class DeliveryEstimator {
    private DeliveryEstimator() {}
    public static LocalDate estimate(LocalDate orderDate, long sellerId, int baseDays, int spreadDays) {
        return orderDate.plusDays(baseDays + Math.floorMod(sellerId, Math.max(spreadDays, 1)));
    }
}
