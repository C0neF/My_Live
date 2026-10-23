import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // No Kotlin plugin here on purpose: AGP 9 brings Kotlin support with it, and applying
    // kotlin.android on top fails with a duplicate `kotlin` extension. :app does the same.
    alias(libs.plugins.android.test)
}

android {
    namespace = "com.mylive.app.macrobenchmark"
    // Kept in step with :app, which is what this module instruments.
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        create("benchmark") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

androidComponents {
    beforeVariants(selector().all()) {
        it.enable = it.buildType == "benchmark"
    }
}

tasks.matching { it.name == "connectedBenchmarkAndroidTest" }.configureEach {
    dependsOn(":app:installBenchmark")
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.junit)
    implementation(libs.androidx.uiautomator)
}
