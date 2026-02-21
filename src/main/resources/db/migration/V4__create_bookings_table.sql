-- V4: Create bookings table
CREATE TABLE bookings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_reference VARCHAR(6) NOT NULL UNIQUE,
    passenger_id UUID NOT NULL REFERENCES passengers(id),
    flight_id UUID NOT NULL REFERENCES flights(id),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_passenger_flight UNIQUE (passenger_id, flight_id),
    CONSTRAINT chk_booking_status CHECK (status IN ('ACTIVE', 'CANCELLED', 'COMPLETED'))
);

CREATE INDEX idx_bookings_booking_reference ON bookings(booking_reference);
CREATE INDEX idx_bookings_passenger_id ON bookings(passenger_id);
CREATE INDEX idx_bookings_flight_id ON bookings(flight_id);

