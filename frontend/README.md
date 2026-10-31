# ShopSphere storefront

A React storefront and marketplace workspace for the hosted ShopSphere API. The page theme follows the supplied references: near-black surfaces, violet light, considered editorial typography, and small, responsive transitions.

## Run locally

Requirements: Node.js 20.19+ or 22.12+.

```powershell
cd path\to\shopsphere\frontend
npm install
npm run dev
```

Open the local URL Vite prints, normally `http://localhost:5173`.

The app defaults to the API host listed in the OpenAPI file. To point at a different backend, create `frontend/.env.local` with the backend origin only (do not append `/api`):

```dotenv
VITE_API_BASE_URL=https://your-backend-host.example
```

Restart Vite after changing `.env.local`. The hosted backend must allow the frontend’s origin through CORS. Browser requests use bearer access tokens; login, refresh, and logout also permit the backend’s refresh cookie.

## Build

```powershell
npm run build
npm run preview
```

## Included workflows

- Catalog discovery, text search, categories, brands, stock and price filters, sorting, pagination, product detail, wishlist, and comparison.
- Account registration, email verification, login, password reset, saved addresses, profile, password change, notifications, cart, checkout, order history, cancellation, and delivery tracking.
- Seller application and seller studio for products, image upload, stock, listing status, orders, insights, and shop profile.
- Admin studio for dashboard, customers, sellers, product inventory, orders and shipment advancement, and categories.
- Product recommendation assistant wired to `/api/chatbot/message`.

The frontend only calls routes defined in the supplied OpenAPI document. Email delivery, social sign-in initiation, payments, and other provider services remain controlled by the backend and its provider configuration.

## Libraries

- React 19 and Vite 8 for the application and build.
- React Router for client-side pages.
- TanStack Query for API state and cache invalidation.
- Motion for React page, card, and dialog transitions.
- Lucide React for icons.
- Manrope and DM Sans variable fonts are bundled locally through Fontsource.

## Configuration and safety

- Never put backend secrets in `VITE_*` variables. Vite variables are visible to browser users.
- Access tokens are held in session storage and cleared on sign-out. Refresh tokens are expected to be HttpOnly cookies when the backend supports them.
- API errors render as retryable states; no demo catalog is silently substituted for backend data.
