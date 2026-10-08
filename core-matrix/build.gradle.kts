plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "chat.operator.core.matrix"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // Trixnity 5's Room repositories use Kotlin context parameters.
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

dependencies {
    api(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serialization.json)

    // The Matrix protocol layer. Never write our own protocol or crypto code (SPEC §4.1).
    api(libs.trixnity.client)
    implementation(libs.trixnity.repository.room)
    implementation(libs.trixnity.media.okio)
    implementation(libs.trixnity.cryptodriver.libolm)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    // The store is encrypted at rest (SPEC §4.2): SQLCipher under Room, key in the Android keystore.
    implementation(libs.sqlcipher.android)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}
