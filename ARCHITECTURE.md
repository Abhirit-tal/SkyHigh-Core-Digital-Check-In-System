# SkyHigh Core - System Architecture

## 1. Architecture Overview

The SkyHigh Core Digital Check-In System is designed as a **monolithic application** with clear internal module boundaries, optimized for high concurrency during peak check-in hours. It uses an **event-driven architecture** via RabbitMQ for waitlist processing and notifications.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              CLIENTS                                         │
│                    (Web Browser, Mobile App, Kiosk)                         │
└─────────────────────────────┬───────────────────────────────────────────────┘
                              │ HTTPS + JWT
                              ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                       SKYHIGH CHECK-IN SERVICE                               │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                        API LAYER                                     │    │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────┐ │    │
│  │  │  Auth    │ │  Flight  │ │  Seat    │ │ Check-In │ │ Waitlist  │ │    │
│  │  │Controller│ │Controller│ │Controller│ │Controller│ │Controller │ │    │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └───────────┘ │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                    │                                         │
│  ┌─────────────────────────────────┴───────────────────────────────────┐    │
│  │                      SERVICE LAYER                                   │    │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────┐ │    │
│  │  │  Seat    │ │ Check-In │ │ Waitlist │ │RateLimiter│ │  Event    │ │    │
│  │  │ Service  │ │ Service  │ │ Service  │ │ Service  │ │ Publisher │ │    │
│  │  └────┬─────┘ └──────────┘ └─────┬────┘ └──────────┘ └─────┬─────┘ │    │
│  │       │                          │                          │       │    │
│  │  ┌────┴─────┐              ┌─────┴──────┐            ┌──────┴─────┐ │    │
│  │  │ SeatLock │              │  Waitlist   │            │  RabbitMQ  │ │    │
│  │  │ Service  │              │  Event      │            │  Template  │ │    │
│  │  │ (Redis)  │              │  Listener   │            │            │ │    │
│  │  └──────────┘              └─────────────┘            └────────────┘ │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                    │                                         │
│  ┌─────────────────────────────────┴───────────────────────────────────┐    │
│  │                    DATA ACCESS LAYER                                 │    │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────┐ │    │
│  │  │  Seat    │ │ Booking  │ │ CheckIn  │ │ Waitlist │ │  Abuse    │ │    │
│  │  │   Repo   │ │   Repo   │ │   Repo   │ │   Repo   │ │ Audit Repo│ │    │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └───────────┘ │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                    │                                         │
│  ┌──────────────────────┐    ┌─────┴─────────────┐   ┌──────────────────┐   │
│  │     SCHEDULERS       │    │     SECURITY      │   │   RATE LIMITING  │   │
│  │ • Seat Hold Expiry   │    │ • JWT Filter      │   │ • RateLimitFilter│   │
│  │ • Waitlist Offer     │    │ • Access Checker  │   │ • Redis Sliding  │   │
│  │   Expiry             │    │                   │   │   Window         │   │
│  └──────────────────────┘    └───────────────────┘   └──────────────────┘   │
└───────────────────────────────────┬─────────────────────────────────────────┘
                 ┌──────────────────┼──────────────────┬──────────────┐
                 │                  │                  │              │
                 ▼                  ▼                  ▼              ▼
          ┌───────────┐      ┌───────────┐      ┌───────────┐  ┌──────────┐
          │PostgreSQL │      │   Redis   │      │ RabbitMQ  │  │Prometheus│
          │ (Primary  │      │  (Cache + │      │ (Message  │  │+ Grafana │
          │    DB)    │      │   Locks)  │      │  Broker)  │  │(Metrics) │
          └───────────┘      └───────────┘      └───────────┘  └──────────┘
