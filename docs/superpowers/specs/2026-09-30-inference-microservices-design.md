# Inference Engine Microservices Design

Date: 2026-09-30
Status: draft — awaiting review
Scope: Migrate JavaFX inference-engine to Spring Boot service + Gateway + React + Postgres, Docker Compose local run.

## 1. Context

Current app (`/home/ayyoub/working-area/inference-engine`):
- `InferenceEngine.java:16 forwardChaining()` — iterative saturation loop, UI-agnostic.
- `InferenceEngine.java:47 evaluate()` — atomic `{attribute, operator, value}` with `=,!=,>,<,>=,<=` plus `all/any/not/xor/at_least/exactly/at_most`.
- `Fact.java:6`, `Rule.java:16`, `WorkingMemory.java:6`, `KnowledgeBase.java:6` — POJOs, no JavaFX dependency.
- `InferenceEngineUI.java:34` — 2-page JavaFX UI (Fact Builder + Trace View grouped by `level`).
- `facts.json` / `rules.json` — file-based knowledge, 17 demo rules R1-R17 over 4 levels.
- `config/AppModule.java:5` — empty Guice module.

Decisions confirmed: Gateway now, React frontend, Database + CRUD for rules/facts, Docker Compose locally, open API v1 (no auth).

## 2. Architecture

```
React SPA (:5173)
  │ REST /api/v1/*
  ▼
Spring Cloud Gateway (:8080)
  │ route /api/v1/** → inference-service
  ▼
inference-service (:8081, Spring Boot 3, Java 21)
  │ JPA
  ▼
Postgres (:5432)
```

- No Eureka/Consul — Compose DNS (`http://inference-service:8081`) is enough for v1.
- No Kafka, no auth — deferred (YAGNI).
- Future services plug in as new Gateway routes (e.g. `/api/v1/explain/**`).

Repo layout (monorepo, Maven multi-module or separate builds):
```
/
  inference-service/      # Spring Boot 3
  api-gateway/            # Spring Cloud Gateway
  web-client/             # React (Vite + TS)
  docker-compose.yml
```

## 3. Components

### 3.1 inference-service (port, not rewrite)
- Move as-is: `InferenceEngine` → `@Service`, `WorkingMemory` → request-scoped helper (not singleton — must not share facts across requests), `Fact/Rule/condition` → domain + DTOs.
- Replace: Guice `AppModule` → Spring `@Configuration`; `RulesReader/FactsReader/KnowledgeBase()` file-load → `RuleEntity/FactTemplateEntity` JPA + `KnowledgeService`.
- Seed on startup: import current `rules.json`/`facts.json` if tables empty (preserves demo R1-R17).
- Condition map `Map<String,Object>` stored as JSONB (`columnDefinition = "jsonb"`) for Postgres, or `TEXT` + Jackson converter for portability.

DB sketch:
- `rules(id, rule_id UNIQUE, name, condition JSONB, conclusions JSONB, priority, confidence, explanation, version)`
- `fact_templates(id, attribute UNIQUE, sample_value JSONB, value_type, description)`
- No per-inference persistence in v1 (stateless); add `inference_runs` table later if audit needed.

### 3.2 api-gateway
- Spring Cloud Gateway (WebFlux), Java 21.
- Routes:
  - `POST/GET /api/v1/infer/**`, `/api/v1/rules/**`, `/api/v1/facts/**` → `lb://not-needed` → `http://inference-service:8081`
  - `GET /actuator/health` aggregation.
- Global CORS: allow `http://localhost:5173`, plus `StripPrefix=2` or `RewritePath` so service sees clean paths.
- No auth filter v1; reserve `AuthFilter` extension point.

### 3.3 web-client (React + Vite + TS)
- Port of `InferenceEngineUI` pages:
  - Page 1 Fact Builder: `GET /api/v1/facts/templates` → catalogue; typed inputs (boolean toggle, number, select/text) → `POST /api/v1/infer`.
  - Page 2 Trace View: group response `facts[]` by `level`, stats bar (total/initial/derived/levels), cards with `derivedBy + confidence`.
- OpenAPI-generated types (from service `/v3/api-docs`) preferred.

### 3.4 docker-compose.yml
- `postgres:16`, `inference-service` (build), `api-gateway` (build), `web-client` (dev mode v1).
- Single command: `docker compose up --build`.

## 4. Data flow / API

Infer (stateless):
```http
POST /api/v1/infer
{ "facts": [{ "attribute": "A", "operator": "=", "value": true }] }
→ { "facts": [{ "factId":"F1", "attribute":"G", "operator":"=", "value":true,
                "confidence":1.0, "derivedBy":"R1", "level":1 }], ... }
```

CRUD:
```http
GET/POST /api/v1/rules | GET/PUT/DELETE /api/v1/rules/{ruleId}
GET /api/v1/facts/templates | POST /api/v1/facts/templates
GET /actuator/health
```

Engine semantics unchanged: loop until no new fact, `factExists()` dedup, `computeLevel()+1` (`InferenceEngine.java:117`).

## 5. Error handling
- `400` unknown operator / malformed condition (currently `IllegalArgumentException` in `compareValues`) → RFC7807 `ProblemDetail`.
- `422` type mismatch in `compareTo` (e.g. string vs number) → `ProblemDetail` with attribute name.
- `404` ruleId not found; `500` unexpected with correlation id (Gateway `X-Request-Id` passthrough).
- Gateway: `503` when service down + JSON fallback, CORS preflight always `200`.
- Frontend: empty catalogue, inference with 0 facts blocked (same as `runBtn.disable` today).

## 6. Testing
- Service: JUnit 5 — port existing `InferenceEngineApplicationTests`; add slice tests for `all/any/not/xor/at_least/exactly/at_most` + `>,>=,<,<=,!=`; `@DataJpaTest` for CRUD; MockMvc for `/infer` contract.
- Gateway: route + CORS integration test (`SpringBootTest` + WebTestClient).
- Web-client: Vitest for level-grouping + stats; Playwright smoke (builder → infer → trace) against Compose.
- Success criteria: R1-R17 demo from `rules.json` produces identical `FINAL` deduction via `POST /infer` as JavaFX run; `docker compose up` green.

## 7. Migration / rollout
1. Scaffold `inference-service` + port engine, keep JSON seed.
2. Add JPA CRUD, keep engine pure.
3. Scaffold gateway routes + CORS.
4. Scaffold React builder + trace, point to gateway.
5. Compose + docs. JavaFX module frozen/removed after parity.

Out of scope v1: auth/JWT, service discovery, event bus, rule versioning UI, inference history, K8s manifests.
