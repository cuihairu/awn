# Source Layer Architecture

English | [中文](../zh/architecture/source-layer-architecture.md) · [Glossary](source-layer-glossary.md) · [Why These Layers](source-layer-split-rationale.md)

This map records the responsibility layers found by reading the source, not the repository directory names alone. All paths refer to commit `7e43d70`.

| Layer | Responsibility | Representative implementation |
|---|---|---|
| L1 | Client channels | `apps/web/app/layout.tsx:7`; `apps/admin/src/App.tsx:17`; `apps/miniprogram/src/app.ts:17` |
| L2 | Shared frontend packages | `packages/api-client/src/index.ts:9`; `packages/campaign-renderer/src/index.ts:11` |
| L3 | Composition root | `apps/awn-api/src/main/java/site/awn/AwnApplication.java:6-17`; `apps/awn-api/src/main/java/site/awn/api/AdminController.java:41-66` |
| L4 | Business modules | `pom.xml:26-46`; `modules/awn-order/src/main/java/site/awn/order/application/OrderWriteService.java:48-219` |
| L5 | Shared backend infrastructure | `modules/awn-shared/src/main/java/site/awn/shared/package-info.java:1-17`; `modules/awn-shared/src/main/java/site/awn/shared/outbox/OutboxDispatchService.java:17-114` |
| L6 | Runtime processes | `apps/awn-api/src/main/java/site/awn/AwnApplication.java:6-17`; `apps/awn-worker/src/main/java/site/awn/worker/AwnWorkerApplication.java:6-27` |
| L7 | Data | `modules/awn-shared/src/main/java/site/awn/shared/config/PersistenceConfig.java:38-96`; `modules/awn-shared/src/main/java/site/awn/shared/ratelimit/RedisRateLimiter.java:10-33` |
| L8 | Deployment | `deploy/docker-compose.yml:15-156`; `deploy/config/caddy/Caddyfile:4-20` |

## Call Surface

1. Browser web imports `@awn/api-client`, `@awn/campaign-renderer`, `@awn/ticket-components`, and `@awn/ui` (`apps/web/package.json:10-15`). Other channels own their own transports: Taro wraps requests for the miniprogram (`apps/miniprogram/src/lib/taro-transport.ts:10-37`) and Dio wraps public read calls for Flutter (`apps/client/lib/src/api/awn_client.dart:15-55`).
2. Caddy routes the public root to `web:3000`, `/admin` to `admin:80`, and `/api/v1` plus `/actuator` to `awn-api:8080` (`deploy/config/caddy/Caddyfile:4-20`).
3. API controllers are thin composition adapters. For example, order creation imports only `OrderFacade` and its command/view types (`apps/awn-api/src/main/java/site/awn/api/OrderController.java:13-17,36-54`); payment operations import only `OrderFacade` and the payment facade contract (`apps/awn-api/src/main/java/site/awn/api/PaymentController.java:13-19,44-98`).
4. Within the backend, module POM dependencies are directed: catalog depends on event; inventory depends on catalog; marketplace depends on audit; order depends on inventory, marketplace, and catalog; payment depends on order (`modules/awn-catalog/pom.xml`, `modules/awn-inventory/pom.xml`, `modules/awn-marketplace/pom.xml`, `modules/awn-order/pom.xml`, `modules/awn-payment/pom.xml`). The controller package otherwise imports `site.awn.<module>.api`, deliberately exposed domain exceptions, or `site.awn.shared` utilities.
5. Worker schedules do not contain business rules. Each task enables itself and calls a facade, such as order timeout through `OrderFacade` (`apps/awn-worker/src/main/java/site/awn/worker/task/OrderTimeoutTask.java:8-49`) or outbox dispatch through `OutboxDispatchService` (`apps/awn-worker/src/main/java/site/awn/worker/task/OutboxDispatchTask.java:8-45`).

## State Storage

### PostgreSQL

