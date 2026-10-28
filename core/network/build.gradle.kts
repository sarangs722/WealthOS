plugins { id("com.android.library") }
android {
    namespace="com.wealthos.publicdata"
    compileSdk=37
    buildToolsVersion="36.1.0"
    defaultConfig {minSdk=29;testInstrumentationRunner="androidx.test.runner.AndroidJUnitRunner"}
    compileOptions {sourceCompatibility=JavaVersion.VERSION_21;targetCompatibility=JavaVersion.VERSION_21}
}
dependencies {
    api(project(":core:finance"))
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
