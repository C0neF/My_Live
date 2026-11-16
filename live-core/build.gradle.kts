plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

kotlin {
    jvmToolchain(17)
    sourceSets.main { kotlin.srcDir("src/main/java") }
}

dependencies {
    api(project(":shared-model"))
    api(libs.okhttp)
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:${libs.versions.coroutines.get()}")
    // Android supplies org.json; only JVM hosts need to package a JSON implementation.
    compileOnly(libs.jvm.json)
    api("javax.inject:javax.inject:1")
    implementation(libs.brotli.dec)
    testImplementation(libs.junit)
    testImplementation(libs.jvm.json)
    testImplementation(libs.mockk)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${libs.versions.coroutines.get()}")
}
