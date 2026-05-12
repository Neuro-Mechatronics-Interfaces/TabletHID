import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.navigation.safe.args)
}

// Load signing credentials from local.properties (never committed to git).
val localProps = Properties().also { props ->
    val f = rootProject.file("local.properties")
    if (f.exists()) props.load(f.inputStream())
}

android {
    namespace = "com.tablet.hid"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tablet.hid"
        minSdk = 29
        targetSdk = 35
        versionCode = 44
        versionName = "1.7.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "COMMUNITY_API_BASE_URL",
            "\"${localProps.getProperty("COMMUNITY_API_BASE_URL", "")}\"")
    }

    signingConfigs {
        create("upload") {
            storeFile     = file(localProps["UPLOAD_KEY_PATH"] as? String ?: "missing.jks")
            storePassword = localProps["UPLOAD_KEY_STORE_PASSWORD"] as? String ?: ""
            keyAlias      = localProps["UPLOAD_KEY_ALIAS"]          as? String ?: ""
            keyPassword   = localProps["UPLOAD_KEY_PASSWORD"]       as? String ?: ""
        }
    }

    buildTypes {
        debug {
            buildConfigField("Boolean", "DEV_MODE", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("Boolean", "DEV_MODE", "false")
            signingConfig = signingConfigs.getByName("upload")
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
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.swiperefreshlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}