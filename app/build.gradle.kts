// app module build file
plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
    id("dagger.hilt.android.plugin")
}

// Release signing is optional at build time: pass -PreleaseStoreFile=…
// plus the three secrets, or set them via CI properties. Nothing secret is
// committed; assembleDebug and `gradlew test` stay unaffected.
val signingStoreFile = project.findProperty("releaseStoreFile") as String?
val signingReady =
    listOf(
        signingStoreFile,
        project.findProperty("releaseStorePassword"),
        project.findProperty("releaseKeyAlias"),
        project.findProperty("releaseKeyPassword"),
    ).all { it != null && it.toString().isNotBlank() }

android {
    namespace = "com.example.newtube"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.example.newtube"
        minSdk = 24
        targetSdk = 34
        versionCode = 5
        versionName = "1.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (signingReady) {
            create("release") {
                storeFile = file(signingStoreFile!!)
                storePassword = project.property("releaseStorePassword").toString()
                keyAlias = project.property("releaseKeyAlias").toString()
                keyPassword = project.property("releaseKeyPassword").toString()
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (signingReady) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    packaging {
        resources {
            excludes += setOf("META-INF/*.kotlin_module", "META-INF/*.version")
        }
    }
    testOptions {
        // ViewModels log via android.util.Log; JVM tests get no-op stubs instead
        // of "Method e in android.util.Log not mocked".
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.03.00"))
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.47")
    kapt("com.google.dagger:hilt-android-compiler:2.47")

    // Retrofit & OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Media3 (ExoPlayer)
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    // Paging 3 (Shorts feed mediator)
    implementation("androidx.paging:paging-runtime-ktx:3.2.1")

    // Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    // Modules
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    implementation("io.coil-kt:coil-compose:2.5.0")

    // Testing
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.03.00"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("io.mockk:mockk:1.13.10")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
