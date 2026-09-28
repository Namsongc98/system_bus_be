# MySQL

DB chính của hệ thống: `quan-ly-ban-hang`. Schema của `manage-revenue-ticket` do Flyway tạo
(`manage-revenue-ticket/src/main/resources/db/migration/V1__…`, `V2__…`); `booking_ticket` chỉ
`validate`.

Tổng quan hạ tầng: [../README.md](../README.md)

## Chạy

```bash
docker network create ticket-system-network   # 1 lần, nếu chưa có
docker compose up -d                          # trong thư mục này
docker compose ps                             # mysql-ticket-system Up
```

Chạy **đầu tiên**, trước Kafka/Redis/Kong/app.

## Kết nối

| Ai | Địa chỉ |
|---|---|
| BE trong IntelliJ, DBeaver | `jdbc:mysql://localhost:3306/quan-ly-ban-hang` (biến `DB_URL`) |
| BE trong container (`../app`) | `jdbc:mysql://mysql-ticket-system:3306/quan-ly-ban-hang` (compose đặt sẵn) |
| Test (`ManageRevenueTicketApplicationTests`, `ActuatorExposureTest`…) | Testcontainers tự dựng `mysql:8.0` riêng — không dùng DB này |

User `root`, mật khẩu = `MYSQL_ROOT_PASSWORD` trong `docker-compose.yml`. BE đọc mật khẩu từ biến
`DB_PASSWORD` → hai giá trị phải trùng.

## Kiểm tra

```bash
docker exec -it mysql-ticket-system mysql -uroot -p -e "SHOW DATABASES;"
docker exec -it mysql-ticket-system mysql -uroot -p quan-ly-ban-hang \
  -e "SELECT version, description, success FROM flyway_schema_history;"
```

## Dữ liệu

Không có volume có tên — image tự tạo volume ẩn danh cho `/var/lib/mysql`:

- `docker compose up -d` / recreate: **giữ** dữ liệu.
- `docker compose down` rồi `up`: container + volume mới → **DB trống**, phải chạy lại Flyway
  (khởi động `manage-revenue-ticket`). Volume cũ vẫn còn (`docker volume ls`) nhưng không được gắn.

## Lỗi thường gặp

| Hiện tượng | Nguyên nhân / xử lý |
|---|---|
| BE báo `Access denied for user 'root'` | `DB_PASSWORD` khác `MYSQL_ROOT_PASSWORD` |
| BE báo `Communications link failure` | container chưa chạy; hoặc BE container không cùng `ticket-system-network` |
| Flyway báo checksum mismatch | đã sửa file migration đã chạy — không sửa file `V<n>` cũ, tạo `V<n+1>` mới |
| Cổng 3306 bị chiếm | MySQL cài sẵn trên máy đang chạy — tắt nó hoặc đổi cổng host (`"3307:3306"`) |

## Tồn đọng (B29)

- Mật khẩu root hardcode trong file đã commit — chỉ chấp nhận cho dev; prod cần lấy từ biến môi trường.
- Chưa có volume có tên → mất dữ liệu khi `down`/`up`.
