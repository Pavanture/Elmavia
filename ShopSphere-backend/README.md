# ShopSphere — E-Commerce Backend

A production-style e-commerce REST API built with **Java 17** and **Spring Boot**. ShopSphere implements user authentication, a product catalog, a shopping cart, and a transactional order pipeline, with JWT-based security, role-based authorization, global exception handling, and OpenAPI documentation.



---

## 1. Features

- **Authentication & Authorization** — JWT access tokens, BCrypt password hashing, `ROLE_USER` / `ROLE_ADMIN` role-based access control.
- **User profiles** — self-service view/update of the authenticated user's own account.
- **Product catalog** — CRUD (admin-only writes), pagination, keyword search, category filtering.
- **Categories** — CRUD (admin-only writes).
- **Shopping cart** — add/update/remove items, stock validation, subtotal & item-count calculation.
- **Addresses** — per-user shipping address book.
- **Orders** — atomic order placement from the cart (stock check → order + items → total → stock deduction → cart clear), order history, cancellation with stock restoration, admin order management.
- **Global exception handling** — consistent JSON error shape via `@RestControllerAdvice`.
- **Bean Validation** — all incoming DTOs validated with clear field-level messages.
- **OpenAPI / Swagger UI** — interactive API docs.
- **Unit & integration tests** — JUnit 5 + Mockito for services, MockMvc + H2 for authorization rules.

---

## 2. Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.3 |
| Web | Spring Web (MVC) |
| Persistence | Spring Data JPA + Hibernate |
| Security | Spring Security + JJWT (JWT) |
| Database | MySQL 8 (H2 in-memory for tests) |
| Build | Maven |
| Boilerplate reduction | Lombok |
| Validation | Jakarta Bean Validation |
| Docs | springdoc-openapi (Swagger UI) |
| Testing | JUnit 5, Mockito, Spring Security Test |

---

## 3. Architecture

Layered, package-by-responsibility structure:

```text
com.shopsphere
│
├── ShopSphereApplication.java     # Spring Boot entry point
│
├── config/            # Security, CORS, OpenAPI configuration
├── controller/         # Thin REST controllers (request/response only)
├── service/            # Business-logic interfaces
│   └── impl/           # Business-logic implementations
├── repository/          # Spring Data JPA repositories
├── entity/             # JPA entities
│   └── enums/           # Role, OrderStatus, PaymentStatus
├── dto/
│   ├── request/          # Inbound, validated request bodies
│   └── response/         # Outbound response shapes (never expose entities)
├── mapper/              # Entity <-> DTO conversion
├── security/            # JWT util, filter, UserDetails, entry point
├── exception/            # Custom exceptions + global handler
└── util/                # Cross-cutting helpers (SecurityUtils)
```

Controllers depend only on service interfaces; services depend only on repositories and mappers. Entities are never returned directly from any endpoint — every response goes through a DTO built by a mapper.

### Request flow

```
Client → Controller → Service (interface) → ServiceImpl → Repository → Database
                                    │
                                    ▼
                                 Mapper → DTO → Client
```

### Authentication flow

1. `POST /api/auth/register` or `/api/auth/login` → server validates credentials, issues a signed JWT (HS256) containing `sub` (email), `userId`, and `role`.
2. Client sends the token on every subsequent request: `Authorization: Bearer <token>`.
3. `JwtAuthenticationFilter` runs once per request, validates the token, loads the user, and populates Spring Security's `SecurityContext`.
4. `SecurityConfig` enforces route-level rules (public reads, authenticated writes, `ROLE_ADMIN`-only management endpoints).
5. Unauthenticated requests to protected routes get a consistent JSON 401 via `JwtAuthEntryPoint`; unauthorized (wrong role) requests get a JSON 403 via `GlobalExceptionHandler`.

---

## 4. Database Design

```text
User 1 ─── 1 Cart
Cart 1 ─── * CartItem
Product 1 ─── * CartItem

User 1 ─── * Order
Order 1 ─── * OrderItem
Product 1 ─── * OrderItem

Category 1 ─── * Product
User 1 ─── * Address
```

Key design decisions:

