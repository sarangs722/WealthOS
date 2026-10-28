plugins {
    id("com.android.library")
    id("com.google.devtools.ksp")
    id("androidx.room")
}
android {
    namespace = "com.wealthos.database"
    compileSdk = 37
    buildToolsVersion = "36.1.0"
    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
}
androidComponents {onVariants {variant->variant.sources.assets?.addStaticSourceDirectory("schemas")}}
room { schemaDirectory("$projectDir/schemas") }
dependencies {
    api(project(":core:finance"))
    api(project(":core:security"))
    api("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("net.zetetic:sqlcipher-android:4.20.0@aar")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.room:room-testing:2.8.5")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}
