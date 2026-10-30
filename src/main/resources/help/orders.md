# Orders, delivery and cancellation

## Multi-seller orders
One checkout creates one order. Items are grouped into shipments by seller. Each shipment has its own status, its own estimated delivery date and its own timeline, so two sellers in the same order can deliver on different days.

## Shipment statuses
Placed, Confirmed, Processing, Packed, Shipped, Out for Delivery, Delivered, and Cancelled. Statuses move forward one step at a time and are updated by the seller (or an administrator).

## Estimated delivery dates
Delivery dates and tracking in this project are simulated demo data, not real courier information. The estimate is the order date plus 3 days plus a seller-specific 0 to 4 days. It is stored when the order is placed and does not change on refresh.

## Order status
Placed (nothing started), In progress, Partially cancelled (some items cancelled, others continuing), Delivered, Cancelled.

## Cancelling
You can cancel a whole order, one seller's shipment, or a single item, with a reason, as long as the affected shipment has not shipped yet (Placed up to Packed). After a shipment is Shipped it can no longer be cancelled in the normal workflow. Cancelling one seller's items never affects other sellers' shipments. Cancelled quantities are returned to stock exactly once. No real payment refund is involved because payments are demo only.

## Where to look
Open Account then Orders, choose an order to see each shipment's timeline and estimated delivery.
