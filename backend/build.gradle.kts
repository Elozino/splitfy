plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(ktorLibs.plugins.ktor)
}

group = "com.elozino.splitify"
version = rootProject.version

application {
    mainClass = "io.ktor.server.netty.EngineMain"

    // Keep dev-mode reload off by default; enable with -Dio.ktor.development=true.
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=${providers.gradleProperty("ktor.development").getOrElse("false")}")
}

kotlin {
    jvmToolchain(libs.versions.jvmToolchain.get().toInt())
}

dependencies {
    implementation(projects.core)

    // Ktor server
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.auth)
    implementation(ktorLibs.server.auth.jwt)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.server.cors)
    implementation(ktorLibs.server.callId)
    implementation(ktorLibs.server.callLogging)
    implementation(ktorLibs.server.compression)
    implementation(ktorLibs.server.defaultHeaders)
    implementation(ktorLibs.server.forwardedHeader)
    implementation(ktorLibs.server.rateLimit)
    implementation(ktorLibs.server.requestValidation)
    implementation(ktorLibs.server.statusPages)
    implementation(ktorLibs.server.config.yaml)
    implementation(ktorLibs.server.swagger)
    implementation(ktorLibs.serialization.kotlinx.json)

    // Dependency injection
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.ktor)
    implementation(libs.koin.logger.slf4j)

    // Persistence
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.exposed.json)
    implementation(libs.hikaricp)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)

    // Security
    implementation(libs.bcrypt)

    // Observability
    implementation(libs.logback.classic)

    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)
    testImplementation(ktorLibs.client.contentNegotiation)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.h2database)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
