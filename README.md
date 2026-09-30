# OrderFlow — Multi-Vendor E-Commerce Marketplace

A microservices-based e-commerce platform demonstrating production-style patterns: independent Spring Boot services, event-driven messaging via SQS, JWT-based auth, and a React storefront — deployed on real AWS infrastructure (not LocalStack, not a demo mode).

**Live stack:** two Spring Boot microservices on ECS (EC2 launch type), two RDS Postgres instances, a real SQS queue, and a React/TypeScript frontend.

---

## Architecture
┌─────────────────┐ ┌──────────────────────┐ ┌──────────────────────┐
│ React Frontend │ HTTP │ catalog-service │ HTTP │ order-service │
│ (Vite + TS) │────────▶│ (Spring Boot :8080) │ │ (Spring Boot :8081) │
└─────────────────┘ └───────────┬───────────┘ └───────────┬───────────┘
│ │
▼ ▼
┌───────────────────┐ ┌───────────────────┐
│ RDS Postgres │ │ RDS Postgres │
│ (catalog DB) │ │ (orders DB) │
└────────────────────┘ └──────────┬──────────┘
│
▼
┌────────────────────┐
│ AWS SQS │
│ (order-events) │
└────────────────────┘

Both services run as ECS tasks on a single shared EC2 instance (bridge networking), backed by independent RDS Postgres databases — one per service, enforcing a strict bounded-context separation typical of microservice architectures.

---

## Services

### catalog-service (:8080)
Owns sellers and products.
- Seller registration + admin approval workflow (`PENDING` → `APPROVED`)
- Product CRUD, with ownership-enforced delete (a seller can only delete their own listings; admins can delete any)
- JWT issuance and validation (shared secret with order-service)

### order-service (:8081)
Owns orders and multi-vendor order splitting.
- A single checkout can span multiple sellers; each seller's portion becomes an independent `SellerOrder` with its own state machine (`PLACED → PAID → SHIPPED → DELIVERED`, or `CANCELLED`)
- Publishes an event to SQS on every status transition, decoupling order-state changes from any downstream consumers (notifications, analytics, etc.)
- Validates JWTs issued by catalog-service — no shared database, only a shared signing secret

---

## Key Technical Decisions

| Decision | Rationale |
|---|---|
| **Multi-vendor order splitting** | Cart is grouped by seller client-side; each seller-order tracks its own status independently, matching how real marketplaces (Etsy, Amazon Marketplace) reconcile multi-seller carts |
| **Stateless JWT cross-service trust** | order-service verifies tokens it never issued, using only a shared secret — no session store, no service-to-service auth calls |
| **SQS publish-and-continue** | A messaging failure is caught and logged, never rolled back with the business transaction — order state is the source of truth, not the event bus |
| **EC2 launch type over Fargate** | Fargate has no AWS free tier; EC2 t3.micro uses the 750 free hours/month, at the cost of manually managing container placement |
| **Independent databases per service** | No cross-service joins possible by design — forces the same discipline a true microservice boundary requires |
| **linux/amd64 explicit builds** | Local dev is Apple Silicon (arm64); ECS runs x86_64, so every image is built with `docker buildx build --platform linux/amd64` |

---

## Local Development

**Prerequisites:** Java 21, Maven, Docker, Node 18+

```bash
# Start local infra (Postgres x2 + LocalStack for SQS)
docker run -d --name orderflow-postgres -p 5433:5432 -e POSTGRES_PASSWORD=postgres postgres:16
docker run -d --name orderflow-order-postgres -p 5434:5432 -e POSTGRES_PASSWORD=postgres postgres:16
docker run -d --name orderflow-localstack -p 4566:4566 localstack/localstack:4.4.0

# Run each service
cd catalog-service && ./mvnw spring-boot:run   # :8080
cd order-service && ./mvnw spring-boot:run     # :8081

# Run the frontend
cd frontend && npm install && npm run dev      # :5173
```

## Production Deployment

Both services are containerized and deployed to a single-instance ECS cluster (EC2 launch type) behind no load balancer (portfolio-scale, not HA). Redeploying after a code change:

```bash
./mvnw clean package -DskipTests
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin <account>.dkr.ecr.us-east-1.amazonaws.com
docker buildx build --platform linux/amd64 -t <account>.dkr.ecr.us-east-1.amazonaws.com/orderflow-<service>:latest --push .
aws ecs update-service --cluster orderflow-cluster --service <service> --force-new-deployment
```

---

## API Reference (abridged)

**catalog-service**
| Method | Path | Auth |
|---|---|---|
| POST | `/sellers/register` | none |
| POST | `/auth/login` | none |
| GET | `/products` | none |
| POST | `/products` | seller |
| DELETE | `/products/{id}` | owning seller or admin |
| PATCH | `/sellers/{id}/status` | admin |

**order-service**
| Method | Path | Auth |
|---|---|---|
| POST | `/orders` | none* |
| POST | `/orders/{id}/pay` | none* |
| GET | `/orders/buyer/{buyerId}` | none* |
| GET | `/seller-orders/seller/{sellerId}` | seller |
| PATCH | `/seller-orders/{id}/status` | seller |

*\*buyerId is a hardcoded placeholder — see "Known Limitations" below.*

---

## Known Limitations (by design, for a portfolio scope)

- **No buyer account system** — `buyerId` is a fixed placeholder UUID rather than derived from an authenticated buyer identity. A real implementation would add buyer registration/login mirroring the seller flow.
- **No load balancer / single point of failure** — one EC2 instance hosts both services; a production system would run each service across multiple AZs behind an ALB.
- **Frontend not publicly hosted** — currently runs locally against the live AWS backend; not yet deployed to S3/CloudFront.
- **No dead-letter queue** — a failed SQS publish is logged and swallowed rather than retried or routed to a DLQ.

---

## Stack

Spring Boot 4.1.1 · Java 21 · Spring Data JPA / Hibernate 7 · Spring Security 7 + JJWT · PostgreSQL 16 · AWS SQS · React + TypeScript (Vite) · AWS ECS (EC2) · RDS · ECR
