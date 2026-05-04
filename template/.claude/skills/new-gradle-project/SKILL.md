---
name: new-gradle-project
description: Use this skill when the user is bootstrapping a new Gradle project (library, application, or recipe project) and wants it set up with current best practices — version catalog in TOML, minimal JUnit dependency declarations, the Ben-Manes versions plugin for update checking, the JVM args that silence the Java 21+ native-access warning, and a typical build.gradle.kts skeleton. Invoke when phrases like "new gradle project", "scaffold a gradle build", "set up a fresh gradle project", or "gradle project best practices" appear — or when the user is cloning a fresh template and asks for the modern conventions. For OpenRewrite-specific scaffolding, combine with the new-recipe skill.
---

# Setting up a new Gradle project

Opinionated baseline for a modern Gradle build. Apply this after `gradle init` (or after cloning a template) and before you start writing real code.

## 1. Version catalog in TOML

Centralize every dependency and plugin version in `gradle/libs.versions.toml`:

```toml
[versions]
junit = "6.1.0-M1"
assertj = "4.0.0-M1"
jspecify = "1.0.0"
versions = "0.53.0"

[libraries]
junit-jupiter          = { module = "org.junit.jupiter:junit-jupiter", version.ref = "junit" }
junit-platform-launcher = { module = "org.junit.platform:junit-platform-launcher", version.ref = "junit" }
assertj-core           = { module = "org.assertj:assertj-core", version.ref = "assertj" }
jspecify               = { module = "org.jspecify:jspecify", version.ref = "jspecify" }

[plugins]
versions = { id = "com.github.ben-manes.versions", version.ref = "versions" }
```

**Why**: single source of truth, typed accessors in the IDE, painless bumps. Never hardcode versions in `build.gradle.kts`.

Reference from the build file:

```kotlin
dependencies {
    testImplementation(libs.junit.jupiter)
    compileOnly(libs.jspecify)
}
```

## 2. Condense JUnit dependencies

`junit-jupiter` is an aggregator — it transitively pulls `api`, `params`, `engine`. Do this:

```kotlin
testImplementation(libs.junit.jupiter)
testRuntimeOnly(libs.junit.platform.launcher)
```

Not this (the old ceremony):

```kotlin
testImplementation(platform("org.junit:junit-bom:6.0.3"))
testImplementation(libs.junit.jupiter.api)
testImplementation(libs.junit.jupiter.params)
testRuntimeOnly(libs.junit.jupiter.engine)
testRuntimeOnly(libs.junit.platform.launcher)
```

Two lines instead of five. The BOM is only needed if you're pinning individual JUnit modules to different versions, which you shouldn't be.

## 3. Ben-Manes versions plugin

```kotlin
plugins {
    alias(libs.plugins.versions)
}
```

Then:

```bash
./gradlew dependencyUpdates
```

Prints a report of every dependency with a newer release available. Cheap release-hygiene win — include it in the pre-release checklist.

## 4. `gradle.properties`: silence Java 21+ native-access warnings

```properties
org.gradle.jvmargs=--add-opens=java.base/java.lang=ALL-UNNAMED --enable-native-access=ALL-UNNAMED
```

Gradle's native library calls `sun.misc.Unsafe` reflection paths that JDK 21+ warns about on startup. These flags silence the warnings without disabling the underlying behavior. Add them even if you're currently on JDK 17 — they're harmless on older JDKs.

## 5. `build.gradle.kts` skeleton

```kotlin
plugins {
    java
    alias(libs.plugins.versions)
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(libs.jspecify)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Compile production code at the minimum supported runtime target.
tasks.named<JavaCompile>("compileJava") {
    options.compilerArgs.add("-parameters")
    options.release.set(17)
}

// Compile tests at the toolchain version so tests can use newer language features.
tasks.named<JavaCompile>("compileTestJava") {
    options.compilerArgs.add("-parameters")
    options.release.set(25)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}
```

Two things worth noting:

- **Split `release` targets** for main vs tests. Production code compiles at the floor version you promise (17 is sensible for 2026-era libraries). Tests can use the newest language features because they never ship.
- **`-parameters`** preserves parameter names in the bytecode — required for frameworks that read them reflectively (Spring, JUnit-Jupiter parameterized tests, OpenRewrite's `MethodMatcher`, etc.).

## 6. Checklist

After `gradle init`:

- [ ] All versions in `gradle/libs.versions.toml`; none hardcoded in build scripts.
- [ ] JUnit dependency declared as two lines (aggregator + platform-launcher).
- [ ] Ben-Manes versions plugin wired via `libs.plugins.versions`.
- [ ] `gradle.properties` has the native-access JVM args.
- [ ] `compileJava` pinned to the supported runtime floor; `compileTestJava` at toolchain version.
- [ ] `-parameters` on both compile tasks.
- [ ] `./gradlew build` green.
- [ ] `./gradlew dependencyUpdates` shows no unexpected surprises.
