-- V7: Create audit tables
CREATE TABLE seat_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    seat_id UUID NOT NULL,
    flight_id UUID NOT NULL,
    seat_number VARCHAR(4) NOT NULL,
    previous_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    changed_by_passenger_id UUID,
    change_reason VARCHAR(50),
    metadata TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_seat_audit_log_seat_id ON seat_audit_log(seat_id);
CREATE INDEX idx_seat_audit_log_flight_id ON seat_audit_log(flight_id);
CREATE INDEX idx_seat_audit_log_created_at ON seat_audit_log(created_at);

-- Create idempotency keys table for preventing duplicate requests
CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(100) PRIMARY KEY,
    endpoint VARCHAR(100) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    response_status INTEGER,
    response_body TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_idempotency_keys_expires_at ON idempotency_keys(expires_at);

