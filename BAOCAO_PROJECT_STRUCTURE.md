# BÁO CÁO CẤU TRÚC DỰ ÁN EMO GARDEN

## TỔNG QUAN DỰ ÁN

Đây là dự án **Emo Garden** - một ứng dụng di động giúp trẻ em học và nhận biết cảm xúc thông qua các trò chơi tương tác. Dự án bao gồm 2 phần chính:

1. **Android App** (Frontend): Ứng dụng di động viết by Kotlin với Jetpack Compose
2. **Backend API** (Backend): Server viết by Python với FastAPI

---

## 1. CẤU TRÚC THƯ MỤC ANDROID

### 1.1. Thư mục gốc `android/`

| Thư mục/File | Nhiệm vụ |
|-------------|----------|
| `build.gradle.kts` | File cấu hình build chính của project Android |
| `settings.gradle.kts` | Cấu hình module và repository |
| `gradle.properties` | Cấu hình thuộc tính Gradle |
| `gradlew/gradlew.bat` | Gradle Wrapper cho Linux/Mac và Windows |
| `app/` | Module ứng dụng chính |
| `gradle/` | Thư mục chứa Gradle wrapper và version catalog |

### 1.2. Thư mục `android/app/src/main/java/com/example/appmobile/`

#### **`MainActivity.kt`**
- Activity chính của ứng dụng
- Khởi tạo ứng dụng và quản lý navigation giữa các màn hình

#### **`data/` - Lớp truy xuất dữ liệu**

##### `local/` - Cơ sở dữ liệu cục bộ (Room Database)
| File | Nhiệm vụ |
|------|----------|
| `AppDatabase.kt` | Định nghĩa database Room với 14 entities: User, Child, Game, GameContent, Session, Report, Progress, v.v. |
| `AppSession.kt` | Quản lý session người dùng cục bộ |
| `dao/` | Data Access Objects - các interface truy xuất database |
| `entity/` | Các entity class ánh xạ tới bảng database |

##### `local/dao/` - Các DAO
| File | Nhiệm vụ |
|------|----------|
| `GameContentDao.kt` | Truy xuất dữ liệu game, câu hỏi, nội dung game |
| `SessionDao.kt` | Truy xuất dữ liệu phiên chơi game |
| `UserDao.kt` | Truy xuất dữ liệu người dùng |
| `ReportDao.kt` | Truy xuất báo cáo tiến độ |

##### `local/entity/` - Các entity database
| File | Nhiệm vụ |
|------|----------|
| `UserEntity.kt` | Entity người dùng |
| `ChildEntity.kt` | Entity trẻ em (con của phụ huynh) |
| `GameEntity.kt` | Entity thông tin game |
| `GameContentEntity.kt` | Entity nội dung câu hỏi trong game |
| `SessionEntity.kt` | Entity phiên chơi game |
| `SessionQuestionEntity.kt` | Entity câu trả lời trong phiên |
| `GameDataEntity.kt` | Entity dữ liệu game đã chơi |
| `ProgressEntity.kt` | Entity tiến độ người chơi |
| `ReportEntity.kt` | Entity báo cáo |
| `EmotionConceptEntity.kt` | Entity khái niệm cảm xúc để học |
| `QuestionEntity.kt` | Entity câu hỏi |
| `ChatbotLogEntity.kt` | Entity log chat với assistant |

##### `remote/` - Kết nối API backend
| File | Nhiệm vụ |
|------|----------|
| `NetworkClient.kt` | Client HTTP kết nối tới backend API |
| `FirebaseAuthHelper.kt` | Xác thực người dùng bằng Firebase |
| `api/ApiService.kt` | Interface Retrofit định nghĩa tất cả API endpoints |
| `dto/` | Data Transfer Objects - các data class gửi/nhận từ API |

