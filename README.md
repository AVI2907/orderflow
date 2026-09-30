# OrderFlow — Multi-Vendor E-Commerce Marketplace

A microservices e-commerce marketplace where multiple sellers list products and buyers check out a single cart that is split into per-seller sub-orders. Built with Spring Boot and React, and deployed on real AWS infrastructure.

**Live demo:** https://d2j1wxboivmdi8.cloudfront.net

Register a buyer account on the site to browse, add items to your cart, and check out. Payments are simulated; no real card is charged.

---

## Architecture

```
                         ┌──────────────────────────────┐
  Browser ──HTTPS──────▶ │      Amazon CloudFront       │
                         └──┬───────────┬───────────┬───┘
               default (/*) │           │           │ /orders*, /seller-orders*
                            ▼           │           ▼
                     ┌───────────┐      │   ┌──────────────────┐
                     │ S3 bucket │      │   │  order-service   │──▶ RDS Postgres (orders)
                     │ React SPA │      │   │  Spring Boot     │──▶ Amazon SQS (order-events)
                     └───────────┘      │   └──────────────────┘
                                        ▼ /products*, /sellers*, /buyers*, /auth/*
                                ┌──────────────────┐
                                │ catalog-service  │──▶ RDS Postgres (catalog)
                                │ Spring Boot      │
                                └──────────────────┘
               Both services run as containers on ECS (EC2 launch type), images in ECR
```

- **catalog-service**: sellers, buyers, products, authentication. Issues JWTs.
- **order-service**: orders, per-seller sub-orders, order status state machine, event publishing. Verifies JWTs statelessly using the shared signing secret; it has no login endpoint of its own.
- **Frontend**: React + TypeScript (Vite), served from S3 through CloudFront. API calls use relative paths, and CloudFront routes them by path to the right service, so the browser only ever talks to one origin.

## Features

- **Three roles**: buyer, seller, and admin, enforced with Spring Security and JWT claims (`role`, `sellerId`, `buyerId`).
- **Multi-vendor checkout**: one cart becomes one order with a sub-order per seller, each tracked independently.
- **Order state machine**: `PLACED → PAID → SHIPPED → DELIVERED` (or `CANCELLED`), with invalid transitions rejected (409). The parent order's status is derived from its least-advanced sub-order.
- **Ownership checks**: sellers can only update their own sub-orders and delete their own products; buyers can only list their own orders. Buyer identity comes from the JWT, never from the request body.
- **Seller approval**: new sellers start as `PENDING` until an admin approves them.
- **Event-driven**: every status change publishes an event to SQS.
- **Price snapshots**: product name and price are copied onto each order item at purchase time, so later catalog edits don't rewrite order history.

## API reference

**catalog-service**

| Method | Path | Auth |
|---|---|---|
| POST | `/sellers/register` | none |
| POST | `/buyers/register` | none |
| POST | `/auth/login` | none |
| GET | `/products`, `/products/{id}`, `/products/seller/{sellerId}` | none |
| POST | `/products` | seller |
| DELETE | `/products/{id}` | owning seller or admin |
| PATCH | `/sellers/{id}/status` | admin |

**order-service**

| Method | Path | Auth |
|---|---|---|
| POST | `/orders` | buyer (buyer ID taken from the JWT) |
| POST | `/orders/{id}/pay` | authenticated (simulated payment) |
| GET | `/orders/{id}` | none |
| GET | `/orders/buyer/{buyerId}` | that buyer or admin |
| GET | `/seller-orders/seller/{sellerId}` | none |
| PATCH | `/seller-orders/{id}/status` | owning seller or admin |

## Tech stack

Java 21 · Spring Boot 4.1 · Spring Data JPA / Hibernate 7 · Spring Security 7 + JJWT · PostgreSQL 16 · React + TypeScript (Vite) · AWS ECS (EC2), ECR, RDS, SQS, S3, CloudFront · Docker

## Configuration

No secrets are committed. In production, the services read them from environment variables set in the ECS task definitions:

| Variable | Used by |
|---|---|
| `DB_PASSWORD` | both services |
| `JWT_SECRET` | both services (must match) |
| `ADMIN_PASSWORD` | catalog-service |
| `SPRING_PROFILES_ACTIVE=aws` | both services |

Without the `aws` profile, the services fall back to local-development defaults.

## Running the backend locally

Requires Java 21 and Docker.

