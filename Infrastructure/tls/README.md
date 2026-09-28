# TLS — NGINX + Let's Encrypt (chỉ production)

NGINX chỉ **giải mã HTTPS** rồi chuyển nguyên request sang Kong. Không route, không rate-limit —
việc đó Kong làm. Máy dev không cần thư mục này (FE gọi thẳng Kong `:8000`).

```
Internet :443 (HTTPS) → tls-proxy (nginx) → http://kong:8000 → BE
Internet :80          → redirect HTTPS (trừ /.well-known/acme-challenge/ cho certbot)
```

Tổng quan hạ tầng: [../README.md](../README.md)

## Điều kiện

- VM có tên miền trỏ về IP công khai; cổng 80 và 443 mở.
- Kong đã chạy (cùng `ticket-system-network`) **trước** NGINX — hai compose khác file nên không dùng
  `depends_on`; NGINX gọi Kong bằng tên service `kong`.
- Thay `your-domain.com` (3 chỗ) trong `nginx.conf` bằng tên miền thật.

## Lần đầu: xin chứng chỉ

NGINX cần chứng chỉ để lên cổng 443, còn certbot cần NGINX phục vụ cổng 80 để xác thực. Cách làm:

1. Tạm comment khối `server { listen 443 … }` trong `nginx.conf`.
2. `docker compose up -d nginx`
3. Xin chứng chỉ:
   ```bash
   docker compose run --rm certbot certonly --webroot -w /var/www/certbot -d <tên-miền> --email <email> --agree-tos
   ```
4. Bỏ comment khối 443, rồi `docker compose restart nginx`.

Chứng chỉ nằm ở `./certbot/conf` (không commit).

## Gia hạn (90 ngày/lần)

```bash
docker compose run --rm certbot renew
docker compose exec nginx nginx -s reload
```

Nên đặt cron trên VM chạy 2 lệnh này hằng ngày.

## Kiểm tra

```bash
docker compose exec nginx nginx -t                  # cú pháp nginx.conf
curl -I http://<tên-miền>                           # 301 → https
curl -I https://<tên-miền>/api/auth/me              # 401 từ BE = đi thông tới Kong và BE
```

## Timeout

`proxy_read_timeout` / `proxy_send_timeout` 60s khớp `read_timeout` / `write_timeout` 60000ms của
service trong `../kong/kong.yml`. Đổi một bên thì đổi cả bên kia.

## Tồn đọng (B29)

NGINX gửi `X-Forwarded-For`, nhưng Kong chưa cấu hình `KONG_TRUSTED_IPS` + `KONG_REAL_IP_HEADER`
→ Kong thấy mọi request đến từ IP của NGINX → rate-limit theo IP thành 1 hạn mức chung cho mọi người.
