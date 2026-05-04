---
name: smoke-test
description: Use this skill when designing or extending a pre-release smoke-test procedure for an OpenRewrite recipe project — both the automated `./gradlew smokeTest` runner (single-module §2 cells AND multi-module / build-logic / composite §2a cells, wired into `publishAndReleaseToMavenCentral` as a hard gate) and the manual `/tmp` Gradle bootstrap reference in `SMOKE_TEST.md`. Covers the runner architecture (SmokeVariant + ProjectShapeVariant data models + project scaffolders + ProcessBuilder GradleRunner + JUnit per-cell), the throwaway-project bootstrap pattern, the dryRun → inspect → Run → compile verification cycle, the project-shape matrix design (DSL × topology × deps-style), expected-outcomes tables, and the mavenLocal-resolution check. Invoke when phrases like "author a smoke test", "add a smoke cell", "release checklist", "pre-publish verification", "add a project-shape template", or "how do we validate the recipe end-to-end" appear — or when a user has just added a new recipe or new supported project shape and needs to extend either the runner or the manual `SMOKE_TEST.md`.
---

# Pre-release smoke tests for recipe projects

For running the existing smoke tests:
- **Single-module §2 cells AND multi-module / `build-logic` / `includeBuild` §2a templates A–F**: `./gradlew smokeTest` (~4 min). Wired into `publishAndReleaseToMavenCentral` as a hard dependency, so the gate is structural, not operator discipline.
- **§3 mavenLocal coordinates round-trip**: still manual, follow `SMOKE_TEST.md` at the repo root.

This skill is about **designing and extending** both layers.

## Why smoke tests exist

`RewriteTest` matrix coverage proves the visitor + templates emit the right tree. It does NOT prove that:

- The composed YAML recipe actually resolves dependencies in a real Gradle project.
- The produced `build.gradle(.kts)` compiles after the rewrite.
- `org.openrewrite.gradle.AddDependency` does the right thing without `withToolingApi()`.
- Multi-build composite projects (`includeBuild`) behave as expected when the recipe runs in each build.
- The published Maven artifact resolves when pulled by coordinates from a clean project.

Each of those is a real Gradle concern, not a RewriteTest concern. A smoke test bridges the gap between "visitor produces the right diff in isolation" and "the recipe works for a real user."

The right mental model: RewriteTest is the inner loop; the smoke test is the release gate.

## Runner architecture

`./gradlew smokeTest` runs a JUnit-driven scaffold-and-execute loop. Two parameterized tests, one per matrix:
- `SmokeTest` — single-module §2 cells (one row per top-level recipe × catalog axis).
- `ProjectShapeSmokeTest` — multi-module / build-logic / composite §2a templates A–F.

Files live under `src/smokeTest/java/`; the source set is wired in `build.gradle.kts` parallel to `integrationTest`. The pieces:

