import unittest

from app.api.endpoints.assistant import (
    ChatActionResponse,
    ChatRequest,
    GAME_CATALOG,
    _build_prompt,
    _sanitize_actions,
)


class AssistantEndpointTest(unittest.TestCase):
    def test_prompt_contains_progress_and_conversation(self):
        request = ChatRequest(
            game_id="home",
            message="Con nên chơi gì?",
            history=[{"role": "user", "text": "Con muốn luyện cảm xúc buồn"}],
        )

        prompt = _build_prompt(
            request,
            {
                "available": True,
                "average_score": 60,
                "weak_emotions": [{"emotion": "Buồn bã", "accuracy_percent": 40}],
            },
        )

        self.assertIn("Buồn bã", prompt)
        self.assertIn("Con muốn luyện cảm xúc buồn", prompt)
        self.assertIn("Chiếc hộp cảm xúc", prompt)

    def test_actions_are_limited_to_safe_targets(self):
        actions = _sanitize_actions(
            [
                ChatActionResponse(
                    type="OPEN_GAME",
                    label="Chơi ngay",
                    target=next(iter(GAME_CATALOG)),
                ),
                ChatActionResponse(type="DELETE_ACCOUNT", label="Xóa tài khoản"),
                ChatActionResponse(
                    type="START_EMOTION_CHALLENGE",
                    label="Thử cảm xúc",
                    target="unknown",
                ),
            ]
        )

        self.assertEqual(1, len(actions))
        self.assertEqual("OPEN_GAME", actions[0].type)


if __name__ == "__main__":
    unittest.main()
