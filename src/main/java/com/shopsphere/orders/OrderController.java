package com.shopsphere.orders;

import com.shopsphere.orders.OrderDtos.*;
import com.shopsphere.orders.OrderService.Scope;
import com.shopsphere.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orders;
    public OrderController(OrderService orders) { this.orders = orders; }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Order place(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PlaceOrder r) {
        long uid = CurrentUser.id(jwt);
        return orders.get(uid, orders.place(uid, r));
    }

    @GetMapping public List<OrderSummary> list(@AuthenticationPrincipal Jwt jwt) { return orders.list(CurrentUser.id(jwt)); }
    @GetMapping("/{id}") public Order get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) { return orders.get(CurrentUser.id(jwt), id); }

    /** Cancel the whole order (eligible shipments only). */
    @PostMapping("/{id}/cancel")
    public Map<String, Object> cancelOrder(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody CancelRequest r) {
        return Map.of("cancelledItems", orders.cancel(CurrentUser.id(jwt), id, Scope.ORDER, null, r.reason()));
    }
    /** Cancel one seller's shipment without touching the others. */
    @PostMapping("/{id}/shipments/{groupId}/cancel")
    public Map<String, Object> cancelGroup(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @PathVariable long groupId, @Valid @RequestBody CancelRequest r) {
        return Map.of("cancelledItems", orders.cancel(CurrentUser.id(jwt), id, Scope.GROUP, groupId, r.reason()));
    }
    /** Cancel a single item. */
    @PostMapping("/{id}/items/{itemId}/cancel")
    public Map<String, Object> cancelItem(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @PathVariable long itemId, @Valid @RequestBody CancelRequest r) {
        return Map.of("cancelledItems", orders.cancel(CurrentUser.id(jwt), id, Scope.ITEM, itemId, r.reason()));
    }
}