##### `repository/` - Repository pattern
| File | Nhiệm vụ |
|------|----------|
| `GameRepository.kt` | **QUAN TRỌNG**: Quản lý logic game, gọi API startGame, endLevel, getGameProgress |
| `UserRepository.kt` | Quản lý thông tin người dùng |
| `SessionRepository.kt` | Quản lý phiên chơi |
| `AnalysisRepository.kt` | Phân tích dữ liệu |
| `AssistantRepository.kt` | Tương tác với AI assistant |

##### `mapper/`
| File | Nhiệm vụ |
|------|----------|
| `Mappers.kt` | Chuyển đổi giữa Entity ↔ Domain Model ↔ DTO |

#### **`domain/` - Lớp nghiệp vụ**

##### `model/Models.kt`
- Định nghĩa các model nghiệp vụ: Game, GameContent, EmotionConcept, User, v.v.

#### **`ui/` - Giao diện người dùng (Jetpack Compose)**

##### `pages/` - Các màn hình chính
| Thư mục/File | Nhiệm vụ |
|-------------|----------|
| `auth/` | Màn hình đăng nhập, đăng ký |
| `home/` | Màn hình chính |
| `select/` | **QUAN TRỌNG**: Màn hình chọn game và chọn level |
| `game/` | **QUAN TRỌNG**: Các màn hình chơi game |
| `report/` | Màn hình báo cáo tiến độ |
| `learn/` | Màn hình học cảm xúc |
| `profile/` | Hồ sơ người dùng |
| `settings/` | Cài đặt |
| `assistant/` | Trợ lý AI |

##### `pages/game/` - Các loại game
| File | Nhiệm vụ |
|------|----------|
| `GameClick2Page.kt` | **GAME CLICK**: Game ghép khuôn mặt cảm xúc (Face Assembly) |
| `GameClick3Page.kt` | **GAME CLICK**: Game nhận biết cảm xúc |
| `GameClick4Page.kt` | **GAME CLICK**: Game thám tử cảm xúc |
| `GameCVPage.kt` | **GAME CAMERA**: Game câu chuyện khuôn mặt |
| `GameCV2Page.kt` | **GAME CAMERA**: Game thử thách biểu cảm |
| `CvTrainingGamePage.kt` | Game training biểu cảm |
| `RecognizeEmotionPage.kt` | Game nhận diện cảm xúc |
| `GameSessionUi.kt` | UI chung cho session game |

##### `pages/select/` - Chọn game
| File | Nhiệm vụ |
|------|----------|
| `SelectGamePage.kt` | Hiển thị danh sách game theo category (click_game, camera_game) |
| `LevelSelectPage.kt` | Chọn level cho game đã chọn |

##### `catalog/`
| File | Nhiệm vụ |
|------|----------|
| `GameUiCatalog.kt` | Catalog thông tin UI của các game, bao gồm ID và cấu hình |

##### `components/` - Các component UI
| File | Nhiệm vụ |
|------|----------|
| `GameScreenShell.kt` | Khung UI chung cho màn hình game |
| `EmoGardenChrome.kt` | Các element UI trang trí |
| `EmotionUiStyle.kt` | Style cho cảm xúc |
| `atoms/` | Component nguyên tử (button, text, v.v.) |
| `molecules/` | Component phân tử (kết hợp atoms) |

##### `viewmodel/` - ViewModel
| File | Nhiệm vụ |
|------|----------|
| `AuthViewModel.kt` | Quản lý state đăng nhập |
| `HomeViewModel.kt` | Quản lý state màn hình chính |
| `ReportViewModel.kt` | Quản lý state báo cáo |

##### `theme/` - Theme
| File | Nhiệm vụ |
|------|----------|
| `Color.kt` | Định nghĩa màu sắc |
| `Theme.kt` | Theme ứng dụng |
| `Type.kt` | Định nghĩa typography |

---

## 2. CẤU TRÚC THƯ MỤC BACKEND

### 2.1. Thư mục gốc `backend/`

| File | Nhiệm vụ |
|------|----------|
| `requirements.txt` | Danh sách thư viện Python |
| `Dockerfile` | Docker image cho backend |
| `.env` | Biến môi trường |
| `docker/entrypoint.sh` | Script khởi động container |

