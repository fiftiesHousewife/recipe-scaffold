# Changelog

This project tracks changes in [`BACKLOG.md`](./BACKLOG.md), grouped by date under a Shipped / Queued / Parked split. The release notes below are a flat tag-aligned view; refer to BACKLOG for daily granularity.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versioning follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- `recipe-library` Gradle convention plugin under `template/build-logic/`. The reusable shape of the recipe build (toolchain, gates, source sets, jacoco, javadoc, sign-onlyIf, pre-publish smokeTest gate) lives there as a precompiled script plugin pulled in via `pluginManagement.includeBuild("build-logic")`. The scaffolded project's `build.gradle.kts` shrinks to identity + maven-publish coordinates and POM.
- Three opt-in quality gates wired through `gradle.properties`:
  - `recipeLibrary.minLineCoverage=<ratio>` enforces JaCoCo line coverage as part of `check`.
  - `recipeLibrary.spotbugsStrict=true` makes any SpotBugs finding fail `check`.
  - `recipeLibrary.failOnStaleDependencies=true` wires `verifyDependencies` (parses the ben-manes JSON report) into `check`. Prereleases (alpha/beta/rc/milestone/preview/snapshot) are filtered out of the candidate list so the gate doesn't thrash.
- Picocli `@Mixin` `ProjectDirectoryMixin` collapses the duplicated `--directory` option across `add-recipe`, `verify-gates`, `upgrade-skills`.
- `RecipeScaffoldUnitTest` grew to 54 cases: dropfile parsing, AddRecipe validation gates, end-to-end synthesis across the four kinds, overwrite refusal, `--no-tests`, missing snippets dir, no-project-found paths, and `isTextFile` / `isUnderSnippets` predicates.

### Changed

- `Init` lost its private static helpers — they were promoted to package-private statics on `RecipeScaffold` so they are testable without reflection. `Init` is now ~150 lines (was ~280).
- `AddRecipe.call()` factored into `writeRecipeFile` and `writeTestFile` helpers.
- `readDropfile` promoted from private nested static to top-level.
- `runGradle` moved from its stranded position between subcommands into the helpers section.
- `Init.writeDropfile` rewritten using a Java text block.
- README expanded with a subcommand-overview table, a six-walkthrough Examples section, a What-you-get aspect/ships table, and grouped References tables (OpenRewrite, JBang, picocli, Gradle, build & publishing tooling, this project).

## [0.2.0] — 2026-05-04

Initial public beta. JBang catalog at `jbang recipescaffold@fiftiesHousewife/recipescaffold`. Four subcommands (`init`, `add-recipe`, `verify-gates`, `upgrade-skills`); four recipe types (`java`, `scanning`, `yaml`, `refaster`); two test styles (`block`, `method`); five distribution paths (JBang catalog/direct, `./gradlew run`, `./gradlew installDist`, fat jar). CI exercises bash-scaffold, jbang-scaffold, in-repo TestKit harness, and actionlint.

See [`BACKLOG.md`](./BACKLOG.md) under the 2026-05-04 Shipped entries for the per-subcommand granularity.
