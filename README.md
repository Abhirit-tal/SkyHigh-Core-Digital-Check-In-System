# SkyHigh Core - Digital Check-In System

A high-concurrency backend service for airline self-check-in that handles peak-hour traffic with hundreds of concurrent users.

## Features

- **Seat Lifecycle Management**: AVAILABLE → HELD → CONFIRMED → CANCELLED
- **Time-Bound Seat Holds**: 120-second reservation window with automatic release
- **Conflict-Free Seat Assignment**: Redis distributed locks + PostgreSQL optimistic locking
- **Seat Cancellation**: Confirmed seats can be cancelled; triggers waitlist processing
- **Waitlist System**: FIFO queue per flight with auto-assignment via event-driven architecture
- **Abuse/Bot Detection**: Redis sliding-window rate limiter (50 req/2s per source, 5-min block)
- **Event-Driven Architecture**: RabbitMQ for seat-released events and waitlist notifications
- **Baggage Validation**: Weight validation with excess fee calculation (₹200/kg over 25kg)
- **Payment Processing**: Mock payment service with deterministic test scenarios
- **Boarding Pass Generation**: PDF generation with QR codes
- **High-Performance Seat Map**: Redis caching with <1 second P95 latency
- **Observability**: Prometheus metrics + Grafana dashboards + structured logging

## Tech Stack

- **Language**: Java 17
- **Framework**: Spring Boot 3.2
- **Database**: PostgreSQL 15
- **Cache/Locking**: Redis 7
- **Message Broker**: RabbitMQ 3.12 (event-driven waitlist processing)
- **Metrics**: Prometheus + Grafana
- **Documentation**: OpenAPI 3.0 (Swagger)
- **Containerization**: Docker & Docker Compose

## Prerequisites

- Java 17 or higher
- Maven 3.8+
- Docker & Docker Compose
- PostgreSQL 15 (or use Docker)
- Redis 7 (or use Docker)
- RabbitMQ 3.12 (or use Docker)

## Quick Start

### Option 1: Using Docker Compose (Recommended)

```bash
# Clone the repository
git clone <repository-url>
cd SkyHigh-Core-Digital-Check-In-System

# Start all services
docker-compose up --build

# Access the application
# API: http://localhost:8080
# Swagger UI: http://localhost:8080/swagger-ui.html
# RabbitMQ Management: http://localhost:15672 (guest/guest)
# Prometheus: http://localhost:9090
# Grafana: http://localhost:3000 (admin/admin)
```

### Option 2: Local Development

1. **Start PostgreSQL, Redis, and RabbitMQ**

```bash
# Using Docker
docker run -d --name postgres -e POSTGRES_DB=skyhigh_checkin -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:15-alpine

docker run -d --name redis -p 6379:6379 redis:7-alpine

docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3.12-management-alpine
```

2. **Build and Run the Application**

```bash
# Build
mvn clean package -DskipTests

# Run
java -jar target/skyhigh-core-checkin-1.0.0.jar
```

3. **Access the Application**
   - API: http://localhost:8080
   - Swagger UI: http://localhost:8080/swagger-ui.html
   - API Docs: http://localhost:8080/api-docs

## Database Setup

The application uses Flyway for database migrations. All schema and seed data are automatically applied on startup.

### Important: Create Database First

**If running locally (not with Docker)**, you must create the database before starting the application:

```sql
-- Connect to PostgreSQL as superuser (e.g., using psql or pgAdmin)
CREATE DATABASE skyhigh_checkin;
```

**If running with Docker Compose**, the database is created automatically.

### Seed Data

The following test data is pre-loaded:

| Booking Reference | Passenger | Flight | Status |
|-------------------|-----------|--------|--------|
| ABC123 | John Doe (john.doe@email.com) | SH101 | Check-in OPEN |
| DEF456 | Jane Smith (jane.smith@email.com) | SH101 | Check-in OPEN |
| GHI789 | Robert Johnson (robert.j@email.com) | SH102 | Check-in CLOSED |
| JKL012 | Emily Williams (emily.w@email.com) | SH103 | Check-in NOT YET OPEN |
| MNO345 | Michael Brown (michael.b@email.com) | SH104 | Check-in OPEN |

## API Usage

### 1. Authentication

```bash
# Login
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "bookingReference": "ABC123",
    "lastName": "Doe",
    "email": "john.doe@email.com"
  }'
```