- The primary trading datasource uses `spring.datasource` (`apps/awn-api/src/main/resources/application.yml:4-9`).
- The intelligence datasource is a separate Hikari unit configured by `spring.intel-datasource` (`apps/awn-api/src/main/resources/application.yml:10-17`).
- `PersistenceConfig` explicitly registers the trading repository scanner while excluding intelligence repositories, then registers intelligence repositories against `intelEntityManagerFactory` and `intelTransactionManager` (`modules/awn-shared/src/main/java/site/awn/shared/config/PersistenceConfig.java:43-67`).
- The ordered SQL schema lives in `modules/awn-shared/src/main/resources/db/migration/`, starting at `V1__baseline.sql:1-20`; Flyway runs only in the API process (`apps/awn-api/src/main/resources/application.yml:22-31`; `apps/awn-worker/src/main/resources/application.yml:22-24`).
- Transactional state includes order, inventory, payment, catalog, event, campaign, fulfillment, marketplace, promotion, notification, audit, star, identity, and shared message tables; representative JPA owners are `Order` (`modules/awn-order/src/main/java/site/awn/order/domain/Order.java:15-21`) and `InventoryReservation` (`modules/awn-inventory/src/main/java/site/awn/inventory/domain/InventoryReservation.java:17-67`).
- Intelligence state includes raw observations, normalized observations, price history, and data sources; representative repositories are under `modules/awn-intelligence/src/main/java/site/awn/intelligence/infrastructure/`.

### Redis and Memory

- Redis is the optional rate-limit store. `RedisRateLimiter` increments `awn:ratelimit:<key>` and sets expiry on first increment (`modules/awn-shared/src/main/java/site/awn/shared/ratelimit/RedisRateLimiter.java:10-33`).
- Redis connection inputs are `spring.data.redis.host` and port (`apps/awn-api/src/main/resources/application.yml:32-35`); production selects the shared store with `AWN_RATELIMIT_STORE` (`apps/awn-api/src/main/resources/application-prod.yml:1-31`; `deploy/docker-compose.yml:70-73`).
- `InMemoryRateLimiter` is the default when the store is `memory` or omitted (`modules/awn-shared/src/main/java/site/awn/shared/ratelimit/InMemoryRateLimiter.java:10-35`).

### In-Process State

- Request-scoped trace state is stored in a `ThreadLocal` and cleared in a filter `finally` block (`apps/awn-api/src/main/java/site/awn/api/TraceIdFilter.java:14-55`).
- The API has no authoritative client session table in the composition root; token authentication derives the request identity (`apps/awn-api/src/main/java/site/awn/api/auth/ApiAuthenticationFilter.java:15-77`).

## Configuration and Lifecycle

| Area | Schema/input source | Runtime effect |
|---|---|---|
| API transport | `apps/awn-api/src/main/resources/application.yml:37-38` | HTTP server on `8080`. |
| Worker transport | `apps/awn-worker/src/main/resources/application.yml:4-5,30-36` | No business HTTP server; management endpoint on `8081`. |
| Databases | `apps/awn-api/src/main/resources/application.yml:4-29`; `modules/awn-shared/src/main/java/site/awn/shared/config/PersistenceConfig.java:68-96` | Trading pool, intelligence pool, and migration connection. |
| Redis/rate limiting | `apps/awn-api/src/main/resources/application.yml:32-35,65-70`; `modules/awn-shared/src/main/java/site/awn/shared/ratelimit/InMemoryRateLimiter.java:10-17` | Store selection plus admin resource/window limits. |
| Authentication | `apps/awn-api/src/main/resources/application.yml:59-64`; `apps/awn-api/src/main/java/site/awn/api/auth/ApiTokenService.java` | Admin token and API token secret. |
| Worker tasks | `apps/awn-worker/src/main/resources/application.yml:43-61` | Global enablement, task toggles, and fixed delays. |
| Images/containers | `deploy/docker-compose.yml:15-156`; `deploy/docker/api.Dockerfile:5-41`; `deploy/docker/worker.Dockerfile:1-41` | Build, non-root execution, health checks, dependencies, and volumes. |
| Proxy | `deploy/config/caddy/Caddyfile:4-20` | Public path routing. |

API startup does not enable Worker schedules (`apps/awn-api/src/main/resources/application.yml:55-58`), while Worker startup sets `awn.worker.enabled=true` and disables the business web server (`apps/awn-worker/src/main/java/site/awn/worker/AwnWorkerApplication.java:19-26`). Compose starts API, then starts Worker only after API health succeeds (`deploy/docker-compose.yml:77-106`). The API and Worker images are separate; the API image exposes `8080` and defines a health check (`deploy/docker/api.Dockerfile:38-40`), while the Worker image exposes `8081` and has no business port (`deploy/docker/worker.Dockerfile:1-41`).

