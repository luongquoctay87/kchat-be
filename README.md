# k-chat-api

> Backend chat nội bộ · REST + WebSocket · JWT · FCM · S3

| | | |
|:--|:--|:--|
| **Port** | `8864` | **Package** `com.kchat` |
| **DB** | PostgreSQL `kchat` | **Profile** `dev` / `prod` |

---

## Tech stack

| Layer | Công nghệ |
|:------|:----------|
| Runtime | Java 21 · Spring Boot 3.5.x · Maven |
| API | REST (JSON snake_case) · SpringDoc / Swagger |
| Realtime | WebSocket · Redis Pub/Sub |
| Persistence | JPA / Hibernate · Flyway |
| Auth | JWT access + refresh · `X-Device-Token` |
| Media | S3 presigned URL |
| Push | Firebase Admin · FCM data-only |
| Calls | WebRTC signaling (WS) · STUN / TURN |
| Jobs | ShedLock · disappearing messages · call timeout |
| Deploy | Docker → ECR → ECS Fargate |

---

## Kiến trúc hệ thống

```mermaid
flowchart TB
    subgraph clients["Clients"]
        APP["Android App"]
    end

    subgraph aws["AWS — staging"]
        ALB["ALB<br/>chat-api-test.tayjava.net"]
        ECS["ECS Fargate<br/>kpay-staging-kchat"]
    end

    subgraph api["k-chat-api :8864"]
        REST["REST Controllers"]
        WS["WebSocket Handler"]
        SVC["Services"]
        REST --> SVC
        WS --> SVC
    end

    subgraph data["Data & external"]
        PG[("PostgreSQL<br/>kchat")]
        REDIS[("Redis<br/>presence · fanout")]
        S3[("S3<br/>media")]
        FCM["FCM"]
    end

    APP -->|"HTTPS / WSS"| ALB
    ALB --> ECS
    ECS --> api
    SVC --> PG
    SVC --> REDIS
    SVC --> S3
    SVC --> FCM
```

### Cấu trúc package

```mermaid
flowchart LR
    subgraph layers["src/main/java/com/kchat/"]
        direction TB
        C["controller/<br/>Auth · Room · User · Call · Device · Hook"]
        S["service/<br/>Chat · FCM · Push · Auth"]
        R["repository/<br/>JPA"]
        W["ws/<br/>Handler · Redis pub/sub"]
        J["job/<br/>Scheduled cleanup"]
        SEC["security/<br/>JWT · device session · rate limit"]
        CFG["config/<br/>JWT · S3 · FCM · WebRTC"]
    end

    C --> S --> R
    W --> S
    J --> S
    SEC --> C
```