```

## 2. Component Architecture

### 2.1 API Layer

| Controller | Endpoints | Responsibility |
|------------|-----------|----------------|
| AuthController | `/api/v1/auth/*` | Login, token refresh, logout |
| FlightController | `/api/v1/flights/*` | Flight info, seat maps |
| SeatController | `/api/v1/seats/*` | Hold, release, confirm, cancel seats |
| CheckInController | `/api/v1/check-in/*` | Check-in lifecycle |
| WaitlistController | `/api/v1/flights/{id}/waitlist/*`, `/api/v1/waitlist/*` | Waitlist join, leave, status, accept/decline |
| BoardingPassController | `/api/v1/boarding-pass/*` | Boarding pass retrieval |

### 2.2 Service Layer

| Service | Responsibility |
|---------|----------------|
| AuthService | JWT generation, credential validation |
| FlightService | Flight information retrieval |
| SeatService | Seat lifecycle management (hold, confirm, cancel), caching |
| SeatLockService | Redis distributed locking (SETNX + TTL) |
| SeatEventPublisher | Publishes seat released events to RabbitMQ |
| WaitlistService | Waitlist FIFO management, offer/accept/decline/expire |
| WaitlistEventListener | RabbitMQ consumer — processes seat released events |
| RateLimiterService | Redis sliding-window rate limiter, abuse detection |
| CheckInService | Check-in orchestration |
| WeightService | Baggage validation (mock) |
| PaymentService | Payment processing (mock) |
| BoardingPassService | PDF/QR generation |

### 2.3 Data Layer

| Repository | Entity | Special Features |
|------------|--------|------------------|
| FlightRepository | Flight | Scheduled flights lookup |
| SeatRepository | Seat | Optimistic locking, expired hold queries |
| BookingRepository | Booking | Multi-criteria search |
| CheckInRepository | CheckIn | Session expiry queries |
| BoardingPassRepository | BoardingPass | Barcode lookup |

## 3. Data Flow

### 3.1 Authentication Flow

```
┌────────┐     ┌────────────────┐     ┌─────────────┐     ┌──────────────┐
│ Client │────▶│ AuthController │────▶│ AuthService │────▶│ BookingRepo  │
│        │     │                │     │             │     │              │
│        │◀────│  JWT Tokens    │◀────│  Validate   │◀────│ Find Booking │
└────────┘     └────────────────┘     └─────────────┘     └──────────────┘
```

### 3.2 Seat Hold Flow

```
┌────────┐     ┌────────────────┐     ┌─────────────┐     ┌──────────────┐
│ Client │────▶│ SeatController │────▶│ SeatService │────▶│ SeatLockSvc  │
│        │     │                │     │             │     │   (Redis)    │
│        │     │                │     │             │     │              │
│        │     │                │     │             │     │  SETNX +TTL  │
│        │     │                │     │             │◀────│  (120 sec)   │
│        │     │                │     │             │     └──────────────┘
│        │     │                │     │             │     ┌──────────────┐
│        │     │                │     │             │────▶│  SeatRepo    │
│        │◀────│  HoldResponse  │◀────│ Update DB   │◀────│ (Optimistic) │
└────────┘     └────────────────┘     └─────────────┘     └──────────────┘
```

### 3.3 Check-In Complete Flow

```
┌────────┐     ┌────────────────┐     ┌──────────────┐     ┌────────────┐
│ Client │────▶│CheckInController────▶│CheckInService│────▶│ SeatService│
│        │     │                │     │              │     │            │
│        │     │                │     │              │     │ConfirmSeat │
│        │     │                │     │              │◀────│            │
│        │     │                │     │              │     └────────────┘
│        │     │                │     │              │     ┌────────────┐
│        │     │                │     │              │────▶│BoardingPass│
│        │     │                │     │              │     │  Service   │
│        │◀────│CheckInResponse │◀────│              │◀────│Generate PDF│
└────────┘     └────────────────┘     └──────────────┘     └────────────┘
```

## 4. Concurrency Design

### 4.1 Hybrid Locking Strategy

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         SEAT LOCKING STRATEGY                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                      REDIS (Distributed Lock)                        │    │
│  │                                                                      │    │
│  │  Purpose: Fast, TTL-based seat holds (120 seconds)                  │    │
│  │  Key:     seat:lock:{flightId}:{seatNumber}                         │    │
│  │  Value:   passengerId                                               │    │
│  │  TTL:     120 seconds (auto-expire)                                 │    │
│  │                                                                      │    │
│  │  Operations:                                                         │    │
│  │  • SETNX - Acquire lock (atomic, no race conditions)                │    │
│  │  • GET   - Check lock holder                                        │    │
│  │  • DEL   - Release lock                                             │    │
│  │                                                                      │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                    +                                         │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                    POSTGRESQL (Optimistic Lock)                      │    │
│  │                                                                      │    │
│  │  Purpose: Data consistency for seat confirmation                    │    │
│  │  Column:  version (integer, auto-increment on update)               │    │
│  │                                                                      │    │
│  │  Operations:                                                         │    │
│  │  • SELECT ... WHERE id = ? (get current version)                    │    │
│  │  • UPDATE ... SET version = version + 1 WHERE version = ?           │    │
│  │  • If version mismatch → OptimisticLockException → Retry            │    │
│  │                                                                      │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  Why Hybrid?                                                                 │
│  • Redis: Fast (sub-millisecond), automatic TTL expiry                      │
│  • PostgreSQL: ACID guarantees for final confirmation                       │
│  • Defense-in-depth: Two independent systems prevent conflicts              │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 4.2 Race Condition Prevention

```
Scenario: Two passengers (A & B) try to hold seat 12A simultaneously

Timeline:
──────────────────────────────────────────────────────────────────────────────
T0:  A requests hold     │  B requests hold
T1:  A: SETNX → SUCCESS  │  B: SETNX → FAIL (key exists)
T2:  A: Update DB        │  B: Return 409 Conflict
T3:  A: Return success   │  
──────────────────────────────────────────────────────────────────────────────

Result: Only A holds the seat. B gets clear error with retry suggestion.
```

## 5. Caching Architecture

### 5.1 Seat Map Caching

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         SEAT MAP CACHING                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Cache Key: seatmap:{flightId}                                              │
│  TTL: 5 seconds                                                             │
│  Strategy: Read-Through with Write-Through Invalidation                     │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                        READ PATH                                     │    │
│  │                                                                      │    │
│  │  Request ──▶ Check Redis ──┬── HIT ──▶ Return cached data           │    │
│  │                            │                                         │    │
│  │                            └── MISS ──▶ Query PostgreSQL            │    │
│  │                                              │                       │    │
│  │                                              ▼                       │    │
│  │                                        Cache result (5s TTL)        │    │
│  │                                              │                       │    │
│  │                                              ▼                       │    │
│  │                                        Return data                   │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                        WRITE PATH                                    │    │
│  │                                                                      │    │
│  │  Seat Status Change ──▶ Update PostgreSQL ──▶ Invalidate Cache     │    │
│  │  (Hold/Confirm/Release)                       (DEL seatmap:*)       │    │
│  │                                                                      │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 6. Security Architecture

### 6.1 Authentication Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         JWT AUTHENTICATION                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Login Request                                                               │
│  ┌─────────────────┐                                                        │
│  │ bookingReference│                                                        │
│  │ lastName        │────▶ Validate against DB ────▶ Generate JWT           │
│  │ email           │                                                        │
│  └─────────────────┘                                                        │
│                                                                              │
│  JWT Payload                                                                 │
│  ┌─────────────────┐                                                        │
│  │ sub: passengerId│                                                        │
│  │ email           │                                                        │
│  │ firstName       │                                                        │
│  │ lastName        │                                                        │
│  │ flightIds: [...]│  ◀── List of authorized flights                       │
│  │ exp: timestamp  │                                                        │
│  └─────────────────┘                                                        │
│                                                                              │
│  Authorization Check (per request)                                          │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │ 1. Extract JWT from Authorization header                             │   │
│  │ 2. Validate signature and expiration                                 │   │
│  │ 3. Extract flightIds claim                                           │   │
│  │ 4. Check: requested flightId ∈ flightIds?                            │   │
│  │ 5. Additional DB check: active booking exists?                       │   │
│  │ 6. If all pass → Allow access                                        │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 7. Background Processing

### 7.1 Scheduled Tasks

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         BACKGROUND SCHEDULERS                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │            SEAT HOLD EXPIRY SCHEDULER (every 10 seconds)            │    │
│  │                                                                      │    │
│  │  Purpose: Release seats where hold_until < now()                    │    │
│  │                                                                      │    │
│  │  Process:                                                           │    │
│  │  1. Query: SELECT * FROM seats WHERE status='HELD' AND              │    │
│  │            held_until < CURRENT_TIMESTAMP                           │    │
│  │  2. For each seat:                                                  │    │
│  │     a. Delete Redis lock (cleanup)                                  │    │
│  │     b. Update status to AVAILABLE                                   │    │
│  │     c. Log to audit table                                           │    │
│  │                                                                      │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │         CHECK-IN SESSION EXPIRY SCHEDULER (every 1 minute)          │    │
│  │                                                                      │    │
│  │  Purpose: Expire sessions where expires_at < now()                  │    │
│  │                                                                      │    │
│  │  Process:                                                           │    │
│  │  1. Query: SELECT * FROM check_ins WHERE status IN                  │    │
│  │            ('IN_PROGRESS', 'WAITING_PAYMENT') AND                   │    │
│  │            expires_at < CURRENT_TIMESTAMP                           │    │
│  │  2. For each session:                                               │    │
│  │     a. Release any held seat                                        │    │
│  │     b. Update status to EXPIRED                                     │    │
│  │                                                                      │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 8. Deployment Architecture

### 8.1 Docker Compose Setup

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         DOCKER DEPLOYMENT                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                     docker-compose.yml                               │    │
│  │                                                                      │    │
│  │  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐         │    │
│  │  │   PostgreSQL   │  │     Redis      │  │  Check-In Svc  │         │    │
│  │  │   Port: 5432   │  │   Port: 6379   │  │   Port: 8080   │         │    │
│  │  │                │  │                │  │                │         │    │
│  │  │  Health Check  │  │  Health Check  │  │  Health Check  │         │    │
│  │  │  pg_isready    │  │  redis-cli     │  │  /actuator/    │         │    │
│  │  │                │  │    ping        │  │   health       │         │    │
│  │  └────────────────┘  └────────────────┘  └────────────────┘         │    │
│  │         ▲                   ▲                    │                   │    │
│  │         │                   │                    │                   │    │
│  │         └───────────────────┴────────────────────┘                   │    │
│  │                    depends_on (service_healthy)                      │    │
│  │                                                                      │    │
│  │  Networks: skyhigh-network (bridge)                                  │    │
│  │  Volumes:  postgres_data, redis_data                                │    │
│  │                                                                      │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 9. Error Handling Architecture

### 9.1 Exception Hierarchy

```
RuntimeException
    └── SkyHighBaseException
            ├── Authentication
            │   └── InvalidCredentialsException
            │
            ├── Authorization
            │   └── FlightAccessDeniedException
            │
            ├── Resource Not Found
            │   └── ResourceNotFoundException
            │
            ├── Conflicts
            │   ├── SeatAlreadyHeldException
            │   ├── SeatAlreadyConfirmedException
            │   ├── CheckInAlreadyExistsException
            │   └── AlreadyOnWaitlistException
            │
            ├── Business Rules
            │   ├── CheckInWindowNotOpenException
            │   ├── CheckInWindowClosedException
            │   ├── SeatHoldExpiredException
            │   ├── SessionExpiredException
            │   ├── PaymentRequiredException
            │   ├── PaymentFailedException
            │   ├── WaitlistFullException
            │   └── WaitlistOfferExpiredException
            │
            ├── Rate Limiting
            │   └── RateLimitExceededException (HTTP 429)
            │
            └── Validation
                └── InvalidSeatStateException
```

## 10. Event-Driven Architecture

### 10.1 RabbitMQ Message Flow

```
┌──────────────┐     ┌───────────────────┐     ┌──────────────────────┐
│ Seat Service │────▶│  skyhigh.events   │────▶│ WaitlistEventListener│
│ (Publisher)  │     │  (Topic Exchange) │     │ (Consumer)           │
│              │     │                   │     │                      │
│ Cancel/Expiry│     │  Routing Keys:    │     │ offerSeatToNextInLine│
│ triggers     │     │  seat.released    │     │                      │
│ event publish│     │  waitlist.offer   │     │ Processes waitlist   │
│              │     │  waitlist.notify  │     │ queue, sends offers  │
└──────────────┘     └───────────────────┘     └──────────────────────┘
```

### 10.2 Event Types

| Event | Routing Key | Trigger | Consumer |
|-------|-------------|---------|----------|
| SeatReleasedEvent | `seat.released` | Hold expiry, cancellation | WaitlistEventListener |
| WaitlistOfferEvent | `waitlist.notification` | Seat offered to waitlisted passenger | Notification stub |

### 10.3 Fault Tolerance

- **RabbitMQ unavailable**: SeatEventPublisher logs warning; waitlist processing falls back to scheduler-based polling
- **Consumer failure**: RabbitMQ retry (3 attempts, exponential backoff)
- **Message ordering**: Per-queue FIFO guarantees correct waitlist order

## 11. Abuse Detection & Rate Limiting

### 11.1 Sliding Window Algorithm

```
┌──────────────────────────────────────────────────────────────────┐
│                   Redis Sorted Set (per source)                   │
│  Key: ratelimit:seat-map:{IP}:{passengerId}                     │
│  Score: timestamp (ms)     Value: request timestamp              │
│                                                                   │
│  ─────[────────── 2-second window ──────────]─────▶ time         │
│       t-2000ms                              now                   │
│                                                                   │
│  ZADD (add new request) → ZREMRANGEBYSCORE (remove old)          │
│  → ZCARD (count in window) → if > 50: BLOCK source              │
└──────────────────────────────────────────────────────────────────┘
```

### 11.2 Blocking Mechanism

| Step | Action |
|------|--------|
| 1 | `RateLimitingFilter` intercepts GET `/api/v1/flights/*/seats` requests |
| 2 | `RateLimiterService` checks Redis sorted set count in 2s window |
| 3 | If > 50 requests → set `blocked:{source}` key with 5-minute TTL |
| 4 | Record abuse event in `abuse_audit_log` table |
| 5 | Return HTTP 429 with `Retry-After` header |

## 12. Observability

### 12.1 Metrics (Prometheus)

| Metric | Type | Description |
|--------|------|-------------|
| `skyhigh.seats.holds` | Counter | Number of seat holds |
| `skyhigh.seats.confirms` | Counter | Number of seat confirmations |
| `skyhigh.seats.cancellations` | Counter | Number of seat cancellations |
| `skyhigh.waitlist.joins` | Counter | Number of waitlist joins |
| `skyhigh.waitlist.offers` | Counter | Number of waitlist offers |
| `skyhigh.waitlist.assignments` | Counter | Number of waitlist assignments |
| `skyhigh.ratelimit.blocks` | Counter | Number of sources blocked |

### 12.2 Infrastructure

| Tool | Port | Purpose |
|------|------|---------|
| Prometheus | 9090 | Metrics collection (scrapes /actuator/prometheus every 10s) |
| Grafana | 3000 | Dashboard visualization (admin/admin) |
| RabbitMQ Management | 15672 | Message broker monitoring (guest/guest) |

## 13. Performance Considerations

| Component | Optimization |
|-----------|--------------|
| Seat Map | Redis caching (5s TTL) |
| Seat Locks | Redis SETNX (sub-ms) |
| Database | Connection pooling (HikariCP, 20 connections) |
| Queries | Indexed columns, optimized JPA queries |
| Schedulers | Non-blocking, every 10s/60s, ShedLock-protected |

## 14. Distributed Scheduling (ShedLock)

When the application is scaled horizontally (multiple instances), each `@Scheduled` task must run on **only one instance** at a time to prevent duplicate processing.

**Solution**: ShedLock with Redis-backed `LockProvider`.

| Scheduler | Frequency | Lock At Least | Lock At Most |
|-----------|-----------|---------------|--------------|
| `SeatHoldExpiryScheduler` | 10s | 5s | 2m |
| `WaitlistOfferExpiryScheduler` | 30s | 10s | 2m |
| `CheckInSessionExpiryScheduler` | 60s | 15s | 3m |

**Lock key pattern**: `skyhigh-checkin:{schedulerName}`

```
Instance A: @Scheduled fires → acquires ShedLock → executes → releases lock
Instance B: @Scheduled fires → ShedLock held → skips execution
```

## 15. Transactional Event Safety

Seat-released events (for waitlist processing) are published using Spring's `@TransactionalEventListener(phase = AFTER_COMMIT)`:

```
1. @Transactional method: cancel seat → save to DB → register domain event
2. Transaction commits successfully
3. @TransactionalEventListener fires → SeatEventPublisher → RabbitMQ
```

**Guarantees**:
- If DB transaction rolls back → no event is published (no ghost messages)
- If RabbitMQ is down → DB change is committed, scheduler fallback handles waitlist
- Eventual consistency between DB state and message broker

## 16. JWT Secret Management

| Environment | Secret Source |
|-------------|-------------|
| Development | Default fallback in `application.yml` |
| Docker Compose | `${JWT_SECRET}` env var (override via `.env` file) |
| Production | External vault (AWS Secrets Manager, HashiCorp Vault, K8s Secrets) |

Startup warning is logged if the default secret is detected.