```bash
docker run -d --name orderflow-postgres -p 5433:5432 \
  -e POSTGRES_DB=orderflow -e POSTGRES_USER=orderflow -e POSTGRES_PASSWORD=orderflow postgres:16
docker run -d --name orderflow-order-postgres -p 5434:5432 \
  -e POSTGRES_DB=orderflow_orders -e POSTGRES_USER=orderflow -e POSTGRES_PASSWORD=orderflow postgres:16
docker run -d --name orderflow-localstack -p 4566:4566 localstack/localstack:4.4.0

cd catalog-service && ./mvnw spring-boot:run   # port 8080
cd order-service   && ./mvnw spring-boot:run   # port 8081
```

The frontend uses relative API paths, which CloudFront routes in production. To run it locally against these services, add a proxy for those paths in `vite.config.ts`.

## Deploying

```bash
./mvnw clean package -DskipTests
docker buildx build --platform linux/amd64 \
  -t <account>.dkr.ecr.us-east-1.amazonaws.com/orderflow-<service>:latest --push .
aws ecs update-service --cluster orderflow-cluster --service <service> --force-new-deployment
```

Images are built for `linux/amd64` because development happens on Apple Silicon but the ECS host is x86_64.

Frontend:

```bash
cd frontend && npm run build
aws s3 sync dist/ s3://<bucket> --delete
aws cloudfront
# OrderFlow — Multi-Vendor E-Commerce Marketplace

A microservices e-commerce marketplace where multiple sellers list products and buyers check out a single cart that is split into per-seller sub-orders. Built with Spring Boot and React, and deployed on real AWS infrastructure.

**Live demo:** https://d2j1wxboivmdi8.cloudfront.net

Register a buyer account on the site to browse, add items to your cart, and check out. Payments are simulated; no real card is charged.

---

## Architecture

```
                         ┌──────────────────────────────┐
  Browser ──HTTPS──────▶ │      Amazon CloudFront       │
                         └──┬───────────┬───────────┬───┘
               default (/*) │           │           │ /orders*, /seller-orders*
                            ▼           │           ▼
                     ┌───────────┐      │   ┌──────────────────┐
                     │ S3 bucket │      │   │  order-service   │──▶ RDS Postgres (orders)
                     │ React SPA │      │   │  Spring Boot     │──▶ Amazon SQS (order-events)
                     └───────────┘      │   └──────────────────┘
                                        ▼ /products*, /sellers*, /buyers*, /auth/*
                                ┌──────────────────┐
                                │ catalog-service  │──▶ RDS Postgres (catalog)
                                │ Spring Boot      │
                                └──────────────────┘
               Both services run as containers on ECS (EC2 launch type), images in ECR
```

- **catalog-service**: sellers, buyers, products, authentication. Issues JWTs.
- **order-service**: orders, per-seller sub-orders, order status state machine, event publishing. Verifies JWTs statelessly using the shared signing secret; it has no login endpoint of its own.
- **Frontend**: React + TypeScript (Vite), served from S3 through CloudFront. API calls use relative paths, and CloudFront routes them by path to the right service, so the browser only ever talks to one origin.

## Features

- **Three roles**: buyer, seller, and admin, enforced with Spring Security and JWT claims (`role`, `sellerId`, `buyerId`).
- **Multi-vendor checkout**: one cart becomes one order with a sub-order per seller, each tracked independently.
- **Order state machine**: `PLACED → PAID → SHIPPED → DELIVERED` (or `CANCELLED`), with invalid transitions rejected (409). The parent order's status is derived from its least-advanced sub-order.
- **Ownership checks**: sellers can only update their own sub-orders and delete their own products; buyers can only list their own orders. Buyer identity comes from the JWT, never from the request body.
- **Seller approval**: new sellers start as `PENDING` until an admin approves them.
- **Event-driven**: every status change publishes an event to SQS.
- **Price snapshots**: product name and price are copied onto each order item at purchase time, so later catalog edits don't rewrite order history.

## API reference

**catalog-service**

| Method | Path | Auth |
|---|---|---|
| POST | `/sellers/register` | none |
| POST | `/buyers/register` | none |
| POST | `/auth/login` | none |
| GET | `/products`, `/products/{id}`, `/products/seller/{sellerId}` | none |
| POST | `/products` | seller |
| DELETE | `/products/{id}` | owning seller or admin |
| PATCH | `/sellers/{id}/status` | admin |

**order-service**

| Method | Path | Auth |
|---|---|---|
| POST | `/orders` | buyer (buyer ID taken from the JWT) |
| POST | `/orders/{id}/pay` | authenticated (simulated payment) |
| GET | `/orders/{id}` | none |
| GET | `/orders/buyer/{buyerId}` | that buyer or admin |
| GET | `/seller-orders/seller/{sellerId}` | none |
| PATCH | `/seller-orders/{id}/status` | owning seller or admin |

## Tech stack

Java 21 · Spring Boot 4.1 · Spring Data JPA / Hibernate 7 · Spring Security 7 + JJWT · PostgreSQL 16 · React + TypeScript (Vite) · AWS ECS (EC2), ECR, RDS, SQS, S3, CloudFront · Docker

## Configuration

No secrets are committed. In production, the services read them from environment variables set in the ECS task definitions:

| Variable | Used by |
|---|---|
| `DB_PASSWORD` | both services |
| `JWT_SECRET` | both services (must match) |
| `ADMIN_PASSWORD` | catalog-service |
| `SPRING_PROFILES_ACTIVE=aws` | both services |

Without the `aws` profile, the services fall back to local-development defaults.

## Running the backend locally

Requires Java 21 and Docker.

```bash
docker run -d --name orderflow-postgres -p 5433:5432 \
  -e POSTGRES_DB=orderflow -e POSTGRES_USER=orderflow -e POSTGRES_PASSWORD=orderflow postgres:16
docker run -d --name orderflow-order-postgres -p 5434:5432 \
  -e POSTGRES_DB=orderflow_orders -e POSTGRES_USER=orderflow -e POSTGRES_PASSWORD=orderflow postgres:16
docker run -d --name orderflow-localstack -p 4566:4566 localstack/localstack:4.4.0

cd catalog-service && ./mvnw spring-boot:run   # port 8080
cd order-service   && ./mvnw spring-boot:run   # port 8081
```

The frontend uses relative API paths, which CloudFront routes in production. To run it locally against these services, add a proxy for those paths in `vite.config.ts`.

## Deploying

```bash
./mvnw clean package -DskipTests
docker buildx build --platform linux/amd64 \
  -t <account>.dkr.ecr.us-east-1.amazonaws.com/orderflow-<service>:latest --push .
aws ecs update-service --cluster orderflow-cluster --service <service> --force-new-deployment
```

Images are built for `linux/amd64` because development happens on Apple Silicon but the ECS host is x86_64.

Frontend:

```bash
cd frontend && npm run build
aws s3 sync dist/ s3://<bucket> --delete
aws cloudfront create-invalidation --distribution-id <id> --paths "/*"
```

## Problems solved along the way

- **Login worked from curl but failed in the browser.** Browsers send an `Origin` header and curl doesn't. Spring's CORS config only allowed `http://localhost:5173`, so every browser request through CloudFront was rejected with `Invalid CORS request`. Fixed by adding the CloudFront origin to both services.
- **A CloudFront error rule was hiding real API errors.** A `403 → /index.html (200)` rule, added so the SPA's client-side routes would load, also rewrote genuine API 403s into successful HTML responses. The frontend then stored `"undefined"` as the login token. Fixed by granting CloudFront `s3:ListBucket`, so missing S3 paths return 404 instead of 403, and removing the 403 rule.
- **Containers wouldn't start on ECS** (`no matching manifest for linux/amd64`). Fixed by building with `docker buildx --platform linux/amd64`.
- **Deployments stalled on a single t3.micro** because ECS tried to run old and new tasks side by side. Fixed by setting `minimumHealthyPercent=0, maximumPercent=100`, trading brief downtime for fitting in memory.
- **Secrets moved out of source.** The JWT secret, admin password and database password went from hardcoded properties to environment variables before the repo went public, and the secrets were rotated.

## Known limitations

Deliberate trade-offs for a free-tier portfolio deployment:

- **Simulated payments.** `/orders/{id}/pay` marks orders as paid without charging a card. Real processing (e.g. Stripe) is the planned next step.
- **Single EC2 instance, no load balancer.** One t3.micro runs both services; there's no multi-AZ redundancy, and deploys cause brief downtime.
- **CloudFront to EC2 is plain HTTP.** Browser traffic is HTTPS to CloudFront, but the hop to the backend isn't encrypted. An ALB with a TLS certificate would fix this.
- **Secrets are plain task-definition environment variables.** AWS Secrets Manager or SSM Parameter Store would be the production-grade choice.
- **Some read endpoints are open.** `GET /orders/{id}` and `GET /seller-orders/seller/{id}` don't check ownership, and `/orders/{id}/pay` doesn't verify that the caller owns the order.
- **No SQS consumer yet.** Events are published but nothing reads them; a Lambda for notifications is planned. There is also no dead-letter queue.
- **Seller onboarding and admin approval are API-only**; there's no UI for them yet.
- **No automated tests.**
