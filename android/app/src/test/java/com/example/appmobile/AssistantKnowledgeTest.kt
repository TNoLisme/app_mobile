package com.example.appmobile

import com.example.appmobile.ui.catalog.GameUiCatalog
import com.example.appmobile.ui.pages.assistant.AssistantKnowledge
import com.example.appmobile.ui.pages.assistant.ChatActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantKnowledgeTest {
    @Test
    fun gameRecommendationChoosesBeginnerGameForGenericQuestion() {
        val reply = AssistantKnowledge.reply(
            message = "T nên chơi game gì?",
            context = AssistantKnowledge.contextFor("home", null)
        )

        assertTrue(reply.text.contains("Chiếc hộp cảm xúc"))
        assertEquals(ChatActionType.OPEN_GAME, reply.actions.first().type)
        assertEquals(GameUiCatalog.GAME_RECOGNIZE_EMOTION, reply.actions.first().target)
    }

    @Test
    fun gameRecommendationChoosesCameraChallengeWhenRequested() {
        val reply = AssistantKnowledge.reply(
            message = "Gợi ý game camera để luyện biểu cảm",
            context = AssistantKnowledge.contextFor("home", null)
        )

        assertTrue(reply.text.contains("Thử thách cảm xúc"))
        assertEquals(GameUiCatalog.GAME_CV_REQUEST, reply.actions.first().target)
    }
}
