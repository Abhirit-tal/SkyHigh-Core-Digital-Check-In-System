# SkyHigh Core - Workflow Design

## 1. Primary Workflows

### 1.1 Complete Check-In Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      COMPLETE CHECK-IN WORKFLOW                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌────────────────┐                                                         │
│  │    START       │                                                         │
│  └───────┬────────┘                                                         │
│          │                                                                   │
│          ▼                                                                   │
│  ┌────────────────┐     ┌───────────────┐                                   │
│  │     LOGIN      │────▶│ Validate      │                                   │
│  │                │     │ Credentials   │                                   │
│  └────────────────┘     └───────┬───────┘                                   │
│                                 │                                            │
│                    ┌────────────┴────────────┐                              │
│                    │                         │                              │
│                    ▼                         ▼                              │
│            ┌──────────────┐         ┌──────────────┐                        │
│            │   SUCCESS    │         │    FAIL      │                        │
│            │ (JWT Token)  │         │  (401 Error) │                        │
│            └──────┬───────┘         └──────────────┘                        │
│                   │                                                          │
│                   ▼                                                          │
│  ┌────────────────────────────────────────────────────────────────────┐     │
│  │                    CHECK-IN WINDOW VALIDATION                       │     │
│  │                                                                     │     │
│  │   departure - 24h ◀─────── OPEN ──────▶ departure - 1h             │     │
│  │                                                                     │     │
│  │   Before 24h: 422 "Check-in not open"                              │     │
│  │   After 1h:   422 "Check-in closed"                                │     │
│  └────────────────────────────────────────────────────────────────────┘     │
│                   │                                                          │
│                   ▼                                                          │
│  ┌────────────────────────────────────────────────────────────────────┐     │
│  │                      START CHECK-IN                                 │     │
│  │                                                                     │     │
│  │   POST /api/v1/check-in/start                                      │     │
│  │   ─────────────────────────────                                    │     │
│  │   • Create check-in session                                        │     │
│  │   • Set expires_at = now + 10 minutes                              │     │
│  │   • Status: IN_PROGRESS                                            │     │
│  │                                                                     │     │
│  │   Response: checkInId, session expiry time                         │     │
│  └────────────────────────────────────────────────────────────────────┘     │
│                   │                                                          │
│                   ▼                                                          │
│  ┌────────────────────────────────────────────────────────────────────┐     │
│  │                       VIEW SEAT MAP                                 │     │
│  │                                                                     │     │
│  │   GET /api/v1/flights/{flightId}/seats                             │     │
│  │   ────────────────────────────────────                             │     │
│  │   • Retrieve from Redis cache (if available)                       │     │
│  │   • Otherwise query PostgreSQL                                     │     │
│  │   • Cache result for 5 seconds                                     │     │
│  │                                                                     │     │
│  │   Response: Seats grouped by class with availability               │     │
│  └────────────────────────────────────────────────────────────────────┘     │
│                   │                                                          │
│                   ▼                                                          │
│  ┌────────────────────────────────────────────────────────────────────┐     │
│  │                        HOLD SEAT                                    │     │
│  │                                                                     │     │
│  │   POST /api/v1/seats/{seatId}/hold                                 │     │
│  │   ────────────────────────────────                                 │     │
│  │   1. Acquire Redis lock (SETNX, 120s TTL)                          │     │
│  │   2. Update database status to HELD                                │     │
│  │   3. Invalidate seat map cache                                     │     │
│  │                                                                     │     │
│  │   Success: Seat reserved for 120 seconds                           │     │
│  │   Failure: 409 "Seat already held"                                 │     │
│  └────────────────────────────────────────────────────────────────────┘     │
│                   │                                                          │
│                   ▼                                                          │
│  ┌────────────────────────────────────────────────────────────────────┐     │
│  │                       ADD BAGGAGE                                   │     │
│  │                                                                     │     │
│  │   POST /api/v1/check-in/{checkInId}/baggage                        │     │
│  │   ──────────────────────────────────────                           │     │
│  │   • Validate weight (max 25kg)                                     │     │
│  │   • Calculate excess fee (₹200/kg)                                 │     │
│  │                                                                     │     │
│  │   If weight ≤ 25kg: Continue to confirmation                       │     │
│  │   If weight > 25kg: Status → WAITING_PAYMENT                       │     │
│  └────────────────────────────────────────────────────────────────────┘     │
│                   │                                                          │
│          ┌───────┴───────┐                                                  │
│          │               │                                                  │
│          ▼               ▼                                                  │
│  ┌──────────────┐  ┌──────────────┐                                        │
│  │ Weight OK    │  │ Weight Over  │                                        │
│  │ (≤ 25kg)     │  │ (> 25kg)     │                                        │
│  └──────┬───────┘  └──────┬───────┘                                        │
│         │                 │                                                  │
│         │                 ▼                                                  │
│         │    ┌────────────────────────────────────────────────────────┐     │
│         │    │                   PROCESS PAYMENT                       │     │
│         │    │                                                         │     │
│         │    │   POST /api/v1/check-in/{checkInId}/payment            │     │
│         │    │   ──────────────────────────────────────               │     │
│         │    │   • Validate amount matches excess fee                 │     │
│         │    │   • Process payment (mock service)                     │     │
│         │    │   • Update payment status                              │     │
│         │    │                                                         │     │
│         │    │   Success: Status → IN_PROGRESS                        │     │
│         │    │   Failure: 422 "Payment failed"                        │     │
│         │    └────────────────────────────────────────────────────────┘     │
│         │                 │                                                  │
│         └────────┬────────┘                                                  │
│                  │                                                           │
│                  ▼                                                           │
│  ┌────────────────────────────────────────────────────────────────────┐     │
│  │                     CONFIRM CHECK-IN                                │     │
│  │                                                                     │     │
│  │   POST /api/v1/check-in/{checkInId}/confirm                        │     │
│  │   ──────────────────────────────────────                           │     │
│  │   1. Validate seat still held by passenger                         │     │
│  │   2. Confirm seat (optimistic lock)                                │     │
│  │   3. Generate boarding pass (PDF + QR)                             │     │
│  │   4. Update check-in status to COMPLETED                           │     │
│  │                                                                     │     │
│  │   Success: Boarding pass generated                                 │     │
│  │   Failure: 422 "Seat hold expired"                                 │     │
│  └────────────────────────────────────────────────────────────────────┘     │
│                  │                                                           │
│                  ▼                                                           │
│  ┌────────────────────────────────────────────────────────────────────┐     │
│  │                   DOWNLOAD BOARDING PASS                            │     │
│  │                                                                     │     │
│  │   GET /api/v1/boarding-pass/{checkInId}/download                   │     │
│  │   ───────────────────────────────────────────                      │     │
│  │   • Returns PDF with passenger details                             │     │
│  │   • Includes QR code for scanning                                  │     │
│  └────────────────────────────────────────────────────────────────────┘     │
│                  │                                                           │
│                  ▼                                                           │
│          ┌──────────────┐                                                   │
│          │     END      │                                                   │
│          └──────────────┘                                                   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 Seat Hold & Release Lifecycle

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      SEAT LIFECYCLE STATE MACHINE                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│                              ┌─────────────┐                                │
│                              │  AVAILABLE  │                                │
│                              └──────┬──────┘                                │
│                                     │                                        │
│                      ┌──────────────┴──────────────┐                        │
│                      │  POST /seats/{id}/hold      │                        │
│                      │  ────────────────────────   │                        │
│                      │  1. SETNX Redis lock        │                        │
│                      │  2. TTL = 120 seconds       │                        │
│                      │  3. Update DB status        │                        │
│                      └──────────────┬──────────────┘                        │
│                                     │                                        │
│                                     ▼                                        │
│   ┌──────────────────┐       ┌─────────────┐       ┌──────────────────┐    │
│   │ 120s Timeout     │       │    HELD     │       │ Manual Release   │    │
│   │ (Auto-release)   │◀──────│             │──────▶│ DEL /seats/.../  │    │
│   │                  │       │             │       │      hold        │    │
│   └────────┬─────────┘       └──────┬──────┘       └────────┬─────────┘    │
│            │                        │                       │              │
│            │                        │                       │              │
│            │            ┌───────────┴───────────┐           │              │
│            │            │ POST /seats/{id}/     │           │              │
│            │            │      confirm          │           │              │
│            │            │ ───────────────────── │           │              │
│            │            │ 1. Verify lock owner  │           │              │
│            │            │ 2. Check not expired  │           │              │
│            │            │ 3. Optimistic lock    │           │              │
│            │            │ 4. Update DB status   │           │              │
│            │            └───────────┬───────────┘           │              │
│            │                        │                       │              │
│            │                        ▼                       │              │
│            │                 ┌─────────────┐                │              │
│            │                 │  CONFIRMED  │ (Terminal)     │              │
│            │                 │             │                │              │
│            │                 │ No further  │                │              │
│            │                 │ transitions │                │              │
│            │                 └─────────────┘                │              │
│            │                                                │              │
│            └────────────────────────┬───────────────────────┘              │
│                                     │                                       │
│                                     ▼                                       │
│                              ┌─────────────┐                                │
│                              │  AVAILABLE  │                                │
│                              │  (Released) │                                │
│                              └─────────────┘                                │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.3 Check-In Session States

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     CHECK-IN SESSION STATE MACHINE                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│                         POST /check-in/start                                │
│                                  │                                           │
│                                  ▼                                           │
│                          ┌─────────────┐                                    │
│                          │ IN_PROGRESS │◀──────────────────────┐            │
│                          └──────┬──────┘                       │            │
│                                 │                              │            │
│          ┌──────────────────────┼──────────────────┬───────────┤            │
│          │                      │                  │           │            │
│    (Select Seat)         (Add Baggage)      (10 min timeout)   │            │
│          │                      │                  │           │            │
│          │                      ▼                  ▼           │            │
│          │             ┌───────────────┐   ┌──────────┐       │            │
│          │             │ Baggage >25kg │   │ EXPIRED  │       │            │
│          │             │      ?        │   └──────────┘       │            │
│          │             └───────┬───────┘                      │            │
│          │                Yes  │  No                          │            │
│          │               ┌─────┴─────┐                        │            │
│          │               ▼           │                        │            │
│          │   ┌─────────────────┐     │                        │            │
│          │   │   WAITING_      │     │                        │            │
│          │   │   PAYMENT       │     │                        │            │
│          │   └───────┬─────────┘     │                        │            │
│          │           │               │                        │            │
│          │    (Payment OK)           │                        │            │
│          │           │               │                        │            │
│          │           └─────┬─────────┘                        │            │
│          │                 │                                  │            │
│          │                 ▼                                  │            │
│          │    POST /check-in/{id}/confirm                    │            │
│          │                 │                                  │            │
│          │                 ▼                                  │            │
│          │         ┌─────────────┐                           │            │
│          └────────▶│  COMPLETED  │                           │            │
│                    └─────────────┘                           │            │
│                                                              │            │
│          DELETE /check-in/{id}                               │            │
│                    │                                          │            │
│                    ▼                                          │            │
│             ┌─────────────┐                                  │            │
│             │  CANCELLED  │──────────────────────────────────┘            │
│             └─────────────┘  (Release held seat)                          │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 2. Database Schema Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        DATABASE SCHEMA                                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────┐         ┌─────────────────────┐                    │
│  │      FLIGHTS        │         │     PASSENGERS      │                    │
│  ├─────────────────────┤         ├─────────────────────┤                    │
│  │ id (PK)             │         │ id (PK)             │                    │
│  │ flight_number       │         │ first_name          │                    │
│  │ departure_time      │         │ last_name           │                    │
│  │ arrival_time        │         │ email               │                    │
│  │ origin              │         │ phone               │                    │
│  │ destination         │         │ date_of_birth       │                    │
│  │ aircraft_type       │         │ passport_number     │                    │
│  │ status              │         │ created_at          │                    │
│  │ total_seats         │         │ updated_at          │                    │
│  │ gate                │         └──────────┬──────────┘                    │
│  │ created_at          │                    │                               │
│  │ updated_at          │                    │                               │
│  └──────────┬──────────┘                    │                               │
│             │                               │                               │
│             │ 1:N                           │ 1:N                           │
│             ▼                               ▼                               │
│  ┌─────────────────────┐         ┌─────────────────────┐                    │
│  │       SEATS         │         │      BOOKINGS       │                    │
│  ├─────────────────────┤         ├─────────────────────┤                    │
│  │ id (PK)             │         │ id (PK)             │                    │
│  │ flight_id (FK)      │◀────────│ flight_id (FK)      │                    │
│  │ seat_number         │         │ passenger_id (FK)   │─────────────────┐  │
│  │ seat_class          │         │ booking_reference   │                 │  │
│  │ status              │◀───┐    │ status              │                 │  │
│  │ held_by_passenger_id│    │    │ created_at          │                 │  │
│  │ held_until          │    │    │ updated_at          │                 │  │
│  │ confirmed_by_pass..│    │    └──────────┬──────────┘                 │  │
│  │ version (optimistic)│    │              │                            │  │
│  │ created_at          │    │              │ 1:N                        │  │
│  │ updated_at          │    │              ▼                            │  │
│  └──────────┬──────────┘    │    ┌─────────────────────┐                │  │
│             │               │    │     CHECK_INS       │                │  │
│             │               │    ├─────────────────────┤                │  │
│             │               │    │ id (PK)             │                │  │
│             │               └────│ seat_id (FK)        │                │  │
│             │                    │ booking_id (FK)     │                │  │
│             │                    │ status              │                │  │
│             │                    │ baggage_weight      │                │  │
│             │                    │ excess_baggage_fee  │                │  │
│             │                    │ payment_status      │                │  │
│             │                    │ payment_reference   │                │  │
│             │                    │ started_at          │                │  │
│             │                    │ last_activity_at    │                │  │
│             │                    │ completed_at        │                │  │
│             │                    │ expires_at          │                │  │
│             │                    │ version             │                │  │
│             │                    └──────────┬──────────┘                │  │
│             │                               │                           │  │
│             │                               │ 1:1                       │  │
│             │                               ▼                           │  │
│             │                    ┌─────────────────────┐                │  │
│             │                    │   BOARDING_PASSES   │                │  │
│             │                    ├─────────────────────┤                │  │
│             │                    │ id (PK)             │                │  │
│             │                    │ check_in_id (FK)    │                │  │
│             │                    │ passenger_name      │                │  │
│             │                    │ flight_number       │                │  │
│             │                    │ seat_number         │                │  │
│             │                    │ seat_class          │                │  │
│             │                    │ origin              │                │  │
│             │                    │ destination         │                │  │
│             │                    │ departure_time      │                │  │
│             │                    │ gate                │                │  │
│             │                    │ boarding_time       │                │  │
│             │                    │ barcode_data        │                │  │
│             │                    │ qr_code_data        │                │  │
│             │                    │ generated_at        │                │  │
│             │                    └─────────────────────┘                │  │
│             │                                                           │  │
│             │ 1:N (Audit)                                              │  │
│             ▼                                                           │  │
│  ┌─────────────────────┐                                               │  │
│  │   SEAT_AUDIT_LOG    │                                               │  │
│  ├─────────────────────┤                                               │  │
│  │ id (PK)             │                                               │  │
│  │ seat_id             │                                               │  │
│  │ flight_id           │                                               │  │
│  │ seat_number         │                                               │  │
│  │ previous_status     │                                               │  │
│  │ new_status          │                                               │  │
│  │ changed_by_pass_id  │◀──────────────────────────────────────────────┘  │
│  │ change_reason       │                                                  │
│  │ metadata            │                                                  │
│  │ created_at          │                                                  │
│  └─────────────────────┘                                                  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 3. API Flow Sequences

