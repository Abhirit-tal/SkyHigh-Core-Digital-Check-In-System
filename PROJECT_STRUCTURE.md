# SkyHigh Core - Project Structure

## Overview

The project follows a standard Spring Boot layered architecture with clear separation of concerns.

```
SkyHigh-Core-Digital-Check-In-System/
│
├── src/
│   ├── main/
│   │   ├── java/com/skyhigh/checkin/
│   │   │   ├── SkyHighCheckInApplication.java    # Main application entry point
│   │   │   │
│   │   │   ├── config/                           # Configuration classes
│   │   │   │   ├── CheckInConfig.java            # Business configuration properties
│   │   │   │   ├── OpenApiConfig.java            # Swagger/OpenAPI configuration
│   │   │   │   ├── RabbitMQConfig.java           # RabbitMQ exchange/queue/binding config
│   │   │   │   ├── RedisConfig.java              # Redis connection configuration
│   │   │   │   ├── SchedulerConfig.java          # ShedLock distributed scheduler locking
│   │   │   │   └── SecurityConfig.java           # Spring Security configuration
│   │   │   │
│   │   │   ├── controller/                       # REST API controllers
│   │   │   │   ├── AuthController.java           # Authentication endpoints
│   │   │   │   ├── BoardingPassController.java   # Boarding pass endpoints
│   │   │   │   ├── CheckInController.java        # Check-in process endpoints
│   │   │   │   ├── FlightController.java         # Flight & seat map endpoints
│   │   │   │   ├── SeatController.java           # Seat management (hold/confirm/cancel)
│   │   │   │   └── WaitlistController.java       # Waitlist join/leave/status/accept/decline
│   │   │   │
│   │   │   ├── dto/                              # Data Transfer Objects
│   │   │   │   ├── event/                        # Event DTOs (RabbitMQ)
│   │   │   │   │   ├── SeatReleasedEvent.java    # Published when seat becomes available
│   │   │   │   │   └── WaitlistOfferEvent.java   # Published when seat offered to waitlisted passenger
│   │   │   │   │
│   │   │   │   ├── request/                      # Request DTOs
│   │   │   │   │   ├── BaggageRequest.java
│   │   │   │   │   ├── HoldSeatRequest.java
│   │   │   │   │   ├── JoinWaitlistRequest.java  # Waitlist join request
│   │   │   │   │   ├── LoginRequest.java
│   │   │   │   │   ├── PaymentRequest.java
│   │   │   │   │   ├── RefreshTokenRequest.java
│   │   │   │   │   └── StartCheckInRequest.java
│   │   │   │   │
│   │   │   │   └── response/                     # Response DTOs
│   │   │   │       ├── BoardingPassResponse.java
│   │   │   │       ├── CheckInResponse.java
│   │   │   │       ├── ErrorResponse.java
│   │   │   │       ├── FlightResponse.java
│   │   │   │       ├── LoginResponse.java
│   │   │   │       ├── PaymentResponse.java
│   │   │   │       ├── SeatHoldResponse.java
│   │   │   │       ├── SeatMapResponse.java
│   │   │   │       └── WaitlistResponse.java     # Waitlist status/offer response
│   │   │   │
│   │   │   ├── exception/                        # Exception handling
│   │   │   │   ├── SkyHighBaseException.java     # Base exception class
│   │   │   │   ├── GlobalExceptionHandler.java   # Global exception handler (incl. 429)
│   │   │   │   ├── AlreadyOnWaitlistException.java  # 409 Conflict
│   │   │   │   ├── BookingNotActiveException.java
│   │   │   │   ├── BaggageWeightExceededException.java
│   │   │   │   ├── CheckInAlreadyExistsException.java
│   │   │   │   ├── CheckInWindowClosedException.java
│   │   │   │   ├── CheckInWindowNotOpenException.java
│   │   │   │   ├── FlightAccessDeniedException.java
│   │   │   │   ├── InvalidCredentialsException.java
│   │   │   │   ├── InvalidSeatStateException.java
│   │   │   │   ├── PaymentFailedException.java
│   │   │   │   ├── PaymentRequiredException.java
│   │   │   │   ├── RateLimitExceededException.java  # 429 Too Many Requests
│   │   │   │   ├── ResourceNotFoundException.java
│   │   │   │   ├── SeatAlreadyConfirmedException.java
│   │   │   │   ├── SeatAlreadyHeldException.java
│   │   │   │   ├── SeatHoldExpiredException.java
│   │   │   │   ├── SessionExpiredException.java
│   │   │   │   ├── WaitlistFullException.java       # Waitlist capacity exceeded
│   │   │   │   └── WaitlistOfferExpiredException.java # Offer timed out
│   │   │   │
│   │   │   ├── model/                            # Domain models
│   │   │   │   ├── entity/                       # JPA entities
│   │   │   │   │   ├── AbuseAuditLog.java        # Abuse/bot detection audit log
│   │   │   │   │   ├── BoardingPass.java
│   │   │   │   │   ├── Booking.java
│   │   │   │   │   ├── CheckIn.java
│   │   │   │   │   ├── Flight.java
│   │   │   │   │   ├── Passenger.java
│   │   │   │   │   ├── Seat.java                 # Includes CANCELLED state + cancel fields
│   │   │   │   │   ├── SeatAuditLog.java
│   │   │   │   │   └── WaitlistEntry.java        # Waitlist queue entries
│   │   │   │   │
│   │   │   │   └── enums/                        # Enumerations
│   │   │   │       ├── BookingStatus.java
│   │   │   │       ├── CheckInStatus.java
│   │   │   │       ├── FlightStatus.java
│   │   │   │       ├── PaymentStatus.java
│   │   │   │       ├── SeatClass.java
│   │   │   │       ├── SeatStatus.java           # AVAILABLE, HELD, CONFIRMED, CANCELLED
│   │   │   │       └── WaitlistStatus.java       # WAITING, OFFERED, ASSIGNED, EXPIRED, LEFT
│   │   │   │
│   │   │   ├── repository/                       # Data access layer
│   │   │   │   ├── AbuseAuditLogRepository.java  # Abuse event queries
│   │   │   │   ├── BoardingPassRepository.java
│   │   │   │   ├── BookingRepository.java
│   │   │   │   ├── CheckInRepository.java
│   │   │   │   ├── FlightRepository.java
│   │   │   │   ├── PassengerRepository.java
│   │   │   │   ├── SeatAuditLogRepository.java
│   │   │   │   ├── SeatRepository.java
│   │   │   │   └── WaitlistRepository.java       # FIFO priority queries, expired offer queries
│   │   │   │
│   │   │   ├── scheduler/                        # Background jobs
│   │   │   │   ├── CheckInSessionExpiryScheduler.java
│   │   │   │   ├── SeatHoldExpiryScheduler.java  # Also publishes events for waitlist
│   │   │   │   └── WaitlistOfferExpiryScheduler.java # Expires unclaimed waitlist offers
│   │   │   │
│   │   │   ├── security/                         # Security components
│   │   │   │   ├── CorrelationIdFilter.java      # MDC request ID for log tracing
│   │   │   │   ├── FlightAccessChecker.java      # Authorization logic
│   │   │   │   ├── JwtAuthenticationFilter.java  # JWT filter
│   │   │   │   ├── JwtTokenProvider.java         # JWT generation/validation
│   │   │   │   ├── PassengerPrincipal.java       # User details
│   │   │   │   └── RateLimitingFilter.java       # Redis sliding-window rate limiter filter
│   │   │   │
│   │   │   └── service/                          # Business logic
│   │   │       ├── AuthService.java              # Authentication service
│   │   │       ├── BoardingPassService.java      # Boarding pass generation
│   │   │       ├── CheckInService.java           # Check-in orchestration (incl. cancel)
│   │   │       ├── FlightService.java            # Flight information
│   │   │       ├── PaymentService.java           # Payment processing (mock)
│   │   │       ├── RateLimiterService.java       # Redis sliding-window abuse detection
│   │   │       ├── SeatDomainEventPublisher.java  # Spring ApplicationEvent publisher (post-commit)
│   │   │       ├── SeatEventPublisher.java       # RabbitMQ event publisher
│   │   │       ├── TransactionalSeatEventHandler.java # @TransactionalEventListener (AFTER_COMMIT)
│   │   │       ├── SeatLockService.java          # Redis distributed locking
│   │   │       ├── SeatService.java              # Seat management (hold/confirm/cancel)
│   │   │       ├── WaitlistEventListener.java    # RabbitMQ consumer for seat released events
│   │   │       ├── WaitlistService.java          # Waitlist FIFO management
│   │   │       └── WeightService.java            # Baggage validation (mock)
│   │   │
│   │   └── resources/
│   │       ├── application.yml                   # Main configuration
│   │       └── db/migration/                     # Flyway migrations
│   │           ├── V1__create_flights_table.sql
│   │           ├── V2__create_passengers_table.sql
│   │           ├── V3__create_seats_table.sql
│   │           ├── V4__create_bookings_table.sql
│   │           ├── V5__create_check_ins_table.sql
│   │           ├── V6__create_boarding_passes_table.sql
│   │           ├── V7__create_audit_tables.sql
│   │           ├── V8__seed_data.sql
│   │           ├── V9__add_cancelled_seat_status.sql   # CANCELLED state + cancel columns
│   │           ├── V10__create_waitlist_table.sql       # waitlist_entries table
│   │           └── V11__create_abuse_audit_log.sql      # abuse_audit_log table
│   │
│   └── test/                                     # Test classes
│       └── java/com/skyhigh/checkin/
│
├── docs/                                         # Documentation
│
├── docker-compose.yml                            # Docker orchestration
├── Dockerfile                                    # Application container
├── pom.xml                                       # Maven build configuration
│
├── PRD.md                                        # Product Requirements
├── README.md                                     # Project overview
├── PROJECT_STRUCTURE.md                          # This file
├── ARCHITECTURE.md                               # System architecture
├── WORKFLOW_DESIGN.md                            # Flow diagrams
├── API-SPECIFICATION.yml                         # OpenAPI spec
├── IMPLEMENTATION_PLAN.md                        # Implementation plan
└── CHAT_HISTORY.md                               # AI collaboration log
```

