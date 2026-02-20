plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.kilu.pocketagent"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kilu.pocketagent"
        minSdk = 26
        targetSdk = 34
        versionCode = 38
        versionName = "0.3.8"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            val storeFilePath = System.getenv("KILU_RELEASE_STORE_FILE") ?: project.findProperty("KILU_RELEASE_STORE_FILE")?.toString()
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("KILU_RELEASE_STORE_PASSWORD") ?: project.findProperty("KILU_RELEASE_STORE_PASSWORD")?.toString()
                keyAlias = System.getenv("KILU_RELEASE_KEY_ALIAS") ?: project.findProperty("KILU_RELEASE_KEY_ALIAS")?.toString()
                keyPassword = System.getenv("KILU_RELEASE_KEY_PASSWORD") ?: project.findProperty("KILU_RELEASE_KEY_PASSWORD")?.toString()
                storeType = System.getenv("KILU_RELEASE_STORE_TYPE") ?: project.findProperty("KILU_RELEASE_STORE_TYPE")?.toString() ?: "JKS"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isDebuggable = false
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            buildConfigField("String", "DEFAULT_CONTROL_PLANE_URL", "\"http://10.0.2.2:8788\"")
            buildConfigField("String", "SERVER_KID", "\"dev_key_1\"")
            buildConfigField("String", "SERVER_PUBKEY_B64", "\"dev_public_key_base64_placeholder_abdsafkjasdflkj\"")
            buildConfigField("boolean", "ENFORCE_HTTPS", "false")
            applicationIdSuffix = ".dev"
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("String", "DEFAULT_CONTROL_PLANE_URL", "\"https://kilu-control-plane.heizungsrechner.workers.dev\"")
            buildConfigField("String", "SERVER_KID", "\"prod_key_1\"")
            buildConfigField("String", "SERVER_PUBKEY_B64", "\"prod_public_key_base64_placeholder_abdsafkjasdflkj\"")
            buildConfigField("boolean", "ENFORCE_HTTPS", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    
    // Core Logic
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Crypto
    implementation("org.bouncycastle:bcprov-jdk15to18:1.77")
    
    // QR
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation("com.google.zxing:core:3.5.3")
    
    // Biometric
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
