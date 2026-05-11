# Pre-release smoke test

The pre-publish gate is `./gradlew smokeTest` — a matrix runner that scaffolds a `/tmp` Gradle project per cell, runs the recipe end-to-end via a nested Gradle process, and compiles the rewritten Java. `publishAndReleaseToMavenCentral` structurally `dependsOn("smokeTest")`, so there is no path to Central that skips it.

If you publish via tag-driven CI (`.github/workflows/release.yml`), the `central-roundtrip` job exercises the post-publish check below automatically — wait for it to go green before announcing the release. **This file is the manual equivalent for releases cut outside CI.**

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
