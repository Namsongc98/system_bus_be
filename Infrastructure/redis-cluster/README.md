# Redis Cluster

6 node Redis 7 (3 master + 3 replica): cache (`spring.cache.type=redis`), session
(`spring.session.store-type=redis`) và giữ ghế (Phase 2). Cả 2 service Spring dùng chung,
cấu hình trong `common-library/src/main/java/com/ticket_system/common/config/RedisConfig.java`.

Tổng quan hạ tầng: [../README.md](../README.md)

## Chạy

```bash
docker compose up -d        # trong thư mục này — tạo mạng redis-cluster-net 172.30.0.0/16
docker compose ps           # redis-1 … redis-6 Up
```

Tạo cluster **1 lần** (sau lần `up` đầu tiên, hoặc sau khi xoá `./data`):

```bash
docker exec -it redis-1 redis-cli --cluster create \
  172.30.0.11:7001 172.30.0.12:7002 172.30.0.13:7003 \
  172.30.0.14:7004 172.30.0.15:7005 172.30.0.16:7006 \
  --cluster-replicas 1
```

Sơ đồ cluster lưu trong `./data/node-N/nodes.conf` → các lần `up` sau không cần tạo lại.

## Kết nối

Mỗi node có IP cố định (`ipv4_address`) và tự giới thiệu IP đó cho client (`--cluster-announce-ip`).
Client nối vào 1 node, nhận sơ đồ cluster với các IP `172.30.0.11-16`, rồi nối thẳng tới node giữ key.

| BE chạy ở | `spring.redis.cluster.nodes` | Đi tới Redis bằng |
|---|---|---|
| IntelliJ | `172.30.0.11:7001,…,172.30.0.16:7006` (`application.properties` của 2 service) | `RedisConfig` đổi `172.x` → `127.0.0.1` (`app.redis.map-docker-ips-to-localhost=true`, mặc định) rồi qua cổng publish 7001-7006 |
| Container (`../app`) | `redis-1:7001,…,redis-6:7006` (`SPRING_REDIS_CLUSTER_NODES`) | thẳng IP `172.30.x` qua mạng `redis-cluster-net` (`APP_REDIS_MAP_DOCKER_IPS_TO_LOCALHOST=false`) |

`RedisConfig` đọc `spring.redis.cluster.nodes` — **không** phải `spring.data.redis.*` (các key
`spring.data.redis.*` trong `application.properties` hiện không có tác dụng, B27).

## Kiểm tra

```bash
docker exec redis-1 redis-cli -p 7001 cluster info | head -3     # cluster_state:ok
docker exec redis-1 redis-cli -p 7001 cluster nodes              # 3 master + 3 slave, IP 172.30.0.x
curl -s localhost:8082/actuator/health/readiness                  # BE IntelliJ: UP (gồm redis)
```

## Reset cluster (mất toàn bộ cache/session)

```bash
docker compose down
rm -rf ./data/node-*        # dữ liệu runtime — chỉ xoá khi thật sự muốn reset
docker compose up -d
# rồi chạy lại lệnh --cluster create ở trên
```

`setup-cluster.sh` hiện **không** reset được (xoá nhầm `./redis-data`, B29).

## Lỗi thường gặp

| Hiện tượng | Nguyên nhân / xử lý |
|---|---|
| BE container log `Unable to connect to /127.0.0.1:700x` | container đang đổi `172.x` → `127.0.0.1`: thiếu `APP_REDIS_MAP_DOCKER_IPS_TO_LOCALHOST=false` |
| BE IntelliJ không tới Redis, nodes là `173.20.0.x` hoặc IP khác `172.30.x` | sai dải mạng (B28 đã sửa cho `booking_ticket`) |
| `cluster_state:fail` / `CLUSTERDOWN` | chưa chạy `--cluster create`, hoặc quá nửa master chết |
| Readiness BE `DOWN`, Kong UNHEALTHY | readiness gồm `redis` — Redis không tới được thì Kong ngừng định tuyến |
| Compose báo mạng `redis-cluster-net` thiếu nhãn | mạng được tạo tay trước (hướng dẫn cũ) — `docker network rm redis-cluster-net` rồi `up` (B29) |

## File khác trong thư mục

- `Dockerfile` — **không dùng**: Dockerfile Spring Boot cũ nằm nhầm chỗ (B29).
- `setup-cluster.sh` — không hoạt động như tên gọi (B29).
- `redis-cluster.tmpl` — rỗng.
- `data/` — dữ liệu runtime của 6 node; không sửa tay, không commit.

## Production

`../docker-compose.prod.override.yml`: mỗi node `mem_limit: 150m`. Redis chạy không mật khẩu
(`--protected-mode no`) — chỉ an toàn khi cổng 7001-7006 không mở ra Internet.
