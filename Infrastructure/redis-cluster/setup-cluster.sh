# ⚠ Lệnh này KHÔNG reset được cluster hiện tại (B29): dữ liệu thật nằm ở ./data/node-1..6
#   (volume trong docker-compose.yml), không phải ./redis-data. Thư mục ./redis-data không tồn tại.
# Tạo cluster sau khi 6 node đã chạy: xem CLAUDE.md
#   docker exec -it redis-1 redis-cli --cluster create 172.30.0.11:7001 … 172.30.0.16:7006 --cluster-replicas 1
# Reset hẳn: docker compose down, xoá ./data/node-*, up lại rồi chạy lệnh create ở trên (mất toàn bộ cache/session).
rm -rf ./redis-data
