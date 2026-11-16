import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    android {
        namespace = "com.mylive.shared.ui"
        compileSdk = 36
        minSdk = 26
        compilerOptions { jvmTarget = JvmTarget.JVM_17 }
    }
    jvm { compilerOptions { jvmTarget = JvmTarget.JVM_17 } }
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
        }
    }
}
