-- Old DB (no flyway_schema_history) with two non-cancelled tickets on seat 1 of trip 1: V2 must refuse it.
INSERT INTO users (id, email, password, role) VALUES (1, 'driver@example.com', 'hash', 'DRIVER');
INSERT INTO routes (id, route_name) VALUES (1, 'HN - HP');
INSERT INTO buses (id, plate_number, capacity, status) VALUES (1, '29A-ACTIVE', 40, 'ACTIVE');
INSERT INTO trips (id, route_id, bus_id, driver_id, departure_time) VALUES (1, 1, 1, 1, '2026-01-01 08:00:00');
INSERT INTO tickets (id, trip_id, seat_number, price, status_ticket) VALUES
    (1, 1, 1, 100000, 'SUCCESS'),
    (2, 1, 1, 100000, 'PENDING');
