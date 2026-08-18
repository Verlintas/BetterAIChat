plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.betteraichat.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

}

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.android)
    api(libs.okhttp)
    api(libs.androidx.core.ktx)
    api(libs.room.runtime)
    api(libs.room.ktx)
    api(libs.snakeyaml)
    ksp(libs.room.compiler)
}
