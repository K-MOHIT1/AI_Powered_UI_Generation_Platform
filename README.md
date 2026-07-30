# Promptic

**Promptic** is an AI-powered UI generation platform — describe what you want in plain English, and it scaffolds, generates, and live-previews a working frontend project for you, streamed token-by-token straight from an LLM into a running sandbox.

🔗 **Live:** [promptic.site](https://promptic.site)
📦 **Repo:** [github.com/K-MOHIT1/AI_Powered_UI_Generation_Platform](https://github.com/K-MOHIT1/AI_Powered_UI_Generation_Platform)

---

## What it does

1. You describe a UI or app in a chat interface.
2. `intelligence-service` builds context — it injects the project's current file tree into the prompt and gives the model tools to pull specific file contents on demand, rather than resending the whole codebase — then calls the LLM and streams the response back over WebSockets.
3. Generated files are written/diffed and synced into a **live, isolated preview environment** — a warm Kubernetes pod per project, running Vite/Node, reachable at `your-project.previews.promptic.site`.
4. You keep iterating conversationally; the platform tracks file diffs instead of re-sending whole files to keep context small and generation fast.

---

## Architecture

The system is a Spring Boot microservices backend on Kubernetes (GKE), fronted by a reactive API Gateway, with Kafka for async events, Redis for session/rate-limit state, MinIO (S3-compatible) for project file storage, and Postgres for persistence (a pgvector-enabled instance is provisioned in the cluster for planned embedding-based context retrieval).

```
                                   ┌────────────────┐
                     HTTPS/WSS     │  nginx Ingress  │
        client ───────────────────►  (promptic.site) │
                                   └───────┬─────────┘
                                           │
                                   ┌───────▼─────────┐
                                   │   api-gateway    │  Spring Cloud Gateway (WebFlux)
                                   │  routing, WS,    │
                                   │  JWT validation  │
                                   └───────┬─────────┘
                     ┌─────────────────────┼──────────────────────┐
                     │                     │                      │
             ┌───────▼───────┐    ┌────────▼────────┐    ┌────────▼─────────┐
             │ account-service│    │intelligence-svc  │    │ workspace-service │
             │ JWT auth,      │    │ LLM orchestration│    │ live preview pods,│
             │ users, billing │    │ file-tree context│    │ Kafka consumer,   │
             │ (Stripe)       │    │ + streaming      │    │ file sync (MinIO) │
             └───────┬────────┘    └────────┬─────────┘    └─────────┬────────┘
                     │                      │                        │
                     └──────────┬───────────┴────────────┬──────────┘
                                │                         │
                        ┌───────▼──────┐          ┌───────▼───────┐
                        │    Kafka      │          │   Postgres     │
                        │ (async events,│          │ Redis / MinIO  │
                        │ saga/status)  │          │                │
                        └───────────────┘          └────────────────┘

        discovery-service (Eureka)  +  config-service (Spring Cloud Config, Git-backed)
        underpin service registration and centralized externalized config for every service above.
```

### Services

| Service | Responsibility |
|---|---|
| `api-gateway` | Reactive edge gateway (Spring Cloud Gateway/WebFlux) — routes REST + WebSocket traffic to downstream services |
| `discovery-service` | Eureka service registry |
| `config-service` | Spring Cloud Config Server, backed by a Git repo, serves centralized config per profile (`k8s`, local, etc.) |
| `account-service` | Auth (JWT), user profiles/preferences, Stripe billing & subscriptions |
| `intelligence-service` | Talks to the LLM (Spring AI + OpenRouter), builds context from the project's file tree + on-demand file tools, streams generation events, publishes/consumes Kafka events for saga-style status updates |
| `workspace-service` | Manages live preview infrastructure — provisions/reuses warm runner pods, streams generated files into them via MinIO, handles the ingress-based reverse proxy routing to each preview |
| `common-lib` | Shared DTOs/config/security used across services |

### Key design decisions

- **Warm pod pool for previews** — a pool of idle Node/Vite pods is kept ready in a dedicated `promptic-previews` namespace so a new preview doesn't pay cold-start cost; a sidecar syncs generated files from MinIO into the pod's workspace volume.
- **Event-driven generation pipeline** — Kafka carries `step`, `fileUpdate`, and completion events between `intelligence-service` and `workspace-service`, decoupling LLM generation from file sync/preview updates.
- **File-tree-aware context, not full files** — the model gets the project's file tree plus tools to fetch a specific file's content on demand, instead of the whole codebase being resent on every turn.
- **Namespace isolation** — `promptic-core` (stateless services) is separated from `promptic-previews` (untrusted, per-project generated code) with network policies restricting east-west traffic.
- **Externalized config** — all services pull config from `config-service` at startup instead of baking environment-specific values into images.

---

## Tech stack

- **Backend:** Java 21, Spring Boot 3, Spring Cloud Gateway, Spring Cloud Config, Spring Cloud Netflix Eureka, Spring AI (OpenAI-compatible client via OpenRouter), Spring Data JPA, Spring Security, Spring Kafka
- **Data/infra:** PostgreSQL, Redis, Apache Kafka, MinIO (pgvector provisioned in-cluster for planned embedding-based retrieval, not yet wired into the generation flow)
- **Payments:** Stripe
- **Preview runtime:** Node.js/Vite pods orchestrated dynamically on Kubernetes
- **Container builds:** Google Jib (no Dockerfile needed for the Spring services)
- **Deployment:** Google Kubernetes Engine (GKE), nginx-ingress, cert-manager (Let's Encrypt)
- **CI/CD:** GitHub Actions → Docker Hub → GKE, via Workload Identity Federation (no long-lived service account keys)

---

## CI/CD

Each service has its own path-filtered GitHub Actions workflow (`.github/workflows/deploy-*.yaml`), so a push to `master` only rebuilds and redeploys the services that actually changed:

1. Checkout + set up JDK 21 (Temurin), Maven cache
2. Build & install `common-lib` (shared dependency for all services)
3. Build & push a container image with **Jib**, tagged with the commit SHA
4. Authenticate to GCP via **Workload Identity Federation**
5. Fetch GKE cluster credentials
6. `kubectl set image` the corresponding deployment and wait on rollout status

```yaml
on:
  push:
    branches: [master]
    paths:
      - 'account-service/**'
```

This keeps deploys fast and blast radius small — a change to `account-service` never triggers a rebuild of `intelligence-service`.

---

## Kubernetes layout (`/k8s`)

```
k8s/
├── infra/       # namespaces, ingress, cert-manager issuer, network policies, runner pool
├── services/    # Deployments/Services for each Spring Boot service + frontend
├── stateful/    # Kafka, Redis, MinIO, pgvector
└── proxy/       # Node-based reverse proxy for routing *.previews.promptic.site → the right pod
```

- `promptic-core` — the always-on backend services and stateful infra
- `promptic-previews` — the pool of runner pods used for per-project live previews, isolated by network policy from `promptic-core`

---

## Running locally

```bash
# 1. Bring up infra (Postgres, Redis, Kafka, MinIO)
docker-compose up -d

# 2. Start config & discovery first
cd config-service && ./mvnw spring-boot:run
cd discovery-service && ./mvnw spring-boot:run

# 3. Install the shared lib
cd common-lib && ./mvnw clean install -DskipTests

# 4. Start the remaining services (each registers with Eureka & pulls config from config-service)
cd api-gateway && ./mvnw spring-boot:run
cd account-service && ./mvnw spring-boot:run
cd intelligence-service && ./mvnw spring-boot:run
cd workspace-service && ./mvnw spring-boot:run
```

Each service pulls its config from `config-service` (`http://localhost:8888` by default) and needs its own secrets (DB creds, `CONFIG_SERVER_URL`, LLM API key, Stripe keys, MinIO creds, JWT secret) supplied via environment variables — see each service's `application.yaml` for what it expects.

---

## Roadmap / optimizations tracked

- Sliding-window, Redis-backed rate limiting sharded across nodes
- IP-level rate limiting + WAF at the gateway
- Kafka-queue-length-driven KEDA autoscaling for preview runners
- Storing AI-generated chat summaries instead of full history to keep context bounded
