// Convention plugin for an OpenRewrite recipe library. Holds every piece of
// the build that is reusable across recipes — toolchain, gates, source sets,
// integration and smoke tests, publishing scaffolding. The consuming project's
// build.gradle.kts only declares its own identity (group, version, POM).
//
// Edit this file to change the gates centrally. Every recipe library scaffolded
// from recipescaffold inherits the same shape; if you fork the gates, do it
// here so a future `recipescaffold upgrade-skills` style refresh has a single
// place to land.

import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import com.github.spotbugs.snom.SpotBugsTask
import groovy.json.JsonSlurper

plugins {
    java
    jacoco
    id("org.openrewrite.rewrite")
    id("com.github.ben-manes.versions")
    id("com.vanniktech.maven.publish")
    id("com.github.spotbugs")
}

// Programmatic catalog access: the typed `libs` accessor isn't generated for
// convention scripts without a known kotlin-dsl hack. Going through the
// extension keeps the build readable and avoids that hack.
val libs = versionCatalogs.named("libs")
fun lib(alias: String) = libs.findLibrary(alias).orElseThrow { IllegalStateException("missing library: $alias") }
fun ver(alias: String) = libs.findVersion(alias).orElseThrow { IllegalStateException("missing version: $alias") }.requiredVersion

repositories {
    mavenCentral()
}

val javaTargetMain = providers.gradleProperty("recipeLibrary.javaTargetMain").orElse("17").get().toInt()
val javaTargetTests = providers.gradleProperty("recipeLibrary.javaTargetTests").orElse("25").get().toInt()

dependencies {
    "implementation"(platform(lib("openrewrite-recipe-bom")))
    "implementation"(lib("openrewrite-java"))
    "implementation"(lib("openrewrite-toml"))
    "implementation"(lib("openrewrite-gradle"))
    "runtimeOnly"(lib("openrewrite-java8"))
    "runtimeOnly"(lib("openrewrite-java11"))
    "runtimeOnly"(lib("openrewrite-java17"))
    "runtimeOnly"(lib("openrewrite-java21"))
    "runtimeOnly"(lib("openrewrite-java25"))

    "compileOnly"(lib("jspecify"))

    "compileOnly"(lib("lombok"))
    "annotationProcessor"(lib("lombok"))

    // Refaster recipe authoring. Annotation processor generates the Recipe
    // class at compile time; the runtime API ships in rewrite-templating;
    // @BeforeTemplate / @AfterTemplate come from Errorprone Refaster.
    "implementation"(lib("openrewrite-templating"))
    "annotationProcessor"(lib("openrewrite-templating"))
    "compileOnly"(lib("errorprone-core")) {
        // Match moderneinc/rewrite-recipe-starter: Errorprone pulls in
        // auto-service annotations and a stale dataflow-errorprone that
        // collide with cleaner alternatives already on the classpath.
        exclude(group = "com.google.auto.service", module = "auto-service-annotations")
        exclude(group = "io.github.eisop", module = "dataflow-errorprone")
    }

    "testImplementation"(lib("junit-jupiter"))
    "testRuntimeOnly"(lib("junit-platform-launcher"))
    "testImplementation"(lib("assertj-core"))
    "testImplementation"(lib("openrewrite-test"))
    "testImplementation"(lib("openrewrite-properties"))
    "testImplementation"(lib("openrewrite-gradle-tooling-api"))
    "testImplementation"(gradleApi())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    finalizedBy(tasks.named("jacocoTestReport"))
}

jacoco {
    toolVersion = "0.8.14"
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

// signAllPublications() pulls signing into every publishToMavenLocal, including
// the one smokeTest depends on. In CI there's no GPG key, so signing fails and
// smokeTest never runs. Throwaway smoke projects resolve unsigned artifacts
// fine, so make signing a no-op when no key is configured. Local publish to
// Maven Central is unaffected because the key is in ~/.gradle/gradle.properties.
tasks.withType<Sign>().configureEach {
    onlyIf("a signing key is configured") {
        project.hasProperty("signing.keyId")
                || project.hasProperty("signingInMemoryKey")
                || System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey") != null
    }
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:all,-missing", "-quiet")
}

tasks.named<JavaCompile>("compileJava") {
    options.compilerArgs.add("-parameters")
    // Tells the rewrite-templating annotation processor to read the parser
    // classpath from src/main/resources rather than recomputing it. Required
    // for Refaster recipes; harmless when there are none.
    options.compilerArgs.add("-Arewrite.javaParserClasspathFrom=resources")
    options.release.set(javaTargetMain)
}

tasks.named<JavaCompile>("compileTestJava") {
    options.compilerArgs.add("-parameters")
    options.release.set(javaTargetTests)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaTargetTests))
    }
}

