import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val finderVersion = Properties().apply {
    rootProject.file("../version.properties").inputStream().use { load(it) }
}
val releaseSigning = listOf("PICO_RELEASE_KEYSTORE_PATH", "PICO_RELEASE_STORE_PASSWORD",
    "PICO_RELEASE_KEY_ALIAS", "PICO_RELEASE_KEY_PASSWORD").associateWith { providers.environmentVariable(it).orNull }
val signingReady = releaseSigning.values.all { !it.isNullOrBlank() }
require(signingReady || releaseSigning.values.all { it.isNullOrBlank() }) {
    "Release signing requires all four PICO_RELEASE_* environment variables; see docs/releasing.md"
}
android {
    namespace = "local.ble.finder"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "local.ble.finder"
        minSdk = 31
        targetSdk = 36
        versionCode = providers.gradleProperty("finderVersionCode").orElse(finderVersion.getProperty("versionCode")).get().toInt().also {
            require(it in 1..2100000000) { "finderVersionCode must be 1..2100000000" }
        }
        versionName = providers.gradleProperty("finderVersionName").orElse(finderVersion.getProperty("versionName")).get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    signingConfigs {
        if (signingReady) create("release") {
            storeFile = file(releaseSigning.getValue("PICO_RELEASE_KEYSTORE_PATH")!!)
            storePassword = releaseSigning.getValue("PICO_RELEASE_STORE_PASSWORD")
            keyAlias = releaseSigning.getValue("PICO_RELEASE_KEY_ALIAS")
            keyPassword = releaseSigning.getValue("PICO_RELEASE_KEY_PASSWORD")
            enableV1Signing = false
            enableV2Signing = true
            enableV3Signing = true
        }
    }
    buildTypes {
        getByName("release") {
            isDebuggable = false
            if (signingReady) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        warningsAsErrors = true
        lintConfig = file("lint.xml")
    }
}
kotlin {
    jvmToolchain(21)
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
dependencies {
    implementation(project(":core"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    debugImplementation(libs.compose.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.ext.junit)
    testImplementation(libs.junit)
    testImplementation(libs.json)
}
