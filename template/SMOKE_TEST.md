# Pre-release smoke test

Run this checklist before tagging and publishing a new version. It exercises each top-level recipe against a fresh Gradle project and confirms the transformed code still compiles — the thing the unit tests alone can't prove.

The automated `./gradlew smokeTest` task covers the bulk of this: it scaffolds a /tmp Gradle project per matrix cell, runs the recipe end-to-end via a nested Gradle process, and compiles the rewritten Java. Cells run in parallel (cap=3, see `src/smokeTest/resources/junit-platform.properties`).

This document covers the manual cells the automated runner can't reach.

## 1. Build the local jar

```bash
./gradlew clean publishToMavenLocal
```

Confirm the jar lands in `~/.m2/repository/{{group}}/{{artifact}}/{{initialVersion}}/`.

## 2. Manual /tmp bootstrap (sanity check)

The automated `./gradlew smokeTest` does this for you per cell — this section is only for one-off debugging when the runner result needs explaining.

```bash
mkdir /tmp/smoke-{{artifact}} && cd /tmp/smoke-{{artifact}}
gradle init --type basic --dsl kotlin --project-name smoke
```

Add to `build.gradle.kts`:

```kotlin
plugins {
    java
    id("org.openrewrite.rewrite") version "{{rewritePluginVersion}}"
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    rewrite("{{group}}:{{artifact}}:{{initialVersion}}")
}

rewrite {
    activeRecipe("{{rootPackage}}.ExampleRecipe")
}
```

Drop a fixture under `src/main/java/com/example/Example.java`, then:

```bash
./gradlew rewriteDryRun       # inspect proposed changes
./gradlew rewriteRun          # apply
./gradlew compileJava         # confirm it still compiles
```

## 3. Maven Central coordinates round-trip

After publishing to Maven Central, wait ~10 minutes for the artifact to propagate, then in a fresh `/tmp/consumer-test`:

```kotlin
dependencies {
    rewrite("{{group}}:{{artifact}}:<published-version>")
}
```

Run `./gradlew rewriteResolveDependencies`. Should resolve from Central, not mavenLocal.
