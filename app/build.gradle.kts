plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.internetspeedmeeter"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.internetspeedmeeter"
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
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)
    implementation(libs.recyclerview)
    // SpeedChecker SDK — accurate speed test with 285+ global servers, up to 1 Gbps
    implementation("com.speedchecker:android-sdk:4.2.299")
    // MPAndroidChart for beautiful Weekly Usage bar chart
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}