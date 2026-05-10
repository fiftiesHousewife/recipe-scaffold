# Pre-release smoke test

The pre-publish gate is `./gradlew smokeTest`. It scaffolds a `/tmp` Gradle project per matrix cell, runs the recipe end-to-end via a nested Gradle process, and compiles the rewritten Java. Cells run in parallel (cap=3, see `src/smokeTest/resources/junit-platform.properties`). `publishAndReleaseToMavenCentral` structurally `dependsOn("smokeTest")`, so there is no path to Central that skips it.

This document covers the one cell the automated runner cannot reach: **post-publish round-trip from Maven Central**.

## Maven Central coordinates round-trip

After publishing, wait ~10 minutes for the artifact to propagate to Central, then in a fresh `/tmp/consumer-test`:

```kotlin
plugins {
    java
    id("org.openrewrite.rewrite") version "{{rewritePluginVersion}}"
}

repositories {
    mavenCentral()
}

dependencies {
    rewrite("{{group}}:{{artifact}}:<published-version>")
}

rewrite {
    activeRecipe("{{rootPackage}}.ExampleRecipe")
}
```

Run:

```bash
./gradlew rewriteResolveDependencies
```

Should resolve from Central, not `mavenLocal`. Catches: GAV typos that crept past `smokeTest` (which uses `mavenLocal`), classifier mismatches, and propagation lag if the publish silently half-succeeded.
