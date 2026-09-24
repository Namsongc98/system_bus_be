-- V2: one non-cancelled ticket per (trip, seat). Task 0.8 / B4.
-- active_seat_number holds seat_number only while the ticket is not CANCELLED, so a cancelled
-- ticket frees its seat and NULLs never collide. Not mapped in the Ticket entity.
-- Fails if active duplicates already exist: clean them up by hand first (spec review 0.8, D4).
ALTER TABLE tickets
    ADD COLUMN active_seat_number INT
        GENERATED ALWAYS AS (IF(status_ticket <> 'CANCELLED', seat_number, NULL)) STORED,
    ADD CONSTRAINT uk_tickets_trip_active_seat UNIQUE (trip_id, active_seat_number);
