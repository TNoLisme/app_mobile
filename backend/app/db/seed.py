from sqlalchemy.orm import Session

from app.models.game import EmotionConcept, Game, GameContent


GAME_RECOGNIZE_EMOTION = "3bcb2108-721c-4a15-a585-31f3084ed000"
GAME_FACE_ASSEMBLY = "33ecafaa-ec7e-40d2-9c67-ed0a29ac0051"
GAME_EMOTION_MATCH = "08bbffbf-d147-4556-bccb-b7621cafbf15"
GAME_DETECTIVE = "aacaf79e-e15e-42a9-a3d1-a522720d919b"
GAME_CV_STORY = "e05909f3-3dee-42a6-9a75-fd985b1bdf47"
GAME_CV_REQUEST = "61f5e09e-eefa-44c1-86e1-87dfceac3b8e"


GAMES = [
    {
        "game_id": GAME_RECOGNIZE_EMOTION,
        "game_type": "click_game",
        "name": "Chiếc hộp cảm xúc",
        "level": 3,
        "difficulty_level": "normal",
        "max_errors": 3,
        "level_threshold": 70,
        "time_limit": None,
    },
    {
        "game_id": GAME_FACE_ASSEMBLY,
        "game_type": "click_game",
        "name": "Xưởng lắp ghép",
        "level": 3,
        "difficulty_level": "normal",
        "max_errors": 3,
        "level_threshold": 70,
        "time_limit": None,
    },
    {
        "game_id": GAME_EMOTION_MATCH,
        "game_type": "click_game",
        "name": "Cảm xúc đúng chỗ",
        "level": 3,
        "difficulty_level": "normal",
        "max_errors": 3,
        "level_threshold": 75,
        "time_limit": None,
    },
    {
        "game_id": GAME_DETECTIVE,
        "game_type": "click_game",
        "name": "Thám tử cảm xúc",
        "level": 3,
        "difficulty_level": "normal",
        "max_errors": 3,
        "level_threshold": 75,
        "time_limit": None,
    },
    {
        "game_id": GAME_CV_STORY,
        "game_type": "camera_game",
        "name": "Câu chuyện khuôn mặt",
        "level": 6,
        "difficulty_level": "normal",
        "max_errors": 2,
        "level_threshold": 40,
        "time_limit": 30,
    },
    {
        "game_id": GAME_CV_REQUEST,
        "game_type": "camera_game",
        "name": "Thử thách biểu cảm",
        "level": 6,
        "difficulty_level": "normal",
        "max_errors": 2,
        "level_threshold": 40,
        "time_limit": 20,
    },
]


EMOTION_CONCEPTS = [
    {
        "concept_id": "concept-happy",
        "emotion": "happy",
        "level": 1,
        "title": "Vui vẻ",
        "video_path": "/fe/assets/videos/concepts/vui.mp4",
        "image_path": "/fe/assets/images/concepts/vui.jpg",
        "audio_path": "/fe/assets/audio/vui.mp3",
        "description": "Vui khi được khen, được nhận quà hoặc chơi cùng bạn bè.",
    },
    {
        "concept_id": "concept-sad",
        "emotion": "sad",
        "level": 1,
        "title": "Buồn bã",
        "video_path": "/fe/assets/videos/concepts/buon.mp4",
        "image_path": "/fe/assets/images/concepts/buon.jpg",
        "audio_path": None,
        "description": "Buồn khi mất đồ, bị mắng hoặc phải xa người thân.",
    },
    {
        "concept_id": "concept-angry",
        "emotion": "angry",
        "level": 1,
        "title": "Tức giận",
        "video_path": "/fe/assets/videos/concepts/tuc.mp4",
        "image_path": "/fe/assets/images/concepts/tuc.jpg",
        "audio_path": None,
        "description": "Tức giận khi bị lấy đồ hoặc bị đối xử không công bằng.",
    },
    {
        "concept_id": "concept-fear",
        "emotion": "fear",
        "level": 1,
        "title": "Sợ hãi",
        "video_path": None,
        "image_path": "/fe/assets/images/concepts/so.jpg",
        "audio_path": "/fe/assets/audio/so.mp3",
        "description": "Sợ khi nghe tiếng to, gặp điều lạ hoặc cảm thấy không an toàn.",
    },
    {
        "concept_id": "concept-surprise",
        "emotion": "surprise",
        "level": 1,
        "title": "Ngạc nhiên",
        "video_path": "/fe/assets/videos/concepts/ngac.mp4",
        "image_path": "/fe/assets/images/concepts/ngac.jpg",
        "audio_path": None,
        "description": "Ngạc nhiên khi có điều bất ngờ xảy ra.",
    },
    {
        "concept_id": "concept-disgust",
        "emotion": "disgust",
        "level": 1,
        "title": "Ghê tởm",
        "video_path": None,
        "image_path": "/fe/assets/images/concepts/ghe.jpg",
        "audio_path": None,
        "description": "Ghê tởm khi ngửi mùi hôi hoặc thấy thứ bẩn.",
    },
]


