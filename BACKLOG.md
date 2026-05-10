


# Backlog — recipe-scaffold

This repo's own backlog. The scaffolded project gets its own `template/BACKLOG.md`; do not conflate.

B-numbered and A-numbered items below are historical labels from a prior planning doc; treat them as opaque tags.

## Shipped

### 2026-05-10 (upgrade-build-logic subcommand)

- **`upgrade-build-logic`** — fifth JBang subcommand. Walks upward to find `.recipe-scaffold.yml` (or accepts `--directory`), locates upstream `template/build-logic/` (or accepts `--template-dir`), and replaces the project's whole `build-logic/` tree with the upstream copy. Wholesale refresh (not per-subdir like `upgrade-skills`) because `build-logic/` is fully scaffold-managed — no expectation that consumers edit any of its three files. `gradle/libs.versions.toml` is intentionally **not** touched; user-extensible catalog merging is deferred until a marker-comment convention is added in a later template release. Closes the manual-`curl` burden documented in the v0.3.0 migration block. Tests: exit 2 when no dropfile, exit 3 when upstream has no `build-logic/`, dry-run reports without writing, real run replaces a stale tree with the upstream content. ~50 LoC + 4 tests.

### 2026-05-09 (build-logic convention plugin + quality gates)

- **`recipe-library` convention plugin** — extracted the reusable shape of the template's build (toolchain, integrationTest/smokeTest source sets and tasks, jacoco, javadoc, sign-onlyIf, pre-publish smoke gate, dep wiring) into `template/build-logic/src/main/kotlin/recipe-library.gradle.kts` as an included build. Scaffolded `template/build.gradle.kts` shrinks to project identity (group/version) plus maven-publish coordinates and POM. Java targets flow through `gradle.properties` keys `recipeLibrary.javaTargetMain` and `recipeLibrary.javaTargetTests` so the convention plugin stays project-agnostic. Catalog-driven plugin classpath in `build-logic/build.gradle.kts` uses programmatic catalog access (`VersionCatalogsExtension`) because the typed `libs` accessor is not generated for build-logic's own build script.
- **Three opt-in quality gates** wired into the convention plugin, all default-off so a fresh scaffold still builds:
  - `recipeLibrary.minLineCoverage=<ratio>` enables `jacocoTestCoverageVerification` with a line-coverage minimum.
  - `recipeLibrary.spotbugsStrict=true` makes any SpotBugs finding fail `check`. Plugin always applied so reports show up; non-strict mode reports without blocking.
  - `recipeLibrary.failOnStaleDependencies=true` wires `verifyDependencies` (parses ben-manes JSON report) into `check`. Prereleases (alpha/beta/rc/milestone/preview/snapshot) filtered out so the gate doesn't thrash.
- **`ProjectDirectoryMixin`** — picocli `@Mixin` extracted from the duplicated `--directory` `@Option` on `add-recipe`, `verify-gates`, and `upgrade-skills`. Single source of truth for the help text and default behaviour.
- **AddRecipe split** — `AddRecipe.call()` (90-line method) factored into `writeRecipeFile` and `writeTestFile` helpers. `readDropfile` promoted from private inner static to package-private top-level on `RecipeScaffold` so it is testable directly.
- **Wider unit coverage** — `RecipeScaffoldUnitTest` grows to 38 cases: dropfile parsing edge cases (quoted/unquoted/comment/blank/malformed), AddRecipe validation gates (unknown type, lowercase name, bad test-style, method+yaml combo, missing rootPackage), end-to-end synthesis across all four kinds, overwrite refusal, `--no-tests`, missing snippets dir, and the no-project-found path on `verify-gates`/`upgrade-skills`.

### 2026-05-04 (upgrade-skills subcommand)

