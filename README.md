<div align="center">

<img src="docs/public/logo.svg" alt="AWN logo" width="64"/>

# AWN · 麦芒网

Ticketing trading & intelligence platform

[![ci](https://github.com/cuihairu/awn/actions/workflows/ci.yml/badge.svg)](https://github.com/cuihairu/awn/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-21-6db33f?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.6-6db33f?logo=springboot&logoColor=white)
![Spring Modulith](https://img.shields.io/badge/Spring%20Modulith-1.4.2-6db33f?logo=spring&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169e1?logo=postgresql&logoColor=white)
![pnpm](https://img.shields.io/badge/pnpm-10-f9ad00?logo=pnpm&logoColor=white)
![Flutter](https://img.shields.io/badge/Flutter-client-02569b?logo=flutter&logoColor=white)
![License](https://img.shields.io/badge/License-Apache--2.0-2563eb)

[English](README.md) | [简体中文](README.zh-CN.md)

</div>

AWN is a ticketing trading and intelligence platform. A Java 21 + Spring Boot modular monolith forms the trading core, PostgreSQL is the single source of truth for inventory, and all clients share one API contract (`/api/v1`).

Full design docs: run `pnpm docs:dev` to start the VitePress site (architecture, API contracts, implementation plan, ADRs and UI prototypes).

## Repository layout

```
├─ docs/                  Design docs site (VitePress + mermaid, prototypes on the home page)
├─ modules/               Spring Modulith business modules (Maven multi-module)
│  ├─ awn-shared            Shared kernel: common infrastructure, unified exception model
│  ├─ awn-identity          Accounts: registration, bcrypt credential checks, roles
│  ├─ awn-event             Events, sessions, venues
│  ├─ awn-catalog           Show / ticket tier catalog
│  ├─ awn-campaign          Campaign pages: config-driven rendering + immutable published revisions
│  ├─ awn-inventory         Inventory pool: reserve, confirm, release (authoritative in PostgreSQL)
│  ├─ awn-order             Order state machine + outbox
│  ├─ awn-payment           Acquiring, refunds, reconciliation
│  ├─ awn-fulfillment       Ticketing & delivery
│  ├─ awn-marketplace       Sellers, listings, supply review
│  ├─ awn-promotion         Coupons: templates, quota-guarded issuance, redemption
│  ├─ awn-intelligence      Intelligence: separate schema, never writes trading tables
│  ├─ awn-notification      In-app messages delivered (email / SMS channel pending)
│  └─ awn-audit             Operation audit, sensitive action records
├─ apps/
│  ├─ awn-api             Modular monolith API (:8080)
│  ├─ awn-worker          Worker: order timeout, inventory/payment reconciliation, outbox dispatch (actuator :8081)
│  ├─ web                 Consumer web (Next.js 15)
│  ├─ admin               Operations admin (Vite 7 + React)
│  ├─ miniprogram         WeChat miniprogram (Taro 4 + React)
│  └─ client              Mobile client (Flutter skeleton; needs `flutter create .` for platform projects)
├─ packages/              Shared frontend packages (@awn/ui, @awn/campaign-renderer,
│                           @awn/ticket-components, @awn/api-client)
├─ design-system/awn/     Design system master + page overrides (ui-ux-pro-max output)
├─ deploy/                docker compose stack + Caddy config + Dockerfiles
└─ pom.xml                Maven parent (Spring Boot 3.5.6 / Modulith 1.4.2 / Java 21)
```

## Quick start

| Target | Command | Notes |
|---|---|---|
| Docs site | `pnpm install && pnpm docs:dev` | http://localhost:5173 |
| API | `./mvnw -pl apps/awn-api spring-boot:run` (needs local PostgreSQL + Redis) | http://localhost:8080/actuator/health |
| Web | `pnpm --filter @awn/web dev` | http://localhost:3000 |
| Admin | `pnpm --filter @awn/admin dev` | http://localhost:5174 |
| Miniprogram | `pnpm --filter @awn/miniprogram dev:weapp` | Import `apps/miniprogram` into WeChat DevTools |
| Mobile client | `cd apps/client && flutter run` | Skeleton: Dart SDK ≥ 3.8 (Flutter 3.32+); run `flutter create .` once to generate platform projects |
| One-shot deploy | `cd deploy && cp .env.example .env && docker compose up -d --build` | See `deploy/README.md` |
| One-command build | `scripts/build.sh` | Backend fat jars + `apps/web/.next/` + `apps/admin/dist/` + docs site; non-zero exit on failure |
| One-shot verify | `docker compose -f deploy/docker-compose.yml up -d --build` → `curl -s http://127.0.0.1:18080/actuator/health` returns `UP` | Smoke + details: `deploy/README.md` |


## 本地测试口径

CI 与本地开发按改动面三路分流——改一行文档不该全量编译。Maven 版本经 wrapper 钉定（`./mvnw`，3.10.0，首次运行自动下载），本地与 CI 一致。

1. **文档改动**（`*.md`、`docs/**`、图片）：不编译。主 CI workflow 的
   `paths-ignore` 已跳过这些路径。
2. **代码改动**：只跑改动子集，不全量。
   - `./mvnw -pl <改动模块> -am test`
   - `pnpm --filter @awn/<包> test`
3. **全量**（`./mvnw -B -ntp verify -pl apps/awn-api,apps/awn-worker -am`）：仅以下情形触发——pom.xml / pnpm-lock.yaml / package.json、
   `.github/**`、Docker/Compose 配置、发布前。

## Implementation phases

Phase 0 (architecture baseline) → 01 engineering skeleton & module governance → 02 catalog & campaign → 03 inventory & order core → 04 payment, refunds & reliable async → 05 fulfillment & seller marketplace → 06 intelligence & operations (complete). Task briefs and acceptance criteria: `docs/implementation-plan.md`.

## Ground rules

1. Inventory trusts PostgreSQL only; atomic conditional UPDATE for deduction. 2. Order + reservation + outbox in one transaction. 3. Payment / refund idempotent at the domain level (idempotency key enforced in facades; HTTP surface pending). 4. Campaigns render published revisions only, module whitelist enforced. 5. Intelligence is bounded at the database level (separate `intelligence` schema with its own `awn_intel` role, least-privilege) and never writes back to trading tables; the app currently connects as the bootstrap superuser, so the boundary applies when connecting as `awn_intel`. 6. Modules talk via `api` packages or events only — no cross-module table access.
