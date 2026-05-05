# JBang template extraction plan + OpenRewrite audit

**Provenance.** Generated 2026-05-03 by a Plan-type subagent (description: "OpenRewrite audit + JBang template plan") in session `7f36eae0-dc97-4699-a95f-27791031f4bd`. The agent transcript is the original source; this file is its persisted form. Status markers (`[ok]` / `[minor]` / `[missing]` / `[add]`) replaced the agent's emoji column to comply with the project's no-emojis-in-docs rule.

## Status as of 2026-05-04

Part B11.2 has shipped — see the Part B status section below. Part A findings have moved since the audit was written. Treat the inline statuses below as snapshots from 2026-05-03; the table here is the live view.

### Part B (template-extraction) progress

| Step | Status |
| --- | --- |
| B11.1 — `template/` payload + `tests/ci-smoke.sh` | shipped |
| B11.2 — JBang `Init` subcommand at `jbang/RecipeScaffold.java` | shipped 2026-05-04 |
| B11.2a — Repo-root CI (`.github/workflows/ci.yml`) running both bash and JBang flows | shipped 2026-05-04 |
| B11.2b — Template additions: `LICENSE`, `AGENTS.md`, `.editorconfig`, `release.yml`, `wrapper-validation.yml`, `dependabot.yml` | shipped 2026-05-04 |
| B11.3 — `add-recipe <name>` subcommand + `template/snippets/*.template` | queued |
| B11.4 — `verify-gates` subcommand | queued |
| B11.5+ — `bump-versions`, `release`, `--upgrade-skills` | parked |

See [`BACKLOG.md`](./BACKLOG.md) for the full Queued/Parked breakdown.

### Part A (upstream OpenRewrite findings)

| Finding | Then | Now |
| --- | --- | --- |
| A1 — Lombok `@Value` on recipes | minor | shipped 0.8 |
| A2 — `Preconditions.check` wrappers | missing | shipped (all 5 leaf recipes) |
| A3 — Recipe ID / package consistency | minor | open, deferred to next major |
| A4 — `getTags()` + `getEstimatedEffortPerOccurrence()` | add | shipped 0.8 |
| A5 — YAML `tags:` on composed recipes | add | shipped 0.8 |
| A6 — `@Option` nullability | minor | closed: leave as-is |
| A7 — `@NullMarked` | ok | closed |
| A8 — `JavaIsoVisitor` choice | ok | closed |
| A9 — `JavaTemplate` imports | ok | closed |
| A10 — `MethodMatcher` for `SystemOut` | ok-ish | open: still string-compares `select.toString()`; convert when convenient |
| A11 — `JavaSourceSet` marker | ok | closed |
| A12 — `ScanningRecipe` boolean accumulator | ok | open observation; revisit if smoke surfaces a multi-build leak |
| A13 — `TypeValidation.none()` scope | ok | open cosmetic; narrow to `afterTypeValidationOptions` where possible |
| A14 — `recipe-library-base` plugin | minor | open — tracked as BACKLOG A14 (extract our own convention plugin) |
| A15 — Java release target | ok | closed |
| A16 — CI workflow shape | minor | partially shipped (CI runs `check smokeTest`); publish-on-tag workflow still queued under BACKLOG "Move publishAndReleaseToMavenCentral into CI" |
| A17 — `@RecipeDescriptor` / Refaster | ok | closed N/A |
| A18 — `recipes.csv` + community-recipes PR | add | open — visibility win, no `recipes.csv` yet |
| A19 — Idempotence | ok | closed |
| A20 — `JavaTemplate.builder` shape | ok | closed |
| A21 — Hand-built LST in `UseCatalogReferenceForDependency` | minor | closed with documented justification |
| A22 — BOM version pinning | ok | open — TOML still pins individual `8.79.6` entries the BOM should align |

Active items worth carrying into the next release cycle: **A10**, **A14**, **A16 (publish workflow)**, **A18**, **A22**. Part B (the JBang scaffolder design) is unchanged — the scaffold surface only grew.

---

## Part A — OpenRewrite best-practice audit

Notes on methodology: I cross-checked findings against (1) the official `moderneinc/rewrite-recipe-starter` `build.gradle.kts` and CI, (2) `docs.openrewrite.org` recipe-authoring pages, (3) the `0002-recipe-naming` ADR, (4) two real recipes from `openrewrite/rewrite-logging-frameworks` (`SystemPrintToLogging`, `Slf4jLogShouldBeConstant`), and (5) the `recipe-writing-lessons.md` from `rewrite-static-analysis`.

### A1. Lombok `@Value` / `@EqualsAndHashCode` vs hand-rolled equals/hashCode/getters
- **Status**: [minor] mildly nonstandard
- **What we do**: Every recipe (`SystemOutToSlf4j.java:73-82`, `AddLombokSlf4jAnnotation.java:77-86`, `JulToSlf4j.java:114-123`, `PrintStackTraceToLog.java:65-74`, `ConvertManualLoggerToSlf4j.java:88-97`, `UseCatalogReferenceForDependency.java:76-85`, `AddVersionCatalogEntry.java:76-88`) hand-rolls `equals`, `hashCode`, plus `@SuppressWarnings("unused") public boolean isXxx()` getters. No Lombok in the project at all.
- **Standard**: The official starter uses `@Value @EqualsAndHashCode(callSuper = false)` — see `SystemPrintToLogging.java` in `rewrite-logging-frameworks` and the `recipeequalsandhashcodecallsuper` recipe that flips authors' `callSuper = true` to `false`. Docs explicitly say "Recipes are value objects, so should use `@EqualsAndHashCode(callSuper = false)`" and "It is typical, but not required, that recipes use `@lombok.Value`."
- **Recommendation**: Adding Lombok as `compileOnly` + `annotationProcessor` would delete ~15 lines per recipe (×7 recipes). The current code is correct, just verbose. Worth doing now while the surface is small. Use `@Value` + `@EqualsAndHashCode(callSuper = false)`. Caveat: the project ships a Lombok-migration recipe — having Lombok in the recipe project itself is fine, but worth a one-line README note that this is intentional and not self-referential.

