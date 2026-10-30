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
