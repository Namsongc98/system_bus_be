#!/bin/sh
# minio-init — chạy 1 lần (chạy lại được) trong container pgsty/mc, mount qua docker-compose.yml.
#   docker compose --env-file ../../.env --env-file ../../.env.minio --profile init run --rm minio-init
#
# Việc làm: dùng tài khoản root tạo bucket + user riêng cho BE, user này CHỈ có quyền trên bucket đó
# → lộ MINIO_ACCESS_KEY/SECRET_KEY của app không mất quyền admin, không đọc được bucket khác.
# Biến môi trường (compose truyền vào): MINIO_ROOT_USER, MINIO_ROOT_PASSWORD (từ .env.minio),
#   MINIO_ACCESS_KEY, MINIO_SECRET_KEY, MINIO_BUCKET (từ .env).
# Thành công: log kết thúc bằng `mc ls local` có bucket, container Exited (0). Lỗi → Exited (1).

# -e: một lệnh lỗi → dừng cả script (không báo thành công giả)
# -u: dùng biến chưa đặt → lỗi (bắt gõ nhầm tên biến)
set -eu

# ── 1. Kiểm tra đầu vào ─────────────────────────────────────────────────────────
# `:` là lệnh không làm gì; `${VAR:?msg}` dừng script với msg nếu VAR rỗng/chưa đặt.
: "${MINIO_ROOT_USER:?thiếu MINIO_ROOT_USER trong .env.minio}" "${MINIO_ROOT_PASSWORD:?thiếu MINIO_ROOT_PASSWORD trong .env.minio}"
: "${MINIO_ACCESS_KEY:?thiếu MINIO_ACCESS_KEY trong .env}" "${MINIO_SECRET_KEY:?thiếu MINIO_SECRET_KEY trong .env}"
MINIO_BUCKET="${MINIO_BUCKET:-user-avatars}"

# Chặn sớm, trước khi tạo bucket/policy. MinIO từ chối user app trùng admin và secret < 8 ký tự,
# nhưng chỉ báo sau khi đã làm nửa chừng. Thông báo chỉ nêu tên biến, KHÔNG in giá trị key.
fail() { echo "minio-init: $1" >&2; exit 1; }
[ "$MINIO_ACCESS_KEY" != "$MINIO_ROOT_USER" ] || fail "MINIO_ACCESS_KEY phải khác MINIO_ROOT_USER (key app ≠ admin) — sửa .env"
[ "$MINIO_SECRET_KEY" != "$MINIO_ROOT_PASSWORD" ] || fail "MINIO_SECRET_KEY phải khác MINIO_ROOT_PASSWORD — sửa .env"
[ "${#MINIO_SECRET_KEY}" -ge 8 ] || fail "MINIO_SECRET_KEY cần ≥ 8 ký tự — sửa .env"

POLICY="app-$MINIO_BUCKET"

# ── 2. Đăng nhập bằng root ──────────────────────────────────────────────────────
# Lưu kết nối tên `local` → các lệnh sau viết `local/...`. `minio` = tên container trên ticket-system-network.
mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"

# ── 3. Tạo bucket ───────────────────────────────────────────────────────────────
# --ignore-existing: bucket đã có thì bỏ qua (để chạy lại không lỗi).
mc mb --ignore-existing "local/$MINIO_BUCKET"

# ── 4. Policy chỉ cho bucket này ────────────────────────────────────────────────
# Không dùng policy có sẵn `readwrite` vì nó cho quyền MỌI bucket.
# Statement 1: liệt kê bucket. Statement 2: đọc/ghi/xoá object bên trong (`bucket/*`). Không gì khác.
cat > /tmp/app-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:ListBucket", "s3:GetBucketLocation"],
      "Resource": ["arn:aws:s3:::$MINIO_BUCKET"]
    },
    {
      "Effect": "Allow",
      "Action": ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"],
      "Resource": ["arn:aws:s3:::$MINIO_BUCKET/*"]
    }
  ]
}
EOF
mc admin policy create local "$POLICY" /tmp/app-policy.json   # chưa có → tạo, đã có → ghi đè

# ── 5. User cho app ─────────────────────────────────────────────────────────────
mc admin user add local "$MINIO_ACCESS_KEY" "$MINIO_SECRET_KEY"   # đã có → cập nhật secret

# ── 6. Gắn policy cho user ──────────────────────────────────────────────────────
# `attach` báo lỗi nếu đã gắn rồi → gỡ trước rồi gắn lại để chạy lại được.
# Gỡ cả `readwrite` mà bản init cũ từng gắn (quyền mọi bucket).
# `|| true`: gỡ thứ chưa gắn sẽ lỗi — bỏ qua, không để `set -e` dừng script.
mc admin policy detach local readwrite --user "$MINIO_ACCESS_KEY" >/dev/null 2>&1 || true
mc admin policy detach local "$POLICY" --user "$MINIO_ACCESS_KEY" >/dev/null 2>&1 || true
mc admin policy attach local "$POLICY" --user "$MINIO_ACCESS_KEY"

# ── 7. Bằng chứng ───────────────────────────────────────────────────────────────
mc ls local
