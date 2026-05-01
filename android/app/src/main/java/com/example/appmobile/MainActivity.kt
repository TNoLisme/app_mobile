package com.example.appmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.appmobile.ui.pages.auth.LoginPage
import com.example.appmobile.ui.pages.auth.RegisterPage
import com.example.appmobile.ui.pages.home.HomePage
import com.example.appmobile.ui.theme.AppMobileTheme
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Bật hiển thị tràn viền
        enableEdgeToEdge()

        setContent {
            AppMobileTheme {
                // Scaffold tự động xử lý khoảng trống hệ thống (Status bar, Navigation bar)
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Truyền modifier chứa innerPadding vào để UI không bị đè
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

    // Kiểm tra trạng thái đăng nhập Firebase
    val startDestination = if (auth.currentUser != null) "home" else "login"

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        // Màn hình Đăng nhập
        composable("login") {
            LoginPage(
                onNavigateToRegister = { navController.navigate("register") },
                onLoginSuccess = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        // Màn hình Đăng ký
        composable("register") {
            RegisterPage(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Màn hình Trang chủ
        composable("home") {
            HomePage(
                onLogout = {
                    auth.signOut()
                    navController.navigate("login") {
                        popUpTo("home") { inclusive = true }
                    }
                },
                onNavigateToGame = { gameType ->
                    // Xử lý điều hướng game sau
                }
            )
        }
    }
}