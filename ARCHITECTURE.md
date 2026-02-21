# SkyHigh Core - System Architecture

## 1. Architecture Overview

The SkyHigh Core Digital Check-In System is designed as a **monolithic application** with clear internal module boundaries, optimized for high concurrency during peak check-in hours.

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
│  │  │  Auth    │ │  Flight  │ │  Seat    │ │ Check-In │ │ Boarding  │ │    │
│  │  │Controller│ │Controller│ │Controller│ │Controller│ │   Pass    │ │    │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └───────────┘ │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                    │                                         │
│  ┌─────────────────────────────────┴───────────────────────────────────┐    │
│  │                      SERVICE LAYER                                   │    │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────┐ │    │
│  │  │  Auth    │ │  Seat    │ │ Check-In │ │ Payment  │ │ Boarding  │ │    │
│  │  │ Service  │ │ Service  │ │ Service  │ │ Service  │ │   Pass    │ │    │
│  │  └──────────┘ └────┬─────┘ └──────────┘ └──────────┘ └───────────┘ │    │
│  │                    │                                                │    │
│  │               ┌────┴─────┐                                          │    │
│  │               │ SeatLock │  (Redis Distributed Lock)                │    │
│  │               │ Service  │                                          │    │
│  │               └──────────┘                                          │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                    │                                         │
│  ┌─────────────────────────────────┴───────────────────────────────────┐    │
│  │                    DATA ACCESS LAYER                                 │    │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────┐ │    │
│  │  │ Flight   │ │  Seat    │ │ Booking  │ │ CheckIn  │ │ Boarding  │ │    │
│  │  │   Repo   │ │   Repo   │ │   Repo   │ │   Repo   │ │ Pass Repo │ │    │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └───────────┘ │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                    │                                         │
│  ┌──────────────────┐    ┌─────────┴─────────┐                              │
│  │   SCHEDULERS     │    │     SECURITY      │                              │
│  │ • Seat Expiry    │    │ • JWT Filter      │                              │
│  │ • Session Expiry │    │ • Access Checker  │                              │
│  └──────────────────┘    └───────────────────┘                              │
└───────────────────────────────────┬─────────────────────────────────────────┘
                                    │
                 ┌──────────────────┼──────────────────┐
                 │                  │                  │
                 ▼                  ▼                  ▼
          ┌───────────┐      ┌───────────┐      ┌───────────┐
          │PostgreSQL │      │   Redis   │      │   File    │
          │ (Primary  │      │  (Cache + │      │  System   │
          │    DB)    │      │   Locks)  │      │  (PDFs)   │
          └───────────┘      └───────────┘      └───────────┘
```

## 2. Component Architecture

### 2.1 API Layer

| Controller | Endpoints | Responsibility |
|------------|-----------|----------------|
| AuthController | `/api/v1/auth/*` | Login, token refresh, logout |
| FlightController | `/api/v1/flights/*` | Flight info, seat maps |
| SeatController | `/api/v1/seats/*` | Hold, release, confirm seats |
| CheckInController | `/api/v1/check-in/*` | Check-in lifecycle |
| BoardingPassController | `/api/v1/boarding-pass/*` | Boarding pass retrieval |

### 2.2 Service Layer

| Service | Responsibility |
|---------|----------------|
| AuthService | JWT generation, credential validation |
| FlightService | Flight information retrieval |
| SeatService | Seat lifecycle management, caching |
| SeatLockService | Redis distributed locking |
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
            │   └── CheckInAlreadyExistsException
            │
            ├── Business Rules
            │   ├── CheckInWindowNotOpenException
            │   ├── CheckInWindowClosedException
            │   ├── SeatHoldExpiredException
            │   ├── SessionExpiredException
            │   ├── PaymentRequiredException
            │   └── PaymentFailedException
            │
            └── Validation
                └── InvalidSeatStateException
```

## 10. Performance Considerations

| Component | Optimization |
|-----------|--------------|
| Seat Map | Redis caching (5s TTL) |
| Seat Locks | Redis SETNX (sub-ms) |
| Database | Connection pooling (HikariCP, 20 connections) |
| Queries | Indexed columns, optimized JPA queries |
| Schedulers | Non-blocking, every 10s/60s |

