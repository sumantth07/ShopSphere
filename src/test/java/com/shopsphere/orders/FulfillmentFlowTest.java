package com.shopsphere.orders;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FulfillmentFlowTest {
    @Test void advancesOneStepAtATime() {
        assertEquals("CONFIRMED", FulfillmentFlow.next("PLACED"));
        assertEquals("DELIVERED", FulfillmentFlow.next("OUT_FOR_DELIVERY"));
        assertNull(FulfillmentFlow.next("DELIVERED"));
        assertNull(FulfillmentFlow.next("CANCELLED"));
    }
    @Test void cancellableOnlyBeforeShipping() {
        for (String s : new String[]{"PLACED", "CONFIRMED", "PROCESSING", "PACKED"}) assertTrue(FulfillmentFlow.cancellable(s), s);
        for (String s : new String[]{"SHIPPED", "OUT_FOR_DELIVERY", "DELIVERED", "CANCELLED"}) assertFalse(FulfillmentFlow.cancellable(s), s);
    }
    @Test void differentSellersGetDifferentStableEstimates() {
        LocalDate d = LocalDate.of(2026, 10, 20);
        LocalDate a = DeliveryEstimator.estimate(d, 1, 3, 5), b = DeliveryEstimator.estimate(d, 2, 3, 5);
        assertNotEquals(a, b);
        assertEquals(a, DeliveryEstimator.estimate(d, 1, 3, 5));   // deterministic: never changes between page loads
        assertEquals(LocalDate.of(2026, 10, 24), a);
    }
}
