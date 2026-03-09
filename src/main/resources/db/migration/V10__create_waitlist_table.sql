-- V10: Create waitlist_entries table for FIFO seat waitlist management
CREATE TABLE IF NOT EXISTS waitlist_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flight_id UUID NOT NULL REFERENCES flights(id),
    passenger_id UUID NOT NULL REFERENCES passengers(id),
    preferred_seat_class VARCHAR(20),
    priority INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    joined_at TIMESTAMP NOT NULL DEFAULT NOW(),
    offered_at TIMESTAMP,
    offer_expires_at TIMESTAMP,
    assigned_seat_id UUID REFERENCES seats(id),
    assigned_at TIMESTAMP,
    notification_sent BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    CONSTRAINT uq_waitlist_flight_passenger UNIQUE (flight_id, passenger_id),
    CONSTRAINT waitlist_status_check CHECK (status IN ('WAITING', 'OFFERED', 'ASSIGNED', 'EXPIRED', 'LEFT'))
);

CREATE INDEX idx_waitlist_flight_status ON waitlist_entries(flight_id, status);
CREATE INDEX idx_waitlist_flight_priority ON waitlist_entries(flight_id, priority);
CREATE INDEX idx_waitlist_passenger ON waitlist_entries(passenger_id);
CREATE INDEX idx_waitlist_offer_expires ON waitlist_entries(offer_expires_at) WHERE status = 'OFFERED';

