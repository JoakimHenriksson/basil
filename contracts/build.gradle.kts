plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.library")
}
kotlin {
    jvmToolchain(17)
    jvm()
    androidTarget()
    if (System.getProperty("os.name").startsWith("Mac")) {
        iosArm64(); iosSimulatorArm64(); iosX64()
    }
    sourceSets {
        commonMain.dependencies { implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3") }
    }
}
android { namespace = "app.basil.contracts"; compileSdk = 35; defaultConfig { minSdk = 26 } }
