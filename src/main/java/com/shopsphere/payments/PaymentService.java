package com.shopsphere.payments;

import java.math.BigDecimal;

/**
 * Payment abstraction. Today only {@link CodPaymentService} exists (Cash on Delivery, demo).
 * To add a gateway (Stripe, Razorpay...): implement this interface, return a PENDING payment with a provider reference,
 * and mark it PAID ONLY from a signature-verified webhook handler, never from the browser.
 */
public interface PaymentService {
    String method();
    /** Creates the payment record for a new order and returns its initial status (never PAID without provider evidence). */
    String createFor(long orderId, BigDecimal amount);
}