### A2. `Preconditions.check(...)` at `getVisitor()` level
- **Status**: [missing] missing — this is the single biggest deviation
- **What we do**: Every leaf recipe returns a bare `JavaIsoVisitor` with no precondition wrapper. Class-presence checks happen inline inside `LombokClasspathGate.isAvailable(getCursor())` per node visit.
- **Standard**: `Slf4jLogShouldBeConstant` shows the canonical shape: `return Preconditions.check(new UsesMethod<>(SLF4J_LOG), new JavaVisitor<…>() {...})`. The conventions doc calls this out for performance: "Preconditions benefit recipe execution performance when they efficiently prevent unnecessary execution of a more computationally expensive visitor."
- **Recommendation**: Wrap visitors in `Preconditions.check(...)`:
  - `SystemOutToSlf4j` → `Preconditions.check(Preconditions.or(new UsesMethod<>("java.io.PrintStream println(..)"), new UsesMethod<>("java.io.PrintStream print(..)"), new UsesMethod<>("java.io.PrintStream printf(..)")), …)`
  - `JulToSlf4j` → `Preconditions.check(new UsesType<>("java.util.logging.Logger", false), …)`
  - `PrintStackTraceToLog` → `Preconditions.check(new UsesMethod<>(PRINT_STACK_TRACE), …)`
  - `ConvertManualLoggerToSlf4j` → `Preconditions.check(new UsesType<>("org.apache.logging.log4j.Logger", false), …)`
  - `AddLombokSlf4jAnnotation` → `Preconditions.or` over the three triggers.
- The Lombok-classpath gate is a separate concern (cursor-time check on a per-CU marker) and is correct; precondition wrapping is in addition, not instead of.

### A3. Recipe IDs / package convention
- **Status**: [minor] mildly nonstandard — but defensible
- **What we do**: YAML composed-recipe IDs sit at `<rootPackage>.<RecipeName>` (root namespace); Java leaf-recipe FQNs sit at `<rootPackage>.recipes.<RecipeName>`. The two namespaces are different by design.
- **Standard**: ADR-0002 says "DO start every OpenRewrite recipe package with `org.openrewrite.<LANGUAGE>`". That's the rule for OpenRewrite-org-internal recipes; community recipes universally use their own group (e.g. `io.moderne.…`, `tech.picnic.…`). What's worth flagging: the YAML/Java namespace split is intentional but a reader might assume it's a typo.
- **Recommendation**: Leave the split as-is and document it. Consider standardising YAML IDs to also live under `.recipes` at the next major bump — breaking for downstream users.

### A4. `getTags()` and `getEstimatedEffortPerOccurrence()`
- **Status**: [add] missing-but-could-add
- **What we do**: No recipe overrides either.
- **Standard**: `Slf4jLogShouldBeConstant` includes `Set<String> tags = new HashSet<>(Arrays.asList("logging", "slf4j"))`. The static-analysis recipe-writing-lessons doc says "include RSPEC identifier in `getTags()`". `setdefaultestimatedeffortperoccurrence` is a real recipe that flags missing values.
- **Recommendation**: Add `getTags()` returning `["logging", "lombok", "slf4j", "log4j"]` (subset per recipe) and `getEstimatedEffortPerOccurrence()` returning a `Duration.ofMinutes(N)` estimate (5 for the leaf transforms is typical). Both can land on the YAML composed recipes too. Cheap discoverability win on docs.openrewrite.org if you ever submit to community-recipes.

### A5. YAML composition shape — `tags` and `estimatedEffortPerOccurrence` and `preconditions`
- **Status**: [add] missing-but-could-add
- **What we do**: `system-out-to-lombok.yml` has `name`/`displayName`/`description`/`recipeList` only.
- **Standard**: Real composed recipes (e.g. `slf4j.yml` in `rewrite-logging-frameworks`) add `tags:` and sometimes `preconditions:`. Both are optional but recommended.
- **Recommendation**: Add `tags: [logging, lombok, slf4j, log4j]` to each top-level composed recipe. `preconditions:` blocks (e.g. `org.openrewrite.java.search.UsesType: java.io.PrintStream`) are nice but lower priority — your composed recipes are user-invoked, so the cost of a no-op cycle is trivial. `estimatedEffortPerOccurrence` is more about leaf recipes.

