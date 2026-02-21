-- V5: Create check_ins table
CREATE TABLE check_ins (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID NOT NULL REFERENCES bookings(id),
    seat_id UUID REFERENCES seats(id),
    status VARCHAR(30) NOT NULL DEFAULT 'IN_PROGRESS',
    baggage_weight DECIMAL(5,2),
    excess_baggage_fee DECIMAL(10,2),
    payment_status VARCHAR(20),
    payment_reference VARCHAR(50),
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_activity_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_checkin_status CHECK (status IN ('IN_PROGRESS', 'WAITING_PAYMENT', 'COMPLETED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT chk_payment_status CHECK (payment_status IS NULL OR payment_status IN ('PENDING', 'COMPLETED', 'FAILED', 'DECLINED'))
);

CREATE INDEX idx_check_ins_booking_id ON check_ins(booking_id);
CREATE INDEX idx_check_ins_status ON check_ins(status);
CREATE INDEX idx_check_ins_expires_at ON check_ins(expires_at) WHERE status IN ('IN_PROGRESS', 'WAITING_PAYMENT');

