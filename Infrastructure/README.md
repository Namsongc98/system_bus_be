# Hạ tầng (Infrastructure)

Tài liệu cho người chạy hệ thống ở máy dev và trên VM production. Mỗi thư mục có `README.md` riêng;
file này là tổng quan: các thành phần nối với nhau thế nào, chạy theo thứ tự nào, xử lý lỗi thường gặp.

| Thư mục | Thành phần | Tài liệu |
|---|---|---|
| `mysql/` | MySQL 8 — DB `quan-ly-ban-hang` | [mysql/README.md](mysql/README.md) |
| `kafka/` | Kafka 3 broker (KRaft) + Kafka UI | [kafka/README.md](kafka/README.md) |
| `redis-cluster/` | Redis Cluster 6 node | [redis-cluster/README.md](redis-cluster/README.md) |
| `kong/` | Kong API Gateway — điểm vào duy nhất của FE | [kong/README.md](kong/README.md) |
| `app/` | 2 service Spring Boot chạy bằng container | [app/README.md](app/README.md) |
| `tls/` | NGINX + Let's Encrypt trước Kong (chỉ prod) | [tls/README.md](tls/README.md) |
| `k8s-sandbox/` | Bài tập Kubernetes (k3s, VM riêng) | [k8s-sandbox/README.md](k8s-sandbox/README.md) |
| `docker-compose.prod.override.yml` | Giới hạn RAM trên VM prod 6GB | mục [Production](#production) bên dưới |

## Sơ đồ

```
Trình duyệt (FE :5173, VITE_KONG_API_URL=http://localhost:8000/api)
   │                                   (prod: Internet :443 → tls/ nginx → kong:8000)
   ▼
Kong :8000 ── /api/booking → booking-upstream  → booking_ticket        :8081
           └─ /api/*       → revenue-upstream  → manage-revenue-ticket :8082
                                                   │
                     ┌─────────────────────────────┼──────────────────────────┐
                     ▼                             ▼                          ▼
              MySQL :3306                  Kafka 29092 / 9092-9094     Redis 7001-7006
              (quan-ly-ban-hang)           (topic order-events)        (cache, session)
```

## Mạng Docker

Các compose nằm ở thư mục khác nhau, nối với nhau qua 3 mạng. Container chỉ gọi được tên container
khác khi **cùng mạng**.

| Mạng | Tạo bởi | Thành viên |
|---|---|---|
| `ticket-system-network` | tay: `docker network create ticket-system-network` | mysql, kong, app, tls |
| `kafka_kafka-net` | `kafka/docker-compose.yml` (tên = `<project kafka>_kafka-net`) | broker1-3, kafka-ui, app |
| `redis-cluster-net` (172.30.0.0/16) | `redis-cluster/docker-compose.yml` | redis-1..6 (IP cố định 172.30.0.11-16), app |

## Thứ tự chạy (lần đầu)

```bash
docker network create ticket-system-network            # 1 lần
cd ticket-system/Infrastructure
(cd mysql && docker compose up -d)
(cd kafka && docker compose up -d)
(cd redis-cluster && docker compose up -d)              # rồi tạo cluster 1 lần — xem redis-cluster/README.md
(cd kong && docker compose up -d)                       # hoặc chế độ dev, xem bên dưới
(cd app && docker compose up -d --build)                # chỉ khi BE chạy bằng Docker
```

## Hai chế độ chạy BE — chọn 1, không chạy song song

| | BE chạy trong IntelliJ (code/debug) | BE chạy bằng container (tích hợp/triển khai) |
|---|---|---|
| Kong | `cd kong && docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --force-recreate` | `cd kong && docker compose up -d --force-recreate` |
| Kong dùng | `kong.dev.yml` → `host.docker.internal:8081/8082` | `kong.yml` → `booking-container:8081`, `manage-revenue-ticket:8082` |
| BE | Run 2 Application trong IntelliJ (profile mặc định) | `cd app && docker compose up -d --build` |
| BE nối DB/Kafka/Redis | `localhost:3306`, `localhost:9092-9094`, `172.30.0.x` → `127.0.0.1:7001-7006` | `mysql-ticket-system`, `broker1-3:29092`, `redis-1..6` |
| Log BE | cửa sổ IntelliJ | `docker logs manage-revenue-ticket` |

Chạy cả hai cùng lúc: 2 bản BE tranh chung Redis và Kafka consumer group → lỗi khó đoán.

## Cổng trên máy host

| Cổng | Thành phần |
|---|---|
| 8000 | Kong proxy (FE gọi) |
| 127.0.0.1:8001 | Kong Admin API (chỉ máy local) |
| 8081 / 8082 | BE trong IntelliJ (container BE không publish cổng) |
| 3306 | MySQL |
| 9092 / 9093 / 9094 | Kafka (listener cho host) |
| 9000 | Kafka UI |
| 7001-7006, 17001-17006 | Redis (client, bus cluster) |
| 80 / 443 | NGINX TLS (chỉ prod) |

## Kiểm tra nhanh

```bash
curl -s http://127.0.0.1:8001/upstreams/revenue-upstream/health   # "health": "HEALTHY"?
curl -s http://127.0.0.1:8001/upstreams/booking-upstream/health
curl -i -X OPTIONS http://localhost:8000/api/auth/login \
  -H 'Origin: http://localhost:5173' -H 'Access-Control-Request-Method: POST'   # 200 + Access-Control-Allow-Origin
```

## Lỗi thường gặp

**Trình duyệt báo `blocked by CORS policy ... No 'Access-Control-Allow-Origin'`** — gần như luôn là
Kong trả 503 (không có BE khoẻ), không phải lỗi CORS. Header CORS do BE trả; Kong lỗi thì không có.
Kiểm theo thứ tự:

1. Kong đúng chế độ chưa? BE chạy IntelliJ mà Kong dùng `kong.yml` (hoặc ngược lại) → 503.
2. `…/upstreams/*/health` UNHEALTHY → BE có chạy không, readiness có `UP` không
   (`curl localhost:8082/actuator/health/readiness` với IntelliJ).
3. Readiness `DOWN` → DB/Redis BE không tới được (xem README từng thành phần).
4. Vừa recreate BE container → đợi 10-20 giây (Kong phân giải lại DNS, `KONG_DNS_VALID_TTL=10`).

Các lỗi hạ tầng đã gặp và cách sửa: `.claude/docs/plan/screen-feature-plan.md` mục B25; tồn đọng: B27, B29.

## Production

VM 6GB chạy toàn bộ bằng Docker (chế độ container) + `tls/`. Ghép giới hạn RAM bằng `-f`:

```bash
docker compose -f kafka/docker-compose.yml -f docker-compose.prod.override.yml up -d
```

`docker-compose.prod.override.yml` gộp theo **tên service** (`broker1`, `mysql`, `kong`,
`redis-node-1..6`, `booking-service`, `manage-revenue-ticket`) — chỉ ghép cùng compose gốc có service
đó. `kafka-ui` bị tắt (profile `debug`). CI (`ticket-system/.github/workflows/ci-cd.yml`) build và push
image khi push nhánh `main`.

## Secret

`app/` đọc `ticket-system/.env` (không commit). Tên biến (không giá trị):
`.claude/references/backend/environment-variable-names.md`. Tạo file, `chmod 600`, khởi động service
cần secret từ terminal riêng.
