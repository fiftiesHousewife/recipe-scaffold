plugins {
    java
    alias(libs.plugins.versions)
}

repositories {
    mavenCentral()
}

// No toolchain spec on purpose: the harness is a developer/CI tool, run
// against whatever JDK is on PATH. JDK 17+ is required (the JBang script
// uses //JAVA 17+); we enforce that via `options.release` rather than a
// toolchain so we don't drag in Foojay auto-provisioning.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

// The JBang script doubles as the production source. Pointing the main
// source set at jbang/ keeps `jbang jbang/RecipeScaffold.java` working
// while letting Gradle compile the same file for the in-repo TestKit
// harness. The //DEPS, //JAVA, and shebang lines at the top are Java
// line comments — javac and the IDE ignore them.
sourceSets {
    main {
        java.setSrcDirs(listOf("jbang"))
    }
}

dependencies {
    implementation(libs.picocli)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
    testImplementation(gradleTestKit())
}

tasks.withType<Test> {
    useJUnitPlatform()
    // The harness scaffolds and runs Gradle in nested processes; give
    // it some headroom and forward its stdout/stderr so failures are
    // diagnosable from CI logs without re-running with --info.
    maxHeapSize = "1g"
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