## Key Modules

### Config (`config/`)
Contains configuration classes for Spring Security, Redis, OpenAPI documentation, and business rules.

### Controller (`controller/`)
REST API endpoints organized by domain:
- **AuthController**: Login, token refresh, logout
- **FlightController**: Flight details, seat map
- **SeatController**: Hold, release, confirm seats
- **CheckInController**: Start, manage, complete check-in
- **BoardingPassController**: View and download boarding pass

### DTO (`dto/`)
Data Transfer Objects for API request/response:
- **Request DTOs**: Validated input from clients
- **Response DTOs**: Structured output to clients

### Exception (`exception/`)
Comprehensive exception hierarchy with global handler:
- Business rule exceptions (seat conflicts, payment required)
- Authentication/authorization exceptions
- Resource not found exceptions

### Model (`model/`)
Domain entities and enums:
- **Entities**: JPA entities with relationships
- **Enums**: Status and type enumerations

### Repository (`repository/`)
Spring Data JPA repositories with custom queries for:
- Optimistic locking
- Complex searches
- Batch operations

### Scheduler (`scheduler/`)
Background jobs for:
- Releasing expired seat holds (every 10 seconds)
- Expiring inactive check-in sessions (every minute)

### Security (`security/`)
JWT-based authentication and authorization:
- Token generation and validation
- Flight-level access control
- Authentication filter

### Service (`service/`)
Business logic layer:
- **AuthService**: Authentication flow
- **CheckInService**: Check-in orchestration
- **SeatService**: Seat lifecycle management
- **SeatLockService**: Redis distributed locking
- **BoardingPassService**: PDF/QR generation
- **WeightService**: Baggage validation (mock)
- **PaymentService**: Payment processing (mock)

## Database Migrations

Flyway migrations in execution order:
1. **V1**: Flights table
2. **V2**: Passengers table
3. **V3**: Seats table (with optimistic locking)
4. **V4**: Bookings table
5. **V5**: Check-ins table
6. **V6**: Boarding passes table
7. **V7**: Audit tables (seat history, idempotency keys)
8. **V8**: Seed data for testing

