-- V8: Seed data for testing

-- Insert sample flights
-- Flight 1: Departing tomorrow (check-in OPEN)
INSERT INTO flights (id, flight_number, departure_time, arrival_time, origin, destination, aircraft_type, status, total_seats, gate)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'SH101', CURRENT_TIMESTAMP + INTERVAL '20 hours', CURRENT_TIMESTAMP + INTERVAL '22 hours 30 minutes', 'DEL', 'BOM', 'Airbus A320', 'SCHEDULED', 180, 'A12'),
    ('22222222-2222-2222-2222-222222222222', 'SH102', CURRENT_TIMESTAMP + INTERVAL '30 minutes', CURRENT_TIMESTAMP + INTERVAL '3 hours', 'BOM', 'DEL', 'Airbus A320', 'SCHEDULED', 180, 'B05'),
    ('33333333-3333-3333-3333-333333333333', 'SH103', CURRENT_TIMESTAMP + INTERVAL '7 days', CURRENT_TIMESTAMP + INTERVAL '7 days 2 hours 30 minutes', 'DEL', 'BLR', 'Boeing 737', 'SCHEDULED', 160, NULL),
    ('44444444-4444-4444-4444-444444444444', 'SH104', CURRENT_TIMESTAMP + INTERVAL '18 hours', CURRENT_TIMESTAMP + INTERVAL '21 hours', 'BLR', 'CCU', 'Airbus A321', 'SCHEDULED', 200, 'C08');

-- Insert sample passengers
INSERT INTO passengers (id, first_name, last_name, email, phone, date_of_birth)
VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'John', 'Doe', 'john.doe@email.com', '+91-9876543210', '1990-05-15'),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'Jane', 'Smith', 'jane.smith@email.com', '+91-9876543211', '1985-08-22'),
    ('cccccccc-cccc-cccc-cccc-cccccccccccc', 'Robert', 'Johnson', 'robert.j@email.com', '+91-9876543212', '1978-12-03'),
    ('dddddddd-dddd-dddd-dddd-dddddddddddd', 'Emily', 'Williams', 'emily.w@email.com', '+91-9876543213', '1995-03-28'),
    ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 'Michael', 'Brown', 'michael.b@email.com', '+91-9876543214', '1982-07-10');

