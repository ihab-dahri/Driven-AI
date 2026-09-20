plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.driven_ai"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.driven_ai"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    // 👇 --- AJOUTE CES LIGNES ICI --- 👇

    // 🧠 SDK Google Gemini (Pour le GenerativeModel, Chat et Function Calling)
    implementation("com.google.mediapipe:tasks-genai:0.10.14")



    // ⚡ Coroutines & Lifecycle (Pour exécuter les requêtes réseau sans bloquer l'interface)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")

    // (Optionnel) Si tu veux traiter des JSON facilement pour les réponses matérielles
    implementation("org.json:json:20240303")
}