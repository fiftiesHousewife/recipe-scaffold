plugins {
    java
    application
    alias(libs.plugins.versions)
}

repositories {
    mavenCentral()
}

application {
    // The fat jar's Main-Class. Also drives `./gradlew run --args=...` and
    // `./gradlew installDist`, which produces bin/recipescaffold and
    // bin/recipescaffold.bat launcher scripts at
    // build/install/recipescaffold/. Corporate-friendly path: clone +
    // ./gradlew installDist + run the script. No JBang or brew required.
    mainClass.set("recipescaffold.RecipeScaffold")
    applicationName = "recipescaffold"
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

// Wrapper de-dup. The canonical Gradle wrapper assets ship to scaffolded
// users from template/. The repo-root copy here is derived: the harness
// build needs them at the root to run, but the template's are the source of
// truth. `syncWrappersFromTemplate` copies template -> root; `check` runs
// `verifyWrapperParity` so a drift in either direction fails CI.
val syncWrappersFromTemplate by tasks.registering(Copy::class) {
    description = "Copy gradle wrapper assets from template/ to repo root."
    group = "build setup"
    from("template/gradle/wrapper") {
        into("gradle/wrapper")
    }
    from("template") {
        include("gradlew", "gradlew.bat")
        into(".")
    }
    into(".")
    // Copy preserves source POSIX permissions on POSIX FS (gradlew is 0755);
    // Windows users get the regenerated content via this task on next pull.
}

val verifyWrapperParity by tasks.registering {
    description = "Fail if root wrapper assets diverge from template's."
    group = "verification"
    doLast {
        val pairs = listOf(
                "gradle/wrapper/gradle-wrapper.jar" to "template/gradle/wrapper/gradle-wrapper.jar",
                "gradle/wrapper/gradle-wrapper.properties" to "template/gradle/wrapper/gradle-wrapper.properties",
                "gradlew" to "template/gradlew",
                "gradlew.bat" to "template/gradlew.bat"
        )
        val drift = pairs.filter { (root, tpl) ->
            !file(root).readBytes().contentEquals(file(tpl).readBytes())
        }
        if (drift.isNotEmpty()) {
            throw GradleException(
                    "Wrapper drift: ${drift.map { it.first }}. " +
                    "Run ./gradlew syncWrappersFromTemplate to fix.")
        }
    }
}
tasks.named("check") { dependsOn(verifyWrapperParity) }

// Fat jar so non-JBang users can `java -jar build/libs/recipescaffold.jar init …`.
// Bundles picocli (the only runtime dep) so the jar is self-contained.
tasks.jar {
    archiveBaseName.set("recipescaffold")
    archiveVersion.set("")
    manifest {
        attributes["Main-Class"] = "recipescaffold.RecipeScaffold"
        attributes["Implementation-Version"] = project.version.toString()
    }
    from({
        configurations.runtimeClasspath.get()
                .filter { it.name.endsWith("jar") }
                .map { zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    // Exclude signature files from the bundled deps (none today, but
    // future deps with signed jars would otherwise produce an
    // InvalidJarException at runtime).
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}
