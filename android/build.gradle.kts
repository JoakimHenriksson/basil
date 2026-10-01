plugins { id("com.android.application"); kotlin("android"); kotlin("plugin.compose") }
android {
    namespace = "app.basil.android"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.basil.android"
        minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "0.1.0"
        buildConfigField("String", "API_URL", "\"${providers.gradleProperty("basil.apiUrl").getOrElse("http://10.0.2.2:8787")}\"")
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(project(":mobile"))
    implementation("androidx.activity:activity-compose:1.9.3")
}