### A6. `@Option` nullability and required handling
- **Status**: [minor] mildly nonstandard
- **What we do**: Boolean options (`requireLombokOnClasspath`) are declared as primitive `boolean` with `required = false` and a default of `false` via overloaded zero-arg constructor.
- **Standard**: `SystemPrintToLogging` uses `@Nullable Boolean addLogger` — boxed type, nullable, no overloaded constructor. This makes "unset" distinguishable from "explicitly false," which YAML-composed recipes can rely on.
- **Recommendation**: Leave as-is. Your semantics genuinely are boolean — there's no third "unset" state to distinguish. The overloaded constructor is fine. If you ever add an option where "unset means default-from-environment," migrate to boxed-nullable.

### A7. `@NullMarked` (JSpecify) vs the Lombok-style starter
- **Status**: [ok] standard, slightly ahead of curve
- **What we do**: `@NullMarked` from JSpecify on every recipe class.
- **Standard**: Starter declares `parserClasspath("org.jspecify:jspecify:1.0.0")`; `Log4jToSlf4j`-family recipes use `@Nullable` from JSpecify selectively but not blanket `@NullMarked`. Your blanket-mark approach is more conservative.
- **Recommendation**: Keep. It's strictly more rigorous than upstream.

### A8. `JavaIsoVisitor` vs `JavaVisitor`
- **Status**: [ok] standard
- **What we do**: All your visitors use `JavaIsoVisitor` because your transforms return the same-type tree.
- **Standard**: `recipe-writing-lessons.md`: "Use `JavaIsoVisitor` when returning the same LST element type; use `JavaVisitor` when transforming to different types." `Slf4jLogShouldBeConstant` uses `JavaVisitor` because it sometimes returns a different node from `visitMethodInvocation`.
- **Recommendation**: No change.

### A9. `JavaTemplate` `imports(...)` / `staticImports(...)` / `javaParser(...)`
- **Status**: [ok] standard
- **What we do**: Use `imports(SLF4J.fqn())` correctly in `AddLombokSlf4jAnnotation` and `ConvertManualLoggerToSlf4j` paired with `maybeAddImport`. Don't pass a custom `javaParser(...)` — relying on context is fine because `log` and Lombok-generated symbols are resolved at runtime.
- **Standard**: Lessons doc: "Always declare imports when templates introduce types." Custom `javaParser(...)` is for when the template references a type the test parser can't see; you handle that with `TypeValidation.none()` instead, which is also acceptable.
- **Recommendation**: No change. No `staticImports` opportunities visible — Lombok-generated `log` is a field, not a static import.

### A10. `MethodMatcher`
- **Status**: [ok] standard
- **What we do**: `private static final` matchers at class top, fully-qualified signatures: `new MethodMatcher("java.lang.Throwable printStackTrace(..)")`, the JUL family in `JulToSlf4j`.
- **Standard**: Identical pattern in `Slf4jLogShouldBeConstant`.
- **Recommendation**: One miss — `SystemOutToSlf4j.isSystemOutOrErr(...)` does string-comparison on `select.toString()` rather than using `MethodMatcher` against `java.io.PrintStream println(..)`. A real `MethodMatcher` would catch overloads correctly and would let you wire it as a `UsesMethod` precondition (see A2). Consider switching.

### A11. `JavaSourceSet` marker for classpath gating
- **Status**: [ok] standard
- **What we do**: `LombokClasspathGate` reads `JavaSourceSet` from compilation-unit markers and looks for `lombok.extern.slf4j.Slf4j` on the classpath.
- **Standard**: This is the documented mechanism. Multi-module awareness via `JavaProject` is the alternative when state needs aggregating; you don't need that.
- **Recommendation**: No change.

### A12. `ScanningRecipe` for `UseCatalogReferenceForDependency`
- **Status**: [ok] standard, well-applied
- **What we do**: Two-phase scan/visit — scan checks for `libs.versions.toml`, visit phase no-ops (`TreeVisitor.noop()`) when absent.
- **Standard**: Canonical `ScanningRecipe<Acc>` shape. Conventions doc: "Use `ScanningRecipe` accumulators for cross-visitor data."
- **Recommendation**: One observation — `Accumulator.catalogFound` is a `boolean`. The conventions doc warns "avoid boolean fields that should be per-project maps" for multi-module. In a multi-module Gradle build with one catalog at the root, your boolean is correct; but if a downstream user runs this on a workspace with multiple independent Gradle builds (composite or not), the boolean can leak. Worth adding a `Map<JavaProject, Boolean>` if a smoke test ever surfaces this.

### A13. `TypeValidation.none()` in tests
- **Status**: [ok] standard escape hatch, used appropriately
- **What we do**: `SystemOutToSlf4jTest.java:18` sets `TypeValidation.none()` because Lombok-generated `log` field can't be resolved by the test parser.
- **Standard**: FAQ: "If you're unable to resolve the missing types issue, you can disable the type validation through `RecipeSpec.afterTypeValidationOptions` or `RecipeSpec.typeValidationOptions`." Recommends `afterTypeValidationOptions` for cases where only the post-recipe tree has unresolved types.
- **Recommendation**: Several of your tests use the broader `typeValidationOptions(TypeValidation.none())` (both before and after). Where the before-source has `@Slf4j` already declared and parses cleanly, you can narrow to `afterTypeValidationOptions(TypeValidation.none())`. Cosmetic; not a bug.

