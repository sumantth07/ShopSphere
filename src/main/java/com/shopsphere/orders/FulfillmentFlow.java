package com.shopsphere.orders;

import java.util.List;

/** The only legal fulfilment transitions. One step forward at a time; cancellation only before shipping. */
public final class FulfillmentFlow {
    private FulfillmentFlow() {}
    public static final List<String> STEPS = List.of("PLACED", "CONFIRMED", "PROCESSING", "PACKED", "SHIPPED", "OUT_FOR_DELIVERY", "DELIVERED");

    /** @return the next status, or null when none (delivered or cancelled). */
    public static String next(String current) {
        int i = STEPS.indexOf(current);
        return i < 0 || i == STEPS.size() - 1 ? null : STEPS.get(i + 1);
    }
    /** Cancellable up to and including PACKED. Once SHIPPED the normal workflow no longer allows it. */
    public static boolean cancellable(String current) {
        int i = STEPS.indexOf(current);
        return i >= 0 && i <= STEPS.indexOf("PACKED");
    }
}
