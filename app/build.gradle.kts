
plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.nailit"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.nailit"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "NEON_DATA_API_KEY", "\"${project.findProperty("NEON_DATA_API_KEY") ?: ""}\"")
        buildConfigField("String", "NEON_CLIENT_ID", "\"${project.findProperty("NEON_CLIENT_ID") ?: ""}\"")
        buildConfigField("String", "NEON_CLIENT_SECRET", "\"${project.findProperty("NEON_CLIENT_SECRET") ?: ""}\"")
        buildConfigField("String", "NEON_AUTH_MODE", "\"${project.findProperty("NEON_AUTH_MODE") ?: "password"}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.camera.view)
    // For GG API
    implementation("com.google.android.libraries.places:places:3.5.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:1.8.0"))

    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.android.gms:play-services-maps:18.2.0")



    //Google API

    implementation("com.google.android.libraries.places:places:3.5.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:1.8.0"))

    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.android.gms:play-services-maps:18.2.0")

    //Detect hand and nail

    implementation ("com.google.mediapipe:tasks-vision:0.10.14")
    implementation ("androidx.camera:camera-camera2:1.3.1")
    implementation ("androidx.camera:camera-lifecycle:1.3.1")
    implementation ("androidx.camera:camera-view:1.3.1")


    //Retrofit + OkHttp + Gson
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    //EncryptedSharedPreferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    //RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    //Glide
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")
    implementation(libs.camera.lifecycle)


    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
