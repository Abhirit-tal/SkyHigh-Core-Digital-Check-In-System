-- V11: Create abuse_audit_log table for rate limiting and bot detection audit trail
CREATE TABLE IF NOT EXISTS abuse_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_identifier VARCHAR(255) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    endpoint VARCHAR(255),
    request_count INTEGER,
    window_seconds INTEGER,
    details TEXT,
    blocked_until TIMESTAMP,
    passenger_id UUID REFERENCES passengers(id),
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_abuse_source ON abuse_audit_log(source_identifier);
CREATE INDEX idx_abuse_event_type ON abuse_audit_log(event_type);
CREATE INDEX idx_abuse_created_at ON abuse_audit_log(created_at);
CREATE INDEX idx_abuse_passenger ON abuse_audit_log(passenger_id);