// Integration tests live in their own source set and drive an embedded Gradle
// daemon via withToolingApi(). The daemon is pinned to Gradle 8.14.3 because
// rewrite-gradle's AddDependency has a known catalog-handling regression on
// Gradle 9.x. That daemon's bundled Groovy/ASM cannot read Java 25 bytecode,
// so integrationTest compiles at release=21 and runs on a JDK 21 launcher.
//
// integrationTest's classpath deliberately excludes sourceSets.test.output:
// when javaTargetTests >= 25 the test classes are class file v69, which the
// embedded Gradle 8.14.3's bundled Groovy/ASM cannot read. Including them
// poisons the parser when withToolingApi() walks the classpath. Nothing in
// integrationTest should reference test/ helpers — keep them decoupled.
sourceSets {
    create("integrationTest") {
        java.srcDir("src/integrationTest/java")
        resources.srcDir("src/integrationTest/resources")
        compileClasspath += sourceSets["main"].output
        runtimeClasspath += sourceSets["main"].output
    }
}

configurations {
    named("integrationTestImplementation") { extendsFrom(configurations["testImplementation"]) }
    named("integrationTestRuntimeOnly") { extendsFrom(configurations["testRuntimeOnly"]) }
}

// rewrite-java-25 is compiled at JDK 25 bytecode (class file v69). When
// withToolingApi() spins up the embedded Gradle 8.14.3 in-process, its
// bundled Groovy/ASM walks the test JVM's classpath during _BuildScript_
// semantic analysis and bombs on the first v69 class. Production users still
// ship with rewrite-java-25 (it is runtimeOnly on main); this exclusion is
// only safe because integration tests don't exercise Java 25 source parsing.
// Drop this once withToolingApi() can target a Gradle 9.x with bundled
// Groovy 4.0.27+ (i.e. when rewrite-gradle's catalog regression on 9.x is
// fixed and we can re-pin the embedded Gradle).
configurations.named("integrationTestRuntimeClasspath") {
    exclude(group = "org.openrewrite", module = "rewrite-java-25")
}

tasks.named<JavaCompile>("compileIntegrationTestJava") {
    options.compilerArgs.add("-parameters")
    options.release.set(21)
}

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "Runs integration tests that drive an embedded Gradle via withToolingApi()."
    group = "verification"
    useJUnitPlatform()
    maxHeapSize = "2g"

    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    })

    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath

    shouldRunAfter(tasks.named("test"))
}

tasks.named("check") {
    dependsOn(integrationTest)
}

// Smoke tests: scaffold a fresh /tmp Gradle project per recipe variant, run the
// recipe end-to-end via a nested Gradle process, and confirm the rewritten Java
// still compiles. Pinned to Gradle 8.14.3 in the smoke project to sidestep the
// Gradle 9 catalog-handling regression. Daemon runs on JDK 21 because Gradle
// 8.x can't host JDK 25.
sourceSets {
    create("smokeTest")
}

configurations {
    named("smokeTestImplementation") { extendsFrom(configurations["testImplementation"]) }
    named("smokeTestRuntimeOnly") { extendsFrom(configurations["testRuntimeOnly"]) }
}

tasks.named<JavaCompile>("compileSmokeTestJava") {
    options.compilerArgs.add("-parameters")
    options.release.set(21)
}

tasks.register<Test>("smokeTest") {
    description = "Scaffolds a /tmp Gradle project per recipe variant, runs the recipe, " +
            "and confirms the rewritten Java compiles. Slower than integrationTest — " +
            "depends on jar + publishToMavenLocal so the throwaway projects can resolve " +
            "the recipe via coordinates."
    group = "verification"
    useJUnitPlatform()

    dependsOn(tasks.named("publishToMavenLocal"))

    val jdk21Home = javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    }.map { it.metadata.installationPath.asFile.absolutePath }

    val rewritePluginVersion = ver("openrewrite")
    systemProperty("smokeTest.projectGroup", project.group.toString())
    systemProperty("smokeTest.projectVersion", project.version.toString())
    systemProperty("smokeTest.rewritePluginVersion", rewritePluginVersion)
    systemProperty("smokeTest.projectRoot", project.projectDir.absolutePath)
    systemProperty("smokeTest.buildDir", layout.buildDirectory.get().asFile.absolutePath)

    doFirst {
        systemProperty("smokeTest.jdk21Home", jdk21Home.get())
    }

    testClassesDirs = sourceSets["smokeTest"].output.classesDirs
    classpath = sourceSets["smokeTest"].runtimeClasspath

    shouldRunAfter(tasks.named("test"), tasks.named("integrationTest"))
}