## Layer Notes

### L1 Client Channels

- **Implementation:** Next.js web routes and layout (`apps/web/app/layout.tsx:7-42`), React admin views (`apps/admin/src/App.tsx:17-62`), Taro miniprogram pages (`apps/miniprogram/src/app.ts:17-34`), and Flutter public client (`apps/client/lib/src/api/awn_client.dart:15-40`).
- **Configuration schema:** framework and app package definitions (`apps/web/package.json:1-32`; `apps/admin/package.json:1-31`; `apps/miniprogram/package.json:1-43`; `apps/client/pubspec.yaml:1-38`).
- **Protocol files:** HTTP DTOs live in `packages/api-client/src/index.ts`; the Flutter client maps its own model classes (`apps/client/lib/src/models/`).
- **Copy location:** local page labels and component text, such as `apps/web/app/layout.tsx:14-35`, `apps/admin/src/views/OrdersView.tsx:11-42`, and `apps/miniprogram/src/pages/index/index.tsx:20-55`.

### L2 Shared Frontend Packages

- **Implementation:** API client and DTO/error semantics (`packages/api-client/src/index.ts:9-167`), UI themes and components (`packages/ui/src/themes.ts:14-61`), ticket tier contract (`packages/ticket-components/src/tier-card.tsx:11-91`), and campaign renderer (`packages/campaign-renderer/src/index.ts:1-56`; `packages/campaign-renderer/src/renderer.tsx:16-260`).
- **Configuration schema:** package manifests and workspace resolutions (`packages/*/package.json`; `pnpm-workspace.yaml:1-22`).
- **Protocol file:** `packages/campaign-renderer/src/index.ts` defines `schemaVersion 1` and the 13-section whitelist (`packages/campaign-renderer/src/index.ts:10-53`).
- **Copy location:** package-owned fallback/labels and component strings (`packages/ticket-components/src/tier-card.tsx:43-91`; `packages/campaign-renderer/src/renderer.tsx:96-240`).

### L3 Composition Root

- **Implementation:** 16 controllers under `apps/awn-api/src/main/java/site/awn/api/`; three servlet filters (`apps/awn-api/src/main/java/site/awn/api/auth/ApiAuthenticationFilter.java:15-77`, `apps/awn-api/src/main/java/site/awn/api/AdminAuthFilter.java:29-96`, `apps/awn-api/src/main/java/site/awn/api/TraceIdFilter.java:14-55`); the shared exception mapper (`apps/awn-api/src/main/java/site/awn/api/ApiExceptionHandler.java:55-101`).
- **Configuration schema:** `apps/awn-api/src/main/resources/application.yml:4-70` and `apps/awn-api/src/main/resources/application-prod.yml`.
- **Protocol file:** `apps/awn-api/src/main/java/site/awn/api/ApiExceptionHandler.java` maps module exceptions to the shared error envelope; `docs/api/contracts.md` is the external contract document.
- **Copy location:** controller messages and filter error text (`apps/awn-api/src/main/java/site/awn/api/AdminAuthFilter.java:59-75`).

### L4 Business Modules

- **Implementation:** fourteen Maven modules declared at `pom.xml:26-46`. Each module follows `api -> application -> domain -> infrastructure`; representative facades are `modules/awn-order/src/main/java/site/awn/order/api/OrderFacade.java:7-42`, `modules/awn-inventory/src/main/java/site/awn/inventory/api/InventoryFacade.java:9-40`, and `modules/awn-payment/src/main/java/site/awn/payment/api/PaymentFacade.java:11-40`.
- **Configuration schema:** module POMs and Spring configuration properties consumed by application services; Worker toggles are in `apps/awn-worker/src/main/resources/application.yml:43-61`.
- **Protocol files:** per-module `api/package-info.java`, facade interfaces, command/view records, and selected `domain/package-info.java` exception exports (`modules/awn-order/src/main/java/site/awn/order/api/package-info.java:1-6`; `modules/awn-order/src/main/java/site/awn/order/domain/package-info.java:1-6`).
- **Copy location:** domain status/messages and facade Javadoc. For example, module responsibilities are documented in `modules/awn-order/src/main/java/site/awn/order/package-info.java:1-15` and `modules/awn-star/src/main/java/site/awn/star/package-info.java:1-15`.

