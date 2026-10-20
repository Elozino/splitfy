import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(ktorLibs.plugins.ktor)
}

group = "com.elozino.splitfy"
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

    // Converts the OpenAPI YAML to JSON for /openapi.json. kaml is the same parser
    // Ktor uses for OpenAPI YAML internally; it already arrives via
    // ktor-server-config-yaml, but is declared here because this module uses it directly.
    implementation(libs.kaml)

    // Outbound HTTP to third-party APIs (email delivery)
    implementation(ktorLibs.client.core)
    implementation(ktorLibs.client.cio)
    implementation(ktorLibs.client.contentNegotiation)

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
    // Ktor's OpenAPI model, used to validate the spec we serve. It only decodes from
    // JSON, so it cannot read the YAML directly — see OpenApiRoutes.
    testImplementation(ktorLibs.openapiSchema)
    testImplementation(ktorLibs.server.testHost)
    testImplementation(ktorLibs.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
    // Real PostgreSQL binaries started inside the test JVM: the migrations run
    // exactly as they do in production, without needing Docker.
    testImplementation(libs.embedded.postgres)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// Several jars declare implementations under the same
// `META-INF/services/<interface>` name. Shadow's default `EXCLUDE` keeps only
// the first copy it meets and drops the rest before any transformer sees them,
// so `flyway-database-postgresql`'s three-entry plugin list silently replaced
// `flyway-core`'s twenty-one. The casualty was `CoreResourceTypeProvider`, and
// without it Flyway stops recognising `V1__*.sql` as a migration at all: the
// fat JAR logged that it had skipped both scripts, then reported a healthy boot
// against an empty schema. `INCLUDE` lets every copy through to the transformer,
// which concatenates them back into one descriptor.
tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
}

/**
 * Fails the build when the packaged JAR declares fewer service providers than its
 * dependencies do.
 *
 * The Flyway breakage was invisible everywhere it could have been caught: the
 * build succeeded, every test passed, and the only symptom was one line in the
 * boot log saying two migrations had been skipped. Comparing the packaged
 * `META-INF/services` descriptors against the ones on the runtime classpath turns
 * that whole class of loss into a build failure, whichever library it happens to.
 */
val verifyPackagedServices = tasks.register("verifyPackagedServices") {
    description = "Checks that the fat JAR kept every ServiceLoader provider its dependencies declare."
    group = "verification"

    // Resolved here rather than inside the action: the configuration cache cannot
    // serialise a reference back to the script, only the values it produced.
    val runtimeJars: FileCollection = configurations.getByName("runtimeClasspath")
    val packagedJar = tasks.named<Jar>("shadowJar").flatMap { it.archiveFile }
    inputs.files(runtimeJars)
    inputs.file(packagedJar)

    doLast {
        val prefix = "META-INF/services/"
        fun readServices(file: File): Map<String, Set<String>> {
            val found = mutableMapOf<String, MutableSet<String>>()
            ZipFile(file).use { zip ->
                for (entry in zip.entries().asSequence()) {
                    if (entry.isDirectory || !entry.name.startsWith(prefix)) continue
                    // `INCLUDE` permits repeated entries under one name, so fold
                    // rather than overwrite.
                    val providers = found.getOrPut(entry.name.removePrefix(prefix)) { mutableSetOf() }
                    for (line in zip.getInputStream(entry).bufferedReader().readLines()) {
                        val provider = line.substringBefore('#').trim()
                        if (provider.isNotEmpty()) providers.add(provider)
                    }
                }
            }
            return found
        }

        val declared = mutableMapOf<String, MutableSet<String>>()
        for (jar in runtimeJars.files) {
            if (!jar.isFile || jar.extension != "jar") continue
            for ((service, providers) in readServices(jar)) {
                declared.getOrPut(service) { mutableSetOf() }.addAll(providers)
            }
        }

        val jarFile = packagedJar.get().asFile
        val packaged = readServices(jarFile)
        val dropped = declared
            .mapNotNull { (service, providers) ->
                val missing = providers - packaged.getOrDefault(service, emptySet())
                if (missing.isEmpty()) null else service to missing
            }
            .sortedBy { it.first }

        if (dropped.isNotEmpty()) {
            val detail = dropped.joinToString("\n") { (service, missing) ->
                "  " + service + " dropped " + missing.size + ": " + missing.sorted().joinToString(", ")
            }
            throw GradleException(
                jarFile.name + " is missing service providers its dependencies declare:\n" + detail +
                    "\nA ServiceLoader lookup that succeeds on the Gradle classpath will fail from this JAR.",
            )
        }

        logger.lifecycle("Verified " + declared.size + " service descriptors in " + jarFile.name)
    }
}

// CI builds the fat JAR, so hanging the check off that task is what puts it in
// front of a release rather than only in front of whoever runs it locally.
tasks.named("buildFatJar") {
    finalizedBy(verifyPackagedServices)
}