GAME_CONTENT = [
    {
        "content_id": "recognize-happy-1",
        "game_id": GAME_RECOGNIZE_EMOTION,
        "level": 1,
        "content_type": "image",
        "media_path": "/fe/assets/images/happy/happy_1.jpg",
        "question_text": "Bạn nhỏ trong ảnh đang cảm thấy thế nào?",
        "correct_answer": "happy",
        "emotion": "happy",
        "explanation": "Nụ cười và ánh mắt sáng gợi ý cảm xúc vui.",
    },
    {
        "content_id": "recognize-sad-1",
        "game_id": GAME_RECOGNIZE_EMOTION,
        "level": 1,
        "content_type": "image",
        "media_path": "/fe/assets/images/sad/sad_1.jpg",
        "question_text": "Bạn nhỏ trong ảnh đang cảm thấy thế nào?",
        "correct_answer": "sad",
        "emotion": "sad",
        "explanation": "Nét mặt trầm và mắt nhìn xuống gợi ý cảm xúc buồn.",
    },
    {
        "content_id": "recognize-angry-2",
        "game_id": GAME_RECOGNIZE_EMOTION,
        "level": 2,
        "content_type": "image",
        "media_path": "/fe/assets/images/angry/angry_1.jpg",
        "question_text": "Khuôn mặt này thể hiện cảm xúc nào?",
        "correct_answer": "angry",
        "emotion": "angry",
        "explanation": "Lông mày chau lại thường là dấu hiệu tức giận.",
    },
    {
        "content_id": "recognize-surprise-2",
        "game_id": GAME_RECOGNIZE_EMOTION,
        "level": 2,
        "content_type": "image",
        "media_path": "/fe/assets/images/surprise/surprise_1.jpg",
        "question_text": "Khuôn mặt này thể hiện cảm xúc nào?",
        "correct_answer": "surprise",
        "emotion": "surprise",
        "explanation": "Mắt và miệng mở to gợi ý ngạc nhiên.",
    },
    {
        "content_id": "match-happy-1",
        "game_id": GAME_EMOTION_MATCH,
        "level": 1,
        "content_type": "text",
        "media_path": None,
        "question_text": "Bé được tặng món quà yêu thích. Cảm xúc nào phù hợp?",
        "correct_answer": "happy",
        "emotion": "happy",
        "explanation": "Được tặng quà thường làm mình vui.",
    },
    {
        "content_id": "match-angry-2",
        "game_id": GAME_EMOTION_MATCH,
        "level": 2,
        "content_type": "text",
        "media_path": None,
        "question_text": "Bạn giật đồ chơi khỏi tay bé. Cảm xúc nào có thể xuất hiện?",
        "correct_answer": "angry",
        "emotion": "angry",
        "explanation": "Bị giật đồ bất ngờ có thể làm mình tức giận.",
    },
    {
        "content_id": "detective-fear-3",
        "game_id": GAME_DETECTIVE,
        "level": 3,
        "content_type": "story",
        "media_path": None,
        "question_text": "Minh bám chặt tay mẹ khi thấy chó lớn. Cảm xúc nào đang ẩn giấu?",
        "correct_answer": "fear",
        "emotion": "fear",
        "explanation": "Bám chặt người lớn khi gặp điều lạ có thể là sợ hãi.",
    },
    {
        "content_id": "cv-story-happy-1",
        "game_id": GAME_CV_STORY,
        "level": 1,
        "content_type": "scenario",
        "media_path": "/fe/assets/images/happy/happy_1.jpg",
        "question_text": "Nhân vật vừa nhận được lời khen. Hãy thể hiện khuôn mặt vui.",
        "correct_answer": "happy",
        "emotion": "happy",
        "explanation": "Hãy mỉm cười và mở mắt tự nhiên.",
    },
    {
        "content_id": "cv-story-sad-1",
        "game_id": GAME_CV_STORY,
        "level": 1,
        "content_type": "scenario",
        "media_path": "/fe/assets/images/sad/sad_1.jpg",
        "question_text": "Nhân vật làm rơi kem xuống đất. Hãy thể hiện khuôn mặt buồn.",
        "correct_answer": "sad",
        "emotion": "sad",
        "explanation": "Hãy hạ miệng nhẹ và nhìn xuống.",
    },
    {
        "content_id": "cv-story-angry-2",
        "game_id": GAME_CV_STORY,
        "level": 2,
        "content_type": "scenario",
        "media_path": "/fe/assets/images/angry/angry_1.jpg",
        "question_text": "Nhân vật bị lấy mất đồ chơi. Hãy thể hiện khuôn mặt tức giận.",
        "correct_answer": "angry",
        "emotion": "angry",
        "explanation": "Hãy chau lông mày và giữ miệng nghiêm.",
    },
    {
        "content_id": "cv-request-happy-1",
        "game_id": GAME_CV_REQUEST,
        "level": 1,
        "content_type": "request",
        "media_path": None,
        "question_text": "Hãy cười thật tươi trong 3 giây.",
        "correct_answer": "happy",
        "emotion": "happy",
        "explanation": "Cảm xúc cần đạt: vui vẻ.",
    },
    {
        "content_id": "cv-request-surprise-3",
        "game_id": GAME_CV_REQUEST,
        "level": 3,
        "content_type": "request",
        "media_path": None,
        "question_text": "Hãy làm khuôn mặt ngạc nhiên.",
        "correct_answer": "surprise",
        "emotion": "surprise",
        "explanation": "Cảm xúc cần đạt: ngạc nhiên.",
    },
]


def _upsert(db: Session, model: type, key_name: str, rows: list[dict]) -> None:
    for row in rows:
        instance = db.get(model, row[key_name])
        if instance is None:
            db.add(model(**row))
        else:
            for key, value in row.items():
                setattr(instance, key, value)


def seed_static_content(db: Session) -> None:
    _upsert(db, Game, "game_id", GAMES)
    _upsert(db, EmotionConcept, "concept_id", EMOTION_CONCEPTS)
    _upsert(db, GameContent, "content_id", GAME_CONTENT)
    db.commit()

