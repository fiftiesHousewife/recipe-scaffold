# Changelog

This project tracks changes in [`BACKLOG.md`](./BACKLOG.md), grouped by date under a Shipped / Queued / Parked split. The release notes below are a flat tag-aligned view; refer to BACKLOG for daily granularity.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versioning follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.3.0] — 2026-05-10

### Added

- `recipe-library` Gradle convention plugin under `template/build-logic/`. The reusable shape of the recipe build (toolchain, gates, source sets, jacoco, javadoc, sign-onlyIf, pre-publish smokeTest gate) lives there as a precompiled script plugin pulled in via `pluginManagement.includeBuild("build-logic")`. Scaffolded `build.gradle.kts` shrinks to identity + maven-publish coordinates and POM.
- Three opt-in quality gates wired through `gradle.properties`:
  - `recipeLibrary.minLineCoverage=<ratio>` enforces JaCoCo line coverage as part of `check`.
  - `recipeLibrary.spotbugsStrict=true` makes any SpotBugs finding fail `check`.
  - `recipeLibrary.failOnStaleDependencies=true` wires `verifyDependencies` (parses the ben-manes JSON report) into `check`. Prereleases (alpha/beta/rc/milestone/preview/snapshot) are filtered out so the gate does not thrash.
- Picocli `@Mixin` `ProjectDirectoryMixin` collapses the duplicated `--directory` option across `add-recipe`, `verify-gates`, `upgrade-skills`.
- `./gradlew downloadJbang` task at repo root downloads the pinned JBang release zip from GitHub for environments that block `brew`/`choco`/`sdk`/`curl | bash`. Installs under `build/jbang/jbang-<version>/`.
- `javax.annotation:javax.annotation-api:1.3.2` `compileOnly` in the convention plugin so Refaster-generated `<Name>Recipes.java` compiles on JDK 11+.
- `pre-push` skill at `.claude/skills/pre-push/SKILL.md` listing the local equivalents of every CI job (actionlint+shellcheck, `./gradlew test`, `tests/ci-smoke.sh`).
- `RecipeScaffoldUnitTest` grew to 64 cases: dropfile parsing, AddRecipe validation gates, end-to-end synthesis across all four kinds, overwrite refusal, `--no-tests`, missing snippets dir, no-project-found paths, `isTextFile`/`isUnderSnippets`/`copyTree`/`renamePackageMarkers`/`substituteIn`/`findResiduals`/`deleteRecursively`/`wrapperScript`/`Init.writeDropfile`.
- `HARNESS_OFFLINE=1` env knob and `-Dkotlin.compiler.execution.strategy=in-process` on the inner `GradleRunner` for sandbox-friendly local repro of harness failures.

### Changed

- `Init` lost its private static helpers — promoted to package-private statics on `RecipeScaffold` so they are testable directly. `Init` is now ~150 lines (was ~280).
- `AddRecipe.call()` factored into `writeRecipeFile` and `writeTestFile` helpers.
- `readDropfile` promoted from private nested static to top-level.
- `runGradle` moved from its stranded position between subcommands into the helpers section.
- All `@Option` fields are now `private`; subcommands access `--directory` via `ProjectDirectoryMixin`.
- File walkers (`deleteRecursively`, `copyDir`, `substituteIn`, `findResiduals`, `readDropfile`) reworked to `Files.walk`/`Files.lines` stream pipelines; `copyTree` keeps `SimpleFileVisitor` for `SKIP_SUBTREE`.
- `Init.writeDropfile` rewritten using a Java text block.
- README expanded with a subcommand-overview table, a six-walkthrough Examples section, a What-you-get aspect/ships table, and grouped References (OpenRewrite, JBang, picocli, Gradle, build & publishing tooling, this project).
- `JBANG_TEMPLATE_PLAN.md` trimmed to the Part A audit (Part B all shipped).

### Fixed

- **`SpotBugsTask.reports.create` clash with downstream plugins.** `tasks.withType<SpotBugsTask>().configureEach { reports.create("html"|"xml") }` threw `Cannot add a SpotBugsReport with name 'xml'` whenever a consuming project applied a second SpotBugs-aware plugin (e.g. `cleancode`) that had already configured those reports. The block is dropped; SpotBugs enables HTML by default and XML enablement is the consuming project's call. Reported by clean-logging.
- **`integrationTest` missing `org.gradle.java.home`.** `withToolingApi()`'s embedded Gradle 8.14.3 daemon read `org.gradle.java.home` from the calling JVM and inherited the outer toolchain (JDK 25), then crashed on v69 system classes. Mirrored the existing `smokeTest` pin via `doFirst { systemProperty("org.gradle.java.home", jdk21Home.get()) }`. Reported by clean-logging.
- **`SmokeYamlRecipeTest` `ScannerException`.** After the openrewrite 7.30 → 7.32.1 / recipe-bom 3.28 → 3.30 bump, `Environment.builder().scanRuntimeClasspath()` threw inside OpenRewrite on some downstream YAML the test had no control over. Test scaffold replaced with a content-based assertion that the manifest exists at the expected classpath path with the expected `type:`/`name:`/`recipeList:` identifiers; the end-to-end activation path remains exercised by the `smokeTest` matrix and by consumers of the published artifact.
- **CI shellcheck SC2086.** Quoted every `${GITHUB_WORKSPACE}` reference in `ci.yml`. Local actionlint without shellcheck on PATH does not surface this; CI does.

### Migration for existing consumers

The `recipe-library` convention plugin is **vendored** into each scaffolded project at `init` time, not published to the Gradle Plugin Portal or Maven Central. Bumping the JBang catalog pin from `v0.2.0` to `v0.3.0` only affects **new** scaffolds. Projects scaffolded from `v0.2.0` (or earlier) still carry their own copy of `build-logic/src/main/kotlin/recipe-library.gradle.kts` and will not auto-pick up the SpotBugs / integrationTest / Refaster fixes from this release.

To pull the fixes into an existing consumer:

```bash
# from the consuming project root
curl -fsSL "https://raw.githubusercontent.com/fiftiesHousewife/recipescaffold/v0.3.0/template/build-logic/src/main/kotlin/recipe-library.gradle.kts" \
  -o build-logic/src/main/kotlin/recipe-library.gradle.kts

# also pull the matching libs.versions.toml additions (javax-annotation-api):
curl -fsSL "https://raw.githubusercontent.com/fiftiesHousewife/recipescaffold/v0.3.0/template/gradle/libs.versions.toml" \
  -o gradle/libs.versions.toml
```

A future `recipescaffold upgrade-build-logic` subcommand (queued in BACKLOG) will automate this in the same shape as `upgrade-skills`.

## [0.2.0] — 2026-05-04

Initial public beta. JBang catalog at `jbang recipescaffold@fiftiesHousewife/recipescaffold`. Four subcommands (`init`, `add-recipe`, `verify-gates`, `upgrade-skills`); four recipe types (`java`, `scanning`, `yaml`, `refaster`); two test styles (`block`, `method`); five distribution paths (JBang catalog/direct, `./gradlew run`, `./gradlew installDist`, fat jar). CI exercises bash-scaffold, jbang-scaffold, in-repo TestKit harness, and actionlint.

See [`BACKLOG.md`](./BACKLOG.md) under the 2026-05-04 Shipped entries for the per-subcommand granularity.
