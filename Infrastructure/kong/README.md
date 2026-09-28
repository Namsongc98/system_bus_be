# Kong API Gateway

Điểm vào **duy nhất** của FE: `VITE_KONG_API_URL=http://localhost:8000/api`. Kong chạy chế độ
DB-less — toàn bộ route/upstream nằm trong 1 file YAML.

Tổng quan hạ tầng: [../README.md](../README.md)

## File

| File | Vai trò |
|---|---|
| `docker-compose.yml` | Container Kong: cổng, mạng, mount `kong.yml` |
| `kong.yml` | Route/upstream khi BE chạy bằng **container** (target = tên container) |
| `docker-compose.dev.yml` | Override: mount `kong.dev.yml` thay `kong.yml`, thêm `host.docker.internal` |
| `kong.dev.yml` | Như `kong.yml`, chỉ khác target = `host.docker.internal:8081/8082` (BE chạy **IntelliJ**) |

`kong.yml` và `kong.dev.yml` phải giữ giống nhau, trừ 2 dòng `target`.

## Chạy

```bash
# BE chạy trong IntelliJ (code/debug)
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --force-recreate

# BE chạy bằng container (../app)
docker compose up -d --force-recreate
```

Sửa `kong.yml` / `kong.dev.yml` → phải `--force-recreate` (Kong chỉ đọc file lúc khởi động).
Kiểm cú pháp trước:

```bash
docker run --rm -v "$PWD/kong.yml:/tmp/kong.yml:ro" -e KONG_DATABASE=off kong:3.9 kong config parse /tmp/kong.yml
```

## Định tuyến

| Path | Upstream | Target (`kong.yml`) | Target (`kong.dev.yml`) |
|---|---|---|---|
| `/api/booking…` | `booking-upstream` | `booking-container:8081` | `host.docker.internal:8081` |
| `/api/…` còn lại | `revenue-upstream` | `manage-revenue-ticket:8082` | `host.docker.internal:8082` |

`strip_path: false` → BE nhận nguyên path `/api/...`. Path dài hơn được ưu tiên.

Plugin: `rate-limiting` (booking 60/phút, revenue 120/phút, theo IP), `correlation-id` (header
`X-Request-ID`), `request-size-limiting` 8MB. **Không** có plugin CORS — header CORS do BE trả
(`common-library/.../security/SecurityConfig.java`).

## Health check (Kong tự kiểm BE)

- `GET /actuator/health/readiness` mỗi 10s (đang khoẻ) / 5s (đang lỗi), `timeout: 5`.
- Readiness = `readinessState, db, redis` (`application.properties` của 2 service) → DB hoặc Redis
  sập thì Kong ngừng gửi request vào instance đó.
- Đánh dấu lỗi sau 3 lỗi HTTP, 2 lỗi TCP hoặc 3 timeout; khoẻ lại sau 2 lần thành công.
- `/actuator/health/**` mở không cần token (Spring Security `permitAll`); chi tiết chỉ ADMIN xem.

## Admin API (chỉ máy local, `127.0.0.1:8001`)

```bash
curl -s http://127.0.0.1:8001/upstreams/revenue-upstream/health    # HEALTHY / UNHEALTHY + IP đang dùng
curl -s http://127.0.0.1:8001/upstreams/booking-upstream/health
curl -s http://127.0.0.1:8001/routes | head
docker logs kong-gateway --since 2m 2>&1 | grep -i healthcheck     # lý do UNHEALTHY
```

## Lỗi thường gặp

| Hiện tượng | Nguyên nhân / xử lý |
|---|---|
| Trình duyệt báo CORS; `curl` qua Kong ra `503 failure to get a peer from the ring-balancer` | không upstream nào khoẻ — xem các dòng dưới |
| BE chạy IntelliJ nhưng Kong UNHEALTHY | Kong đang dùng `kong.yml` → chạy lại với `-f docker-compose.dev.yml` |
| BE container nhưng Kong UNHEALTHY | Kong đang dùng `kong.dev.yml`; hoặc container BE không cùng `ticket-system-network` |
| Log `failed to receive status line` | probe timeout — readiness BE chậm/treo |
| Log `unhealthy TCP increment` | không kết nối được cổng BE (BE tắt, sai IP) |
| Vừa recreate BE, Kong vẫn giữ IP cũ | đợi 10-20s (`KONG_DNS_VALID_TTL=10`) hoặc recreate Kong |
| Readiness trả 404 | BE thiếu `management.endpoint.health.probes.enabled=true` |

## Production

Trước Kong có `../tls` (NGINX HTTPS → `kong:8000`). Tồn đọng (B29): Kong chưa cấu hình
`KONG_TRUSTED_IPS` / `KONG_REAL_IP_HEADER` nên sau NGINX, rate-limit tính theo IP của NGINX.
