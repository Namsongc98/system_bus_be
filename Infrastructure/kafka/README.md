# Kafka

Cluster 3 broker chế độ **KRaft** (không Zookeeper). Topic `order-events`: `booking_ticket` gửi
`BookingEvent`; `manage-revenue-ticket` nhận bằng 3 consumer group độc lập (mỗi group nhận đủ mọi message):

| Consumer group | Listener |
|---|---|
| `booking-group` | `RevenueConsumer` |
| `revenue-group` | `RevenueService` |
| `email-service-group` | `EmailConfirmTicketConsumer` |

Kafka UI xem topic và consumer lag.

Tổng quan hạ tầng: [../README.md](../README.md)

## Chạy

```bash
docker compose up -d        # trong thư mục này — project "kafka", tạo mạng kafka_kafka-net
docker compose ps           # broker1, broker2, broker3, kafka-ui Up
```

Kafka UI: http://localhost:9000

## Kết nối — mỗi broker có 3 listener

| Listener | Địa chỉ | Dùng cho |
|---|---|---|
| `PLAINTEXT` | `broker1:29092`, `broker2:29092`, `broker3:29092` | container ↔ container: broker khác, `../app`, kafka-ui (mạng `kafka_kafka-net`) |
| `CONTROLLER` | `brokerN:29093` | chỉ giữa các broker để bầu controller |
| `PLAINTEXT_HOST` | `localhost:9092`, `localhost:9093`, `localhost:9094` | BE chạy trong IntelliJ (cổng publish) |

`KAFKA_ADVERTISED_LISTENERS` là địa chỉ broker **trả về** cho client sau lần kết nối đầu. Client vào
qua listener nào nhận địa chỉ của listener đó — nên container không bao giờ nhận `localhost`, IntelliJ
không bao giờ nhận `broker1`.

| BE chạy ở | `spring.kafka.bootstrap-servers` |
|---|---|
| IntelliJ | `localhost:9092,localhost:9093,localhost:9094` (`application.properties`) |
| Container | `broker1:29092,broker2:29092,broker3:29092` (`../app/docker-compose.yml`) |

## Độ bền

- Topic nội bộ (offset, transaction) nhân bản 3, `min ISR = 2` → 1 broker chết vẫn chạy.
- Dữ liệu ở volume có tên `kafka_broker1-data`… → còn sau `down`/`up`; xoá hẳn: `docker compose down -v`.
- `CLUSTER_ID` giống nhau ở 3 broker. Đổi `CLUSTER_ID` thì phải xoá volume, nếu không broker không lên.

## Kiểm tra

```bash
docker exec broker1 kafka-topics --bootstrap-server broker1:29092 --list
docker exec broker1 kafka-consumer-groups --bootstrap-server broker1:29092 --list
docker exec broker1 kafka-consumer-groups --bootstrap-server broker1:29092 --describe --group booking-group   # lag từng partition
```

## Lỗi thường gặp

| Hiện tượng | Nguyên nhân / xử lý |
|---|---|
| BE container log `UnknownHostException: broker1` | container không join `kafka_kafka-net` (xem `../app/docker-compose.yml`, `networks`) |
| BE IntelliJ log `Connection to node … (broker1/…:29092) could not be established` | BE dùng `broker1:29092` thay vì `localhost:9092` |
| Broker khởi động rồi thoát, log về `cluster ID` | volume cũ của cluster khác — `docker compose down -v` rồi `up` (mất dữ liệu topic) |
| Chạy IntelliJ và container BE cùng lúc | 2 consumer cùng group chia partition → message đi lung tung; tắt 1 bên |

## Production

`../docker-compose.prod.override.yml`: mỗi broker `mem_limit: 700m`, heap 400m; kafka-ui tắt
(bật khi cần: `--profile debug`).