- **Order line-items snapshot price and product name at purchase time** (`OrderItem.priceAtPurchase`, `OrderItem.productName`), so later product price/name changes never retroactively alter historical orders.
- **Order shipping address is embedded as a snapshot** (`ShippingAddressSnapshot`, `@Embeddable`) rather than a live foreign key to `Address`, so editing or deleting an address book entry never mutates past orders.
- Unique constraints: `users.email`, `categories.name`, `(cart_id, product_id)` on `cart_items`.
- Indexes on `products.name`, `products.category_id`, and `orders.user_id` for common query patterns.
- `Cart` is a required 1:1 with `User`, created automatically at registration.
- Cascading: `Cart → CartItem` and `Order → OrderItem` cascade `ALL` with `orphanRemoval`, so removing a cart/order item is a simple collection operation.

Hibernate manages schema creation via `spring.jpa.hibernate.ddl-auto` (default `update`, safe for local development — use a real migration tool such as Flyway/Liquibase for production).

---

## 5. API Endpoints

All responses are wrapped in a consistent envelope:

```json
{ "success": true, "message": "Product fetched successfully", "data": { } }
```

Errors:

```json
{
  "timestamp": "2026-09-04T10:15:30",
  "status": 404,
  "message": "Product not found with id: 10",
  "path": "/api/products/10"
}
```

### Auth (`/api/auth`) — public

| Method | Path | Description |
|---|---|---|
| POST | `/api/auth/register` | Create a new account, returns a JWT |
| POST | `/api/auth/login` | Authenticate, returns a JWT |

### Users (`/api/users`) — authenticated

| Method | Path | Description |
|---|---|---|
| GET | `/api/users/me` | Get the current user's profile |
| PUT | `/api/users/me` | Update name / phone |

### Products (`/api/products`) — reads public, writes ADMIN

| Method | Path | Description |
|---|---|---|
| GET | `/api/products` | List/search, supports `keyword`, `categoryId`, `page`, `size`, `sortBy`, `direction` |
| GET | `/api/products/{id}` | Get one product |
| POST | `/api/products` | Create (ADMIN) |
| PUT | `/api/products/{id}` | Update (ADMIN) |
| DELETE | `/api/products/{id}` | Delete (ADMIN) |

### Categories (`/api/categories`) — reads public, writes ADMIN

| Method | Path | Description |
|---|---|---|
| GET | `/api/categories` | List all |
| GET | `/api/categories/{id}` | Get one |
| POST | `/api/categories` | Create (ADMIN) |
| PUT | `/api/categories/{id}` | Update (ADMIN) |
| DELETE | `/api/categories/{id}` | Delete (ADMIN) |

### Qikink integration — ADMIN

| Method | Path | Description |
|---|---|---|
| GET | `/api/admin/integrations/qikink/orders` | Fetch Qikink orders; supports optional Qikink filters |

Configure `QIKINK_CLIENT_ID` and `QIKINK_CLIENT_SECRET` in the backend environment.
The backend exchanges the credentials at `/api/token` and sends the resulting `Accesstoken` and `ClientId`
headers to Qikink's `GET /api/order/list` endpoint. Optional query parameters are `order_reference_no`,
`status`, `id`, `ids`, `params`, `live_date_min`, `live_date_max`, and `page_no`.
`QIKINK_BASE_URL`, `QIKINK_TOKEN_PATH`, and `QIKINK_ORDER_LIST_PATH` can override the API base URL,
token route, and order-list route. Credentials are never sent to the frontend.

### Cart (`/api/cart`) — authenticated, own cart only

| Method | Path | Description |
|---|---|---|
| GET | `/api/cart` | Get the current user's cart |
| POST | `/api/cart/items` | Add a product (`productId`, `quantity`) |
| PUT | `/api/cart/items/{productId}` | Update quantity |
| DELETE | `/api/cart/items/{productId}` | Remove one product |
| DELETE | `/api/cart` | Clear the cart |

### Addresses (`/api/addresses`) — authenticated, own addresses only

