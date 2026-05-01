plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false // Đổi sang kotlin-android
    alias(libs.plugins.ksp) apply false // Khai báo KSP
    id("com.google.gms.google-services") version "4.4.1" apply false
}