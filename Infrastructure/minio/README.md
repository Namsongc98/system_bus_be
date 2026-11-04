# MinIO — object storage (avatar, file upload)

S3-compatible storage cho BE. Biến cấu hình: `MINIO_URL`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET`
(`application.properties` của `manage-revenue-ticket`) — nằm trong `ticket-system/.env` (không commit), tuỳ chọn
`MINIO_IMAGE`, `MINIO_MC_IMAGE` cũng ở đó.

**`MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD` nằm RIÊNG trong `ticket-system/.env.minio`** (không commit, git-ignore
theo `.env.*`, `chmod 600`). Lý do: `../app/docker-compose.yml` nạp **cả** `ticket-system/.env` vào container BE
(`env_file`), nên root key để ở đó sẽ vào môi trường của `manage-revenue-ticket` và `booking-container` — mất tác
dụng tách key admin / key app (4.1a review R4). Chỉ compose MinIO đọc `.env.minio`.

> ⚠ **2026-10-07:** `minio/minio` và `minio/mc` không còn tải được (Docker Hub: pull access denied; quay.io:
> no such manifest). Compose đã chuyển sang fork cộng đồng `pgsty/minio:RELEASE.2026-08-04T00-00-00Z` và
> `pgsty/mc:RELEASE.2026-09-16T00-00-00Z` (Pigsty/Silo, AGPL — không phải official/verified publisher).

`minio-init` (chạy lại được, script trong [`init.sh`](init.sh) — có chú thích từng bước): tạo bucket, tạo policy `app-<bucket>` chỉ cho List/Get/Put/Delete trên đúng
bucket đó, tạo user app và gắn policy này (gỡ `readwrite` nếu bản init cũ đã gắn). Bước nào lỗi → exit ≠ 0.

Biến bắt buộc:

| Biến | Kiểm tra ở đâu | Thiếu thì |
|---|---|---|
| `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD` (`.env.minio`) | compose (`${VAR:?}` ở cả `minio` và `minio-init`) | mọi lệnh `docker compose` báo lỗi ngay — nếu không chặn, MinIO lặng lẽ chạy với `minioadmin/minioadmin` |
| `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY` | shell trong `minio-init` | chỉ `minio-init` báo lỗi; `up -d` server vẫn chạy (compose chỉ cảnh báo biến rỗng) |

Không đặt `${VAR:?}` cho biến của `minio-init` ở cấp compose: compose nội suy cả file, kể cả service thuộc
profile chưa bật, nên sẽ chặn luôn `up -d` của server.

## Chạy

```bash
cd ticket-system/Infrastructure/minio
docker compose --env-file ../../.env --env-file ../../.env.minio up -d
docker compose --env-file ../../.env --env-file ../../.env.minio --profile init run --rm minio-init   # 1 lần: bucket + user app
docker ps --filter name=minio        # STATUS phải có (healthy)
```

| Ai gọi | URL |
|---|---|
| BE trong container (`../app`) | `http://minio:9000` |
| BE trong IntelliJ / mvn | `http://localhost:9010` (đặt `MINIO_URL` trong `.env`; host 9000 là kafka-ui) |
| Console (người) | `http://localhost:9011` — trên VM: `ssh -L 9011:127.0.0.1:9011 <vm>` |

## Lưu ý production

- MinIO Community Edition đã ngừng phát hành image chính thức (10/2025) và repo đã archive → image không còn bản vá.
  Giữ cổng chỉ ở `127.0.0.1` + mạng Docker, không route qua Kong/NGINX ra Internet.
- Pin image: sau lần pull đầu, lấy digest bằng `docker image inspect pgsty/minio:RELEASE.2026-08-04T00-00-00Z --format '{{index .RepoDigests 0}}'`
  rồi đặt `MINIO_IMAGE=<digest>` trong `.env`. Kiểm 2026-10-13 (`docker buildx imagetools inspect <image>`): cả 2 image
  có `linux/arm64` (chạy được trên VM Ampere); digest index `pgsty/minio` = `sha256:b6bfe7239bfc83fb90d31612d9704d86039dd714f7904b3f1ad68f211e602372`,
  `pgsty/mc` = `sha256:cfc83108c3abb371f8fb84d99c1fdc88f8c237e022409b0081fb7c0a3be634dd`.
- Trả file cho trình duyệt: **đã chốt ở 4.1a** — BE stream file qua `GET /api/profile/avatar` (có JWT), không
  presigned URL, MinIO không public. Hợp đồng API: `.claude/references/backend/api/profile-avatar/endpoints.md`.
- Dự phòng: Garage hoặc SeaweedFS (S3-compatible, có image ARM64). BE dùng API S3 chuẩn thì chỉ đổi URL + key.
- Backup: volume `minio_minio-data` nằm trên VM → đưa vào job backup cùng MySQL (`mc mirror` ra storage ngoài VM).
