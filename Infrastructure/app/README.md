# App — 2 service Spring Boot chạy bằng container

`booking_ticket` (container `booking-container`, cổng 8081) và `manage-revenue-ticket` (container
`manage-revenue-ticket`, cổng 8082) chạy như trên production. Dùng khi kiểm tích hợp; khi code/debug
thì chạy BE trong IntelliJ và bật Kong chế độ dev (xem [../kong/README.md](../kong/README.md)).

Tổng quan hạ tầng: [../README.md](../README.md)

## Điều kiện

1. MySQL, Kafka, Redis Cluster đang chạy; Kong chạy **chế độ container** (`cd ../kong && docker compose up -d --force-recreate`).
2. File `ticket-system/.env` có đủ secret (tên biến: `.claude/references/backend/environment-variable-names.md`),
   `chmod 600`. Không commit, không dán nội dung vào chat.
3. BE trong IntelliJ đã tắt (không chạy song song).

## Chạy

```bash
docker compose up -d --build        # trong thư mục này, từ terminal riêng (cần secret)
docker compose ps
docker logs -f manage-revenue-ticket
```

`--build` build lại image từ source (Dockerfile multi-stage: Maven build jar trong image, chạy bằng
user `app` không phải root). Build context là `ticket-system/`; `ticket-system/.dockerignore` loại
`.env`, `target/`, `Infrastructure/` khỏi context.

## Cấu hình container (khác khi chạy IntelliJ)

| Biến | Giá trị | Vì sao |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` | nạp `application-prod.properties` (`ddl-auto=validate`, log) |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://mysql-ticket-system:3306/quan-ly-ban-hang` | tên container MySQL |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `broker1:29092,…` | listener nội bộ Kafka |
| `SPRING_REDIS_CLUSTER_NODES` | `redis-1:7001,…` | `RedisConfig` đọc `spring.redis.cluster.nodes` |
| `APP_REDIS_MAP_DOCKER_IPS_TO_LOCALHOST` | `false` | dùng thẳng IP `172.30.x` của cluster |

3 mạng: `ticket-system-network` (Kong, MySQL), `redis-cluster-net`, `kafka_kafka-net`.
Container **không publish cổng** — chỉ Kong gọi vào.

`container_name` phải khớp `target` trong `../kong/kong.yml`. Không đổi tên container.

## Kiểm tra

```bash
docker run --rm --network ticket-system-network curlimages/curl -s \
  http://manage-revenue-ticket:8082/actuator/health/readiness        # {"status":"UP"}
curl -s http://127.0.0.1:8001/upstreams/revenue-upstream/health       # HEALTHY
```

## Lỗi thường gặp

| Hiện tượng | Nguyên nhân / xử lý |
|---|---|
| `env file …/.env not found` | chưa tạo `ticket-system/.env` |
| `container name "/manage-revenue-ticket" is already in use` | container cũ còn — `docker ps -a`, kiểm tra rồi `docker rm <id>` |
| Build lỗi `COPY … not found` | chạy build ở sai context — luôn qua compose này hoặc `docker build -f <module>/Dockerfile .` từ `ticket-system/` |
| Readiness `DOWN` | log BE: Redis (`127.0.0.1:700x` → thiếu biến map), MySQL, … |
| Kong UNHEALTHY sau recreate | IP container đổi — đợi 10-20s (Kong phân giải lại DNS) |

## Production

`../docker-compose.prod.override.yml`: mỗi service `mem_limit: 500m`, heap `-Xmx350m`. Image do CI
build và push lên `ghcr.io/<owner>/ticket-system-<service>:latest` khi push nhánh `main`.
Tồn đọng (B27): chưa có `depends_on`, CI không kiểm compose/Kong, chưa có test cho `booking_ticket`.