### L5 Shared Backend Infrastructure

- **Implementation:** `awn-shared` contains persistence, outbox/inbox, alert, masking, rate limit, trace, URL whitelist, and error support (`modules/awn-shared/src/main/java/site/awn/shared/package-info.java:1-17`; `modules/awn-shared/src/main/java/site/awn/shared/config/PersistenceConfig.java:38-96`).
- **Configuration schema:** datasource prefixes (`spring.datasource`, `spring.intel-datasource`, `spring.flyway` in `apps/awn-api/src/main/resources/application.yml:4-31`) and `awn.ratelimit.*` (`apps/awn-api/src/main/resources/application.yml:65-70`).
- **Protocol files:** `RateLimiter`, `OutboxEventHandler`, and module-facing records/interfaces under `modules/awn-shared/src/main/java/site/awn/shared/`.
- **Copy location:** operational log/alert text inside `OutboxDispatchService` (`modules/awn-shared/src/main/java/site/awn/shared/outbox/OutboxDispatchService.java:106-131`) and alert categories/severities under `modules/awn-shared/src/main/java/site/awn/shared/alert/`.

### L6 Runtime Processes

- **Implementation:** `apps/awn-api` and `apps/awn-worker`. The API main class only starts Spring Boot (`apps/awn-api/src/main/java/site/awn/AwnApplication.java:6-17`); Worker enables scheduling and no business web server (`apps/awn-worker/src/main/java/site/awn/worker/AwnWorkerApplication.java:6-27`).
- **Configuration schema:** both application YAML files plus `awn.worker.*` (`apps/awn-worker/src/main/resources/application.yml:43-61`).
- **Protocol file:** Worker task classes are the scheduling protocol, each owning its toggle and fixed-delay key (`apps/awn-worker/src/main/java/site/awn/worker/task/OrderTimeoutTask.java:27-39`).
- **Copy location:** task Javadoc and log messages (`apps/awn-worker/src/main/java/site/awn/worker/task/WaitlistPromotionTask.java:14-52`).

### L7 Data

- **Implementation:** PostgreSQL entities/repositories split into trading and intelligence units (`modules/awn-shared/src/main/java/site/awn/shared/config/PersistenceConfig.java:43-67`), Flyway SQL (`modules/awn-shared/src/main/resources/db/migration/V1__baseline.sql:1-20`), and Redis/memory limiter implementations (`modules/awn-shared/src/main/java/site/awn/shared/ratelimit/RedisRateLimiter.java:10-33`; `modules/awn-shared/src/main/java/site/awn/shared/ratelimit/InMemoryRateLimiter.java:10-35`).
- **Configuration schema:** `spring.datasource`, `spring.intel-datasource`, `spring.flyway`, `spring.data.redis`, and `awn.ratelimit` (`apps/awn-api/src/main/resources/application.yml:4-35,65-70`).
- **Protocol file:** the ordered SQL migration set is the durable relational protocol; the `RateLimiter` interface is the external-state protocol (`modules/awn-shared/src/main/java/site/awn/shared/ratelimit/RateLimiter.java:5-17`).
- **Copy location:** migration comments and data-model documentation (`modules/awn-shared/src/main/resources/db/migration/V1__baseline.sql:1-20`; `docs/architecture/domain-model.md:1-80`).

### L8 Deployment

- **Implementation:** Compose services for Caddy, web, admin, API, Worker, PostgreSQL, Redis, and RustFS (`deploy/docker-compose.yml:15-156`), with build recipes in `deploy/docker/*.Dockerfile`.
- **Configuration schema:** Compose environment interpolation plus `.env` requirements (`deploy/docker-compose.yml:28-73,95-103,111-145`).
- **Protocol file:** `deploy/config/caddy/Caddyfile:4-20`; each `deploy/docker/*.Dockerfile` is the process build protocol.
- **Copy location:** deployment instructions in `deploy/README.md`, `docs/deployment/production.md`, and `docs/deployment/structure.md`.

