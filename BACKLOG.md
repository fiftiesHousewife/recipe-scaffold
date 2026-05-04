


# Backlog — recipescaffold

This repo's own backlog. The scaffolded project gets its own `template/BACKLOG.md`; do not conflate.

The B-numbered items track [`JBANG_TEMPLATE_PLAN.md`](./JBANG_TEMPLATE_PLAN.md) Part B; the A-numbered items track Part A (upstream OpenRewrite findings) and only appear here when they touch the template payload.

## Shipped

### 2026-05-04 (B11.3.1 — yaml type)

- **B11.3.1 (yaml)** — `add-recipe --type yaml` ships `template/snippets/yaml-composition-block.template` (the `specs.openrewrite.org/v1beta/recipe` manifest with placeholder `recipeList: []` + an inline comment showing the canonical entry shape) and `template/snippets/recipe-test-yaml.template` (a `RewriteTest` skeleton that loads the manifest via `Environment.builder().scanRuntimeClasspath().build().activateRecipes("<id>")` rather than `new RecipeClass()`). AddRecipe was refactored from a flat `CLASS_SNIPPETS` map to a nested `RecipeKind` record dispatch with `mainSnippet`, `testSnippet`, and `mainInResources` — yaml routes the manifest to `src/main/resources/META-INF/rewrite/<kebab>.yml` (no package subdir) while java/scanning still write `src/main/java/<pkg>/<Name>.java`. Two new snippet-time placeholders: `{{recipeId}}` (for yaml = `<rootPackage>.<recipeName>`, root namespace per the example.yml convention; for java/scanning = `<package>.<recipeName>`) and `{{recipeKebab}}` (PascalCase → kebab, used for the manifest filename). Local end-to-end: scaffold + add-recipe (java + scanning + yaml) + `./gradlew check` — green. CI extends both jobs with a third `add-recipe --type yaml SmokeYamlRecipe` cell.

### 2026-05-04 (B11.3.1 — scanning type)

- **B11.3.1 (partial)** — `add-recipe --type scanning` ships `template/snippets/recipe-class-scanning.template` (`ScanningRecipe<Acc>` with `getInitialValue` / `getScanner` / `getVisitor` + a nested `Acc` class holding a `Set<String> seen`). AddRecipe dispatches via `CLASS_SNIPPETS` map (java + scanning); unsupported types report the available list. Same `recipe-test.template` is reused — the no-op default still asserts source unchanged. Local end-to-end: scaffold + add-recipe (java) + add-recipe (scanning) + `./gradlew check` — green. CI extends both jobs with a second `add-recipe --type scanning SmokeScanRecipe` cell. yaml/refaster types still queued.

### 2026-05-04 (B11.3 session)

- **B11.3** — `add-recipe` JBang subcommand. Args: `--name <RecipeName>`, `--type java` (initial; B11.3.1 added `scanning`), `--display-name`, `--description`, `--package`, `-d/--directory`, `--no-tests`, `--force`. Reads `.recipescaffold.yml` dropfile (walks upward from cwd if `--directory` not given), loads `snippets/recipe-class-java.template` + `recipe-test.template`, substitutes `{{package}}` / `{{recipeName}}` / `{{recipeDisplayName}}` / `{{recipeDescription}}`, writes to `src/main/java/<pkg>/recipes/<Name>.java` (+ test). Refuses to overwrite without `--force`; rejects non-PascalCase `--name`; rejects unsupported `--type`.
- **B11.3 — dropfile** — `Init.call()` writes `.recipescaffold.yml` at the output root capturing `recipescaffoldVersion` (= `RecipeScaffold.VERSION`, bumped to `0.2.0`), `group`, `artifact`, `rootPackage`, `javaTargetMain`, `javaTargetTests`. Hand-rolled YAML, no extra deps. `tests/ci-smoke.sh` writes the same shape so the bash flow's output also feeds `add-recipe`.
- **B11.3 — snippets** — `template/snippets/{recipe-class-java,recipe-test}.template` plus a `README.md` documenting the placeholder dialect. Lives under `template/` so the snippet directory ships into every scaffold AND `add-recipe` reads it from the user's project after scaffolding. Init substitutor and residual check both skip files under `<root>/snippets/` so the `{{…}}` markers survive scaffolding intact.
- **B11.3 — CI** — both `bash-scaffold` and `jbang-scaffold` jobs now run `add-recipe SmokeRecipe` after the initial scaffold and re-run `./gradlew check`. `bash-scaffold` gains the `jbangdev/setup-jbang@main` step. Catches snippet-substitution regressions before they ship.

### 2026-05-04 (B11.2 session)

- **B11.2** — JBang `Init` subcommand at `jbang/RecipeScaffold.java`. Picocli, single-file, `//DEPS info.picocli:picocli:4.7.7`, `--verify` runs `./gradlew check smokeTest` post-scaffold. Class renamed `recipescaffold` → `RecipeScaffold` for Java convention.
- Repo-root CI at `.github/workflows/ci.yml`: parallel jobs run `tests/ci-smoke.sh` and `jbang init --verify` on every push/PR. JDK 21+25 installed; uses `jbangdev/setup-jbang@main`.
- Template additions: `LICENSE` (Apache 2.0), `AGENTS.md` (canonical vendor-neutral agent guidance — `CLAUDE.md` is now a stub forwarding to it), `.editorconfig`, `.github/workflows/release.yml` (tag-triggered Maven Central publish), `.github/workflows/wrapper-validation.yml`, `.github/dependabot.yml`.
- Smoke-test regression fixed:
  - `GradleRunner` exports `GRADLE_OPTS=-Dorg.gradle.java.home=<jdk21Home>` so nested Gradle 8.14.3's Kotlin DSL evaluator stays off JDK 25 (`gradle.properties` is parsed too late to help).
  - `integrationTest` source set drops `sourceSets.test.get().output` and adds `exclude("org.openrewrite", "rewrite-java-25")` on `integrationTestRuntimeClasspath` (mirrors upstream).