### A14. Build plugin choice: `vanniktech/gradle-maven-publish-plugin` vs `org.openrewrite.build.recipe-library-base`
- **Status**: [minor] mildly nonstandard (defensible)
- **What we do**: `com.vanniktech.maven.publish:0.36.0` for Maven Central, plus `gradle-versions-plugin`, `org.openrewrite.rewrite` for self-test, and your own clean-code plugin. No OpenRewrite build plugins.
- **Standard**: Starter applies `org.openrewrite.build.recipe-library-base`, `org.openrewrite.build.publish` (Moderne Nexus), `nebula.release`, `org.openrewrite.build.recipe-repositories`. The recipe-library plugin sets compile target to Java 1.8 (per docs), wires `recipeDependencies { parserClasspath(...) }`, enables `createTypeTable` and `downloadRecipeDependencies` tasks, and embeds a `recipes.csv` for catalog discovery.
- **Recommendation**: Two separable concerns:
  - **Java target**: starter targets Java 1.8 to be runnable on widest user JDK; you target Java 17. That's a deliberate choice and is fine — JDK 8 is rare in 2026 and your README can state the floor. Leave at 17.
  - **`recipe-library-base` plugin**: Worth adopting if you want (a) `createTypeTable` (precomputes parser classpaths for faster runtime), (b) `recipeDependencies { parserClasspath(...) }` (declarative classpath for `JavaTemplate` resolution at recipe-author time), (c) auto-generated `recipes.csv` for marketplace discovery. None are required for Maven Central. The vanniktech plugin you use is the modern best-of-breed for direct Central publishing, and `recipe-library-base` doesn't replace it (the official starter pairs `recipe-library-base` with the `publish` plugin and Nexus). My read: keep vanniktech, but adopt `recipe-library-base` *alongside* it for the type-table + recipes.csv benefits. If they conflict (untested), treat the type-table as a "nice to have, skip it."

### A15. Java compile target (release=17)
- **Status**: [ok] standard for community recipes
- **What we do**: `release=17` for production code; tests at 25.
- **Standard**: Starter sets toolchain to JDK 25 with no explicit `release`, implying "newest." `recipe-library-base` per docs targets JDK 1.8.
- **Recommendation**: 17 is right. JDK 8 floor is overkill for a 2026 recipe targeting Lombok consumers (most of whom are on 17+ already).

### A16. CI workflow shape
- **Status**: [minor] mildly nonstandard
- **What we do**: `.github/workflows/gradle.yml` runs `./gradlew build` on push to main + PRs, sets up JDK 21+25.
- **Standard**: Starter CI runs `./gradlew build test` plus `mvn verify`. Uses `actions/setup-gradle@v5`, JDK 25 only.
- **Recommendation**: Worth adding `./gradlew check` (your version pulls in `integrationTest` + JaCoCo + SpotBugs — the actual quality gate). Consider `./gradlew dependencyUpdates` as a non-blocking informational step. You're missing a separate publishing workflow on `v*` tag push that runs `publishAndReleaseToMavenCentral` — currently this is operator-driven from local. Not a deviation per se but a gap given the smoke-gate is structural.

### A17. `@RecipeDescriptor` / Refaster annotation processor
- **Status**: [ok] standard not-applicable
- **What we do**: Imperative recipes only.
- **Standard**: `rewrite-templating` enables `@BeforeTemplate`/`@AfterTemplate` Refaster-style. Pure convenience — generates Recipe classes from before/after method pairs. Best for "this method call → that method call" transforms with no surrounding context.
- **Recommendation**: Refaster wouldn't help you. Your transforms are statement-level with structural changes (annotation insertion, field removal, import removal); Refaster is bad at those. Skip.

### A18. `recipes.csv` / Marketplace discoverability
- **Status**: [add] missing
- **What we do**: No `recipes.csv`, no community-recipes listing.
- **Standard**: Per Moderne docs, "Most OpenRewrite recipe modules now include an embedded recipes.csv file" (auto-generated by the recipe-library plugin). Community-recipes page is manual PR-submission to docs.openrewrite.org. There's no "marketplace registry" you have to register with — discoverability is (a) Maven Central existence, (b) PR to community-recipes docs, (c) optional `recipes.csv` for tooling.
- **Recommendation**: Submit a PR to https://github.com/openrewrite/rewrite-website to add yourself to community-recipes once 0.7 has settled. Adopt `recipe-library-base` for the auto-generated `recipes.csv` (see A14). Both cheap, both visibility wins.

### A19. Idempotence / mutation discipline
- **Status**: [ok] standard
- **What we do**: All visitors are stateless; recipes are constructor-only; LST writes go through `with*` builders.
- **Standard**: Conventions doc — "A Recipe should never mutate a field on an LST." "Recipes receiving identical LST and configuration must produce identical results."
- **Recommendation**: One yellow flag — `SystemOutDetector` and `JulCallDetector` in `AddLombokSlf4jAnnotation` are inner visitors with mutable `boolean found` fields. They're scoped per-class-declaration and discarded immediately, so observably stateless. Pattern is fine. (If you want to be pedantic, replace with `Cursor` messaging or stream-based detection.)

### A20. `JavaTemplate.builder` vs `JavaTemplate.apply`
- **Status**: [ok] standard
- **What we do**: `JavaTemplate.builder(template).imports(...).build().apply(getCursor(), ...)` everywhere.
- **Standard**: Same pattern in `Slf4jLogShouldBeConstant`. The `.contextSensitive()` modifier exists for templates that depend on lexical scope; you don't need it.
- **Recommendation**: No change.

