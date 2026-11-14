# AWN Documentation

AWN is a ticketing trading and intelligence platform: a Java 21 + Spring Boot modular
monolith for the trading core, PostgreSQL as the single authority for inventory, and
one shared `/api/v1` contract across every client.

The design documentation set is published under the Chinese locale
[/zh/](/zh/): architecture, API contracts, implementation plan, ADRs and UI prototypes.

## English pages

The source-layer analysis is maintained in English at the site root:

- [Source layer architecture](/architecture/source-layer-architecture)
- [Source layer glossary](/architecture/source-layer-glossary)
- [Source layer split rationale](/architecture/source-layer-split-rationale)

## Repository documentation

| Area | Location |
|---|---|
| Repo overview, quick start, ground rules | [README](https://github.com/cuihairu/awn/blob/main/README.md) and [README 中文](https://github.com/cuihairu/awn/blob/main/README.zh.md) |
| API contracts and error envelope | [/zh/api/contracts](/zh/api/contracts) |
| Implementation plan, phases 00-06 | [/zh/implementation-plan](/zh/implementation-plan) |
| ADR-0001 through ADR-0008 | [/zh/adr/0001-modular-monolith](/zh/adr/0001-modular-monolith) |
| Deployment structure and operations | [deployment/structure](/deployment/structure) |
| Documentation-code consistency audit | [审计-文档一致性](/审计-文档一致性) |
