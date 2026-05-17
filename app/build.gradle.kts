plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.koukou"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.koukou"
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "1.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"https://zzj.abrdns.com\"")
            buildConfigField("String", "API_BASE_URL_BACKUP", "\"https://120.26.247.39\"")
            buildConfigField("String", "WS_URL", "\"wss://zzj.abrdns.com/ws\"")
            buildConfigField("String", "WS_URL_BACKUP", "\"wss://120.26.247.39/ws\"")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("String", "API_BASE_URL", "\"https://zzj.abrdns.com\"")
            buildConfigField("String", "API_BASE_URL_BACKUP", "\"https://120.26.247.39\"")
            buildConfigField("String", "WS_URL", "\"wss://zzj.abrdns.com/ws\"")
            buildConfigField("String", "WS_URL_BACKUP", "\"wss://120.26.247.39/ws\"")
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
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.core.ktx)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    implementation(libs.swiperefreshlayout)
    
    // Lifecycle
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.lifecycle.runtime)
    
    // Room
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)
    
    // Physics Animation (Spring Physics)
    implementation("androidx.dynamicanimation:dynamicanimation:1.0.0")
    
    // Others
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.glide)
    implementation(libs.ucrop)
    implementation(libs.permissions.dispatcher)
    implementation(libs.datastore.preferences.rxjava3)
    implementation(libs.rxjava3)
    annotationProcessor(libs.permissions.processor)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
