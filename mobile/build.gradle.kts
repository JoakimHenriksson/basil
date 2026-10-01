plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.library")
    id("org.jetbrains.compose")
    kotlin("plugin.compose")
}
kotlin {
    jvmToolchain(17)
    androidTarget()
    if (System.getProperty("os.name").startsWith("Mac")) {
        listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach {
            it.binaries.framework { baseName = "BasilUI"; isStatic = true }
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":contracts"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation("io.coil-kt.coil3:coil-compose:3.0.4")
            implementation("io.coil-kt.coil3:coil-network-ktor3:3.0.4")
            implementation("io.ktor:ktor-client-core:3.0.3")
            implementation("io.ktor:ktor-client-content-negotiation:3.0.3")
            implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("io.ktor:ktor-client-mock:3.0.3")
        }
        androidUnitTest.dependencies { implementation(kotlin("test-junit")) }
        androidMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:3.0.3")
            implementation("androidx.core:core-ktx:1.13.1")
            implementation("androidx.activity:activity-compose:1.9.3")
            implementation("com.journeyapps:zxing-android-embedded:4.3.0")
        }
        if (System.getProperty("os.name").startsWith("Mac")) {
            iosMain.dependencies { implementation("io.ktor:ktor-client-darwin:3.0.3") }
        }
    }
}
android {
    namespace = "app.basil.mobile"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
}