### 2.2. Thư mục `backend/app/`

#### **`main.py`**
- Entry point của FastAPI application
- Cấu hình CORS và register các router API

#### **`api/endpoints/` - Các API endpoints**

| File | Nhiệm vụ |
|------|----------|
| `auth.py` | API đăng ký, đăng nhập, xác thực |
| `content.py` | **QUAN TRỌNG**: API quản lý game, câu hỏi, start game, end level |
| `emotions.py` | API quản lý cảm xúc |
| `reports.py` | API báo cáo |
| `assistant.py` | API chat với AI assistant |
| `tts.py` | API text-to-speech |
| `runtime.py` | API runtime |

#### **`models/` - Database Models (SQLAlchemy)**

| File | Nhiệm vụ |
|------|----------|
| `user.py` | Model User, Child |
| `game.py` | **QUAN TRỌNG**: Model Game, GameContent, GameData, PlaySession, SessionQuestion, EmotionConcept |
| `analytics.py` | Model analytics, ChildProgress |

#### **`db/` - Database Configuration**

| File | Nhiệm vụ |
|------|----------|
| `base.py` | Base class cho SQLAlchemy models |
| `session.py` | Cấu hình database session |
| `seed.py` | Seed data mẫu |

#### **`schemas/` - Pydantic Schemas**
- Định nghĩa request/response schemas cho API

#### **`services/` - Business Logic Services**

| File | Nhiệm vụ |
|------|----------|
| `report_data.py` | Xử lý dữ liệu báo cáo |
| `report_pdf.py` | Tạo PDF báo cáo |

---

## 3. LUỒNG CHẠY ỨNG DỤNG

### 3.1. Luồng tổng quát

```
1. Người dùng mở app → MainActivity.kt
2. Kiểm tra đăng nhập (Firebase Auth)
3. Vào màn hình chính (Home)
4. Chọn "Chơi game" → SelectGamePage
5. Chọn game → LevelSelectPage
6. Chọn level → Game Page (GameClick2Page, GameClick3Page, v.v.)
7. Chơi game → Gửi kết quả về backend
8. Backend lưu kết quả, cập nhật tiến độ
9. Quay lại màn hình chính hoặc chơi tiếp
```

### 3.2. Luồng chi tiết cho GAME CLICK

**Game Click** là các game tương tác click/chọn cảm xúc, bao gồm:
- `GAME_RECOGNIZE_EMOTION`: Chiếc hộp cảm xúc
- `GAME_FACE_ASSEMBLY`: Xưởng lắp ghép (ghép khuôn mặt)
- `GAME_EMOTION_MATCH`: Cảm xúc đúng chỗ
- `GAME_DETECTIVE`: Thám tử cảm xúc

#### **Các file liên quan đến luồng chọn và chơi game click:**

**Android:**
1. `ui/pages/select/SelectGamePage.kt` - Hiển thị danh sách game
2. `ui/pages/select/LevelSelectPage.kt` - Chọn level
3. `ui/pages/game/GameClick2Page.kt` - Game ghép khuôn mặt
4. `ui/pages/game/GameClick3Page.kt` - Game nhận biết cảm xúc
5. `ui/pages/game/GameClick4Page.kt` - Game thám tử
6. `data/repository/GameRepository.kt` - Gọi API startGame, endLevel
7. `data/remote/api/ApiService.kt` - Interface API
8. `ui/catalog/GameUiCatalog.kt` - Catalog game IDs

**Backend:**
1. `api/endpoints/content.py` - Xử lý API start_game, end_level
2. `models/game.py` - Models Game, GameContent, PlaySession, SessionQuestion
3. `models/analytics.py` - Model ChildProgress (lưu tiến độ)

---

## 4. CÁC FILE DATABASE LIÊN QUAN

### 4.1. Android Local Database (Room)

**File định nghĩa:** `android/app/src/main/java/com/example/appmobile/data/local/AppDatabase.kt`

