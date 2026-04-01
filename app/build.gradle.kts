plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.tamengtautan"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.siva.tamengtautan"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // PERUBAHAN 1: Filter Arsitektur CPU (Untuk Menghemat Memori)
        ndk {
            // Hanya menyertakan library native untuk HP fisik (ARM)
            // Ini akan membuang file x86 (Emulator PC) yang ukurannya sangat besar
            // Jika mau test di Emulator Laptop, tambahkan "x86_64" sementara waktu.
            abiFilters.add("arm64-v8a")
            abiFilters.add("armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false      // Memadatkan kode Java/Kotlin
            isShrinkResources = false   // Membuang gambar/layout yang tidak terpakai
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
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.preference)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")

    // Gson
    implementation("com.google.code.gson:gson:2.11.0")

    // Supabase
    implementation ("com.squareup.okhttp3:okhttp:4.12.0")
    implementation ("com.google.code.gson:gson:2.10.1")

    // Retrofit
    implementation ("com.squareup.retrofit2:retrofit:2.11.0")
    implementation ("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation ("com.squareup.okhttp3:logging-interceptor:4.12.0")
}