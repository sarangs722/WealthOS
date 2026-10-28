plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
val coexistingRelease = providers.gradleProperty("wealthos.coexistingRelease").orNull?.let {
    require(it in listOf("true", "false")) { "wealthos.coexistingRelease must be true or false" }
    it.toBoolean()
} ?: false
// Isolated fictional UI test vault; never shares storage with either installed app.
val uiLab = providers.gradleProperty("wealthos.uiLab").orNull?.let {
    require(it in listOf("true", "false")) { "wealthos.uiLab must be true or false" }
    it.toBoolean()
} ?: false
android {
    namespace = "com.wealthos.app"
    compileSdk = 37
    buildToolsVersion = "36.1.0"
    defaultConfig {
        applicationId = "com.wealthos.app"
        minSdk = 29
        targetSdk = 37
        versionCode = 13
        versionName = "0.6.7"
        manifestPlaceholders["appLabel"] = "WealthOS"
        manifestPlaceholders["backupTestAuthority"] = if(uiLab) "com.wealthos.app.uxlab.test.truncated-backup" else "com.wealthos.app.test.truncated-backup"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
    buildTypes {
        debug {
            if(uiLab) {
                applicationIdSuffix = ".uxlab"
                manifestPlaceholders["appLabel"] = "WealthOS UI Lab"
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Explicit opt-in: a newly signed release can coexist with the user's development vault.
            if (coexistingRelease) {
                applicationIdSuffix = ".release"
                manifestPlaceholders["appLabel"] = "WealthOS Release"
            }
        }
    }
    lint { abortOnError = true }
}
dependencies {
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}