> Chi tiết: [`../docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md) · API: Swagger (dev) · [`../docs/BACKEND_PLAN.md`](../docs/BACKEND_PLAN.md)

---

## Luồng chính

### Gửi tin nhắn

```mermaid
sequenceDiagram
    autonumber
    participant A as Android (sender)
    participant API as k-chat-api
    participant PG as PostgreSQL
    participant RD as Redis
    participant B as Android (receiver)

    A->>API: POST /rooms/{id}/messages
    API->>PG: INSERT message
    API->>RD: PUBLISH room:{id}
    RD-->>API: fanout
    API-->>B: WS message_new
    alt Receiver offline
        API->>B: FCM data-only push
    end
    API-->>A: 201 MessageDto
```

### Auth (OTP)

```mermaid
sequenceDiagram
    autonumber
    participant App as Android
    participant API as k-chat-api
    participant Mail as SMTP

    Note over App,Mail: Đăng ký
    App->>API: POST /auth/send-registration-otp
    API->>Mail: Gửi OTP 6 số
    App->>API: POST /auth/verify-registration-otp
    API-->>App: registration_token
    App->>API: POST /auth/register
    API-->>App: access_token + refresh_token

    Note over App,Mail: Quên mật khẩu
    App->>API: POST /auth/forgot-password
    App->>API: POST /auth/verify-reset-otp
    API-->>App: reset_token
    App->>API: POST /auth/reset-password
```

**WebSocket:** `ws(s)://<host>/ws` · `Authorization: Bearer <token>` · `{ "type", "payload" }`

---

## Build & chạy local

**Cần:** JDK 21 · PostgreSQL · Redis · S3 (tuỳ chọn)

```bash
cd k-chat/backend

export DB_URL=jdbc:postgresql://localhost:5432/kchat
export DB_USER=admin DB_PASSWORD=123
export REDIS_HOST=localhost REDIS_PORT=6379 REDIS_PASSWORD=redis123

./mvnw spring-boot:run
```

| Endpoint | URL |
|:---------|:----|
| Health | http://localhost:8864/actuator/health |
| Swagger | http://localhost:8864/swagger-ui.html |
| WebSocket | ws://localhost:8864/ws |

```bash
# DB lần đầu
psql -U admin -d kchat -f ../docs/sql/kchat-schema.sql
psql -U admin -d kchat -f ../docs/sql/kchat-seed.sql   # tuỳ chọn — nguyenva / password

# Build
./mvnw test
./mvnw -DskipTests package
docker build -t kchat-api .
```

Env: [`.env.example`](./.env.example) · OTP email: `KCHAT_PASSWORD_RESET_MAIL_ENABLED=true` + `SMTP_*`

---

## Deploy staging

```mermaid
flowchart LR
    subgraph dev["Developer"]
        CODE["Source code"]
        MVN["mvnw package"]
        DOCK["docker build"]
    end

    subgraph aws["AWS ap-southeast-1"]
        ECR["ECR<br/>kpay/kchat-api"]
        ECS["ECS<br/>kpay-staging-kchat"]
        ALB["ALB<br/>chat-api-test.tayjava.net"]
    end

    CODE --> MVN --> DOCK --> ECR --> ECS --> ALB
```

| | |
|:--|:--|
| REST | https://chat-api-test.tayjava.net/ |
| WS | wss://chat-api-test.tayjava.net/ws |
| ECS | `kpay-staging-kchat` |

Chạy từ root **`kpay/`**:

```bash
./infra/scripts/start-staging.sh --wait
./infra/scripts/deploy-staging.sh kchat --wait
curl -sS https://chat-api-test.tayjava.net/actuator/health
```

| Tình huống | Lệnh |
|:-----------|:-----|
| Chỉ push image | `cd k-chat/backend && ./scripts/push-ecr.sh` |
| Redeploy | `./infra/scripts/redeploy-staging.sh kchat --wait` |
| DB trống | `./infra/scripts/seed-staging-kchat.sh` |
| Bật FCM | `./infra/scripts/enable-staging-kchat-fcm.sh` |
| Terraform | [`infra/terraform/README.md`](../../infra/terraform/README.md) § 4b |

> Staging **không seed user** — đăng ký qua app Android.  
> Prod ECS: `SPRING_PROFILES_ACTIVE=prod` · secrets trong Secrets Manager — [`Dockerfile`](./Dockerfile)  
> Ops: [`infra/scripts/README.md`](../../infra/scripts/README.md)

---

## Cấu hình

| Variable | Dev | Ghi chú |
|:---------|:----|:--------|
| `DB_*` | localhost `kchat` | Bắt buộc |
| `REDIS_*` | `:6379` | WS fanout |
| `JWT_ACCESS_SECRET` | dev default | Đổi trên prod |
| `KCHAT_S3_BUCKET` | trống | Media upload |
| `KCHAT_FCM_ENABLED` | `false` | Staging: Terraform |
| `KCHAT_PASSWORD_RESET_MAIL_ENABLED` | `false` | OTP email |

[`application.yml`](./src/main/resources/application.yml) · [`.env.example`](./.env.example)

---

## Tài liệu

| | |
|:--|:--|
| Yêu cầu | [`REQUIREMENTS.md`](../docs/REQUIREMENTS.md) |
| Kiến trúc | [`ARCHITECTURE.md`](../docs/ARCHITECTURE.md) |
| DB schema | [`DB_SCHEMA.md`](../docs/DB_SCHEMA.md) |
| Android | [`android/README.md`](../android/README.md) |
| Backlog | [`BACKLOG.md`](../docs/BACKLOG.md) |