### 2. Start Check-In

```bash
# Use the access token from login response
curl -X POST http://localhost:8080/api/v1/check-in/start \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <access_token>" \
  -d '{
    "flightId": "11111111-1111-1111-1111-111111111111",
    "bookingReference": "ABC123"
  }'
```

### 3. View Seat Map

```bash
curl -X GET http://localhost:8080/api/v1/flights/11111111-1111-1111-1111-111111111111/seats \
  -H "Authorization: Bearer <access_token>"
```

### 4. Hold a Seat

```bash
curl -X POST http://localhost:8080/api/v1/seats/<seat_id>/hold \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <access_token>" \
  -d '{
    "checkInId": "<check_in_id>"
  }'
```

### 5. Add Baggage

```bash
curl -X POST http://localhost:8080/api/v1/check-in/<check_in_id>/baggage \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <access_token>" \
  -d '{
    "weightKg": 20.5
  }'
```

### 6. Process Payment (if excess baggage)

```bash
curl -X POST http://localhost:8080/api/v1/check-in/<check_in_id>/payment \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <access_token>" \
  -d '{
    "amount": 1100.00,
    "currency": "INR"
  }'
```

### 7. Confirm Check-In

```bash
curl -X POST http://localhost:8080/api/v1/check-in/<check_in_id>/confirm \
  -H "Authorization: Bearer <access_token>"
```

### 8. Download Boarding Pass

```bash
curl -X GET http://localhost:8080/api/v1/boarding-pass/<check_in_id>/download \
  -H "Authorization: Bearer <access_token>" \
  --output boarding-pass.pdf
```

## Configuration

Key configuration properties in `application.yml`:

```yaml
skyhigh:
  checkin:
    seat-hold-duration-seconds: 120    # Seat hold timeout
    session-timeout-minutes: 10        # Check-in session timeout
    max-baggage-weight-kg: 25          # Max allowed baggage
    excess-baggage-fee-per-kg: 200     # Fee in INR
    checkin-window-opens-hours: 24     # Hours before departure
    checkin-window-closes-hours: 1     # Hours before departure
```

## Testing

### Run Unit Tests (no Docker required)

```bash
mvn clean test
```

### Run Integration Tests (requires Docker)

```bash
mvn verify -Pintegration-test
```

Integration tests use **Testcontainers** to spin up real PostgreSQL 15 and Redis 7 containers.
They verify:
- Redis distributed locking under 10-thread concurrency
- Redis lock TTL expiry behavior
- Flyway migrations on real PostgreSQL (not H2)
- PostgreSQL pessimistic locking (SELECT FOR UPDATE)

### Run Tests with Coverage

```bash
mvn clean test jacoco:report
# Coverage report: target/site/jacoco/index.html
```

### Test Summary (61 unit tests, 4 integration tests)

| Category | Tests | Scope |
|----------|-------|-------|
| Seat lifecycle | 15 | Hold, confirm, cancel, release, state transitions |
| Waitlist | 6 | Join, leave, offer, accept, decline, FIFO |
| Rate limiting | 6 | Sliding window, blocking, fail-open |
| Redis locks | 9 | Acquire, release, force-release, errors |
| Check-in flow | 8 | Start, baggage, payment, cancel |
| Concurrency | 2 | 10-thread lock acquisition |
| Payment | 4 | Success, decline, timeout, idempotency |
| Auth | 3 | Login, invalid booking ref, invalid email |
| Context | 1 | Spring Boot context load |
| **Integration** | **4** | Real PostgreSQL + Redis via Testcontainers |

## Mock Services

### Weight Service
- Always succeeds instantly
- Returns the weight provided by the passenger
- Calculates excess fee at ₹200/kg for weight > 25kg

### Payment Service
- Synchronous processing
- Deterministic failures:
  - Amount ending in `.99` → DECLINED
  - Amount ending in `.88` → TIMEOUT
  - All other amounts → SUCCESS

## Project Structure

See [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md) for detailed code organization.

## API Documentation

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI Spec**: http://localhost:8080/api-docs
- See [API-SPECIFICATION.yml](API-SPECIFICATION.yml) for full specification

## Architecture

See [ARCHITECTURE.md](ARCHITECTURE.md) for system architecture details.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## License

This project is proprietary to SkyHigh Airlines.

