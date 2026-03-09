# SkyHigh Core - Product Requirements Document (PRD)

## 1. Overview

### 1.1 Problem Statement

SkyHigh Airlines is transforming its airport self-check-in experience to handle heavy peak-hour traffic. During popular flight check-in windows, hundreds of passengers attempt to select seats, add baggage, and complete check-in simultaneously.

### 1.2 Goals

The digital check-in system must:

1. **Prevent Seat Conflicts**: Ensure no two passengers can book the same seat
2. **Handle Time-Bound Reservations**: Implement 120-second seat holds with automatic release
3. **Support Baggage Fee Handling**: Validate baggage weight and process excess fees
4. **Detect Abusive Access Patterns**: Rate limiting and abuse detection
5. **Scale Reliably**: Handle hundreds of concurrent users during peak hours

### 1.3 Key Users

| User Type | Description |
|-----------|-------------|
| **Passengers** | Airline customers who have booked flights and need to check in |
| **System Administrators** | Internal users monitoring system health |

---

## 2. Functional Requirements

### 2.1 Authentication & Authorization

| ID | Requirement | Priority |
|----|-------------|----------|
| AUTH-01 | Passengers authenticate using booking reference, last name, and email | Must Have |
| AUTH-02 | JWT tokens are issued upon successful authentication | Must Have |
| AUTH-03 | Passengers can only access flights they have tickets for | Must Have |
| AUTH-04 | Token refresh mechanism for extended sessions | Must Have |

### 2.2 Seat Management

| ID | Requirement | Priority |
|----|-------------|----------|
| SEAT-01 | Seats follow lifecycle: AVAILABLE → HELD → CONFIRMED → CANCELLED | Must Have |
| SEAT-02 | Seat holds expire after exactly 120 seconds | Must Have |
| SEAT-03 | Only one passenger can hold a seat at a time | Must Have |
| SEAT-04 | CONFIRMED seats can only be CANCELLED by the confirming passenger before departure | Must Have |
| SEAT-05 | Seat map must be retrievable with P95 < 1 second | Must Have |
| SEAT-06 | Support different seat classes (First, Business, Economy) | Should Have |
| SEAT-07 | Cancelled seats immediately become AVAILABLE for waitlisted passengers | Must Have |

### 2.3 Waitlist Management

| ID | Requirement | Priority |
|----|-------------|----------|
| WL-01 | Passengers can join a waitlist when no seats are available | Must Have |
| WL-02 | Waitlist follows FIFO ordering per flight | Must Have |
| WL-03 | When a seat becomes available, it is offered to the next waitlisted passenger | Must Have |
| WL-04 | Waitlist offers expire after 5 minutes if unclaimed | Must Have |
| WL-05 | Passengers are notified via event when their waitlisted seat is assigned | Must Have |
| WL-06 | Passengers can leave the waitlist voluntarily | Should Have |
| WL-07 | Maximum 50 passengers per flight waitlist | Should Have |

### 2.4 Check-In Process

| ID | Requirement | Priority |
|----|-------------|----------|
| CHK-01 | Check-in opens 24 hours before departure | Must Have |
| CHK-02 | Check-in closes 1 hour before departure | Must Have |
| CHK-03 | Check-in sessions expire after 10 minutes of inactivity | Must Have |
| CHK-04 | Passengers can select seat, add baggage, and confirm check-in | Must Have |
| CHK-05 | Boarding pass is generated upon successful check-in | Must Have |

### 2.4 Baggage Handling

| ID | Requirement | Priority |
|----|-------------|----------|
| BAG-01 | Maximum allowed baggage weight is 25kg | Must Have |
| BAG-02 | Excess baggage fee is ₹200 per kg over the limit | Must Have |
| BAG-03 | Check-in pauses until excess fee is paid | Must Have |
| BAG-04 | Single total weight per passenger (not per bag) | Must Have |

### 2.5 Payment Processing

| ID | Requirement | Priority |
|----|-------------|----------|
| PAY-01 | Payment is processed synchronously | Must Have |
| PAY-02 | Payment failures are handled gracefully | Must Have |
| PAY-03 | Idempotency keys prevent duplicate charges | Should Have |

---

## 3. Non-Functional Requirements

### 3.1 Performance

| Metric | Target |
|--------|--------|
| Seat Map P95 Latency | < 1 second |
| Concurrent Users | 500+ |
| Seat Hold Accuracy | 120 seconds ± 1 second |
| API Response Time P95 | < 2 seconds |

### 3.2 Reliability

| Requirement | Description |
|-------------|-------------|
| Data Consistency | No duplicate seat assignments under any circumstances |
| Availability | System available during check-in windows |
| Graceful Degradation | Fallback mechanisms when Redis is unavailable |
| Idempotency | Critical operations are idempotent |

### 3.3 Security

| Requirement | Description |
|-------------|-------------|
| Authentication | JWT-based authentication |
| Authorization | Flight-level access control |
| Rate Limiting | Redis sliding-window rate limiter (50 requests/2s per source) |
| Abuse Detection | Detect and block bots accessing 50+ seat maps in 2 seconds |
| Audit Logging | All abuse events recorded in abuse_audit_log table |
| Input Validation | All inputs validated |

### 3.4 Event-Driven Architecture

| Requirement | Description |
|-------------|-------------|
| Message Broker | RabbitMQ for async event processing |
| Seat Released Events | Published when seats become available (hold expiry, cancellation) |
| Waitlist Notifications | Passengers notified when offered a seat from waitlist |
| Fault Tolerance | Synchronous fallback when broker unavailable |

### 3.5 Observability

| Requirement | Description |
|-------------|-------------|
| Metrics | Prometheus metrics for seats, waitlist, rate limiting |
| Monitoring | Grafana dashboards for real-time visualization |
| Health Checks | Actuator endpoints for all dependencies |
| Structured Logging | Correlation IDs for request tracing |

### 3.6 Scalability

| Requirement | Description |
|-------------|-------------|
| Horizontal Scaling | Stateless design allows multiple instances |
| Caching | Redis caching for frequently accessed data |
| Connection Pooling | Database connection pooling |

---

## 4. System Constraints

| Constraint | Description |
|------------|-------------|
| 1 Booking = 1 Passenger | Each booking reference maps to exactly one passenger |
| Seat Hold Duration | Fixed at 120 seconds, cannot be extended |
| Session Timeout | Fixed at 10 minutes of inactivity |
| Confirmed Seats | Can be cancelled by the confirming passenger before departure |
| Waitlist Capacity | Maximum 50 passengers per flight |

---

## 5. Out of Scope

- Multi-passenger bookings (family bookings)
- Seat upgrades
- Meal selection
- Special assistance requests
- Refund processing
- Real-time flight status updates

---

## 6. Success Metrics

| Metric | Target |
|--------|--------|
| Check-in Completion Rate | > 95% |
| Seat Conflict Rate | 0% |
| System Availability | > 99.5% |
| P95 Response Time | < 2 seconds |

---

## 7. Appendix

### 7.1 Glossary

| Term | Definition |
|------|------------|
| Seat Hold | Temporary 120-second reservation of a seat |
| Check-in Session | Active check-in process with 10-minute timeout |
| Boarding Pass | Digital document generated after check-in completion |
| Excess Baggage | Baggage weight exceeding 25kg limit |

### 7.2 References

- IMPLEMENTATION_PLAN.md - Technical implementation details
- ARCHITECTURE.md - System architecture
- API-SPECIFICATION.yml - API documentation