### 3.1 Concurrent Seat Hold Scenario

```
┌─────────────────────────────────────────────────────────────────────────────┐
│              CONCURRENT SEAT HOLD - CONFLICT RESOLUTION                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Passenger A                    System                    Passenger B       │
│      │                            │                            │            │
│  T0  │──POST /seats/12A/hold─────▶│◀──POST /seats/12A/hold────│            │
│      │                            │                            │            │
│      │                     ┌──────┴──────┐                     │            │
│      │                     │   Redis     │                     │            │
│      │                     │  SETNX      │                     │            │
│      │                     │ seat:lock:  │                     │            │
│      │                     │  12A        │                     │            │
│      │                     └──────┬──────┘                     │            │
│      │                            │                            │            │
│  T1  │◀───────SUCCESS────────────│───────FAIL (Key Exists)───▶│            │
│      │    "Seat held until       │    "409 Conflict:          │            │
│      │     T0 + 120s"            │     Seat already held"     │            │
│      │                            │                            │            │
│      │                            │            ┌───────────────┘            │
│      │                            │            │                            │
│      │                            │            ▼                            │
│      │                            │    ┌───────────────┐                    │
│      │                            │    │ View other    │                    │
│      │                            │    │ available     │                    │
│      │                            │    │ seats         │                    │
│      │                            │    └───────────────┘                    │
│      │                            │                                         │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.2 Payment Required Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    EXCESS BAGGAGE PAYMENT FLOW                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Passenger                      System                    Payment Service   │
│      │                            │                            │            │
│      │──POST /check-in/.../baggage│                            │            │
│      │   { weightKg: 30.5 }       │                            │            │
│      │                            │                            │            │
│      │                     ┌──────┴──────┐                     │            │
│      │                     │  Validate   │                     │            │
│      │                     │  Weight     │                     │            │
│      │                     │  30.5 > 25  │                     │            │
│      │                     └──────┬──────┘                     │            │
│      │                            │                            │            │
│      │◀────WAITING_PAYMENT────────│                            │            │
│      │   { excessKg: 5.5,         │                            │            │
│      │     excessFee: ₹1100 }     │                            │            │
│      │                            │                            │            │
│      │──POST /check-in/.../payment│                            │            │
│      │   { amount: 1100 }         │                            │            │
│      │                            │────Process Payment────────▶│            │
│      │                            │                            │            │
│      │                            │◀───────SUCCESS─────────────│            │
│      │                            │   { reference: PAY-123 }   │            │
│      │                            │                            │            │
│      │◀────IN_PROGRESS────────────│                            │            │
│      │   { paymentStatus:         │                            │            │
│      │     COMPLETED }            │                            │            │
│      │                            │                            │            │
│      │──POST /check-in/.../confirm│                            │            │
│      │                            │                            │            │
│      │◀────COMPLETED──────────────│                            │            │
│      │   { boardingPass: {...} }  │                            │            │
│      │                            │                            │            │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 4. Error Handling Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        ERROR HANDLING FLOW                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Request ──▶ Controller ──▶ Service ──▶ Repository                          │
│      │                                       │                               │
│      │                                       │                               │
│      │           ┌───────────────────────────┘                               │
│      │           │ Exception Thrown                                          │
│      │           ▼                                                           │
│      │    ┌─────────────────────────────────────────────────────────────┐   │
│      │    │              GlobalExceptionHandler                          │   │
│      │    │                                                              │   │
│      │    │  Switch on Exception Type:                                   │   │
│      │    │  ─────────────────────────                                   │   │
│      │    │                                                              │   │
│      │    │  SeatAlreadyHeldException    → 409 Conflict                 │   │
│      │    │                                + retry suggestion            │   │
│      │    │                                                              │   │
│      │    │  ResourceNotFoundException   → 404 Not Found                │   │
│      │    │                                                              │   │
│      │    │  InvalidCredentialsException → 401 Unauthorized             │   │
│      │    │                                                              │   │
│      │    │  FlightAccessDeniedException → 403 Forbidden                │   │
│      │    │                                                              │   │
│      │    │  PaymentRequiredException    → 402 Payment Required         │   │
│      │    │                                                              │   │
│      │    │  CheckInWindowNotOpen        → 422 Unprocessable            │   │
│      │    │                                + opens_at timestamp          │   │
│      │    │                                                              │   │
│      │    │  ValidationException         → 400 Bad Request              │   │
│      │    │                                + field errors                │   │
│      │    │                                                              │   │
│      │    │  Any other Exception         → 500 Internal Error           │   │
│      │    │                                + request ID for support      │   │
│      │    │                                                              │   │
│      │    └──────────────────────────────────────────────────────────────┘   │
│      │                         │                                             │
│      │                         ▼                                             │
│      │◀────────────── ErrorResponse ────────────────────────────────────────│
│                       {                                                      │
│                         error: {                                             │
│                           code: "SEAT_ALREADY_HELD",                        │
│                           message: "...",                                   │
│                           retryable: true,                                  │
│                           retryAfterSeconds: 120                            │
│                         },                                                  │
│                         meta: { requestId, timestamp, path },               │
│                         suggestions: [...]                                  │
│                       }                                                      │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