**Các bảng (entities):**
- `UserEntity` - Người dùng
- `ChildEntity` - Trẻ em
- `GameEntity` - Thông tin game
- `GameContentEntity` - Nội dung câu hỏi game
- `SessionEntity` - Phiên chơi
- `SessionQuestionEntity` - Câu trả lời trong phiên
- `GameDataEntity` - Dữ liệu game đã chơi
- `GameDataQuestionEntity` - Câu hỏi đã chơi
- `ProgressEntity` - Tiến độ người chơi
- `ReportEntity` - Báo cáo
- `EmotionConceptEntity` - Khái niệm cảm xúc
- `QuestionEntity` - Câu hỏi
- `ChatbotLogEntity` - Log chat

### 4.2. Backend Database (SQLAlchemy)

**File định nghĩa models:** `backend/app/models/game.py`

**Các bảng chính:**
- `games` - Thông tin game (game_id, game_type, name, level, difficulty)
- `game_content` - Nội dung câu hỏi (content_id, game_id, level, question_text, correct_answer, emotion)
- `emotion_concepts` - Khái niệm cảm xúc để học
- `game_data` - Dữ liệu game đã chơi (data_id, game_id, user_id, level)
- `game_data_question` - Câu hỏi đã chơi trong game_data
- `sessions` - Phiên chơi (session_id, user_id, game_id, score, level, emotion_errors)
- `session_questions` - Câu trả lời trong phiên (session_id, question_id, is_correct, response_time_ms)

**File models khác:**
- `backend/app/models/user.py` - User, Child
- `backend/app/models/analytics.py` - ChildProgress (tiến độ theo game)

---

## 5. TÓM TẮT LUỒNG GAME CLICK

```
1. SelectGamePage.kt
   └─ Gọi GameRepository.getGames() → API GET /games
   └─ Hiển thị danh sách game click_game

2. LevelSelectPage.kt
   └─ Gọi GameRepository.getGameProgress() → API GET /games/progress/{game_id}
   └─ Hiển thị các level đã mở khóa

3. GameClick2Page.kt (hoặc 3, 4)
   └─ LaunchedEffect: Gọi GameRepository.startGame()
      └─ API POST /games/start/{game_id}
      └─ Backend trả về: session_id, questions, max_errors, level_threshold
   
   └─ Người dùng chơi game (chọn đáp án)
   
   └─ Khi hoàn thành: Gọi GameRepository.endLevel()
      └─ API POST /games/end-level
      └─ Gửi: session_id, results (câu trả lời), reviewEmotions
      └─ Backend trả về: score, passed, progress_level, review_emotions_to_learn

4. Backend xử lý end-level (content.py: end_level function)
   └─ Tính score, accuracy
   └─ Lưu SessionQuestion vào DB
   └─ Cập nhật ChildProgress (level, accuracy, ratio, review_emotions)
   └─ Xác định emotions cần học lại
   └─ Trả kết quả về app
```

---

## 6. DANH SÁCH FILE QUAN TRỌNG CHO GAME CLICK

### Android:
| File | Mô tả |
|------|-------|
| `ui/pages/select/SelectGamePage.kt` | Màn hình chọn game |
| `ui/pages/select/LevelSelectPage.kt` | Màn hình chọn level |
| `ui/pages/game/GameClick2Page.kt` | Game ghép khuôn mặt |
| `ui/pages/game/GameClick3Page.kt` | Game nhận biết cảm xúc |
| `ui/pages/game/GameClick4Page.kt` | Game thám tử |
| `data/repository/GameRepository.kt` | Repository game logic |
| `data/remote/api/ApiService.kt` | API interface |
| `ui/catalog/GameUiCatalog.kt` | Game IDs và config |

### Backend:
| File | Mô tả |
|------|-------|
| `api/endpoints/content.py` | API endpoints cho game |
| `models/game.py` | Database models cho game |
| `models/analytics.py` | Model ChildProgress |
| `db/session.py` | Database session config |

---

*Báo cáo được tạo ngày: 2026-05-21*