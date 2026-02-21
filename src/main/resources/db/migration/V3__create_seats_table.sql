-- V3: Create seats table
CREATE TABLE seats (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flight_id UUID NOT NULL REFERENCES flights(id) ON DELETE CASCADE,
    seat_number VARCHAR(4) NOT NULL,
    seat_class VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    held_by_passenger_id UUID REFERENCES passengers(id),
    held_until TIMESTAMP,
    confirmed_by_passenger_id UUID REFERENCES passengers(id),
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_flight_seat UNIQUE (flight_id, seat_number),
    CONSTRAINT chk_seat_status CHECK (status IN ('AVAILABLE', 'HELD', 'CONFIRMED')),
    CONSTRAINT chk_seat_class CHECK (seat_class IN ('FIRST', 'BUSINESS', 'ECONOMY'))
);

CREATE INDEX idx_seats_flight_id ON seats(flight_id);
CREATE INDEX idx_seats_status ON seats(status);
CREATE INDEX idx_seats_held_until ON seats(held_until) WHERE status = 'HELD';