- **`upgrade-skills`** — fourth JBang subcommand. Walks upward to find `.recipe-scaffold.yml` (or accepts `--directory`), locates upstream `template/.claude/skills/` (or accepts `--template-dir`), and replaces each skill subdir in the project's `.claude/skills/` with the upstream copy. Iterates only over upstream subdirs, so any user-added skill in the project is left alone. Supports `--dry-run` for preview. Tested locally: tampered SKILL.md overwritten cleanly; second run idempotent; error path (non-scaffolded directory) exits 2 with clean message. Refactor: `findTemplateDir`, `findProjectRoot`, and `deleteRecursively` were moved from per-subcommand private statics to top-level `RecipeScaffold` helpers, plus a new `copyDir` (cousin of Init's `copyTree` without the `.gradle`/`build`/`.idea` skip logic). Per-subcommand wrappers retained as thin delegates so the existing call sites are unchanged. Deviates from the BACKLOG-Parked verbiage of "`init --upgrade-skills` flag" — a separate subcommand is cleaner than gating most of init behind a flag.

### 2026-05-04 (B11.3.2 — recipe-method-test.template)

- **B11.3.2** — `add-recipe --test-style method` ships `template/snippets/recipe-method-test.template`. Tighter alternative to the default block-form test: one-line `rewriteRun(java("class T { int m() { return Math.max(1, 2); } }"))` with a commented-out hint showing how to expand to a `java(before, after)` pair when the recipe transforms code. `Math.max` is a stand-in so the parser can bind types (OpenRewrite's `RewriteTest` rejects LSTs with missing type info — the original `foo(1, 2)` placeholder failed for that reason). Restricted to `--type java|scanning`; yaml uses `Environment.builder` and refaster references the generated `<Name>Recipes` aggregate, so neither is meaningfully tighter in one-line form. New AddRecipe option `--test-style block|method` (default `block`); validates the value and the type/style combination. Local end-to-end: scaffold + add-recipe (java + scanning + yaml + refaster + java method-style) + `./gradlew check` — green. CI extends both bash- and jbang-scaffold jobs with a fifth cell. Harness gains a fifth `addRecipe` call.

### 2026-05-04 (B11.4 — verify-gates subcommand)

- **B11.4** — `verify-gates` JBang subcommand. Walks upward from cwd looking for `.recipe-scaffold.yml` (or accepts `--directory`), validates the dropfile is present (refuses non-recipe-scaffold projects to keep the smokeTest assumption honest), then runs `./gradlew check integrationTest smokeTest` via `ProcessBuilder.inheritIO()` and forwards the exit code. The three tasks are listed explicitly so all run even when `check` is up-to-date — the user is asking "are the gates green right now," not "is anything stale." Reuses Init's `runGradle` helper, which was extracted to a top-level static `RecipeScaffold.runGradle(Path, List<String>)` so both Init's `--verify` flow and VerifyGates share the same wrapper invocation. Tested locally: exit 2 with clean error when no dropfile present; happy path begins gradle invocation correctly. Plan §B3 priority 3 — deferred git-init dependency lifted (B11.3.x has settled).

### 2026-05-04 (TestKit harness)

- **In-repo TestKit harness** — `src/test/java/recipescaffold/ScaffoldHarnessTest.java` drives `Init.call()` and `AddRecipe.call()` (one cell per `--type`: java, scanning, yaml, refaster) into a `@TempDir`, then runs `GradleRunner` with `-g <tmpGradleHome> -Dmaven.repo.local=<tmpM2> --stacktrace check` against the scaffolded project. Pattern: Initializr's `ProjectGeneratorTester` shape + Maven Archetype's `archetype:integration-test` scope, ported to Gradle TestKit. Required scaffolding: a Gradle build at the repo root for the first time (`settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, wrapper assets copied from `template/`, `.gitignore` extended for `/build/` and `/.gradle/`); `package recipescaffold;` added to `jbang/RecipeScaffold.java` so a packaged test can import it (Java forbids importing default-package types). The build points the main source set at `jbang/` so the JBang flow keeps working unchanged. CI gains a `harness` job that runs `./gradlew test`. Local end-to-end inside this Claude sandbox can't complete because the forked Gradle daemon JVM can't resolve DNS for fresh artifact downloads (same gap that hit the refaster verification) — but the harness gets through scaffold + add-recipe (all four types) + TestKit invocation, then fails only on the inner gradle fetching the rewrite plugin. CI and unsandboxed local environments will run end-to-end.

### 2026-05-04 (B11.3.1 — refaster type)

- **B11.3.1 (refaster)** — `add-recipe --type refaster` ships `template/snippets/recipe-class-refaster.template` (outer holder class with one nested `@RecipeDescriptor` template-pair, idiomatic per `moderneinc/rewrite-recipe-starter`) and `template/snippets/recipe-test-refaster.template` (instantiates the *generated* `<recipeName>Recipes` aggregate, asserts a one-line before→after rewrite). KINDS map gains a fourth entry. Template build wiring (`template/build.gradle.kts` + `gradle/libs.versions.toml`): `annotationProcessor` + `implementation` of `org.openrewrite:rewrite-templating:1.41.4`, `compileOnly` of `com.google.errorprone:error_prone_core:2.49.0` (with the canonical `auto-service-annotations` and `dataflow-errorprone` excludes), `compileJava` adds `-Arewrite.javaParserClasspathFrom=resources`. CI extends both jobs with a fourth `--type refaster` cell. Local `./gradlew check` deferred to CI: the new artifact downloads fail DNS resolution inside the forked Gradle daemon's JVM, even though the parent shell resolves them — the same sandbox/forked-daemon gap that motivates the queued TestKit harness item.

### 2026-05-04 (B11.3.1 — yaml type)

- **B11.3.1 (yaml)** — `add-recipe --type yaml` ships `template/snippets/yaml-composition-block.template` (the `specs.openrewrite.org/v1beta/recipe` manifest with placeholder `recipeList: []` + an inline comment showing the canonical entry shape) and `template/snippets/recipe-test-yaml.template` (a `RewriteTest` skeleton that loads the manifest via `Environment.builder().scanRuntimeClasspath().build().activateRecipes("<id>")` rather than `new RecipeClass()`). AddRecipe was refactored from a flat `CLASS_SNIPPETS` map to a nested `RecipeKind` record dispatch with `mainSnippet`, `testSnippet`, and `mainInResources` — yaml routes the manifest to `src/main/resources/META-INF/rewrite/<kebab>.yml` (no package subdir) while java/scanning still write `src/main/java/<pkg>/<Name>.java`. Two new snippet-time placeholders: `{{recipeId}}` (for yaml = `<rootPackage>.<recipeName>`, root namespace per the example.yml convention; for java/scanning = `<package>.<recipeName>`) and `{{recipeKebab}}` (PascalCase → kebab, used for the manifest filename). Local end-to-end: scaffold + add-recipe (java + scanning + yaml) + `./gradlew check` — green. CI extends both jobs with a third `add-recipe --type yaml SmokeYamlRecipe` cell.

### 2026-05-04 (B11.3.1 — scanning type)

- **B11.3.1 (partial)** — `add-recipe --type scanning` ships `template/snippets/recipe-class-scanning.template` (`ScanningRecipe<Acc>` with `getInitialValue` / `getScanner` / `getVisitor` + a nested `Acc` class holding a `Set<String> seen`). AddRecipe dispatches via `CLASS_SNIPPETS` map (java + scanning); unsupported types report the available list. Same `recipe-test.template` is reused — the no-op default still asserts source unchanged. Local end-to-end: scaffold + add-recipe (java) + add-recipe (scanning) + `./gradlew check` — green. CI extends both jobs with a second `add-recipe --type scanning SmokeScanRecipe` cell. yaml/refaster types still queued.

### 2026-05-04 (B11.3 session)

- **B11.3** — `add-recipe` JBang subcommand. Args: `--name <RecipeName>`, `--type java` (initial; B11.3.1 added `scanning`), `--display-name`, `--description`, `--package`, `-d/--directory`, `--no-tests`, `--force`. Reads `.recipe-scaffold.yml` dropfile (walks upward from cwd if `--directory` not given), loads `snippets/recipe-class-java.template` + `recipe-test.template`, substitutes `{{package}}` / `{{recipeName}}` / `{{recipeDisplayName}}` / `{{recipeDescription}}`, writes to `src/main/java/<pkg>/recipes/<Name>.java` (+ test). Refuses to overwrite without `--force`; rejects non-PascalCase `--name`; rejects unsupported `--type`.
- **B11.3 — dropfile** — `Init.call()` writes `.recipe-scaffold.yml` at the output root capturing `recipeScaffoldVersion` (= `RecipeScaffold.VERSION`, bumped to `0.2.0`), `group`, `artifact`, `rootPackage`, `javaTargetMain`, `javaTargetTests`. Hand-rolled YAML, no extra deps. `tests/ci-smoke.sh` writes the same shape so the bash flow's output also feeds `add-recipe`.
- **B11.3 — snippets** — `template/snippets/{recipe-class-java,recipe-test}.template` plus a `README.md` documenting the placeholder dialect. Lives under `template/` so the snippet directory ships into every scaffold AND `add-recipe` reads it from the user's project after scaffolding. Init substitutor and residual check both skip files under `<root>/snippets/` so the `{{…}}` markers survive scaffolding intact.
- **B11.3 — CI** — both `bash-scaffold` and `jbang-scaffold` jobs now run `add-recipe SmokeRecipe` after the initial scaffold and re-run `./gradlew check`. `bash-scaffold` gains the `jbangdev/setup-jbang@main` step. Catches snippet-substitution regressions before they ship.

### 2026-05-04 (B11.2 session)

- **B11.2** — JBang `Init` subcommand at `jbang/RecipeScaffold.java`. Picocli, single-file, `//DEPS info.picocli:picocli:4.7.7`, `--verify` runs `./gradlew check smokeTest` post-scaffold. Class renamed `recipe-scaffold` → `RecipeScaffold` for Java convention.
- Repo-root CI at `.github/workflows/ci.yml`: parallel jobs run `tests/ci-smoke.sh` and `jbang init --verify` on every push/PR. JDK 21+25 installed; uses `jbangdev/setup-jbang@main`.
- Template additions: `LICENSE` (Apache 2.0), `AGENTS.md` (canonical vendor-neutral agent guidance — `CLAUDE.md` is now a stub forwarding to it), `.editorconfig`, `.github/workflows/release.yml` (tag-triggered Maven Central publish), `.github/workflows/wrapper-validation.yml`, `.github/dependabot.yml`.
- Smoke-test regression fixed:
  - `GradleRunner` exports `GRADLE_OPTS=-Dorg.gradle.java.home=<jdk21Home>` so nested Gradle 8.14.3's Kotlin DSL evaluator stays off JDK 25 (`gradle.properties` is parsed too late to help).
  - `integrationTest` source set drops `sourceSets.test.get().output` and adds `exclude("org.openrewrite", "rewrite-java-25")` on `integrationTestRuntimeClasspath` (mirrors upstream).
- `template/.gradle/` removed and `.gitignore` already excluded — no longer ships in the scaffold tree.
- Permissions sanitised: committed `.claude/settings.json` with pattern-matched allows for `tests/ci-smoke.sh`, `javac` of the JBang script, and `java -cp recipe-scaffold` invocations. `.claude/settings.local.json` reset to `{}`. `template/.claude/settings.json` ships `Bash(./gradlew *)` for scaffolded users.
- Residual placeholder check tightened to `(?<!\$)\{\{[a-zA-Z][a-zA-Z0-9]*\}\}` (in both `tests/ci-smoke.sh` and the JBang script) so GitHub Actions `${{ secrets.X }}` expressions in `release.yml` don't false-positive.

### Earlier

- **B11.1** — `template/` parameterised payload + `tests/ci-smoke.sh` bash scaffold-and-verify (v0 fallback).

## Queued for next release

- **`doctor` subcommand** — drift + upgrade-path advisor. Reads the running CLI's `RecipeScaffold.VERSION`, the project dropfile's `recipeScaffoldVersion`, and the latest GitHub release tag (single API call, cached locally). Prints which install path the CLI is running from (heuristic: presence of `~/.jbang/bin/recipe-scaffold`, JBang catalog cache, `build/install/recipe-scaffold/`, etc.) and the exact upgrade command for that path. Also flags drift between the dropfile version and the running CLI so a project that was scaffolded with `v0.2.0` and is being driven by `v0.4.0` knows to run `upgrade-skills` + `upgrade-build-logic`. Replaces the manual upgrade table in the README. ~80 LoC + test.
- **Submit `recipe-scaffold` to the public JBang catalog** — PR an entry into [`jbangdev/jbang-catalog`](https://github.com/jbangdev/jbang-catalog)'s `jbang-catalog.json` referencing `fiftiesHousewife/recipe-scaffold`, so the alias is discoverable via `jbang catalog list jbangdev` and reachable as `recipe-scaffold@jbangdev`. Direct reference (`recipe-scaffold@fiftiesHousewife/recipe-scaffold`) already works; this is purely a discovery boost. Best done after a few tagged releases have settled; v0.3.0 is sufficient.
- **build-logic Gradle tasks for post-init subcommands** — register `addRecipe`, `verifyGates`, `upgradeSkills` as Gradle tasks on the `recipe-library` convention plugin so consumers can do `./gradlew addRecipe --name=Foo --type=java` from inside a scaffolded project instead of typing the full `jbang recipe-scaffold@…` reference. `init` stays JBang-only (no project = no Gradle). Sequencing: ship the **shell-out form** first — task `exec`s `jbang recipe-scaffold@fiftiesHousewife/recipe-scaffold <subcommand> …` and forwards args via picocli's `@argfile` or task properties; ~30 lines of Kotlin in `recipe-library.gradle.kts`, no publication change, JBang stays a runtime dep. Later, if consumers want to drop JBang entirely, **publish `recipe-scaffold` to Maven Central** and switch the tasks to embedded `JavaExec` against a `recipeScaffold` configuration; same task names + flags so the migration is invisible. Bumps the publish-flow surface (currently nothing publishes), so weigh against demand.

## Active

- (none)

## Parked (re-open on trigger)

- **Split `Init.call()` into `TemplateLocator`, `Scaffolder`, `PlaceholderSubstitutor`, `GradleVerifier`.** Trigger: when Init grows new responsibilities. The reusable build is now in `build-logic/`, so the lower half of `Init.call()` is mostly fan-out to top-level helpers (`copyTree`, `substituteIn`, `findResiduals`, `writeDropfile`); a five-class split would be ceremony today.
- **`isTextFile()` improvements** — extension allowlist is brittle; `.editorconfig` is currently skipped because of no `.` extension. Either add to `TEXT_NAMES` or switch to content-based detection.
- **`bump-versions` subcommand** — TOML-aware Maven Central checker, subset of Ben-Manes but one-step. Plan §B3 priority 4. Now partly redundant with the `failOnStaleDependencies` gate in the convention plugin; reopen if users want the bump applied automatically rather than just the diagnosis.
- **`release` subcommand** — verify-gates → backlog confirm → version bump → tag → push. Plan §B3 priority 5; risky to automate, leave as documented workflow.
- **Native image via GraalVM** for faster cold-start. Plan §B10. Only if startup becomes a user complaint.
- **Convention plugin breakup into typed `Plugin<Project>` classes with unit tests.** Trigger: when `recipe-library.gradle.kts` either grows past ~400 lines or starts hosting non-trivial branching logic that would benefit from `ProjectBuilder.builder().build()` style tests. Today the script is mostly declarative Provider plumbing — splitting into five classes would add ~50 lines of plumbing per class without catching real bugs.

## See also

- [`maxandersen/rewrite-jbang`](https://github.com/maxandersen/rewrite-jbang) — JBang-distributed *runner* for OpenRewrite recipes (validates our single-file picocli + `jbang app install` pattern; different scope — they run recipes, we scaffold the project that authors them).
