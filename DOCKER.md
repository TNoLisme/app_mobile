# Docker setup

Project này có thể dockerize phần backend FastAPI và SQL Server. Ứng dụng Android vẫn build/chạy ngoài Docker, nhưng sẽ gọi backend qua cổng `8000` đã publish từ container.

Lưu ý: SQL Server của Docker được publish ra host bằng cổng `11433` để tránh xung đột với một SQL Server local khác đang dùng `1433`.

## Chạy nhanh

1. Điều chỉnh giá trị trong `.env.docker` nếu cần.
2. Chạy:

```powershell
docker compose --env-file .env.docker up --build
```

3. Kiểm tra API:

```powershell
curl http://localhost:8000/
```

## Ghi chú cho Android

- Android emulator nên gọi `http://10.0.2.2:8000/`.
- Thiết bị thật nên gọi `http://<LAN_IP_cua_may_tinh>:8000/`.
- File hiện tại đang hardcode base URL ở `android/app/src/main/java/com/example/appmobile/data/remote/NetworkClient.kt`.

## Kết nối SQL Server từ host

- Server trong SSMS/DBeaver: `localhost,11433`
- Login: `sa`
- Password: giá trị `MSSQL_SA_PASSWORD` trong `.env.docker`
- Bật `Trust server certificate`

## Dừng và xoá container

```powershell
docker compose --env-file .env.docker down
```

Nếu muốn xoá luôn volume database:

```powershell
docker compose --env-file .env.docker down -v
```