-- Insert bookings
INSERT INTO bookings (id, booking_reference, passenger_id, flight_id, status)
VALUES
    ('b1111111-1111-1111-1111-111111111111', 'ABC123', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '11111111-1111-1111-1111-111111111111', 'ACTIVE'),
    ('b2222222-2222-2222-2222-222222222222', 'DEF456', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '11111111-1111-1111-1111-111111111111', 'ACTIVE'),
    ('b3333333-3333-3333-3333-333333333333', 'GHI789', 'cccccccc-cccc-cccc-cccc-cccccccccccc', '22222222-2222-2222-2222-222222222222', 'ACTIVE'),
    ('b4444444-4444-4444-4444-444444444444', 'JKL012', 'dddddddd-dddd-dddd-dddd-dddddddddddd', '33333333-3333-3333-3333-333333333333', 'ACTIVE'),
    ('b5555555-5555-5555-5555-555555555555', 'MNO345', 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', '44444444-4444-4444-4444-444444444444', 'ACTIVE');

-- Generate seats for Flight SH101 (180 seats: 8 First, 24 Business, 148 Economy)
-- First Class: Rows 1-2 (4 seats per row: A, C, D, F)
INSERT INTO seats (flight_id, seat_number, seat_class, status)
SELECT '11111111-1111-1111-1111-111111111111', row_num || seat_letter, 'FIRST', 'AVAILABLE'
FROM generate_series(1, 2) AS row_num,
     unnest(ARRAY['A', 'C', 'D', 'F']) AS seat_letter;

-- Business Class: Rows 3-8 (4 seats per row)
INSERT INTO seats (flight_id, seat_number, seat_class, status)
SELECT '11111111-1111-1111-1111-111111111111', row_num || seat_letter, 'BUSINESS', 'AVAILABLE'
FROM generate_series(3, 8) AS row_num,
     unnest(ARRAY['A', 'C', 'D', 'F']) AS seat_letter;

-- Economy Class: Rows 9-33 (6 seats per row: A, B, C, D, E, F)
INSERT INTO seats (flight_id, seat_number, seat_class, status)
SELECT '11111111-1111-1111-1111-111111111111', row_num || seat_letter, 'ECONOMY', 'AVAILABLE'
FROM generate_series(9, 33) AS row_num,
     unnest(ARRAY['A', 'B', 'C', 'D', 'E', 'F']) AS seat_letter;

-- Generate seats for Flight SH102 (same layout as SH101)
INSERT INTO seats (flight_id, seat_number, seat_class, status)
SELECT '22222222-2222-2222-2222-222222222222', row_num || seat_letter, 'FIRST', 'AVAILABLE'
FROM generate_series(1, 2) AS row_num,
     unnest(ARRAY['A', 'C', 'D', 'F']) AS seat_letter;

INSERT INTO seats (flight_id, seat_number, seat_class, status)
SELECT '22222222-2222-2222-2222-222222222222', row_num || seat_letter, 'BUSINESS', 'AVAILABLE'
FROM generate_series(3, 8) AS row_num,
     unnest(ARRAY['A', 'C', 'D', 'F']) AS seat_letter;

INSERT INTO seats (flight_id, seat_number, seat_class, status)
SELECT '22222222-2222-2222-2222-222222222222', row_num || seat_letter, 'ECONOMY', 'AVAILABLE'
FROM generate_series(9, 33) AS row_num,
     unnest(ARRAY['A', 'B', 'C', 'D', 'E', 'F']) AS seat_letter;

-- Generate seats for Flight SH103 (160 seats Boeing 737)
INSERT INTO seats (flight_id, seat_number, seat_class, status)
SELECT '33333333-3333-3333-3333-333333333333', row_num || seat_letter, 'FIRST', 'AVAILABLE'
FROM generate_series(1, 2) AS row_num,
     unnest(ARRAY['A', 'C', 'D', 'F']) AS seat_letter;

INSERT INTO seats (flight_id, seat_number, seat_class, status)
SELECT '33333333-3333-3333-3333-333333333333', row_num || seat_letter, 'BUSINESS', 'AVAILABLE'
FROM generate_series(3, 6) AS row_num,
     unnest(ARRAY['A', 'C', 'D', 'F']) AS seat_letter;

INSERT INTO seats (flight_id, seat_number, seat_class, status)
SELECT '33333333-3333-3333-3333-333333333333', row_num || seat_letter, 'ECONOMY', 'AVAILABLE'
FROM generate_series(7, 30) AS row_num,
     unnest(ARRAY['A', 'B', 'C', 'D', 'E', 'F']) AS seat_letter;

-- Generate seats for Flight SH104 (200 seats Airbus A321)
INSERT INTO seats (flight_id, seat_number, seat_class, status)
SELECT '44444444-4444-4444-4444-444444444444', row_num || seat_letter, 'FIRST', 'AVAILABLE'
FROM generate_series(1, 2) AS row_num,
     unnest(ARRAY['A', 'C', 'D', 'F']) AS seat_letter;

INSERT INTO seats (flight_id, seat_number, seat_class, status)
SELECT '44444444-4444-4444-4444-444444444444', row_num || seat_letter, 'BUSINESS', 'AVAILABLE'
FROM generate_series(3, 9) AS row_num,
     unnest(ARRAY['A', 'C', 'D', 'F']) AS seat_letter;

INSERT INTO seats (flight_id, seat_number, seat_class, status)
SELECT '44444444-4444-4444-4444-444444444444', row_num || seat_letter, 'ECONOMY', 'AVAILABLE'
FROM generate_series(10, 36) AS row_num,
     unnest(ARRAY['A', 'B', 'C', 'D', 'E', 'F']) AS seat_letter;

