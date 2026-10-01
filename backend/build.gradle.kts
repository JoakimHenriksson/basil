plugins { kotlin("jvm"); kotlin("plugin.serialization"); application }
kotlin { jvmToolchain(17) }
application { mainClass.set("app.basil.backend.ApplicationKt") }
dependencies {
    implementation(project(":contracts"))
    implementation("io.ktor:ktor-server-netty:3.0.3")
    implementation("io.ktor:ktor-server-content-negotiation:3.0.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")
    implementation("io.ktor:ktor-server-status-pages:3.0.3")
    implementation("org.jsoup:jsoup:1.21.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("ch.qos.logback:logback-classic:1.5.12")
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.3")
    testImplementation("io.ktor:ktor-server-test-host:3.0.3")
}
tasks.test { useJUnitPlatform() }
