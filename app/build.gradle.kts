plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "jp.haruto.phonebounce"
    compileSdk = 35

    defaultConfig {
        applicationId = "jp.haruto.phonebounce"
        minSdk = 30
        targetSdk = 35
        versionCode = 5
        versionName = "0.4.1"
    }

    // 署名鍵: CI は Secret DEBUG_KEYSTORE_B64 を展開して PB_DEBUG_KEYSTORE に渡す（DreamCamera と同じ鍵）。
    // 無いときは、この機械の既定のデバッグ鍵（スマホに上書きはできない）。
    signingConfigs {
        getByName("debug") {
            val ks = System.getenv("PB_DEBUG_KEYSTORE")
            if (ks != null) {
                storeFile = file(ks)
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("debug") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // 見た目の描画テスト（Robolectric）が、Android の部品（Canvas など）を PC 上で動かすために必要
    testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
