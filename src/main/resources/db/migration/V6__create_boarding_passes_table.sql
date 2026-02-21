-- V6: Create boarding_passes table
CREATE TABLE boarding_passes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    check_in_id UUID NOT NULL UNIQUE REFERENCES check_ins(id),
    passenger_name VARCHAR(100) NOT NULL,
    flight_number VARCHAR(10) NOT NULL,
    seat_number VARCHAR(4) NOT NULL,
    seat_class VARCHAR(20) NOT NULL,
    origin VARCHAR(3) NOT NULL,
    destination VARCHAR(3) NOT NULL,
    departure_time TIMESTAMP NOT NULL,
    gate VARCHAR(10),
    boarding_time TIMESTAMP,
    barcode_data VARCHAR(100) NOT NULL UNIQUE,
    qr_code_data TEXT,
    generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_boarding_passes_check_in_id ON boarding_passes(check_in_id);
CREATE INDEX idx_boarding_passes_barcode ON boarding_passes(barcode_data);

