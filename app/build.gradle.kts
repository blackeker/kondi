plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.myanim.kondi"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.myanim.kondi"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // Release imzası: Gerçek bir keystore yoksa debug anahtarı ile imzalamak yayın
            // güvenliği açığıdır (APK değiştirilebilir, Play'e yüklenemez).
            // Keystore tanımlıysa onu kullan, değilse açık uyarı ver.
            val releaseStorePath: String? = providers.gradleProperty("KONDI_KEYSTORE_PATH").orNull
                ?: System.getenv("KONDI_KEYSTORE_PATH")
            val releaseStorePassword: String? = providers.gradleProperty("KONDI_KEYSTORE_PASSWORD").orNull
                ?: System.getenv("KONDI_KEYSTORE_PASSWORD")
            val releaseKeyAlias: String? = providers.gradleProperty("KONDI_KEY_ALIAS").orNull
                ?: System.getenv("KONDI_KEY_ALIAS")
            val releaseKeyPassword: String? = providers.gradleProperty("KONDI_KEY_PASSWORD").orNull
                ?: System.getenv("KONDI_KEY_PASSWORD")

            if (releaseStorePath != null && releaseStorePassword != null &&
                releaseKeyAlias != null && releaseKeyPassword != null
            ) {
                signingConfig = signingConfigs.create("release") {
                    storeFile = file(releaseStorePath)
                    storePassword = releaseStorePassword
                    keyAlias = releaseKeyAlias
                    keyPassword = releaseKeyPassword
                }
            } else {
                signingConfig = signingConfigs.getByName("debug")
                logger.warn(
                    "KONDİ: Release keystore tanımlı değil (KONDI_KEYSTORE_PATH, KONDI_KEYSTORE_PASSWORD, " +
                        "KONDI_KEY_ALIAS, KONDI_KEY_PASSWORD). Release derlemesi debug anahtarıyla imzalanıyor - " +
                        "BU YAYINLANMA İÇİN UYGUN DEĞİLDİR."
                )
            }
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
    lint {
        checkReleaseBuilds = true
        abortOnError = false
    }
    testOptions {
        unitTests {
            // android.util.Log / Timber gibi Android API'leri saf unit testlerde
            // "not mocked" hatası yerine stub döndürsün.
            isReturnDefaultValues = true
            // Robolectric tabanlı testler kaynaklara (assets/resources) ihtiyaç duyar.
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        // Room'un ürettiği şema JSON'ları migration testlerinde kullanılabilsin.
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

ksp {
    // Room şema JSON'ları dışa aktarılır: migration testleri yazılabilir ve
    // migration hataları CI'da yakalanabilir.
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    
    // Networking & Parsing
    implementation(libs.jsoup)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-jackson:2.9.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.12.7")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.12.7")
    
    // Image Loading
    implementation(libs.coil)
    
    // Navigation
    implementation(libs.navigation.compose)
    
    // Media Playback
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.effect)
    implementation(libs.mediarouter)
    implementation(libs.play.services.cast.framework)
    implementation(libs.play.services.auth)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.gson)

    // Room Database
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.localbroadcastmanager)
    
    // yt-dlp & FFmpeg
    implementation(libs.youtubedl.android)
    implementation(libs.ffmpeg.kit.https)

    testImplementation(libs.junit)
    testImplementation("org.json:json:20231013")
    // Deterministik coroutine testleri (runTest, StandardTestDispatcher) için.
    // "delay(100) ile bekle" tekniği yerine bunun kullanılması gerekir.
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(libs.timber)

}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
