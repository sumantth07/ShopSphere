package com.shopsphere.orders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public final class OrderDtos {
    private OrderDtos() {}
    public record Item(long id, long productId, String name, String imageUrl, BigDecimal unitPrice, int quantity, String status, String cancelReason, boolean cancellable) {}
    public record HistoryEntry(String status, String note, OffsetDateTime at) {}
    public record Group(long id, long sellerId, String sellerName, String status, LocalDate estimatedDelivery, BigDecimal subtotal,
                        boolean cancellable, String cancelReason, List<Item> items, List<HistoryEntry> history) {}
    public record ShipTo(String name, String phone, String line1, String line2, String city, String state, String postalCode, String country) {}
    public record Order(long id, String status, OffsetDateTime createdAt, BigDecimal subtotal, BigDecimal shippingTotal, BigDecimal total,
                        String paymentMethod, String paymentStatus, boolean demo, ShipTo shipTo, List<Group> groups) {}
    public record OrderSummary(long id, String status, OffsetDateTime createdAt, BigDecimal total, int itemCount, int sellerCount,
                               String paymentMethod, String paymentStatus, boolean demo, String firstImage) {}
    public record PlaceOrder(@jakarta.validation.constraints.NotNull Long addressId, @jakarta.validation.constraints.NotBlank String paymentMethod) {}
    public record CancelRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 300) String reason) {}
}
