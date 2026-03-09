-- V9: Add CANCELLED seat status and cancellation tracking columns
ALTER TABLE seats
    ADD COLUMN IF NOT EXISTS cancelled_by_passenger_id UUID REFERENCES passengers(id),
    ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP;

-- Update seat status constraint to include CANCELLED
ALTER TABLE seats DROP CONSTRAINT IF EXISTS seats_status_check;
ALTER TABLE seats ADD CONSTRAINT seats_status_check
    CHECK (status IN ('AVAILABLE', 'HELD', 'CONFIRMED', 'CANCELLED'));

CREATE INDEX IF NOT EXISTS idx_seats_cancelled_by ON seats(cancelled_by_passenger_id);