| Method | Path | Description |
|---|---|---|
| GET | `/api/addresses` | List addresses |
| POST | `/api/addresses` | Add an address |
| PUT | `/api/addresses/{id}` | Update an address |
| DELETE | `/api/addresses/{id}` | Delete an address |

### Orders (`/api/orders`) — authenticated, own orders only

| Method | Path | Description |
|---|---|---|
| POST | `/api/orders` | Place an order from the current cart (`addressId`) |
| GET | `/api/orders` | List the current user's orders (paginated) |
| GET | `/api/orders/{id}` | Get one of the current user's orders |
| PUT | `/api/orders/{id}/cancel` | Cancel an order (restocks items) |

### Admin Orders (`/api/admin/orders`) — ADMIN only

| Method | Path | Description |
|---|---|---|
| GET | `/api/admin/orders` | List all orders (paginated) |
| GET | `/api/admin/orders/{id}` | Get any order |
| PUT | `/api/admin/orders/{id}/status` | Update `orderStatus` / `paymentStatus` |

Full interactive documentation: **`GET /swagger-ui.html`** once the app is running.

---

## 6. Order Placement Flow (Business Logic)

`POST /api/orders` runs inside a single `@Transactional` boundary:

1. Load the user's cart; reject if empty.
2. Load and validate the chosen shipping address belongs to the user.
3. Validate stock for **every** cart line before mutating anything.
4. Create the `Order` and snapshot the shipping address.
5. Create `OrderItem`s, snapshotting product name & price, and accumulate the total.
6. Decrement product stock for each item.
7. Clear the user's cart.
8. Persist everything atomically — if any step fails, the whole transaction rolls back.

Cancelling an order (`PUT /api/orders/{id}/cancel`) restores stock for each item and is blocked once the order has shipped or delivered.

---

## Deploying the backend to Render

The repository includes a root-level `render.yaml` Blueprint and a Dockerfile for the Spring Boot API.
The backend requires an externally hosted MySQL database; Render does not provide a managed MySQL
service. Confirm that your MySQL provider allows connections from Render and use a dedicated database
for ShopSphere.

1. Push this repository to GitHub.
2. In Render, choose **New → Blueprint** and connect this repository. Render reads `render.yaml` and
   configures the `shopsphere-backend` web service.
3. Before the service can start, set `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, and `DB_PASSWORD`
   for the service using your MySQL provider's connection values. Do not commit these values.
   `JWT_SECRET` is generated by the Blueprint. The configured CORS origin is the current frontend
   origin; update it if the frontend URL changes. Qikink credentials are not required for the ShopSphere
   API or storefront.
4. Wait for the first deploy to finish, then check
   `https://<your-render-service>.onrender.com/v3/api-docs`.
5. Configure the frontend to use the deployed backend URL followed by `/api` in
   `ShopSphere-frontend/index.html`, then rebuild and redeploy the frontend.

The Blueprint uses Render's free web-service plan to avoid unexpected charges. Free services can spin
down after inactivity, so the first request may take longer. Consider an always-on plan if the storefront
needs consistent response times. Back up the database before deploying with `DDL_AUTO=update`.

---

## 7. Setup Instructions

### Prerequisites

- Java 17 (JDK)
- Maven 3.9+
- MySQL 8.x running locally or reachable

### 1. Clone / unzip the project

```bash
cd ShopSphere-backend
```

### 2. Create the database (optional — the app can auto-create it)

```sql
CREATE DATABASE shopsphere_db;
```

### 3. Configure environment variables

Use `.env.example` as a reference for the required variables. Spring Boot does not load `.env` files automatically, so export the variables in your shell or add them to your IDE run configuration. For example, in PowerShell:

```powershell
$env:DB_HOST = 'localhost'
$env:DB_PORT = '3306'
$env:DB_NAME = 'shopsphere_db'
$env:DB_USERNAME = 'root'
$env:DB_PASSWORD = 'your-db-password'
$env:JWT_SECRET = 'replace-with-a-long-random-base64-secret-at-least-256-bits'
$env:CORS_ALLOWED_ORIGINS = 'http://localhost:3000'
```

Keep real secrets out of source control. Set the same variables in your IDE run configuration when launching from the IDE.

