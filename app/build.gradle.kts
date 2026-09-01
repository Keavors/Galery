plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.keavors.gallery"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.keavors.gallery"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            // Left off until there is time to check every screen against it:
            // R8 without keep rules is exactly the sort of thing that works
            // everywhere except the video trimmer.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Signed with the debug key on purpose: the app is not published,
            // and a release build that cannot be installed cannot be measured —
            // and measuring a debug build of Compose measures the debugger.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        // The About screen shows BuildConfig.VERSION_NAME so it can never drift
        // from the version declared above.
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        unitTests {
            // Everything worth unit-testing (date grouping, zoom levels, URI
            // resolution rules) is plain Kotlin, so no emulator is needed.
            isIncludeAndroidResources = false
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.telephoto.zoomable.coil)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    testImplementation(libs.junit)
    // android.jar ships org.json as stubs that throw; tests need the real one.
    testImplementation(libs.json)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
