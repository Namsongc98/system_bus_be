# K8s sandbox

Bài tập Kubernetes trên **VM2 riêng (k3s)** — thực hành Pod, Deployment, Service, probe. Không nối
Kafka/MySQL/Redis thật, không nằm trong luồng dev hay production. Chỉ dùng chung image do CI build.

Tổng quan hạ tầng: [../README.md](../README.md)

## Nội dung `booking-deployment.yaml`

| Đối tượng | Tên | Ý nghĩa |
|---|---|---|
| Deployment | `booking-replica` | 1 Pod chạy image `ghcr.io/changeme/ticket-system-booking_ticket:latest`, cổng 8081, RAM tối đa 500Mi |
| Service | `booking-replica-svc` | địa chỉ ổn định trong cluster, cổng 80 → Pod 8081 |

Deployment chọn Pod theo nhãn `app: booking-replica`; Service cũng chọn theo nhãn đó.

## Chạy

```bash
kubectl apply -f booking-deployment.yaml
kubectl get pods -l app=booking-replica
kubectl describe pod -l app=booking-replica       # sự kiện probe, OOM
kubectl port-forward svc/booking-replica-svc 8081:80
kubectl delete -f booking-deployment.yaml         # dọn
```

`changeme` trong tên image là placeholder cho owner GitHub — sửa thành owner thật. Image private trên
ghcr.io cần `imagePullSecrets`.

## Lưu ý

- Không có DB/Kafka/Redis trong sandbox → Spring có thể không khởi động hoặc health luôn `DOWN`.
- `readinessProbe` đang dùng `/actuator/health` (gồm cả mail…). Kong dùng `/actuator/health/readiness`
  — nên đổi cho giống (B29). Với sandbox không có hạ tầng, có thể dùng `/actuator/health/liveness`.