- `template/.gradle/` removed and `.gitignore` already excluded — no longer ships in the scaffold tree.
- Permissions sanitised: committed `.claude/settings.json` with pattern-matched allows for `tests/ci-smoke.sh`, `javac` of the JBang script, and `java -cp recipescaffold` invocations. `.claude/settings.local.json` reset to `{}`. `template/.claude/settings.json` ships `Bash(./gradlew *)` for scaffolded users.
- Residual placeholder check tightened to `(?<!\$)\{\{[a-zA-Z][a-zA-Z0-9]*\}\}` (in both `tests/ci-smoke.sh` and the JBang script) so GitHub Actions `${{ secrets.X }}` expressions in `release.yml` don't false-positive.

### Earlier

- **B11.1** — `template/` parameterised payload + `tests/ci-smoke.sh` bash scaffold-and-verify (v0 fallback).

## Queued for next release

- **B11.3.1 (remainder)** — `--type refaster` (`@RecipeDescriptor` annotation skeleton, may need annotation-processor wiring confirmed in template's build.gradle.kts). Plan §B3, §B5.
- **In-repo TestKit harness** — JUnit + `GradleRunner` test that scaffolds via `Init.call()` into `@TempDir` and runs `check` with `-g <tmpHome> -Dmaven.repo.local=<tmpM2> --no-daemon`. Works in sandboxes that block `~/.m2` / `~/.gradle` writes (where the bash flow's `publishMavenPublicationToMavenLocal` currently fails locally). Requires `template/build.gradle.kts` to honour `-Dmaven.repo.local`, or gating `smokeTest`'s publish behind a property. Companion to (not replacement for) the GitHub Actions `bash-scaffold` / `jbang-scaffold` matrix. Pattern source: Gradle TestKit user guide; equivalent role to Maven's `archetype:integration-test`.
- **B11.3.2** — `recipe-method-test.template` — a `RewriteTest` skeleton that takes a one-line `before` / `after` pair instead of the multi-line `java(...)` block in the default test. For when the user wants a tighter assertion form for argument-level transforms.
- **B11.4** — `verify-gates` subcommand (thin `./gradlew check integrationTest smokeTest` wrapper). Plan §B3.
- `git init` + GitHub remote `recipescaffold`. Deferred until B11.3.x has settled the snippet layout fully.

## Active

- (none — pick from Queued)

## Parked (re-open on trigger)

- **Refactor `RecipeScaffold.java` to multi-file (`//SOURCES`) or to a `tooling/recipescaffold/` Maven/Gradle module.** Trigger: when B11.3 doubles the script's line count and `Init` and `AddRecipe` start sharing helpers. Today the script is appropriately sized for one subcommand.
- **Split `Init.call()` into `TemplateLocator`, `Scaffolder`, `PlaceholderSubstitutor`, `GradleVerifier`.** Same trigger as the refactor above. Each becomes individually unit-testable. Clean Code skill `clean-code-functions` would flag the current ~100-line method.
- **Unit tests for the helpers** (substitution correctness, residual detection, `__ROOT_PACKAGE__` rename, copyTree skip-list). Trigger: after the module extraction. CI black-box coverage (today) is enough for now.
- **Extract constants** in `RecipeScaffold.java`: `MARKER_DIR = "__ROOT_PACKAGE__"`, `MARKER_PARENTS = List.of(...)`, `TEXT_EXTENSIONS`, `RESIDUAL_PATTERN`. Cosmetic; bundle into the refactor pass.
- **`isTextFile()` improvements** — extension allowlist is brittle; `.editorconfig` is currently skipped because of no `.` extension. Either add to `TEXT_NAMES` or switch to content-based detection.
- **`init --upgrade-skills` flag** — refresh `template/.claude/skills/` on an existing scaffolded project without overwriting the user's code. Plan §B5.
- **`bump-versions` subcommand** — TOML-aware Maven Central checker, subset of Ben-Manes but one-step. Plan §B3 priority 4.
- **`release` subcommand** — verify-gates → backlog confirm → version bump → tag → push. Plan §B3 priority 5; risky to automate, leave as documented workflow.
- **Native image via GraalVM** for faster cold-start. Plan §B10. Only if startup becomes a user complaint.
- **JBang catalog (`jbang-catalog.json`) entry** so users can `jbang recipescaffold@fiftiesHousewife init …` instead of pointing at the raw URL. Cheap once the GitHub remote exists.

## See also

- [`maxandersen/rewrite-jbang`](https://github.com/maxandersen/rewrite-jbang) — JBang-distributed *runner* for OpenRewrite recipes (validates our single-file picocli + `jbang app install` pattern; different scope — they run recipes, we scaffold the project that authors them).

## Upstream-flow items (originate in `io.github.fiftieshousewife:system-out-to-lombok-log4j`)

These ship in upstream first; sync into the template when stable. Tracked here only because they touch template payload or build conventions.

- **A14** — extract our own `recipe-library-base` convention plugin (replaces parts of the template's hand-rolled `build.gradle.kts`).
- **A16** — publish-on-tag CI workflow (template now ships `release.yml`; upstream still queued).
- **A18** — `recipes.csv` + `community-recipes` PR (upstream first, then template gains it via the `recipe-library` plugin from A14).
- **A22** — BOM-aligned versions in `libs.versions.toml` (template's TOML still pins individual `8.79.6` entries the BOM should align).
- **A10** — `MethodMatcher` conversion for the `SystemOut` detector (upstream-only; here for completeness).
