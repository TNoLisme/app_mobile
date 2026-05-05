package com.example.appmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.appmobile.ui.pages.assistant.AssistantPage
import com.example.appmobile.ui.pages.auth.LoginPage
import com.example.appmobile.ui.pages.auth.RegisterPage
import com.example.appmobile.ui.pages.game.*
import com.example.appmobile.ui.pages.home.HomePage
import com.example.appmobile.ui.pages.learn.EmotionDetailPage
import com.example.appmobile.ui.pages.learn.LearnPage
import com.example.appmobile.ui.pages.profile.ProfilePage
import com.example.appmobile.ui.pages.report.ReportPage
import com.example.appmobile.ui.pages.select.LevelSelectPage
import com.example.appmobile.ui.pages.select.SelectGamePage
import com.example.appmobile.ui.theme.AppMobileTheme
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppMobileTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavigation(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val auth = FirebaseAuth.getInstance()
    val startDestination = if (auth.currentUser != null) "home" else "login"

    fun assistantRoute(gameId: String, level: Int? = null): String {
        return if (level == null) "assistant/$gameId" else "assistant/$gameId?level=$level"
    }

    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        composable("login") {
            LoginPage(
                onNavigateToRegister = { navController.navigate("register") },
                onLoginSuccess = { navController.navigate("home") { popUpTo("login") { inclusive = true } } }
            )
        }
        composable("register") { RegisterPage(onNavigateBack = { navController.popBackStack() }) }
        composable("home") {
            HomePage(
                onLogout = {
                    auth.signOut()
                    navController.navigate("login") { popUpTo("home") { inclusive = true } }
                },
                onNavigateToGame = { gameType -> navController.navigate("select_game/$gameType") },
                onNavigateToLearn = { navController.navigate("learn") },
                onNavigateToReport = { navController.navigate("report") },
                onNavigateToProfile = { navController.navigate("profile") },
                onNavigateToAssistant = { navController.navigate(assistantRoute("home")) }
            )
        }
        composable("select_game/{type}") { backStackEntry ->
            val type = backStackEntry.arguments?.getString("type") ?: ""
            SelectGamePage(
                type = type,
                onBack = { navController.popBackStack() },
                onOpenLevel = { id -> navController.navigate("level_select/$id") },
                onOpenAssistant = { navController.navigate(assistantRoute("select_game")) }
            )
        }
        composable("level_select/{gameId}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("gameId") ?: ""
            LevelSelectPage(
                gameId = id,
                onBack = { navController.popBackStack() },
                onStartGame = { lvl -> navController.navigate("game/$id/$lvl") },
                onOpenAssistant = { navController.navigate(assistantRoute("level_select")) }
            )
        }
        composable("game/{gameId}/{level}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("gameId") ?: ""
            val level = backStackEntry.arguments?.getString("level")?.toIntOrNull() ?: 1
            when (id) {
                // Các game Nhận diện
                "3bcb2108-721c-4a15-a585-31f3084ed000" -> RecognizeEmotionPage(level = level, onBack = { navController.popBackStack() }, onOpenAssistant = { navController.navigate(assistantRoute("recognize_emotion", level)) })
                "33ecafaa-ec7e-40d2-9c67-ed0a29ac0051" -> GameClick2Page(level = level, onBack = { navController.popBackStack() }, onOpenAssistant = { navController.navigate(assistantRoute("game_click_2", level)) })
                "08bbffbf-d147-4556-bccb-b7621cafbf15" -> GameClick3Page(level = level, onBack = { navController.popBackStack() }, onOpenAssistant = { navController.navigate(assistantRoute("game_click_3", level)) })
                "aacaf79e-e15e-42a9-a3d1-a522720d919b" -> GameClick4Page(level = level, onBack = { navController.popBackStack() }, onOpenAssistant = { navController.navigate(assistantRoute("game_click_4", level)) })
                // Các game Biểu cảm
                "e05909f3-3dee-42a6-9a75-fd985b1bdf47" -> GameCVPage(level = level, onBack = { navController.popBackStack() }, onOpenAssistant = { navController.navigate(assistantRoute("gameCV", level)) })
                "61f5e09e-eefa-44c1-86e1-87dfceac3b8e" -> GameCV2Page(level = level, onBack = { navController.popBackStack() }, onOpenAssistant = { navController.navigate(assistantRoute("gameCV", level)) })
                else -> {
                    // Xử lý an toàn khi không tìm thấy Game ID
                    LaunchedEffect(Unit) {
                        navController.popBackStack()
                    }
                }
            }
        }
        composable("learn") {
            LearnPage(
                onBack = { navController.popBackStack() },
                onSelectEmotion = { id -> navController.navigate("learn_detail/$id") },
                onOpenAssistant = { navController.navigate(assistantRoute("learn")) }
            )
        }
        composable("learn_detail/{emotionId}") { backStackEntry ->
            EmotionDetailPage(emotionId = backStackEntry.arguments?.getString("emotionId") ?: "", onBack = { navController.popBackStack() })
        }
        composable("report") { ReportPage(onBack = { navController.popBackStack() }) }
        composable("assistant") { AssistantPage(onBack = { navController.popBackStack() }) }
        composable(
            route = "assistant/{gameId}?level={level}",
            arguments = listOf(
                navArgument("gameId") { type = NavType.StringType },
                navArgument("level") {
                    type = NavType.IntType
                    defaultValue = -1
                }
            )
        ) { backStackEntry ->
            val assistantGameId = backStackEntry.arguments?.getString("gameId") ?: "home"
            val assistantLevel = backStackEntry.arguments?.getInt("level")?.takeIf { it > 0 }
            AssistantPage(
                gameId = assistantGameId,
                level = assistantLevel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("profile") {
            ProfilePage(
                onBack = { navController.popBackStack() },
                onLogout = {
                    auth.signOut()
                    navController.navigate("login") { popUpTo("home") { inclusive = true } }
                }
            )
        }
    }
}