// Hard pre-publish gate: every path to Maven Central goes through smokeTest.
listOf(
        "publishAndReleaseToMavenCentral",
        "publishToMavenCentral",
        "publishAllPublicationsToMavenCentralRepository",
).forEach { name ->
    tasks.matching { it.name == name }.configureEach { dependsOn("smokeTest") }
}

// ---- Coverage gate ---------------------------------------------------------
// Defaults to a no-op (no rules → trivially passes) so a fresh scaffold with
// zero recipes still builds. Set `recipeLibrary.minLineCoverage=0.7` (or any
// 0.0–1.0 ratio) in gradle.properties to start enforcing line coverage.
val minLineCoverage = providers.gradleProperty("recipeLibrary.minLineCoverage")
        .map { it.toBigDecimal() }

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
    if (minLineCoverage.isPresent) {
        violationRules {
            rule {
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = minLineCoverage.get()
                }
            }
        }
    }
}
tasks.named("check") { dependsOn(tasks.named("jacocoTestCoverageVerification")) }

// ---- SpotBugs gate ---------------------------------------------------------
// SpotBugs is wired into `check` automatically once the plugin is applied.
// Default mode is non-blocking (ignoreFailures=true) so the gate surfaces
// findings without breaking unrelated work; flip
// `recipeLibrary.spotbugsStrict=true` in gradle.properties to make any bug
// fail the build.
val spotbugsStrict = providers.gradleProperty("recipeLibrary.spotbugsStrict")
        .map { it.toBoolean() }
        .orElse(false)

spotbugs {
    ignoreFailures.set(spotbugsStrict.map { !it })
    effort.set(com.github.spotbugs.snom.Effort.DEFAULT)
    reportLevel.set(com.github.spotbugs.snom.Confidence.MEDIUM)
}

tasks.withType<SpotBugsTask>().configureEach {
    reports.create("html") { required.set(true) }
    reports.create("xml") { required.set(true) }
}

// ---- Stale-dependency gate -------------------------------------------------
// Configures `dependencyUpdates` to ignore prerelease candidates so we don't
// thrash on alpha/beta/rc/milestone churn, then registers
// `verifyDependencies` which depends on the report and fails when any stable
// upgrade is available. `verifyDependencies` runs as part of `check` only
// when `recipeLibrary.failOnStaleDependencies=true` is set; default is opt-in
// so a fresh checkout doesn't fail the moment upstream cuts a release.
tasks.withType<DependencyUpdatesTask>().configureEach {
    rejectVersionIf {
        val version = candidate.version.lowercase()
        listOf("alpha", "beta", "-rc", ".rc", "-m", ".m", "milestone", "preview", "snapshot")
                .any { version.contains(it) }
    }
    outputFormatter = "json"
    outputDir = layout.buildDirectory.dir("dependencyUpdates").get().asFile.path
    reportfileName = "report"
}

val verifyDependencies = tasks.register("verifyDependencies") {
    description = "Fails if any dependency has a non-prerelease upgrade available."
    group = "verification"
    dependsOn(tasks.named("dependencyUpdates"))
    doLast {
        val report = layout.buildDirectory.file("dependencyUpdates/report.json").get().asFile
        if (!report.exists()) {
            throw GradleException("dependencyUpdates report missing at $report")
        }
        @Suppress("UNCHECKED_CAST")
        val parsed = JsonSlurper().parse(report) as Map<String, Any?>
        val outdated = parsed["outdated"] as Map<String, Any?>?
        val deps = (outdated?.get("dependencies") as List<Map<String, Any?>>?).orEmpty()
        if (deps.isNotEmpty()) {
            val summary = deps.joinToString("\n  - ") { d ->
                val available = d["available"] as Map<String, Any?>?
                val target = available?.get("release")
                        ?: available?.get("milestone")
                        ?: available?.get("integration")
                "${d["group"]}:${d["name"]} ${d["version"]} -> $target"
            }
            throw GradleException(
                    "Stale dependencies (${deps.size}). Bump in libs.versions.toml " +
                            "or run ./gradlew dependencyUpdates for details:\n  - $summary")
        }
    }
}

if (providers.gradleProperty("recipeLibrary.failOnStaleDependencies").map { it.toBoolean() }.orElse(false).get()) {
    tasks.named("check") { dependsOn(verifyDependencies) }
}