- `SmokeVariant` (record): one §2 cell — recipe id, catalog mode (`WITHOUT_TOML` / `WITH_EMPTY_TOML` / `NOT_APPLICABLE`), `managesDependencies` flag, list of fixtures.
- `ProjectShapeVariant` (record): one §2a template — recipe id, `Topology` (MULTI_MODULE / BUILD_LOGIC_INCLUDE / COMPOSITE_INCLUDE_BUILD), `Dsl` (KOTLIN / GROOVY), and a `rewriteRunSubdirs` list (`[""]` for single-invocation shapes, `["", "build-logic"]` for composites that need one rewriteRun per included build).
- `Fixture` (record, §2 only): one Java source file the recipe should transform, plus any `prerequisiteImplementationDeps` the **original** source needs to compile (e.g. `org.apache.logging.log4j:log4j-api` for a manual-Log4j2 fixture — without that on the classpath OpenRewrite can't resolve types and the recipe matches nothing).
- `SmokeProject`: writes a fresh single-module Gradle project per §2 cell — wrapper (pinned to a known-good Gradle version that hosts the JDKs you need), `settings.gradle.kts`, `build.gradle.kts` with `mavenLocal()` first and the recipe pulled by coordinates, the fixture source files, and (when the cell calls for it) an empty `gradle/libs.versions.toml`.
- `ProjectShapeScaffolder`: writes a fresh §2a project — branches on `topology` and `dsl` to lay out multi-module subprojects, `include("build-logic")` setups, or composite `includeBuild` builds (each with its own wrapper, settings, build script, and catalog).
- `GradleRunner`: invokes nested `./gradlew <task>` via `ProcessBuilder`, captures combined stdout/stderr to `<cell-dir>/run.log`, sets `JAVA_HOME` to a JDK old enough to host the smoke project's Gradle daemon (`smokeTest.jdk21Home` system property, wired in `build.gradle.kts` from `javaToolchains.launcherFor`). Reused by both matrices.
- `ProjectShapeSmokeTest` adds a post-rewrite `@Slf4j` sweep across the project's Java sources — guards against silent no-op failures where rewriteRun "succeeds" but doesn't actually edit anything (compileJava would still pass on the unmodified `System.out` source). Worth applying back to `SmokeTest` if a similar regression is ever observed.

### Adding a new §2 cell

1. If a new fixture shape is needed, add a `Fixture` constant. Declare any prerequisite implementation deps the unrewritten source needs.
2. Add a `cell(...)` entry to `SmokeTest.matrix()`. Pick the right `CatalogMode` and `managesDependencies` for the variant you're testing.
3. Run `./gradlew smokeTest`. If the cell fails, the JUnit failure message includes the path to `run.log` — read it, fix the issue, re-run.

### Adding a new §2a template

1. If the new template fits an existing `Topology` value, just add a `cell(...)` entry to `ProjectShapeSmokeTest.matrix()` with the appropriate `Topology` + `Dsl` + `rewriteRunSubdirs`.
2. If the new template needs a new topology, add a value to `ProjectShapeVariant.Topology` and a corresponding `scaffold...()` branch in `ProjectShapeScaffolder.scaffold()`. Reuse the helper methods (`writeSettings`, `writeRootBuildWithSubprojectJavaPlugin`, `writeStandaloneRootBuild`, `writeSubprojectBuild`, `writeJava`, `writeEmptyCatalog`, `copyWrapper`) — they already branch on Kotlin vs Groovy DSL.
3. Run `./gradlew smokeTest --tests "*ProjectShapeSmokeTest*"`. Per-cell `run.log` paths surface in the failure message.

### The runner is wired into the publish gate

```kotlin
listOf(
        "publishAndReleaseToMavenCentral",
        "publishToMavenCentral",
        "publishAllPublicationsToMavenCentralRepository",
).forEach { name ->
    tasks.matching { it.name == name }.configureEach { dependsOn("smokeTest") }
}
```

There's no path to Maven Central that skips smoke. `publishToMavenLocal` is deliberately NOT gated — `smokeTest` itself depends on it (so throwaway projects can resolve the recipe via coordinates), and gating would be circular.

### What stays manual

The §3 mavenLocal coordinates round-trip across release-shaped consumer projects still lives in `SMOKE_TEST.md`. Everything else in §2 and §2a is automated.

## The verification cycle (per template)

For every project-shape template:

1. **Bootstrap**: create a throwaway directory (typically under `/tmp`), copy the Gradle wrapper in, write minimal fixture files (settings, build script, catalog if any, a Java source that exercises what the recipe targets).
2. **Dry run**: `./gradlew rewriteDryRun` and inspect `build/reports/rewrite/rewrite.patch`. Every change in the patch should look intentional. An empty patch when the fixture targeted something the recipe claims to handle is a red flag.
3. **Apply**: `./gradlew rewriteRun`.
4. **Compile**: `./gradlew compileJava` (or the subproject-scoped equivalent for multi-module / composite). A green compile is the authoritative sign that dependencies were added correctly and the generated code parses.

Any cell that doesn't complete all four steps is a regression.

## Bootstrap pattern

```bash
set -euo pipefail
TEST=/tmp/smoke-<template-name>-<version>
rm -rf "$TEST"
mkdir -p "$TEST/gradle/wrapper" \
         "$TEST/src/main/java/<pkg>"
cp <project-root>/gradle/wrapper/gradle-wrapper.* "$TEST/gradle/wrapper/"
cp <project-root>/gradlew <project-root>/gradlew.bat "$TEST/"
chmod +x "$TEST/gradlew"

# Write settings, build script, source with heredoc <<'EOF' blocks so $ isn't expanded.
```

A few conventions that pay off:

- **Name the dir after the template and the version** (`smoke-multi-kotlin-0.6`). Easy to re-run individually, easy to diff against an earlier version's output.
- **Use `<<'EOF'` heredocs** (single-quoted) so `$variable` references inside fixture files aren't interpreted by the shell.
- **Resolve the recipe by Maven coordinates, not by `files(...)`.** This forces you to `./gradlew publishToMavenLocal` beforehand, which proves the POM + module metadata are correct — an independent failure mode worth catching at smoke-test time.

## Designing the project-shape matrix

Enumerate the project shapes your recipe actually has to work in. Typical axes:

| Axis | Values |
| --- | --- |
| Build-script DSL | Kotlin (`build.gradle.kts`) × Groovy (`build.gradle`) |
| Project topology | single-module × multi-module × composite (`includeBuild`) |
| Dependency declaration | version catalog × inline × `gradle.properties`-driven |
| Convention-plugin location | none × `buildSrc/` × `build-logic/` (included build or subproject) |

Don't cross-product every axis — 2 × 3 × 3 × 3 = 54 cells mostly add no signal. Pick the intersections where axes interact meaningfully:

- Groovy DSL is where paren-less syntax and `GString` interpolation risk lives → test with catalog + with inline + with `$var` interpolation.
- Multi-module is where dependency-block fanout matters → test with catalog and with inline.
- Composite is where the "two separate Gradle builds" semantics bite → one Kotlin composite + one Groovy composite is usually enough.

Aim for 6–14 templates total. Each should pull its weight.

## The expected-outcomes table

End the matrix section of your smoke-test doc with a table that's fast to eyeball during a release:

| Template | Rewrite invocations | Java rewritten | Build file rewritten | `compileJava` green |
| --- | --- | --- | --- | --- |
| A — multi-module Kotlin | 1× at root | ✓ both modules | ✓ both modules | ✓ |
| E — Kotlin composite (`includeBuild`) | 2× (outer + inner) | ✓ outer | ✓ both builds, each via its own catalog | ✓ |
| ... | ... | ... | ... | ... |

Columns worth including: number of `rewriteRun` invocations (makes the composite distinction visible), whether Java sources are rewritten, whether build files are rewritten, whether compilation succeeds. Add more columns if your recipe has dimensions the defaults don't capture (e.g. "catalog seeded", "XML config created").

A mismatch between the table and reality is the release gate.

## `include` vs `includeBuild`

When a project has a `build-logic/` directory, whether it's a regular subproject or a composite-included build changes the correct smoke-test procedure. Document both variants as distinct templates, not as notes on one template:

- `include("build-logic")` — build-logic is a subproject; Gradle's catalog resolution uses the single root `gradle/libs.versions.toml`; one outer `rewriteRun` fans out to every module including `build-logic/`.
- `includeBuild("build-logic")` — build-logic is a separate Gradle build; each build has its own catalog; the OpenRewrite plugin at the outer build does NOT reach in; run `rewriteRun` in each build independently.

This is the exact distinction that trips up smoke-test authors — make it explicit.

## Resolve-by-coordinates check

After publishing to mavenLocal, point a fresh project at the artifact by coordinates (not `files(...)`) to confirm the POM + Gradle module metadata are correct:

```kotlin
repositories { mavenLocal(); mavenCentral() }

dependencies {
    rewrite("com.yourorg:your-recipe:<version>")
}
```

Repeat at least the default and catalog variants. This is where signing issues, wrong group coordinates, and missing transitive deps surface before they hit Maven Central.

## Coverage + dependency-freshness checks

Two more boxes worth checking at release time:

- `./gradlew jacocoTestReport spotbugsMain spotbugsTest` — coverage numbers shouldn't regress meaningfully; SpotBugs should be clean.
- `./gradlew dependencyUpdates` — if an OpenRewrite patch release is out, consider bumping before publishing.

## Version-pin refresh

If your recipe pins specific versions of third-party dependencies inside YAML (e.g. `versionValue: "1.18.44"` in an `AddVersionCatalogEntry` call), `dependencyUpdates` won't flag them because they're not build dependencies of the recipe project. Check them by hand against Maven Central before release:

```bash
curl -s https://repo1.maven.org/maven2/<group-path>/<artifact>/maven-metadata.xml \
  | grep -oE '<release>[^<]+</release>'
```

Update every `version:` in the YAML to match, then re-run the matrix.

## Structure the document as a procedure

`SMOKE_TEST.md` should read top-to-bottom as "do this, then this, then this" — not as reference material to look things up in. Numbered sections, unambiguous "if X then stop and diagnose" escape hatches, and a final "only then" gate before the actual `publishAndReleaseToMavenCentral` command. Pre-release stress is not the time to navigate a reference doc.
