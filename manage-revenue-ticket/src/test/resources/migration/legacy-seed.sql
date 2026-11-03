-- Rows of a DB built by the old ddl-auto=update (runs after V1__baseline.sql, no flyway_schema_history).
-- Buses hold all three pre-V3 statuses: V3 must still map them after the baseline.
-- Seat 1 of trip 1 has one SUCCESS and one CANCELLED ticket: valid for V2 (a cancelled ticket frees its seat).
INSERT INTO users (id, email, password, role) VALUES
    (1, 'legacy@example.com', 'hash', 'ADMIN'),
    (2, 'driver@example.com', 'hash', 'DRIVER');
INSERT INTO routes (id, route_name) VALUES (1, 'HN - HP');
INSERT INTO buses (id, plate_number, capacity, status) VALUES
    (1, '29A-PENDING', 40, 'PENDING'),
    (2, '29A-ACTIVE', 40, 'ACTIVE'),
    (3, '29A-INACTIVE', 40, 'INACTIVE');
INSERT INTO trips (id, route_id, bus_id, driver_id, departure_time) VALUES (1, 1, 2, 2, '2026-01-01 08:00:00');
INSERT INTO tickets (id, trip_id, seat_number, price, status_ticket) VALUES
    (1, 1, 1, 100000, 'SUCCESS'),
    (2, 1, 1, 100000, 'CANCELLED');
