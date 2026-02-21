# SkyHigh Core - AI Collaboration History

## Design Journey with AI Assistant

This document chronicles the key decision points and design evolution during the development of the SkyHigh Core Digital Check-In System.

---

## Session 1: Requirements Analysis & Architecture Planning

### Initial Requirements Review

**User Request**: Analyze the SkyHigh Core requirements and create an implementation plan.

**AI Analysis**:
- Identified core functional requirements: seat lifecycle, time-bound holds, baggage validation
- Identified non-functional requirements: P95 < 1 second, 500+ concurrent users
- Proposed technology stack: Java 17, Spring Boot 3, PostgreSQL, Redis

### Key Questions Raised by AI

1. **Baggage Service Behavior**: How should the mock weight service behave?
   - **Decision**: Simple passthrough with validation against 25kg limit

2. **Excess Fee Calculation**: Fixed or tiered pricing?
   - **Decision**: Fixed fee of ₹200 per kg

3. **Payment Failure Scenarios**: How to trigger test failures?
   - **Decision**: Deterministic failures - amounts ending in .99 = decline, .88 = timeout

4. **Service Structure**: Microservices vs Monolith?
   - **Initial**: Microservices proposed
   - **Final Decision**: Single service (monolith) for simplicity

5. **Seat Lock Strategy**: Redis-only vs Hybrid?
   - **Decision**: Hybrid - Redis for 120s hold, PostgreSQL optimistic locking for confirmation

6. **Multi-passenger Bookings**: Supported?
   - **Decision**: 1 booking = 1 passenger

7. **Session Timeout**: Duration?
   - **Decision**: 10 minutes of inactivity (changed from initial 30 minutes)

---

## Session 2: Security Architecture Enhancement

### User Feedback
"Where is the implementation plan for auth bearer token and passenger validation for seat map access?"

### AI Response
Enhanced the plan with comprehensive security architecture:

1. **JWT Token Structure**: Added flightIds claim for authorization
2. **Flight Access Checker**: Double validation (token claims + database check)
3. **Authorization Matrix**: Defined access rules per endpoint
4. **Database Schema Additions**: Added BOOKINGS and REFRESH_TOKENS tables

---

## Session 3: Error Handling Deep Dive

### User Feedback
"Plan must have proper failure and exception handling at any stage of the check-in as the application must be of production grade"

### AI Response
Added comprehensive error handling:

1. **Exception Hierarchy**: 25+ specific exception types
2. **Stage-wise Failure Scenarios**: Detailed handling for all 6 check-in stages
3. **Resilience Patterns**: Circuit breaker, retry, bulkhead, timeout, fallback
4. **Idempotency**: Protection for critical operations
5. **Compensation/Saga**: Rollback strategy for multi-step operations
6. **Monitoring**: Metrics and alerting thresholds

---

## Session 4: Implementation

### User Request
"Start implementation as a single service"

### Implementation Decisions

1. **Project Structure**: Standard Spring Boot layered architecture
2. **Database Migrations**: Flyway with 8 migration scripts
3. **Caching**: Redis with 5-second TTL for seat maps
4. **Scheduling**: Background jobs for seat hold expiry (10s) and session expiry (60s)
5. **PDF Generation**: iTextPDF for boarding pass
6. **QR Code**: ZXing library for QR generation

---

## Key Design Decisions Summary

| Decision Point | Options Considered | Final Decision | Rationale |
|----------------|-------------------|----------------|-----------|
| Architecture | Microservices, Monolith | Monolith | Simplicity, single deployable |
| Seat Locking | Redis-only, DB-only, Hybrid | Hybrid | Best of both: speed + consistency |
| Session Management | Stateful, Stateless | Stateless | Horizontal scaling |
| Caching | No cache, Short TTL, Long TTL | 5-second TTL | Balance freshness vs performance |
| Payment | Async, Sync | Sync | Simpler for mock service |
| Booking Model | 1:1, 1:N | 1:1 | Simplified scope |

---

## Trade-off Analysis

### Redis vs PostgreSQL for Seat Holds

**Option A: Redis Only**
- Pros: Fast, automatic TTL expiry
- Cons: Not durable, could lose holds on restart

**Option B: PostgreSQL Only**
- Pros: Durable, ACID
- Cons: Slower, requires explicit cleanup

**Chosen: Hybrid**
- Redis for fast 120-second holds with TTL
- PostgreSQL for persistence and confirmation
- Background scheduler as safety net

### Session Timeout: 30 min vs 10 min

**30 minutes**
- Pros: More relaxed for users
- Cons: Resources held longer, more seat conflicts

**10 minutes (Chosen)**
- Pros: Faster seat turnover, better resource utilization
- Cons: Users need to complete faster

---

## Lessons Learned

1. **Security from the Start**: Always plan authentication and authorization early
2. **Error Handling is Critical**: Production systems need comprehensive error handling
3. **Caching Trade-offs**: Short TTL balances freshness and performance
4. **Distributed Locking**: Hybrid approaches provide defense-in-depth
5. **Mock Services**: Deterministic failures are invaluable for testing

---

## Future Improvements Discussed

1. **Rate Limiting**: Not implemented but designed
2. **WebSocket for Real-time Updates**: Deferred for REST-only approach
3. **Multi-passenger Bookings**: Out of scope but schema supports extension
4. **Refund Support**: Payment service designed for future addition

---

*Document generated as part of the SkyHigh Core Digital Check-In System development.*

