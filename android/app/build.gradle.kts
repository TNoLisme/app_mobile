import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp) // Áp dụng KSP
    id("com.google.gms.google-services")
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}

val backendUrl = localProperties
    .getProperty("backend.url", "http://10.0.2.2:8000/")
    .trim()
    .let { if (it.endsWith('/')) it else "$it/" }
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

android {
    namespace = "com.example.appmobile"
    compileSdk = 34 // Dùng số nguyên cho ổn định

    defaultConfig {
        applicationId = "com.example.appmobile"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "BACKEND_URL", "\"$backendUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        // Cấu hình compiler cho Jetpack Compose (vì đã hạ Kotlin xuống 1.9.22)
        kotlinCompilerExtensionVersion = "1.5.10"
    }
    androidResources {
        noCompress += "mp4"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")

    // --- ROOM DATABASE (SỬA LẠI ĐÂY) ---
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler) // Dòng này sẽ tạo file AppDatabase_Impl cho bạn

    // --- CÁC THƯ VIỆN KHÁC ---
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

val reverseBackendPort by tasks.registering(Exec::class) {
    group = "development"
    description = "Expose the local backend on port 8000 to a connected Android device."
    commandLine(
        androidComponents.sdkComponents.adb.get().asFile.absolutePath,
        "reverse",
        "tcp:8000",
        "tcp:8000"
    )
    isIgnoreExitValue = true
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    finalizedBy(reverseBackendPort)
}