### A21. Hand-constructed LST elements (`UseCatalogReferenceForDependency.buildCatalogReference`)
- **Status**: [minor] mildly nonstandard, with documented justification
- **What we do**: Build `J.FieldAccess` and `J.Identifier` by hand to produce `libs.lombok` because `JavaTemplate` struggles with bare expression fragments.
- **Standard**: Conventions doc: "Avoid hand-constructed LSTs. Use `JavaTemplate` or format-specific parsers instead."
- **Recommendation**: This is a known sharp edge — `JavaTemplate` can't produce a field-access expression in argument position cleanly. Your skill file (`new-recipe/SKILL.md` line 78) acknowledges this. Leave the hand-built tree, keep the comment in the skill explaining why. If a future OpenRewrite version adds expression-only template support, migrate.

### A22. Repositories, version pinning, BOM usage
- **Status**: [ok] standard
- **What we do**: `implementation(platform(libs.openrewrite.recipe.bom))` then unversioned `implementation(libs.openrewrite.java)` etc — but your TOML pins individual artifacts to `8.79.6` instead of letting the BOM manage them.
- **Standard**: Starter uses `latest.release` everywhere. The BOM's job is to align versions; pinning individual versions in your TOML duplicates that role and can drift.
- **Recommendation**: In `libs.versions.toml`, drop `version.ref = "openrewrite-core"` from the rewrite-* entries that the BOM manages, since the BOM already aligns them. Keep the BOM version pin. This will also make `dependencyUpdates` quieter.

---

## Part B — JBang + picocli template extraction plan

### B1. What's reusable scaffold vs recipe-content-specific

**Reusable scaffold** (template these, parameterize `{group}`, `{artifact}`, `{rootPackage}`, `{javaTarget}`, `{rewriteVersion}`):
- `build.gradle.kts` skeleton — plugin block, BOM dep, source-set wiring for `integrationTest` + `smokeTest`, the JDK-21-launcher pinning logic, the `publishAndReleaseToMavenCentral` smoke gate, mavenPublishing pom block.
- `gradle/libs.versions.toml` skeleton — version table and `[libraries]` block for the rewrite-* family, JUnit, AssertJ, JSpecify.
- `gradle.properties` (literal, no parameters).
- `gradlew`, `gradlew.bat`, `gradle/wrapper/*` (literal).
- `settings.gradle.kts` (`rootProject.name = "{artifact}"`).
- `.gitignore` (literal — Gradle/IntelliJ/macOS).
- `.github/workflows/gradle.yml` and a new `release.yml` for tag-triggered publish.
- `CLAUDE.md` skeleton — project-structure section, publication-workflow numbered list, coding-standards bullets. Needs `{rootPackage}` substitution.
- `README.md` skeleton — title, install snippet, "Recipes" placeholder section, "Supported project shapes" placeholder.
- `SMOKE_TEST.md` skeleton — §1/§2/§3 structure with placeholder cells.
- `BACKLOG.md` skeleton — Shipped/Queued/Active/Parked headings.
- `.claude/skills/{new-gradle-project,new-recipe,recipe-testing,smoke-test}/SKILL.md` — verbatim copies; the skills are project-agnostic.
- `src/smokeTest/java/{rootPackage}/smoketest/*` — `Fixture.java`, `GradleRunner.java`, `ProjectShapeScaffolder.java`, `ProjectShapeSmokeTest.java`, `ProjectShapeVariant.java`, `RecipeResolutionSmokeTest.java`, `SmokeProject.java`, `SmokeTest.java`, `SmokeTestConfig.java`, `SmokeVariant.java`. Need `{rootPackage}` substitution and the per-recipe `cell(...)` matrix entries blanked or replaced with one example cell.
- `src/integrationTest/java/{rootPackage}/recipes/` — empty directory plus a sample integration test stub.
- `src/test/java/{rootPackage}/recipes/matrix/MatrixTestSupport.java` — generic Gradle marker helper.

