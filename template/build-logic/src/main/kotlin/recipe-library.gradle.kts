// Convention plugin for an OpenRewrite recipe library. Holds every piece of
// the build that is reusable across recipes — toolchain, gates, source sets,
// integration and smoke tests, publishing scaffolding. The consuming project's
// build.gradle.kts only declares its own identity (group, version, POM).
//
// Edit this file to change the gates centrally. Every recipe library scaffolded
// from recipescaffold inherits the same shape; if you fork the gates, do it
// here so a future `recipescaffold upgrade-skills` style refresh has a single
// place to land.

plugins {
    java
    jacoco
    id("org.openrewrite.rewrite")
    id("com.github.ben-manes.versions")
    id("com.vanniktech.maven.publish")
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
