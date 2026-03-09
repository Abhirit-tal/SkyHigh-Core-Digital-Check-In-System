# SkyHigh Core - Implementation Plan

## 1. Technology Stack

### Backend
- **Language**: Java 17
- **Framework**: Spring Boot 3.2
- **Build Tool**: Maven

### Data Layer
- **Primary Database**: PostgreSQL 15
- **Caching/Locking**: Redis 7
- **Message Broker**: RabbitMQ 3.12 (event-driven waitlist processing)
- **Migration**: Flyway

### Security
- **Authentication**: JWT (jjwt 0.12.5)
- **Authorization**: Spring Security 6
- **Rate Limiting**: Redis sliding-window (50 req/2s per source)

### Observability
- **Metrics**: Prometheus + Micrometer
- **Dashboards**: Grafana
- **Health Checks**: Spring Boot Actuator

### Documentation
- **API Docs**: OpenAPI 3.0 / Swagger UI
- **PDF Generation**: iText 5
- **QR Code**: ZXing 3.5

### Testing
- **Unit Tests**: JUnit 5, Mockito
- **Integration Tests**: Testcontainers
- **Coverage**: JaCoCo

## 2. Implementation Phases

### Phase 1: Foundation (Days 1-2)
- [x] Project setup with Spring Boot
- [x] Database schema design
- [x] Flyway migrations
- [x] Entity classes with JPA
- [x] Repository layer

### Phase 2: Security (Days 2-3)
- [x] JWT token generation and validation
- [x] Authentication filter
- [x] Flight access checker
- [x] Security configuration

### Phase 3: Core Business Logic (Days 3-5)
- [x] Seat lifecycle management
- [x] Redis distributed locking
- [x] Check-in service orchestration
- [x] Baggage validation (mock)
- [x] Payment processing (mock)

### Phase 4: Background Jobs (Day 5)
- [x] Seat hold expiry scheduler
- [x] Session expiry scheduler

### Phase 5: Boarding Pass (Day 6)
- [x] QR code generation
- [x] PDF generation
- [x] Download endpoint

### Phase 6: API Layer (Days 6-7)
- [x] REST controllers
- [x] Request/Response DTOs
- [x] Global exception handler
- [x] OpenAPI documentation

### Phase 7: Testing & Documentation (Days 7-8)
- [x] Unit tests
- [ ] Integration tests
- [x] Documentation files

## 3. API Endpoints Summary

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /api/v1/auth/login | Authenticate passenger |
| POST | /api/v1/auth/refresh | Refresh access token |
| GET | /api/v1/flights/{id} | Get flight details |
| GET | /api/v1/flights/{id}/seats | Get seat map |
| POST | /api/v1/seats/{id}/hold | Hold a seat (120s) |
| DELETE | /api/v1/seats/{id}/hold | Release seat hold |
| POST | /api/v1/seats/{id}/confirm | Confirm seat |
| POST | /api/v1/check-in/start | Start check-in session |
| GET | /api/v1/check-in/{id} | Get check-in status |
| POST | /api/v1/check-in/{id}/baggage | Add baggage |
| POST | /api/v1/check-in/{id}/payment | Process payment |
| POST | /api/v1/check-in/{id}/confirm | Complete check-in |
| DELETE | /api/v1/check-in/{id} | Cancel check-in |
| GET | /api/v1/boarding-pass/{id} | Get boarding pass |
| GET | /api/v1/boarding-pass/{id}/download | Download PDF |

## 4. Database Schema

### Tables
1. **flights** - Flight information
2. **passengers** - Passenger details
3. **seats** - Seat inventory with lifecycle status
4. **bookings** - Booking records
5. **check_ins** - Check-in sessions
6. **boarding_passes** - Generated boarding passes
7. **seat_audit_log** - Seat state change history

### Key Indexes
- seats(flight_id, status)
- seats(flight_id, seat_number)
- bookings(booking_reference)
- check_ins(booking_id, status)

## 5. Concurrency Control

### Seat Hold Strategy
1. **Redis Lock**: SETNX with 120-second TTL
2. **PostgreSQL**: Optimistic locking with version column
3. **Background Scheduler**: Cleanup every 10 seconds

### Session Management
- Stateless JWT tokens
- 10-minute activity timeout
- Background expiry scheduler

## 6. Configuration Parameters

| Parameter | Value | Description |
|-----------|-------|-------------|
| seat-hold-duration-seconds | 120 | Seat hold timeout |
| session-timeout-minutes | 10 | Check-in session timeout |
| max-baggage-weight-kg | 25 | Max allowed baggage |
| excess-baggage-fee-per-kg | 200 | Fee in INR |
| checkin-window-opens-hours | 24 | Hours before departure |
| checkin-window-closes-hours | 1 | Hours before departure |

## 7. Error Handling Strategy

### Exception Types
- **4xx Client Errors**: Validation, authentication, authorization
- **409 Conflict**: Seat conflicts, concurrent updates
- **422 Unprocessable**: Business rule violations
- **500 Server Errors**: Unexpected errors

### Response Format
```json
{
  "error": {
    "code": "SEAT_ALREADY_HELD",
    "message": "Human-readable message",
    "retryable": true,
    "retryAfterSeconds": 120
  },
  "meta": {
    "timestamp": "2026-02-21T10:00:00",
    "requestId": "uuid",
    "path": "/api/v1/seats/xxx/hold"
  },
  "suggestions": [
    {
      "action": "VIEW_AVAILABLE_SEATS",
      "endpoint": "GET /api/v1/flights/{id}/seats"
    }
  ]
}
```

## 8. Performance Targets

| Metric | Target | Actual |
|--------|--------|--------|
| Seat Map P95 | < 1 second | TBD |
| Concurrent Users | 500+ | TBD |
| Seat Hold Accuracy | ±1 second | ±0.1 second |

## 9. Deployment

### Docker Compose
- PostgreSQL container
- Redis container
- Application container
- Health checks for all services

### Environment Variables
- `SPRING_DATASOURCE_URL`
- `SPRING_DATA_REDIS_HOST`
- `JWT_SECRET`

## 10. Future Improvements

1. Rate limiting with Resilience4j
2. WebSocket for real-time seat updates
3. Multi-passenger booking support
4. Seat upgrade functionality
5. Email notifications

