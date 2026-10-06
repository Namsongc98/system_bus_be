-- B35 b (lead review 1.3 L18): indexes for GET /api/trip and the overlap checks of TripService.
-- The list filters by status and a departure range and sorts by departure_time; the overlap checks
-- look up the unfinished trips of one bus / one driver around a departure time.
-- (bus_id, ...) and (driver_id, ...) start with the FK column, so MySQL uses them for the foreign keys too.
CREATE INDEX idx_trips_departure        ON trips (departure_time);
CREATE INDEX idx_trips_status_departure ON trips (status, departure_time);
CREATE INDEX idx_trips_bus_departure    ON trips (bus_id, departure_time);
CREATE INDEX idx_trips_driver_departure ON trips (driver_id, departure_time);
