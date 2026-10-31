# ShopSphere backend
Spring Boot 3.3 · Java 21 · Spring Data JPA + `JdbcClient` · Flyway · PostgreSQL · Spring Security (OAuth2 resource server + OAuth2 client) · springdoc · Actuator.

```bash
createdb shopsphere
cp .env.example .env   # then export the variables in your shell/IDE (Spring does not read .env by itself)
mvn spring-boot:run
mvn test               # unit tests (no DB needed)
```
Required: `DB_*`, `JWT_SECRET`, `FRONTEND_ORIGIN`. Optional features: `MAIL_*` (OTP email; dev console mailer otherwise), `GOOGLE_*`, `AI_API_KEY`, `ADMIN_EMAIL/ADMIN_PASSWORD`. See `.env.example`.
Profiles: none (default dev), `demo` (seed demo users/sellers/orders; never in prod), `prod` (refuses the dev mailer).
Docs: Swagger UI `/swagger-ui.html`; guides in `../docs`. Import the catalog with `../docs/CSV_IMPORT_GUIDE.md`.
Logs: sensitive values (passwords, codes, tokens) are never logged, except the **dev-only** console mailer that prints OTP emails when `MAIL_HOST` is empty.
Production notes: HTTPS, `COOKIE_SECURE=true`, strong `JWT_SECRET`, real SMTP, single-instance rate limiter (use a shared store if scaling out), DB backups.
**Status: written but not compiled by the author's sandbox. See `../docs/PROJECT_STATUS.md`.**

penAPI definition
 v0 
OAS 3.0
/v3/api-docs
Servers

https://shopsphere-backend-a59o.onrender.com - Generated server url
user-controller


GET
/api/users/me


PUT
/api/users/me


GET
/api/users/me/notifications

seller-controller


GET
/api/sellers/me


PUT
/api/sellers/me


GET
/api/sellers/me/products/{id}


PUT
/api/sellers/me/products/{id}


GET
/api/sellers/me/products


POST
/api/sellers/me/products


POST
/api/sellers/me/orders/{groupId}/advance


POST
/api/sellers/applications


PATCH
/api/sellers/me/products/{id}/stock


PATCH
/api/sellers/me/products/{id}/status


GET
/api/sellers/me/orders


GET
/api/sellers/me/orders/{groupId}


GET
/api/sellers/me/insights

admin-controller


PUT
/api/admin/categories/{id}


DELETE
/api/admin/categories/{id}


POST
/api/admin/shipments/{groupId}/advance


GET
/api/admin/products


POST
/api/admin/products


GET
/api/admin/categories


POST
/api/admin/categories


PATCH
/api/admin/sellers/{id}


PATCH
/api/admin/products/{id}/stock


PATCH
/api/admin/products/{id}/status


GET
/api/admin/sellers


GET
/api/admin/orders


GET
/api/admin/orders/{id}


GET
/api/admin/dashboard


GET
/api/admin/customers

address-controller


PUT
/api/addresses/{id}


DELETE
/api/addresses/{id}


GET
/api/addresses


POST
/api/addresses

wishlist-controller


GET
/api/wishlist


POST
/api/wishlist


GET
/api/wishlist/ids


DELETE
/api/wishlist/items/{productId}

upload-controller


POST
/api/sellers/me/uploads

order-controller


GET
/api/orders


POST
/api/orders


POST
/api/orders/{id}/shipments/{groupId}/cancel


POST
/api/orders/{id}/items/{itemId}/cancel


POST
/api/orders/{id}/cancel


GET
/api/orders/{id}

chat-controller


POST
/api/chatbot/message

cart-controller


POST
/api/cart/items


DELETE
/api/cart/items/{id}


PATCH
/api/cart/items/{id}


GET
/api/cart

auth-controller


POST
/api/auth/verify-email


POST
/api/auth/reset-password


POST
/api/auth/resend-verification


POST
/api/auth/register


POST
/api/auth/refresh


POST
/api/auth/logout


POST
/api/auth/login


POST
/api/auth/google/confirm-link


POST
/api/auth/forgot-password


POST
/api/auth/change-password


GET
/api/auth/providers

product-controller


GET
/api/products


GET
/api/products/{id}


GET
/api/products/compare


GET
/api/brands

category-controller


GET
/api/categories


Schemas
UpdateProfile
UserDto
ProfileUpdate
SellerProfile
ProductRequest
CategoryRequest
AddressRequest
Address
AddRequest
ApplyRequest
PlaceOrder
Group
HistoryEntry
Item
Order
ShipTo
CancelRequest
ChatMessage
ChatRequest
ChatResult
ProductCard
AddItem
CartLine
CartView
VerifyRequest
AuthResponse
ResetRequest
MessageResponse
EmailRequest
RegisterRequest
LoginRequest
ConfirmLinkRequest
ChangePasswordRequest
AdminProductRequest
StockRequest
StatusRequest
SetQuantity
SellerDecision
Notification
PageSellerProduct
SellerProduct
ProductDetailForEdit
SellerOrderRow
SellerOrderDetail
PageResponseProductSummary
ProductSummary
ProductDetail
ProductCompare
OrderSummary
CategoryResponse
BrandResponse
SellerRow
PageProductRow
ProductRow
AdminOrderRow
PageAdminOrderRow
