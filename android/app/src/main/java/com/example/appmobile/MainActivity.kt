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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.appmobile.ui.pages.auth.LoginPage
import com.example.appmobile.ui.pages.auth.RegisterPage
import com.example.appmobile.ui.pages.game.*
import com.example.appmobile.ui.pages.home.HomePage
import com.example.appmobile.ui.pages.learn.EmotionDetailPage
import com.example.appmobile.ui.pages.learn.LearnPage
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
                onNavigateToLearn = { navController.navigate("learn") }
            )
        }
        composable("select_game/{type}") { backStackEntry ->
            val type = backStackEntry.arguments?.getString("type") ?: ""
            SelectGamePage(type = type, onBack = { navController.popBackStack() }, onOpenLevel = { id ->
                navController.navigate("level_select/$id")
            })
        }
        composable("level_select/{gameId}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("gameId") ?: ""
            LevelSelectPage(gameId = id, onBack = { navController.popBackStack() }, onStartGame = { lvl ->
                navController.navigate("game/$id")
            })
        }
        composable("game/{gameId}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("gameId") ?: ""
            when (id) {
                // Các game Nhận diện
                "3bcb2108-721c-4a15-a585-31f3084ed000" -> RecognizeEmotionPage(onBack = { navController.popBackStack() })
                "33ecafaa-ec7e-40d2-9c67-ed0a29ac0051" -> GameClick2Page(onBack = { navController.popBackStack() })
                "08bbffbf-d147-4556-bccb-b7621cafbf15" -> GameClick3Page(onBack = { navController.popBackStack() })
                "aacaf79e-e15e-42a9-a3d1-a522720d919b" -> GameClick4Page(onBack = { navController.popBackStack() })
                // Các game Biểu cảm
                "e05909f3-3dee-42a6-9a75-fd985b1bdf47" -> GameCVPage(onBack = { navController.popBackStack() })
                "61f5e09e-eefa-44c1-86e1-87dfceac3b8e" -> GameCV2Page(onBack = { navController.popBackStack() })
                else -> {
                    // Xử lý an toàn khi không tìm thấy Game ID
                    LaunchedEffect(Unit) {
                        navController.popBackStack()
                    }
                }
            }
        }
        composable("learn") { LearnPage(onBack = { navController.popBackStack() }, onSelectEmotion = { id -> navController.navigate("learn_detail/$id") }) }
        composable("learn_detail/{emotionId}") { backStackEntry ->
            EmotionDetailPage(emotionId = backStackEntry.arguments?.getString("emotionId") ?: "", onBack = { navController.popBackStack() })
        }
    }
}
