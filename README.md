# EmoGarden

EmoGarden là ứng dụng mobile giúp trẻ học nhận diện cảm xúc và luyện biểu cảm qua bài học, trò chơi, camera, báo cáo cho phụ huynh, Photobooth và Vườn cảm xúc.

Ứng dụng gồm hai phần:

- `android/`: app Android dùng Jetpack Compose.
- `backend/`: FastAPI backend dùng SQL Server, phục vụ đăng nhập, nội dung học, thống kê, báo cáo PDF và gửi email.

## Tính năng chính

- **Trang chủ**: gợi ý bài học, lối vào Vườn cảm xúc, Báo cáo của bé, Photobooth và trò chơi gần đây.
- **Học cảm xúc**: xem video mẫu và tình huống minh họa cho 6 cảm xúc: Vui vẻ, Buồn bã, Tức giận, Sợ hãi, Ngạc nhiên, Ghê tởm.
- **Chơi game cảm xúc**: luyện nhận diện cảm xúc qua các mini game.
- **Game camera**: bé đọc tình huống, biểu hiện cảm xúc trước camera và app nhận diện biểu cảm.
- **Báo cáo của bé**: xem thành tích tuần, tạo/xem PDF và gửi báo cáo cho email phụ huynh.
- **Photobooth cảm xúc**: chọn nhiều cảm xúc, chụp lần lượt, ghép thành một ảnh photobooth có khung.
- **Vườn cảm xúc**: hoàn thành nhiệm vụ để nhận giọt nước/ánh nắng và chăm các loài thực vật cảm xúc.
- **Trợ lý EmoGarden**: trợ lý theo ngữ cảnh trong app.
- **Khu vực phụ huynh**: quản lý tài khoản, email phụ huynh, quyền riêng tư và dữ liệu học tập.

## Công nghệ

### Android

- Kotlin
- Jetpack Compose
- CameraX
- Room
- Retrofit/OkHttp
- Firebase Auth
- Coil

### Backend

- Python/FastAPI
- SQLAlchemy
- SQL Server
- ReportLab để tạo PDF
- SMTP/Gmail App Password để gửi email báo cáo

## Cấu trúc thư mục

```text
app_mobile/
├── android/                  # Ứng dụng Android
│   ├── app/src/main/java/     # Source Kotlin/Compose
│   ├── app/src/main/res/      # Drawable, font, theme, resource
│   └── app/src/main/assets/   # Video mẫu, model/camera asset, web asset
├── backend/                  # FastAPI backend
│   ├── app/api/endpoints/     # API routes
│   ├── app/core/              # Config
│   ├── app/db/                # DB session/seed
│   ├── app/models/            # SQLAlchemy models
│   ├── app/schemas/           # DTO/schema
│   └── app/services/          # Report/email/PDF services
├── docker-compose.yml         # Chạy backend + SQL Server bằng Docker
├── DOCKER.md                  # Ghi chú Docker chi tiết
└── README.md
```

## Chuẩn bị môi trường

### Android

- Android Studio
- JDK đi kèm Android Studio hoặc JDK 17
- Android SDK
- Thiết bị thật hoặc emulator

### Backend

- Python 3.11+ hoặc Python hiện đang dùng trong môi trường dự án
- SQL Server
- ODBC Driver 17/18 for SQL Server
- Gmail App Password nếu muốn gửi email thật

## Chạy backend

Tạo file `backend/.env` từ cấu hình môi trường của máy bạn. Không commit file này.

Ví dụ:

```env
DATABASE_URL=mssql+pyodbc:///?odbc_connect=DRIVER%3D%7BODBC%20Driver%2017%20for%20SQL%20Server%7D%3BSERVER%3Dlocalhost%2C11433%3BDATABASE%3DMobile%3BUID%3Dsa%3BPWD%3Dyour_password%3BTrustServerCertificate%3Dyes
SECRET_KEY=change_me

SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USE_TLS=true
SMTP_FROM_NAME=EmoGarden
EMAIL_USER=your_gmail@gmail.com
EMAIL_PASS=your_gmail_app_password
```

Cài dependency và chạy:

```powershell
cd backend
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

Kiểm tra:

```powershell
curl http://localhost:8000/
```

## Chạy bằng Docker

Nếu muốn chạy backend và SQL Server bằng Docker:

```powershell
docker compose --env-file .env.docker up --build
```

Chi tiết xem `DOCKER.md`.

## Chạy Android app

Mở thư mục `android/` bằng Android Studio, chọn thiết bị và chạy cấu hình `app`.

Hoặc build bằng command line:

```powershell
cd android
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :app:assembleDebug
```

APK debug nằm tại:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

## Kết nối app với backend

File cấu hình API hiện ở:

```text
android/app/src/main/java/com/example/appmobile/data/remote/NetworkClient.kt
```

Mặc định Android emulator gọi backend bằng:

```text
http://10.0.2.2:8000/
```

Nếu dùng điện thoại thật cắm USB, có thể dùng:

```powershell
adb reverse tcp:8000 tcp:8000
```

Nếu điện thoại thật và backend chạy trên cùng Wi-Fi, đảm bảo máy tính và điện thoại cùng mạng, backend bind `0.0.0.0`, firewall cho phép cổng `8000`, rồi dùng IP LAN của máy tính.

## Báo cáo và email phụ huynh

Luồng báo cáo:

1. App lấy cùng một bộ dữ liệu báo cáo tuần.
2. Backend tạo nội dung email và file PDF từ dữ liệu đó.
3. Email gửi đến email phụ huynh đã lưu.
4. PDF báo cáo được đính kèm email.

Lưu ý:

- Không hard-code tài khoản Gmail thật vào source.
- Dùng Gmail App Password, không dùng mật khẩu Gmail chính.
- Nếu gửi email lỗi, kiểm tra `EMAIL_USER`, `EMAIL_PASS`, kết nối mạng và log backend.

## Camera và quyền riêng tư

- Camera dùng để nhận diện biểu cảm khi chơi game camera.
- App không tự động lưu video.
- Ảnh Photobooth chỉ lưu khi người dùng bấm lưu.
- Báo cáo chỉ gửi email sau khi có xác nhận.
- Khu vực phụ huynh dùng để xem/sửa email và quản lý dữ liệu liên quan.

## Build kiểm tra nhanh

```powershell
cd android
.\gradlew.bat :app:assembleDebug
```

Backend:

```powershell
cd backend
python -m compileall app
```

## Lưu ý khi phát triển

- Không commit `.env`, mật khẩu, Gmail App Password hoặc key riêng.
- Không start camera/timer trong body Composable.
- State UI nên nằm trong ViewModel/StateFlow hoặc repository tương ứng.
- Các action nhạy cảm như gửi email, lưu ảnh ra máy, xóa dữ liệu cần confirm hoặc parent gate.
- Khi thêm màn mới, cập nhật trợ lý ngữ cảnh nếu màn đó cần hỗ trợ.

