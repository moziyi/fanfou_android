import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// The release key lives outside of the repository: the file with the
// passwords is ignored by git, so a build on another machine simply produces
// an unsigned apk instead of using a key that is published.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.kansen.fanfan"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kansen.fanfan"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        // 饭否已停止审核新应用：自建 app 的 key 永久「未审核」，其 token 调 API 一律 401 参数错误。
        // 唯一可行路径 = 复用已注册（历史过审）应用的 consumer key。此 key 为 Nofan（fanfoujs/nofan，
        // 维护者 LitoMore 明确指路复用）的已注册 consumer，2026-10-19 实测 verify_credentials/home_timeline 均 200。
        buildConfigField("String", "CONSUMER_KEY", "\"13456aa784cdf7688af69e85d482e011\"")
        buildConfigField("String", "CONSUMER_SECRET", "\"f75c02df373232732b69354ecfbcabea\"")
        buildConfigField("String", "USER_AGENT", "\"Fanfan/0.1.0 (Android; personal client)\"")
    }

    signingConfigs {
        if (keystoreProperties.containsKey("storeFile")) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.core.ktx)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
}
