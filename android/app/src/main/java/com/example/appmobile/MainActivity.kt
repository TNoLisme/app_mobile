package com.example.appmobile

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.appmobile.data.garden.GardenRepository
import com.example.appmobile.data.garden.LearningEvent
import com.example.appmobile.data.local.AppDatabase
import com.example.appmobile.data.local.AppSession
import com.example.appmobile.data.remote.NetworkClient
import com.example.appmobile.data.repository.GameRepository
import com.example.appmobile.ui.catalog.GameUiCatalog
import com.example.appmobile.ui.components.DraggableAssistantBubble
import com.example.appmobile.ui.components.EgDesign
import com.example.appmobile.ui.components.LegalConsentDialog
import com.example.appmobile.ui.pages.assistant.ChatAction
import com.example.appmobile.ui.pages.assistant.ChatActionType
import com.example.appmobile.ui.pages.assistant.AssistantPage
import com.example.appmobile.ui.pages.auth.LoginPage
import com.example.appmobile.ui.pages.auth.RegisterPage
import com.example.appmobile.ui.pages.game.*
import com.example.appmobile.ui.pages.garden.GardenPage
import com.example.appmobile.ui.pages.home.HomePage
import com.example.appmobile.ui.pages.learn.EmotionDetailPage
import com.example.appmobile.ui.pages.learn.LearnPage
import com.example.appmobile.ui.pages.photobooth.PhotoBoothPage
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
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
    val legalAccepted by AppSettingsState.legalPolicyAccepted
    var showLegalNotice by remember { mutableStateOf(false) }

    LaunchedEffect(legalAccepted) {
        showLegalNotice = !legalAccepted
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
        if (showLegalNotice || !legalAccepted) {
            LegalConsentDialog(
                onAccept = {
                    AppSettingsState.setLegalPolicyAccepted(context, true)
                    showLegalNotice = false
                },
                onDismiss = {
                    activity?.finish()
                }
            )
        }
    }
}

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val auth = FirebaseAuth.getInstance()
    val context = LocalContext.current
    val persistedBackendUserId = remember(context) { AppSession.getBackendUserId(context) }
    val assistantBubbleEnabled by AppSettingsState.assistantBubbleEnabled
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val startDestination = if (persistedBackendUserId != null) "home" else "login"
    val repository = remember(context) {
        GameRepository(AppDatabase.getDatabase(context).gameContentDao(), NetworkClient.apiService)
    }
    val gardenRepository = remember(context) { GardenRepository(context) }
    val activeUserId = AppSession.currentBackendUserId() ?: persistedBackendUserId ?: auth.currentUser?.uid

    LaunchedEffect(activeUserId) {
        val userId = activeUserId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        repository.preloadGameProgress(
            userId = userId,
            gameIds = GameUiCatalog.games.map { it.id }
        )
        repository.preloadCvGameData(userId = userId)
    }

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

    fun goParentEmailSettings() {
        navController.navigate("settings_parent_email") { launchSingleTop = true }
    }

    fun handleAssistantAction(action: ChatAction) {
        when (action.type) {
            ChatActionType.OPEN_LEARNING -> goLearn()
            ChatActionType.OPEN_GAME -> goGames()
            ChatActionType.OPEN_REPORT,
            ChatActionType.ASK_CONFIRM_SEND_REPORT -> navController.navigate("report") { launchSingleTop = true }
            ChatActionType.OPEN_GARDEN -> navController.navigate("garden") { launchSingleTop = true }
            ChatActionType.OPEN_PHOTOBOOTH,
            ChatActionType.ASK_CONFIRM_SAVE_PHOTO -> navController.navigate("photobooth") { launchSingleTop = true }
            ChatActionType.OPEN_SETTINGS,
            ChatActionType.OPEN_PRIVACY_SETTINGS -> goSettings()
            ChatActionType.OPEN_PARENT_AREA,
            ChatActionType.OPEN_PARENT_EMAIL_SETTINGS -> goParentEmailSettings()
            ChatActionType.OPEN_EMOTION_LESSON -> {
                val emotionId = action.target?.takeIf { it.isNotBlank() } ?: "happy"
                navController.navigate("learn_detail/$emotionId") { launchSingleTop = true }
            }
            ChatActionType.START_EMOTION_CHALLENGE -> {
                val emotionId = action.target?.takeIf { it.isNotBlank() } ?: "happy"
                navController.navigate("game/${GameUiCatalog.GAME_CV_REQUEST}/1?emotion=$emotionId") {
                    launchSingleTop = true
                }
            }
        }
    }

    fun recordGardenGameCompleted(gameId: String, emotionId: String? = null, score: Int? = null) {
        gardenRepository.onLearningEvent(
            LearningEvent.GameCompleted(
                gameId = gameId,
                emotionId = emotionId,
                score = score
            )
        )
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(EgDesign.background)
    ) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .fillMaxSize()
                .background(EgDesign.background)
        ) {
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
                onNavigateToLevel = { gameId -> navController.navigate("level_select/$gameId") },
                onNavigateToPhotoBooth = { navController.navigate("photobooth") },
                onNavigateToGarden = { navController.navigate("garden") }
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
            when (id.lowercase()) {
                // Các game Nhận diện
                GameUiCatalog.GAME_RECOGNIZE_EMOTION,
                "6695afe0-6414-40a3-b688-b08a98cd2b61" -> EmotionsBoxPage(
                    level = level,
                    onBack = { navController.popBackStack() },
                    onOpenAssistant = { navController.navigate(assistantRoute("emotions_box", level)) },
                    onGameCompleted = { score -> recordGardenGameCompleted(id, emotionId = null, score = score) }
                )
                GameUiCatalog.GAME_FACE_ASSEMBLY,
                "eea09e6c-8c2f-4df1-a361-f5edc89d8281" -> FaceAssemblyPage(
                    level = level,
                    onBack = { navController.popBackStack() },
                    onOpenAssistant = { navController.navigate(assistantRoute("face_assembly", level)) },
                    onGameCompleted = { score -> recordGardenGameCompleted(id, emotionId = null, score = score) }
                )
                GameUiCatalog.GAME_EMOTION_MATCH,
                "afa91963-f75a-4d92-bcf4-72e4e53c84d2" -> EmotionMatchPage(
                    level = level,
                    onBack = { navController.popBackStack() },
                    onOpenAssistant = { navController.navigate(assistantRoute("emotion_match", level)) },
                    onGameCompleted = { score -> recordGardenGameCompleted(id, emotionId = null, score = score) }
                )
                GameUiCatalog.GAME_DETECTIVE,
                "17c0cc09-cec9-48dc-bf06-e574cf8bf303" -> DetectiveGamePage(
                    level = level,
                    onBack = { navController.popBackStack() },
                    onOpenAssistant = { navController.navigate(assistantRoute("detective_game", level)) },
                    onGameCompleted = { score -> recordGardenGameCompleted(id, emotionId = null, score = score) }
                )
                // Các game Biểu cảm
                GameUiCatalog.GAME_CV_STORY,
                "1b450620-ee43-4f60-bad6-1e214642999e" -> GameCVPage(level = level, onBack = { navController.popBackStack() }, onOpenAssistant = { navController.navigate(assistantRoute("gameCV", level)) })
                GameUiCatalog.GAME_CV_REQUEST,
                "3cf6130e-73f3-4146-8d73-d2709b4cf44e" -> GameCV2Page(
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
                onGoHome = ::goHome,
                onOpenGames = ::goGames,
                onOpenProfile = ::goProfile,
                onOpenSettings = ::goSettings
            )
        }
        composable("learn_detail/{emotionId}") { backStackEntry ->
            EmotionDetailPage(emotionId = backStackEntry.arguments?.getString("emotionId") ?: "", onBack = { navController.popBackStack() })
        }
        composable("report") {
            ReportPage(
                onBack = { navController.popBackStack() },
                onPlayNow = ::goGames,
                onUpdateEmail = ::goParentEmailSettings
            )
        }
        composable("photobooth") {
            PhotoBoothPage(
                onBack = { navController.popBackStack() },
                onGoHome = ::goHome
            )
        }
        composable("garden") {
            GardenPage(
                onBack = { navController.popBackStack() },
                onLearnEmotion = { emotionId -> navController.navigate("learn_detail/$emotionId") },
                onOpenGames = ::goGames,
                onOpenPhotoBooth = { navController.navigate("photobooth") },
                onOpenReport = { navController.navigate("report") },
                onStartEmotionChallenge = { emotionId ->
                    navController.navigate("game/${GameUiCatalog.GAME_CV_REQUEST}/1?emotion=$emotionId")
                }
            )
        }
        composable("assistant") {
            AssistantPage(
                onBack = { navController.popBackStack() },
                onChatAction = ::handleAssistantAction
            )
        }
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
                onBack = { navController.popBackStack() },
                onChatAction = ::handleAssistantAction
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
        composable("settings_parent_email") {
            SettingsPage(
                onBack = { navController.popBackStack() },
                onLogout = ::logout,
                onLogin = ::goLogin,
                openParentArea = true,
                openReportEmailEditor = true
            )
        }
    }

        val loggedInForAssistant = !activeUserId.isNullOrBlank()
        if (assistantBubbleEnabled && shouldShowAssistantBubble(currentRoute, loggedInForAssistant)) {
            DraggableAssistantBubble(
                onClick = {
                    navController.navigate(
                        assistantRoute(
                            assistantContext(
                                route = currentRoute,
                                args = currentBackStackEntry?.arguments
                            )
                        )
                    )
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun shouldShowAssistantBubble(route: String?, loggedIn: Boolean): Boolean {
    if (!loggedIn) return false
    if (route == null) return false
    return route != "login" &&
        route != "register" &&
        !route.startsWith("assistant")
}

private fun assistantContext(route: String?, args: Bundle?): String {
    return when {
        route == null -> "home"
        route.startsWith("learn_detail") -> {
            val emotionId = args?.getString("emotionId").orEmpty()
            if (emotionId.isBlank()) "learn" else "learn_$emotionId"
        }
        route.startsWith("learn") -> "learn"
        route.startsWith("select_game") -> "select_game"
        route.startsWith("level_select") -> "level_select"
        route.startsWith("game/") -> {
            val gameId = args?.getString("gameId").orEmpty()
            val emotion = args?.getString("emotion").orEmpty()
            when (gameId) {
                GameUiCatalog.GAME_CV_STORY,
                "1b450620-ee43-4f60-bad6-1e214642999e" -> "gameCV"
                GameUiCatalog.GAME_CV_REQUEST,
                "3cf6130e-73f3-4146-8d73-d2709b4cf44e" -> {
                    if (emotion.isBlank()) "game_cv_2" else "game_cv_2_$emotion"
                }
                GameUiCatalog.GAME_RECOGNIZE_EMOTION,
                "6695afe0-6414-40a3-b688-b08a98cd2b61" -> "emotions_box"
                GameUiCatalog.GAME_FACE_ASSEMBLY,
                "eea09e6c-8c2f-4df1-a361-f5edc89d8281" -> "face_assembly"
                GameUiCatalog.GAME_EMOTION_MATCH,
                "afa91963-f75a-4d92-bcf4-72e4e53c84d2" -> "emotion_match"
                GameUiCatalog.GAME_DETECTIVE,
                "17c0cc09-cec9-48dc-bf06-e574cf8bf303" -> "detective_game"
                else -> "game"
            }
        }
        route == "settings_parent_email" -> "parent_area"
        else -> route.substringBefore("/")
    }
}
