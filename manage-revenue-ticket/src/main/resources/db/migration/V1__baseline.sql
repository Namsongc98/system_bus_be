-- V1 baseline: reproduces the schema previously created by Hibernate ddl-auto=update
-- from the 13 entities of manage-revenue-ticket. Do not edit once applied; add V2__... instead.
-- Known deviations from the entities are kept on purpose (see .claude/docs/review/0.3-flyway-baseline.md):
--   users.role default 'customer' (lowercase), tickets.status_ticket DEFAULT 'NOT_BOOKED'.

CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    created_at    TIMESTAMP    NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    email         VARCHAR(100) NOT NULL,
    password      VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'customer',
    is_active     TINYINT(1)   NOT NULL DEFAULT 1,
    driver_status ENUM('ACTIVE','INACTIVE','PENDING') NULL,
    user_status   ENUM('BOOKED','NOT_BOOKED') NULL,
    PRIMARY KEY (id),
    CONSTRAINT email_unique UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE routes (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    route_name  VARCHAR(100) NOT NULL,
    start_point VARCHAR(100) NULL,
    end_point   VARCHAR(100) NULL,
    distance_km DECIMAL(6,2) NULL,
    status      ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE buses (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP   NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    plate_number VARCHAR(20) NOT NULL,
    capacity     INT         NOT NULL,
    status       ENUM('ACTIVE','INACTIVE','PENDING') NOT NULL DEFAULT 'ACTIVE',
    PRIMARY KEY (id),
    CONSTRAINT plate_number UNIQUE (plate_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE profiles (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    user_id       BIGINT       NOT NULL,
    full_name     VARCHAR(150) NOT NULL,
    phone         VARCHAR(20)  NULL,
    email         VARCHAR(100) NULL,
    address       VARCHAR(255) NULL,
    date_of_birth DATE         NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_profiles_user_id UNIQUE (user_id),
    CONSTRAINT fk_profiles_users FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE trips (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    route_id       BIGINT        NOT NULL,
    bus_id         BIGINT        NOT NULL,
    driver_id      BIGINT        NOT NULL,
    departure_time DATETIME(6)   NOT NULL,
    arrival_time   DATETIME(6)   NULL,
    status         ENUM('SCHEDULED','ONGOING','COMPLETED','CANCELLED') NOT NULL DEFAULT 'SCHEDULED',
    revenue        DECIMAL(15,2) NULL DEFAULT 0.00,
    PRIMARY KEY (id),
    CONSTRAINT trips_fk_route  FOREIGN KEY (route_id)  REFERENCES routes (id),
    CONSTRAINT trips_fk_bus    FOREIGN KEY (bus_id)    REFERENCES buses (id),
    CONSTRAINT trips_fk_driver FOREIGN KEY (driver_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tickets (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    trip_id       BIGINT        NOT NULL,
    customer_id   BIGINT        NULL,
    seller_id     BIGINT        NULL,
    status_ticket ENUM('NOT_BOOKED','PENDING','CANCELLED','SUCCESS') NOT NULL DEFAULT 'NOT_BOOKED',
    seat_number   INT           NULL,
    price         DECIMAL(10,2) NOT NULL,
    user_status   ENUM('BOOKED','NOT_BOOKED') NULL,
    issued_at     TIMESTAMP     NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT tickets_fk_trip     FOREIGN KEY (trip_id)     REFERENCES trips (id),
    CONSTRAINT tickets_fk_customer FOREIGN KEY (customer_id) REFERENCES users (id),
    CONSTRAINT tickets_fk_seller   FOREIGN KEY (seller_id)   REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE revenues (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    trip_id      BIGINT        NOT NULL,
    total_amount DECIMAL(12,2) NOT NULL,
    report_date  DATE          NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT revenues_fk_trip FOREIGN KEY (trip_id) REFERENCES trips (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE salaries (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    user_id      BIGINT        NOT NULL,
    salary_total DECIMAL(12,2) NOT NULL,
    period_month TINYINT       NOT NULL,
    period_year  SMALLINT      NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT salaries_fk_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE base_salaries (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    user_id        BIGINT        NULL,
    role           ENUM('ADMIN','COLLECTOR','CUSTOMER','DRIVER','EMPLOYEE') NULL,
    base_salary    DECIMAL(12,2) NOT NULL,
    allowance      DECIMAL(12,2) NOT NULL,
    commission     DECIMAL(12,2) NOT NULL,
    bonus          DECIMAL(12,2) NOT NULL,
    effective_from DATE          NOT NULL,
    effective_to   DATE          NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_base_salaries_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE loyalty_points (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    customer_id      BIGINT       NOT NULL,
    points           INT          NOT NULL,
    status           ENUM('REDEEMED','UNUSED') NULL,
    alocate          INT          NULL,
    requied_point    INT          NULL,
    transaction_type ENUM('EARN','REDEEM') NOT NULL,
    description      VARCHAR(255) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_loyalty_points_customer FOREIGN KEY (customer_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE base_loyalty_points (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    created_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    role_name            VARCHAR(255)  NULL,
    tickets_per_point    INT           NOT NULL,
    point_value          DECIMAL(38,2) NULL,
    max_points_per_month INT           NULL,
    start_date           DATE          NULL,
    end_date             DATE          NULL,
    status               ENUM('ACTIVE','INACTIVE') NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE loyalty_rewards (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    reward_name     VARCHAR(255) NULL,
    reward_type     ENUM('FREE_TICKET','TICKET_DISCOUNT','VOUCHER') NULL,
    description     VARCHAR(255) NULL,
    active          BIT(1)       NULL,
    points_required INT          NULL,
    status          ENUM('ACTIVE','INACTIVE') NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE audit_logs (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    user_id    BIGINT       NULL,
    action     VARCHAR(255) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_audit_logs_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
