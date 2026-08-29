import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

/**
 * バージョンは Git タグ（リリースワークフロー）から差し込めるようにする。
 * 指定が無ければ手元のビルド用の既定値を使う。
 */
val appVersionName = (project.findProperty("appVersionName") as String?)
    ?: System.getenv("APP_VERSION_NAME")
    ?: "1.0.0"
val appVersionCode = ((project.findProperty("appVersionCode") as String?)
    ?: System.getenv("APP_VERSION_CODE"))
    ?.toIntOrNull()
    ?: 1

/** アプリ内アップデートが更新を探しに行く GitHub リポジトリ。 */
val githubRepository = (project.findProperty("githubRepository") as String?)
    ?: "TaichiOikawa/Diary"

/**
 * リリース署名は `keystore.properties`（コミットしない）か環境変数から読む。
 * どちらも無い場合は debug 鍵にフォールバックし、鍵を持たない環境でも
 * `assembleRelease` が通るようにする。
 */
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun signingValue(propertyKey: String, envKey: String): String? =
    keystoreProperties.getProperty(propertyKey) ?: System.getenv(envKey)

val releaseStoreFilePath = signingValue("storeFile", "RELEASE_STORE_FILE")
val hasReleaseSigning = !releaseStoreFilePath.isNullOrBlank() &&
    rootProject.file(releaseStoreFilePath).exists()

android {
    namespace = "com.amanospica.diary"
    // androidx.core-ktx 1.19 / lifecycle 2.11 が compileSdk 37 以上を要求するため
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.amanospica.diary"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // アプリ内アップデートが参照する GitHub リポジトリ
        buildConfigField("String", "GITHUB_REPOSITORY", "\"$githubRepository\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFilePath!!)
                storePassword = signingValue("storePassword", "RELEASE_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "RELEASE_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 署名鍵が無い環境では debug 鍵で署名する。
            // 未署名 APK は端末にインストールできず、CI の疎通確認にもならないため。
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // BuildConfig.VERSION_NAME と GITHUB_REPOSITORY をアップデート確認で使う
        buildConfig = true
    }
    // MigrationTestHelper が過去バージョンのスキーマを読めるようにする
    sourceSets.getByName("androidTest") {
        assets.srcDir("$projectDir/schemas")
    }
}

// Room のスキーマ JSON を出力し、将来のマイグレーション検証に使う
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
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

    // --- Phase 2: UI / ナビゲーション ---
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)

    // --- Phase 3: ブロックエディタ / メディア再生 ---
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    // --- Phase 4: ダッシュボード / セキュリティ ---
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.lifecycle.process)

    // --- Phase 1: データ層 ---
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.exifinterface)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}