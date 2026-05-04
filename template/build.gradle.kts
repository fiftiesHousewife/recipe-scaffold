plugins {
    java
    jacoco
    alias(libs.plugins.openrewrite)
    alias(libs.plugins.versions)
    alias(libs.plugins.maven.publish)
}

group = "{{group}}"
version = "{{initialVersion}}"

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform(libs.openrewrite.recipe.bom))
    implementation(libs.openrewrite.java)
    implementation(libs.openrewrite.toml)
    implementation(libs.openrewrite.gradle)
    runtimeOnly(libs.openrewrite.java8)
    runtimeOnly(libs.openrewrite.java11)
    runtimeOnly(libs.openrewrite.java17)
    runtimeOnly(libs.openrewrite.java21)
    runtimeOnly(libs.openrewrite.java25)

    compileOnly(libs.jspecify)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
    testImplementation(libs.openrewrite.test)
    testImplementation(libs.openrewrite.properties)
    testImplementation(libs.openrewrite.gradle.tooling.api)
    testImplementation(gradleApi())
}

tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

jacoco {
    toolVersion = "0.8.14"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
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

tasks.withType<Javadoc> {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:all,-missing", "-quiet")
}

tasks.named<JavaCompile>("compileJava") {
    options.compilerArgs.add("-parameters")
    options.release.set({{javaTargetMain}})
}

tasks.named<JavaCompile>("compileTestJava") {
    options.compilerArgs.add("-parameters")
    options.release.set({{javaTargetTests}})
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of({{javaTargetTests}}))
    }
}

// Integration tests live in their own source set and drive an embedded Gradle
// daemon via withToolingApi(). The daemon is pinned to Gradle 8.14.3 because
// rewrite-gradle's AddDependency has a known catalog-handling regression on
// Gradle 9.x. That daemon's bundled Groovy/ASM cannot read Java 25 bytecode,
// so integrationTest compiles at release=21 and runs on a JDK 21 launcher.
//
// integrationTest's classpath deliberately excludes sourceSets.test.output:
// when {{javaTargetTests}} >= 25 the test classes are class file v69, which
// the embedded Gradle 8.14.3's bundled Groovy/ASM cannot read. Including them
// poisons the parser when withToolingApi() walks the classpath. Nothing in
// integrationTest should reference test/ helpers — keep them decoupled.
sourceSets {
    create("integrationTest") {
        java.srcDir("src/integrationTest/java")
        resources.srcDir("src/integrationTest/resources")
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

configurations {
    named("integrationTestImplementation") { extendsFrom(configurations.testImplementation.get()) }
    named("integrationTestRuntimeOnly") { extendsFrom(configurations.testRuntimeOnly.get()) }
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

    shouldRunAfter(tasks.test)
}

tasks.check {
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
    named("smokeTestImplementation") { extendsFrom(configurations.testImplementation.get()) }
    named("smokeTestRuntimeOnly") { extendsFrom(configurations.testRuntimeOnly.get()) }
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

    val rewritePluginVersion = versionCatalogs.named("libs")
            .findVersion("openrewrite").orElseThrow().requiredVersion
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

    shouldRunAfter(tasks.test, tasks.named("integrationTest"))
}

// Hard pre-publish gate: every path to Maven Central goes through smokeTest.
listOf(
        "publishAndReleaseToMavenCentral",
        "publishToMavenCentral",
        "publishAllPublicationsToMavenCentralRepository",
).forEach { name ->
    tasks.matching { it.name == name }.configureEach { dependsOn("smokeTest") }
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    coordinates(group.toString(), "{{artifact}}", version.toString())

    pom {
        name.set("{{recipeName}}")
        description.set("{{recipeDescription}}")
        url.set("https://github.com/{{githubOrg}}/{{githubRepo}}")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("{{authorId}}")
                name.set("{{authorName}}")
                email.set("{{authorEmail}}")
            }
        }
        scm {
            connection.set("scm:git:git://github.com/{{githubOrg}}/{{githubRepo}}.git")
            developerConnection.set("scm:git:ssh://github.com/{{githubOrg}}/{{githubRepo}}.git")
            url.set("https://github.com/{{githubOrg}}/{{githubRepo}}")
        }
    }
}