**Recipe-content-specific** (NOT templated — these are the user's payload):
- The 14 recipe classes under `src/main/java/{rootPackage}/recipes/`.
- `src/main/resources/META-INF/rewrite/{name}.yml` — per-project YAML compositions.
- All `*Test.java` and `*MethodTest.java` files (one set per recipe).
- The smoke-test cell matrix entries (the `cell(...)` lines inside `SmokeTest.matrix()` and `ProjectShapeSmokeTest.matrix()`).
- `LoggerNames.java`, `LombokLoggingAnnotation.java`, `LombokClasspathGate.java`, etc — these are domain-specific helpers.

The runner *infrastructure* (`SmokeProject`, `GradleRunner`, `ProjectShapeScaffolder`) is reusable; the *matrix data* is not. So the template ships the runner with one trivial example cell that the user replaces.

### B2. JBang template shape: one script, fetches templates from a tagged Git repo

Recommendation: **single JBang script, templates pulled from a separate Git repo at a `//DEPS`-pinned tag**, NOT embedded heredocs.

Rationale:
- Embedding ~30 file templates as Java string literals turns the script into a 2,000-line mess and makes upgrades painful.
- JBang scripts can fetch resources at runtime via plain `HttpClient`/`URL` calls; Git tags give you reproducible "scaffold the v0.7-shaped project" semantics.
- The official JBang `init --template` mechanism uses GitHub-hosted catalog templates — same model.

Layout: one script file `recipescaffold.java` with `@Command(subcommands = {Init.class, AddRecipe.class, BumpVersions.class, VerifyGates.class})`. JBang `//DEPS` line for picocli + the template-repo coordinate. The script downloads a release tarball of the template repo at the pinned tag, expands it into the target dir, runs string substitution on each file (`{group}`, `{artifact}`, `{rootPackage}`, `{rewriteVersion}`, `{javaTarget}`, `{authorName}`, `{authorEmail}`, `{githubOrg}`, `{githubRepo}`).

### B3. Picocli command surface

Subcommands (decreasing priority):
1. **`init`** — required. Args: `--group`, `--artifact`, `--package`, `--author`, `--github-org`, `--github-repo`, `--java-target=17`, `--rewrite-version=8.79.6`, `--directory=.`. Scaffolds a fresh project. Highest leverage.
2. **`add-recipe <name>`** — high value. Args: `--name`, `--type=java|yaml|scanning|toml`, `--with-tests`. Drops a recipe class skeleton + test skeleton in `src/main/java/.../recipes/` and `src/test/java/.../recipes/`. Reads the existing project's `build.gradle.kts` to discover `{rootPackage}` automatically. This is where the `new-recipe` skill content gets operationalized.
3. **`verify-gates`** — medium value. Runs `./gradlew check integrationTest smokeTest` in order, prints a summary table. Just a thin wrapper around Gradle but useful as the documented "release-readiness check" entrypoint.
4. **`bump-versions`** — medium value. Reads `gradle/libs.versions.toml`, queries Maven Central for newer versions of each entry, prints a diff and (with `--apply`) rewrites the TOML. Subset of what Ben-Manes does but TOML-aware and one-step.
5. **`release`** — nice to have. Runs `verify-gates`, opens an editor on `BACKLOG.md` to confirm release notes, bumps version in `build.gradle.kts`, tags, pushes. Risky to automate — leave as a documented workflow until proven safe.

Skip `release` and `bump-versions` for v1; ship `init` + `add-recipe` + `verify-gates`.

### B4. Where the template repo lives

Recommendation: **separate GitHub repo** named `system-out-to-lombok-template` (or `recipescaffold` to avoid implying it's an official Moderne template).

- Tagged releases (`v0.1`, `v0.2`, …) — JBang script's `//DEPS` line embeds the tag. Each scaffolded project records the template tag it was created from in a comment in `CLAUDE.md` so you can later diff against the template.
- Separate repo (not a directory inside the recipe project) so:
  - Template upgrades don't churn the recipe project's git history.
  - The template can be developed in isolation with its own integration tests (a CI job that does `jbang init` and `./gradlew check` on the output).
- NOT a GitHub "template repo" (the green-button kind). Those work fine for one-off forks but lack version tagging — and the JBang flow gives users a much better UX than "click button, manually rename packages."

### B5. How the four skills fit in

Skills are **dual-purpose**:
1. **Copied verbatim into the scaffolded project's `.claude/skills/`** so the user gets the same "invoke skill X" workflow you have.
2. **Read by the JBang script for skeleton text** when emitting a new recipe (`add-recipe`). Specifically, `new-recipe/SKILL.md` contains the canonical visitor skeleton (the `JavaIsoVisitor` block, the `MethodMatcher` block, the YAML composition block). The `add-recipe` subcommand parses these blocks (delimited by triple-backtick fences) and emits them with the user's chosen recipe name substituted in.

This means the skill files are the **source of truth** for what a "good recipe" looks like — both the JBang script and Claude Code in the user's project read the same file. No drift.

Important: keep the skill files in the template repo. The JBang script vendors them at scaffold time. If you later improve a skill in the template repo, users on older scaffolds won't auto-get the update — they'd re-run `init --upgrade-skills` (a flag worth adding to `init`).

### B6. Template-repo file layout

```
recipescaffold/
├── README.md                              # how to use the template
├── jbang/
│   └── recipescaffold.java                # the JBang script itself
├── template/                              # everything below this is scaffolded into the user's project
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   ├── gradle.properties
│   ├── gradlew
│   ├── gradlew.bat
│   ├── .gitignore
│   ├── CLAUDE.md
│   ├── README.md
│   ├── SMOKE_TEST.md
│   ├── BACKLOG.md
│   ├── gradle/
│   │   ├── libs.versions.toml
│   │   └── wrapper/
│   │       ├── gradle-wrapper.jar
│   │       └── gradle-wrapper.properties
│   ├── .github/workflows/
│   │   ├── gradle.yml
│   │   └── release.yml
│   ├── .claude/skills/
│   │   ├── new-gradle-project/SKILL.md
│   │   ├── new-recipe/SKILL.md
│   │   ├── recipe-testing/SKILL.md
│   │   └── smoke-test/SKILL.md
│   └── src/
│       ├── main/
│       │   ├── java/__ROOT_PACKAGE__/recipes/
│       │   │   └── ExampleRecipe.java     # one demo recipe so a fresh project compiles
│       │   └── resources/META-INF/rewrite/
│       │       └── example.yml
│       ├── test/java/__ROOT_PACKAGE__/recipes/
│       │   ├── ExampleRecipeTest.java
│       │   └── matrix/MatrixTestSupport.java
│       ├── integrationTest/java/__ROOT_PACKAGE__/recipes/
│       │   └── ExampleIntegrationTest.java
│       └── smokeTest/
│           ├── java/__ROOT_PACKAGE__/smoketest/
│           │   ├── Fixture.java
│           │   ├── GradleRunner.java
│           │   ├── ProjectShapeScaffolder.java
│           │   ├── ProjectShapeSmokeTest.java
│           │   ├── ProjectShapeVariant.java
│           │   ├── RecipeResolutionSmokeTest.java
│           │   ├── SmokeProject.java
│           │   ├── SmokeTest.java
│           │   ├── SmokeTestConfig.java
│           │   └── SmokeVariant.java
│           └── resources/
│               └── gradle-wrapper.properties
├── snippets/                              # source-of-truth fragments add-recipe inserts
│   ├── recipe-class-java.template
│   ├── recipe-class-scanning.template
│   ├── recipe-class-toml.template
│   ├── recipe-test.template
│   ├── recipe-method-test.template
│   └── yaml-composition-block.template
└── tests/                                 # CI: scaffold + ./gradlew check on the output
    └── ci-smoke.sh
```

`__ROOT_PACKAGE__` is the literal directory marker the script renames at scaffold time (Java source dirs are derived from package). File contents use `{rootPackage}`-style placeholders so substitution is a single pass.

### B7. JBang invocation UX

Sample commands:

```bash
# One-time install
jbang trust add https://github.com/fiftiesHousewife/recipescaffold
jbang app install --name=recipescaffold \
  https://github.com/fiftiesHousewife/recipescaffold/blob/v0.1/jbang/recipescaffold.java

# Scaffold a new recipe project
recipescaffold init \
  --group=io.github.acme \
  --artifact=acme-rewrite-recipes \
  --package=io.github.acme.recipes \
  --author="Jane Doe" \
  --github-org=acme \
  --github-repo=acme-rewrite-recipes \
  --directory=./acme-rewrite-recipes

# Inside an existing scaffolded project
cd acme-rewrite-recipes
recipescaffold add-recipe --name=ConvertFooToBar --type=java --with-tests
recipescaffold verify-gates
```

The `init` subcommand should be interactive when args are missing (picocli supports this via `interactive = true` on options, or just a fallback prompt loop). For automation, all options must be passable as flags.

### B8. Maintenance burden + flow direction

Push-based with a manual sync step. An upstream recipe project (whichever real recipe library this scaffold was originally extracted from) can act as the source of truth for evolving the template content. When you fix a build-script issue or extend a skill there, you propagate it to the template repo manually:

```
recipe-project (canonical) ──(rsync + tag)──> template-repo (scaffold source)
```

Concrete process:
1. Land the fix in the recipe project (test, smoke, ship).
2. Cherry-pick the relevant files into the template repo, replacing concrete identifiers with `{group}`/`{rootPackage}` placeholders.
3. CI in the template repo runs `jbang init --apply --directory=/tmp/scaffold-test ...` and `./gradlew check` against the result.
4. Tag the template repo (`v0.2`).
5. Bump the JBang `app install` URL in the template repo's README to point at the new tag.

Anti-pattern to avoid: trying to make the template repo a strict superset of the recipe project (e.g. via git subtree). Templates need to evolve independently — the recipe project will grow recipe-specific complexity that doesn't belong in a template.

A `RECIPE_FROM_TEMPLATE_VERSION` field at the top of the scaffolded `CLAUDE.md` lets users diff their project against the template they came from.

### B9. Picocli specifics

```java
//DEPS info.picocli:picocli:4.7.7
//DEPS org.apache.commons:commons-compress:1.27.1   // tar/zip extraction
//SOURCES Init.java AddRecipe.java VerifyGates.java // multi-file JBang

@Command(name = "recipescaffold",
         mixinStandardHelpOptions = true,
         version = "0.1",
         subcommands = {Init.class, AddRecipe.class, VerifyGates.class})
public class RecipeScaffold implements Runnable {
    public void run() { CommandLine.usage(this, System.err); }
    public static void main(String[] args) {
        System.exit(new CommandLine(new RecipeScaffold()).execute(args));
    }
}

@Command(name = "init", description = "Scaffold a new OpenRewrite recipe project.")
class Init implements Callable<Integer> {
    @Option(names = "--group", required = true) String group;
    @Option(names = "--artifact", required = true) String artifact;
    @Option(names = "--package", required = true) String rootPackage;
    @Option(names = "--author", required = true) String author;
    @Option(names = "--github-org", required = true) String githubOrg;
    @Option(names = "--github-repo", required = true) String githubRepo;
    @Option(names = "--java-target", defaultValue = "17") int javaTarget;
    @Option(names = "--rewrite-version", defaultValue = "8.79.6") String rewriteVersion;
    @Option(names = "--template-version", defaultValue = "v0.1") String templateVersion;
    @Option(names = "--directory", defaultValue = ".") Path directory;
    @Option(names = "--upgrade-skills", description = "Only refresh .claude/skills/, leave everything else.") boolean upgradeSkillsOnly;
    public Integer call() throws Exception { /* ... */ return 0; }
}
```

- Use **class-based subcommands** (one class per command) rather than method-based — your subcommands have non-trivial logic and shared helpers; classes are easier to extract and test.
- `@Spec` injection for shared spec access.
- Skip `picocli-shell-jline3` autocomplete for now — `init` is run once per project, the autocomplete value is low. Add later if `add-recipe` becomes a frequent inner-loop tool.
- Help text via `description = ""` per command. Picocli auto-generates `-h`/`--help`.

### B10. Risks / unknowns

- **JBang script size**: even with templates externalized, the script will hit ~600–800 lines across `Init`, `AddRecipe`, `VerifyGates`. JBang handles multi-file via `//SOURCES` but multi-file JBang scripts are less convenient than single-file. Mitigation: ship as a single fat script for v1; if it grows, convert to a regular Maven module published as a fat jar that JBang downloads.
- **Tag→template-source coupling**: the JBang script must download the template at the same tag the script was published at. Easy bug: shipping JBang `recipescaffold.java@v0.2` that fetches `template/@v0.1`. Mitigation: bake the tag into the script as `private static final String TEMPLATE_TAG = "v0.2"` and have a CI assert that the script's tag matches `TEMPLATE_TAG`.
- **Wrapper jar**: `gradle-wrapper.jar` is a binary and changes occasionally. Don't try to template it — copy bytes-for-bytes from the template repo, refresh the template when you bump Gradle.
- **Smoke runner is hardcoded for Lombok**: `SmokeProject.writeBuild` hardcodes `org.projectlombok:lombok:1.18.44`, `org.slf4j:slf4j-api:2.0.17`, etc. Templating these as `// EDIT THIS BLOCK FOR YOUR RECIPE'S DEPS` comments is the honest move — there's no clean abstraction here and pretending otherwise will produce a worse runner.
- **`add-recipe` package detection**: needs to read `build.gradle.kts` to find `group =` and infer `{rootPackage}`. Brittle. Mitigation: drop a `.recipescaffold.yml` file at scaffold time recording the chosen package, group, artifact, author — `add-recipe` reads from there with no parsing.
- **JBang trust prompts**: first invocation will prompt to trust the GitHub URL. Document this in the install snippet.
- **Picocli + GraalVM**: a future native-image build of the script is appealing for startup time but not necessary; JBang's caching gets you near-instant warm starts.

### B11. Implementation sequence

1. **Week 1**: Create `recipescaffold` repo. Manually copy current files from the upstream recipe project with placeholder substitution. Verify by hand: `unzip + sed + ./gradlew check`. No JBang yet.
2. **Week 2**: Write `Init` JBang subcommand. Tag template `v0.1`. Test: `jbang init` produces a project that `./gradlew check` passes on a fresh machine.
3. **Week 3**: Write `AddRecipe` subcommand using snippet templates. Test: scaffold a project, add three recipes, `./gradlew check` still passes.
4. **Week 4**: Write `VerifyGates` (thin Gradle wrapper). Add CI to template repo that runs end-to-end scaffold + check on every PR.
5. **Later**: `BumpVersions`, `Release`, `--upgrade-skills` mode for `init`.

---

### Critical Files for Implementation

Reference paths in any source-of-truth recipe-project checkout (paths are relative to a typical OpenRewrite recipe library; substitute your own package and recipe names):

- `build.gradle.kts`
- `gradle/libs.versions.toml`
- `src/smokeTest/java/<rootPackage>/smoketest/SmokeProject.java`
- `.claude/skills/new-recipe/SKILL.md`
- `src/main/resources/META-INF/rewrite/<recipe-name>.yml`

### Sources

Part A audit cited:
- [Recipe conventions and best practices | OpenRewrite Docs](https://docs.openrewrite.org/authoring-recipes/recipe-conventions-and-best-practices)
- [Use of @EqualsAndHashCode on Recipe | OpenRewrite Docs](https://docs.openrewrite.org/recipes/java/recipes/recipeequalsandhashcodecallsuper)
- [Writing a Java refactoring recipe | OpenRewrite Docs](https://docs.openrewrite.org/authoring-recipes/writing-a-java-refactoring-recipe)
- [Recipe development environment | OpenRewrite Docs](https://docs.openrewrite.org/authoring-recipes/recipe-development-environment)
- [ADR-0002 recipe naming](https://github.com/openrewrite/rewrite/blob/main/doc/adr/0002-recipe-naming.md)
- [Frequently asked questions | OpenRewrite Docs](https://docs.openrewrite.org/reference/faq)
- [recipe-writing-lessons.md (rewrite-static-analysis)](https://github.com/openrewrite/rewrite-static-analysis/blob/main/recipe-writing-lessons.md)
- [moderneinc/rewrite-recipe-starter build.gradle.kts](https://raw.githubusercontent.com/moderneinc/rewrite-recipe-starter/main/build.gradle.kts)
- [Gradle Plugin Portal: org.openrewrite.build.recipe-library](https://plugins.gradle.org/plugin/org.openrewrite.build.recipe-library)
- [openrewrite/rewrite-logging-frameworks: SystemPrintToLogging.java + Slf4jLogShouldBeConstant.java](https://github.com/openrewrite/rewrite-logging-frameworks)
- [Community recipes | OpenRewrite Docs](https://docs.openrewrite.org/reference/community-recipes)

Part B JBang/picocli grounding:
- [JBang Templates documentation](https://www.jbang.dev/documentation/jbang/latest/templates.html)
- [picocli — GitHub](https://github.com/remkop/picocli)
- [picocli quick guide](https://picocli.info/quick-guide.html)