```text
DB_HOST=localhost
DB_PORT=3306
DB_NAME=shopsphere_db
DB_USERNAME=root
DB_PASSWORD=your-db-password

JWT_SECRET=replace-with-a-long-random-base64-secret-at-least-256-bits
JWT_EXPIRATION=86400000

CORS_ALLOWED_ORIGINS=http://localhost:3000

```

> Never commit real secrets. `JWT_SECRET` should be a long, random value in any real deployment — generate one with `openssl rand -base64 48`.

### 4. Build

```bash
mvn clean install
```

### 5. Run

```bash
mvn spring-boot:run
```

or run the packaged jar:

```bash
java -jar target/shopsphere.jar
```

The API is now available at `http://localhost:8080`, and Swagger UI at `http://localhost:8080/swagger-ui.html`.

### 6. Run tests

```bash
mvn test
```

Tests run against an in-memory H2 database (see `application-test.yml`) and do not require MySQL.

---

## 8. Environment Variables Reference

| Variable | Purpose | Example |
|---|---|---|
| `DB_HOST` | MySQL host | `localhost` |
| `DB_PORT` | MySQL port | `3306` |
| `DB_NAME` | Database name | `shopsphere_db` |
| `DB_USERNAME` | MySQL user | `root` |
| `DB_PASSWORD` | MySQL password | *(secret)* |
| `JWT_SECRET` | HMAC signing key for JWTs | *(secret, ≥256 bits)* |
| `JWT_EXPIRATION` | Access token lifetime, ms | `86400000` (24h) |
| `CORS_ALLOWED_ORIGINS` | Comma-separated allowed origins | `http://localhost:3000` |
| `SERVER_PORT` | HTTP port | `8080` |
| `DDL_AUTO` | Hibernate schema strategy | `update` |
Products are added and managed manually from the Admin product form. Add each colour/size combination as a variant with its public image URL and available stock.

Hibernate creates the variant table and new cart index on startup. On an existing MySQL database, run `src/main/resources/db/manual/20261002_allow_cart_variants.sql` once after restarting the backend to remove the legacy unique index on `(cart_id, product_id)`. This keeps existing cart rows and allows multiple variants of the same product in one cart.

On an existing database, run `src/main/resources/db/manual/20261029_remove_qikink.sql` once to remove the obsolete Qikink columns and variant SKU index. Back up the database before applying this destructive schema cleanup.

---

## 9. Sample Requests

### Register

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Jane Doe",
    "email": "jane@example.com",
    "password": "SecurePass123",
    "phone": "9876543210"
  }'
```

### Login

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{ "email": "jane@example.com", "password": "SecurePass123" }'
```

Response:

```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresInMs": 86400000,
    "user": { "id": 1, "name": "Jane Doe", "email": "jane@example.com", "role": "ROLE_USER" }
  }
}
```

### Add a product to the cart (authenticated)

```bash
curl -X POST http://localhost:8080/api/cart/items \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{ "productId": 1, "quantity": 2 }'
```

### Place an order

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{ "addressId": 1 }'
```

---

## 10. Test / Development Credentials

No accounts are seeded by default. Create your own via `/api/auth/register`; the first `ROLE_ADMIN` user must be promoted manually, e.g.:

```sql
UPDATE users SET role = 'ROLE_ADMIN' WHERE email = 'admin@example.com';
```

For local development only, you might use:

```text
Email:    admin@example.com
Password: AdminPass123!
```

**Do not reuse these values anywhere real** — they exist purely as a placeholder for local testing.

---

## 11. Notes on Production Readiness

This project follows production patterns (DTOs, layered architecture, transactional integrity, centralized error handling, externalized secrets), but a few things to add before a real deployment:

- Schema migrations via Flyway or Liquibase instead of `ddl-auto: update`.
- Refresh tokens / token revocation if long-lived sessions are needed.
- Rate limiting on `/api/auth/**`.
- Centralized structured logging / tracing (e.g. ELK, OpenTelemetry).
- A real payment gateway integration behind `paymentStatus`.
- CI pipeline running `mvn verify` on every push.

---

## 12. License

Provided as-is for portfolio and educational purposes.
