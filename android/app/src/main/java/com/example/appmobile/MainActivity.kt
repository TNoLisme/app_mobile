package com.example.appmobile

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.appmobile.data.local.AppSession
import com.example.appmobile.ui.components.AssistantChatBubble
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
import com.example.appmobile.ui.pages.settings.SettingsPage
import com.example.appmobile.ui.state.AppSettingsState
import com.example.appmobile.ui.state.AppThemeMode
import com.example.appmobile.ui.theme.AppMobileTheme
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContent { AppRoot() }
    }
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    remember(context) {
        AppSettingsState.load(context)
        true
    }

    val themeMode by AppSettingsState.themeMode
    val dynamicColorEnabled by AppSettingsState.dynamicColorEnabled
    val systemDark = isSystemInDarkTheme()
    val useDarkTheme = when (themeMode) {
        AppThemeMode.System -> systemDark
        AppThemeMode.Light -> false
        AppThemeMode.Dark -> true
    }
    val activity = context as? Activity

    SideEffect {
        AppSettingsState.setActiveDarkTheme(useDarkTheme)
        activity?.window?.let { window ->
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = !useDarkTheme
                isAppearanceLightNavigationBars = !useDarkTheme
            }
        }
    }

    AppMobileTheme(darkTheme = useDarkTheme, dynamicColor = dynamicColorEnabled) {
        AppNavigation(modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val auth = FirebaseAuth.getInstance()
    val context = LocalContext.current
    val assistantBubbleEnabled by AppSettingsState.assistantBubbleEnabled
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val startDestination = if (auth.currentUser != null) "home" else "login"

    fun assistantRoute(gameId: String, level: Int? = null): String {
        return if (level == null) "assistant/$gameId" else "assistant/$gameId?level=$level"
    }

    fun goHome() {
        navController.navigate("home") {
            launchSingleTop = true
            popUpTo("home") { inclusive = false }
        }
    }

    fun goLearn() {
        navController.navigate("learn") { launchSingleTop = true }
    }

    fun goGames() {
        navController.navigate("select_game/all") { launchSingleTop = true }
    }

    fun goProfile() {
        navController.navigate("profile") { launchSingleTop = true }
    }

    fun goSettings() {
        navController.navigate("settings") { launchSingleTop = true }
    }

    fun goLogin() {
        navController.navigate("login") {
            launchSingleTop = true
            popUpTo("home") { inclusive = true }
        }
    }

    fun logout() {
        auth.signOut()
        AppSession.clear(context)
        navController.navigate("login") { popUpTo("home") { inclusive = true } }
    }

    Box(modifier = modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = startDestination, modifier = Modifier.fillMaxSize()) {
        composable("login") {
            LoginPage(
                onNavigateToRegister = { navController.navigate("register") },
                onLoginSuccess = { navController.navigate("home") { popUpTo("login") { inclusive = true } } }
            )
        }
        composable("register") { RegisterPage(onNavigateBack = { navController.popBackStack() }) }
        composable("home") {
            HomePage(
                onNavigateToGame = { gameType -> navController.navigate("select_game/$gameType") },
                onNavigateToLearn = ::goLearn,
                onNavigateToReport = { navController.navigate("report") },
                onNavigateToProfile = ::goProfile,
                onNavigateToSettings = ::goSettings,
                onNavigateToLevel = { gameId -> navController.navigate("level_select/$gameId") }
            )
        }
        composable("select_game/{type}") { backStackEntry ->
            val type = backStackEntry.arguments?.getString("type") ?: ""
            SelectGamePage(
                type = type,
                onBack = { navController.popBackStack() },
                onOpenLevel = { id -> navController.navigate("level_select/$id") },
                onGoHome = ::goHome,
                onOpenLearn = ::goLearn,
                onOpenProfile = ::goProfile,
                onOpenSettings = ::goSettings
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
        composable(
            route = "game/{gameId}/{level}?emotion={emotion}",
            arguments = listOf(
                navArgument("gameId") { type = NavType.StringType },
                navArgument("level") { type = NavType.StringType },
                navArgument("emotion") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("gameId") ?: ""
            val level = backStackEntry.arguments?.getString("level")?.toIntOrNull() ?: 1
            val emotion = backStackEntry.arguments?.getString("emotion")?.takeIf { it.isNotBlank() }
            when (id) {
                // Các game Nhận diện
                "3bcb2108-721c-4a15-a585-31f3084ed000" -> RecognizeEmotionPage(level = level, onBack = { navController.popBackStack() }, onOpenAssistant = { navController.navigate(assistantRoute("recognize_emotion", level)) })
                "33ecafaa-ec7e-40d2-9c67-ed0a29ac0051" -> GameClick2Page(level = level, onBack = { navController.popBackStack() }, onOpenAssistant = { navController.navigate(assistantRoute("game_click_2", level)) })
                "08bbffbf-d147-4556-bccb-b7621cafbf15" -> GameClick3Page(level = level, onBack = { navController.popBackStack() }, onOpenAssistant = { navController.navigate(assistantRoute("game_click_3", level)) })
                "aacaf79e-e15e-42a9-a3d1-a522720d919b" -> GameClick4Page(level = level, onBack = { navController.popBackStack() }, onOpenAssistant = { navController.navigate(assistantRoute("game_click_4", level)) })
                // Các game Biểu cảm
                "e05909f3-3dee-42a6-9a75-fd985b1bdf47" -> GameCVPage(level = level, onBack = { navController.popBackStack() }, onOpenAssistant = { navController.navigate(assistantRoute("gameCV", level)) })
                "61f5e09e-eefa-44c1-86e1-87dfceac3b8e" -> GameCV2Page(
                    level = level,
                    selectedEmotion = emotion,
                    onBack = { navController.popBackStack() },
                    onFinish = {
                        val targetRoute = "level_select/$id"
                        if (!navController.popBackStack(targetRoute, inclusive = false)) {
                            navController.navigate(targetRoute) {
                                launchSingleTop = true
                            }
                        }
                    },
                    onOpenAssistant = { navController.navigate(assistantRoute("game_cv_2", level)) }
                )
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
                onGoHome = ::goHome,
                onOpenGames = ::goGames,
                onOpenProfile = ::goProfile,
                onOpenSettings = ::goSettings
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
                onBack = { navController.popBackStack() }
            )
        }
        composable("settings") {
            SettingsPage(
                onBack = { navController.popBackStack() },
                onLogout = ::logout,
                onLogin = ::goLogin
            )
        }
    }

        if (assistantBubbleEnabled && shouldShowAssistantBubble(currentRoute, auth.currentUser != null)) {
            AssistantChatBubble(
                onClick = { navController.navigate(assistantRoute(assistantContext(currentRoute))) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 18.dp, bottom = 24.dp)
            )
        }
    }
}

private fun shouldShowAssistantBubble(route: String?, loggedIn: Boolean): Boolean {
    if (!loggedIn) return false
    if (route == null) return false
    return route != "login" &&
        route != "register" &&
        !route.startsWith("assistant") &&
        route != "settings" &&
        !route.startsWith("game/")
}

private fun assistantContext(route: String?): String {
    return when {
        route == null -> "home"
        route.startsWith("learn") -> "learn"
        route.startsWith("select_game") -> "select_game"
        route.startsWith("level_select") -> "level_select"
        route.startsWith("game/") -> "game"
        else -> route.substringBefore("/")
    }
}
