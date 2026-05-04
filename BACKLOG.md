# Backlog — recipescaffold

This repo's own backlog. The scaffolded project gets its own `template/BACKLOG.md`; do not conflate.

The B-numbered items track [`JBANG_TEMPLATE_PLAN.md`](./JBANG_TEMPLATE_PLAN.md) Part B; the A-numbered items track Part A (upstream OpenRewrite findings) and only appear here when they touch the template payload.

## Shipped

### 2026-05-04 (this session)

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

- **B11.3** — `add-recipe <name>` subcommand. Reads existing scaffolded project's identity from a `.recipescaffold.yml` dropfile; emits a recipe class + test from `template/snippets/*.template` fragments. Plan §B3, §B5.
- **B11.4** — `verify-gates` subcommand (thin `./gradlew check integrationTest smokeTest` wrapper). Plan §B3.
- `git init` + GitHub remote `recipescaffold`. Deferred until B11.3 settles the layout.
- `template/snippets/` directory — source-of-truth recipe-skeleton fragments that both `add-recipe` and the `new-recipe` skill read from.

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

## Upstream-flow items (originate in `/Users/pippanewbold/Claude`)

These ship in upstream first; sync into the template when stable. Tracked here only because they touch template payload or build conventions.

- **A14** — extract our own `recipe-library-base` convention plugin (replaces parts of the template's hand-rolled `build.gradle.kts`).
- **A16** — publish-on-tag CI workflow (template now ships `release.yml`; upstream still queued).
- **A18** — `recipes.csv` + `community-recipes` PR (upstream first, then template gains it via the `recipe-library` plugin from A14).
- **A22** — BOM-aligned versions in `libs.versions.toml` (template's TOML still pins individual `8.79.6` entries the BOM should align).
- **A10** — `MethodMatcher` conversion for the `SystemOut` detector (upstream-only; here for completeness).
