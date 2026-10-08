import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Release signing: `keystore.properties` at the repo root (git-ignored) with
// storeFile, storePassword, keyAlias, keyPassword. Absent → release is unsigned.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "chat.operator.app"
    compileSdk = 36

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0-beta"
        // 32-bit and 64-bit ARM only: the the smaller test phone is armeabi-v7a; no x86 phones in scope.
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
    }

    // Branding lives in a flavour (SPEC §4.4). A manufacturer build adds a
    // flavour here plus `src/<flavour>/res/values/brand.xml` and an icon;
    // no code changes.
    flavorDimensions += "brand"
    productFlavors {
        create("operator") {
            dimension = "brand"
            // The app's permanent identity. GitHub-based, as F-Droid recommends for projects
            // without a domain; the Kotlin package stays chat.operator.app.
            applicationId = "io.github.operatorchat.operator"
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
        }
        getByName("release") {
            // Shipped builds are unshrunk (what has been tested; also what F-Droid builds).
            // `-Pminify` turns R8 on for experiments once the shrinker rules are verified.
            isMinifyEnabled = project.hasProperty("minify")
            isShrinkResources = project.hasProperty("minify")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core-matrix"))
    implementation(project(":core-beeper"))
    implementation(project(":core-push"))

    implementation(libs.kotlinx.coroutines)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.exifinterface)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}
