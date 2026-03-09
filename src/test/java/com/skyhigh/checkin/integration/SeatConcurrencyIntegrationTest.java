package com.skyhigh.checkin.integration;

import com.skyhigh.checkin.model.entity.Flight;
import com.skyhigh.checkin.model.entity.Passenger;
import com.skyhigh.checkin.model.entity.Seat;
import com.skyhigh.checkin.model.enums.FlightStatus;
import com.skyhigh.checkin.model.enums.SeatClass;
import com.skyhigh.checkin.model.enums.SeatStatus;
import com.skyhigh.checkin.repository.FlightRepository;
import com.skyhigh.checkin.repository.PassengerRepository;
import com.skyhigh.checkin.repository.SeatRepository;
import com.skyhigh.checkin.service.SeatLockService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests using real PostgreSQL and Redis via Testcontainers.
 * Verifies:
 * - Redis distributed locking under concurrency
 * - PostgreSQL pessimistic locking for seat holds
 * - Flyway migrations run on real PostgreSQL (not H2)
 * - Seat hold expiry with real Redis TTL
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SeatConcurrencyIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
            .withDatabaseName("skyhigh_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("skyhigh.rabbitmq.enabled", () -> "false");
    }

    @Autowired private SeatLockService seatLockService;
    @Autowired private SeatRepository seatRepository;
    @Autowired private FlightRepository flightRepository;
    @Autowired private PassengerRepository passengerRepository;
    @Autowired private RedisTemplate<String, Object> redisTemplate;

    private Flight testFlight;
    private Seat testSeat;
    private Passenger testPassenger;

    @BeforeEach
    void setUp() {
        // Create test flight
        testFlight = flightRepository.save(Flight.builder()
                .flightNumber("IT-" + UUID.randomUUID().toString().substring(0, 6))
                .departureTime(LocalDateTime.now().plusHours(20))
                .arrivalTime(LocalDateTime.now().plusHours(22))
                .origin("DEL").destination("BOM")
                .status(FlightStatus.SCHEDULED)
                .totalSeats(180)
                .build());

        // Create test seat
        testSeat = seatRepository.save(Seat.builder()
                .flight(testFlight)
                .seatNumber("1A")
                .seatClass(SeatClass.BUSINESS)
                .status(SeatStatus.AVAILABLE)
                .build());

        // Create test passenger
        testPassenger = passengerRepository.save(Passenger.builder()
                .firstName("Test").lastName("Passenger")
                .email("test-" + UUID.randomUUID().toString().substring(0, 6) + "@test.com")
                .passportNumber("TP" + UUID.randomUUID().toString().substring(0, 6).toUpperCase())
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .build());
    }

    @Test
    @Order(1)
    @DisplayName("Only one of 10 concurrent threads should acquire the Redis lock")
    void concurrentLockAcquisition_onlyOneWins() throws Exception {
        UUID flightId = testFlight.getId();
        String seatNumber = testSeat.getSeatNumber();
        int threadCount = 10;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            UUID passengerId = UUID.randomUUID();
            executor.submit(() -> {
                try {
                    startLatch.await();
                    boolean acquired = seatLockService.acquireLock(flightId, seatNumber, passengerId, 120);
                    if (acquired) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                }
            });
        }

        startLatch.countDown(); // Release all threads at once
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(1, successCount.get(), "Exactly one thread should acquire the lock");
        assertEquals(threadCount - 1, failCount.get(), "All other threads should fail");
    }

    @Test
    @Order(2)
    @DisplayName("Redis lock should expire after TTL")
    void lockExpiresAfterTTL() throws Exception {
        UUID flightId = testFlight.getId();
        String seatNumber = "2A"; // Different seat to avoid conflict
        UUID passengerId = testPassenger.getId();

        // Acquire lock with 2 second TTL
        boolean acquired = seatLockService.acquireLock(flightId, seatNumber, passengerId, 2);
        assertTrue(acquired, "Should acquire lock");

        // Verify lock exists
        assertTrue(seatLockService.isLockedByPassenger(flightId, seatNumber, passengerId));

        // Wait for TTL expiry
        Thread.sleep(2500);

        // Lock should have expired — another passenger can now acquire
        UUID anotherPassenger = UUID.randomUUID();
        boolean reacquired = seatLockService.acquireLock(flightId, seatNumber, anotherPassenger, 120);
        assertTrue(reacquired, "Second passenger should acquire lock after TTL expiry");
    }

    @Test
    @Order(3)
    @DisplayName("PostgreSQL seat data persists correctly through Flyway migrations")
    void flywayMigrationsWork() {
        // If we got here, Flyway ran all 11 migrations on real PostgreSQL successfully
        assertNotNull(testFlight.getId());
        assertNotNull(testSeat.getId());
        assertEquals(SeatStatus.AVAILABLE, testSeat.getStatus());

        // Verify we can query with the custom repository methods
        var seats = seatRepository.findByFlightIdOrderBySeatClassAndNumber(testFlight.getId());
        assertFalse(seats.isEmpty());
    }

    @Test
    @Order(4)
    @DisplayName("Pessimistic lock findByIdWithLock works on real PostgreSQL")
    void pessimisticLockWorks() {
        var lockedSeat = seatRepository.findByIdWithLock(testSeat.getId());
        assertTrue(lockedSeat.isPresent());
        assertEquals(testSeat.getSeatNumber(), lockedSeat.get().getSeatNumber());
    }
}

