-- V3: bus status AVAILABLE / IN_USE / MAINTENANCE. Task 1.1 / B15, decision D1 = A.
-- Old names meant: PENDING = free, ACTIVE = running a trip, INACTIVE = broken.
-- Widen the enum, map the rows, then drop the old values, so no row ever holds an invalid value.
ALTER TABLE buses
    MODIFY status ENUM('ACTIVE','INACTIVE','PENDING','AVAILABLE','IN_USE','MAINTENANCE') NOT NULL DEFAULT 'AVAILABLE';

UPDATE buses
SET status = CASE status
                 WHEN 'PENDING' THEN 'AVAILABLE'
                 WHEN 'ACTIVE' THEN 'IN_USE'
                 WHEN 'INACTIVE' THEN 'MAINTENANCE'
                 ELSE status
             END;

ALTER TABLE buses
    MODIFY status ENUM('AVAILABLE','IN_USE','MAINTENANCE') NOT NULL DEFAULT 'AVAILABLE';
