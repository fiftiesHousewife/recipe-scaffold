# OpenRewrite recipe-library audit

> **Historical design doc.** Captured 2026-05-03 as the source plan for what became `recipescaffold`. Part B (the JBang + picocli scaffolder design) has shipped in full; live state lives in [`BACKLOG.md`](./BACKLOG.md) and [`README.md`](./README.md), and Part B is intentionally omitted here. What is preserved: the **Part A audit** of OpenRewrite recipe-library best practices against the upstream recipe project this scaffolder was extracted from. Worth keeping because the audit grading still informs decisions for any recipe library scaffolded from this template.
>
> **Provenance.** Generated 2026-05-03 by a Plan-type subagent (description: "OpenRewrite audit + JBang template plan"). Status markers (`[ok]` / `[minor]` / `[missing]` / `[add]`) replaced the agent's emoji column to comply with the project's no-emojis-in-docs rule.

## Part A status (live)

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
| A14 — `recipe-library-base` plugin | minor | superseded — `template/build-logic/` ships our own convention plugin |
| A15 — Java release target | ok | closed |
| A16 — CI workflow shape | minor | shipped — `release.yml` plus four CI jobs in `ci.yml` |
| A17 — `@RecipeDescriptor` / Refaster | ok | closed N/A |
| A18 — `recipes.csv` + community-recipes PR | add | open — visibility win, no `recipes.csv` yet |
| A19 — Idempotence | ok | closed |
| A20 — `JavaTemplate.builder` shape | ok | closed |
| A21 — Hand-built LST in `UseCatalogReferenceForDependency` | minor | closed with documented justification |
| A22 — BOM version pinning | ok | open — TOML still pins individual `8.79.6` entries the BOM should align |

Active items worth carrying into the next release cycle: **A10**, **A18**, **A22**.

---

## Part A — OpenRewrite best-practice audit

Notes on methodology: I cross-checked findings against (1) the official `moderneinc/rewrite-recipe-starter` `build.gradle.kts` and CI, (2) `docs.openrewrite.org` recipe-authoring pages, (3) the `0002-recipe-naming` ADR, (4) two real recipes from `openrewrite/rewrite-logging-frameworks` (`SystemPrintToLogging`, `Slf4jLogShouldBeConstant`), and (5) the `recipe-writing-lessons.md` from `rewrite-static-analysis`.

### A1. Lombok `@Value` / `@EqualsAndHashCode` vs hand-rolled equals/hashCode/getters
- **Status**: [minor] mildly nonstandard
- **What we did**: Every recipe hand-rolled `equals`, `hashCode`, plus `@SuppressWarnings("unused") public boolean isXxx()` getters. No Lombok in the project at all.
- **Standard**: The official starter uses `@Value @EqualsAndHashCode(callSuper = false)` — see `SystemPrintToLogging.java` in `rewrite-logging-frameworks` and the `recipeequalsandhashcodecallsuper` recipe that flips authors' `callSuper = true` to `false`. Docs explicitly say "Recipes are value objects, so should use `@EqualsAndHashCode(callSuper = false)`" and "It is typical, but not required, that recipes use `@lombok.Value`."
- **Recommendation**: Adding Lombok as `compileOnly` + `annotationProcessor` would delete ~15 lines per recipe (×7 recipes). The current code is correct, just verbose. Worth doing now while the surface is small. Use `@Value` + `@EqualsAndHashCode(callSuper = false)`. Caveat: the project ships a Lombok-migration recipe — having Lombok in the recipe project itself is fine, but worth a one-line README note that this is intentional and not self-referential.

### A2. `Preconditions.check(...)` at `getVisitor()` level
- **Status**: [missing] missing — this is the single biggest deviation
- **What we did**: Every leaf recipe returns a bare `JavaIsoVisitor` with no precondition wrapper. Class-presence checks happen inline inside `LombokClasspathGate.isAvailable(getCursor())` per node visit.
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
- **What we did**: YAML composed-recipe IDs at `<rootPackage>.<RecipeName>` (root namespace); Java leaf-recipe FQNs at `<rootPackage>.recipes.<RecipeName>`. The two namespaces are different by design.
- **Standard**: ADR-0002 says "DO start every OpenRewrite recipe package with `org.openrewrite.<LANGUAGE>`". That's the rule for OpenRewrite-org-internal recipes; community recipes universally use their own group (e.g. `io.moderne.…`, `tech.picnic.…`). What's worth flagging: the YAML/Java namespace split is intentional but a reader might assume it's a typo.
- **Recommendation**: Leave the split as-is and document it. Consider standardising YAML IDs to also live under `.recipes` at the next major bump — breaking for downstream users.

### A4. `getTags()` and `getEstimatedEffortPerOccurrence()`
- **Status**: [add] missing-but-could-add
- **What we did**: No recipe overrides either.
- **Standard**: `Slf4jLogShouldBeConstant` includes `Set<String> tags = new HashSet<>(Arrays.asList("logging", "slf4j"))`. The static-analysis recipe-writing-lessons doc says "include RSPEC identifier in `getTags()`". `setdefaultestimatedeffortperoccurrence` is a real recipe that flags missing values.
- **Recommendation**: Add `getTags()` and `getEstimatedEffortPerOccurrence()` returning a `Duration.ofMinutes(N)` estimate (5 for the leaf transforms is typical). Both can land on the YAML composed recipes too. Cheap discoverability win on docs.openrewrite.org if you ever submit to community-recipes.

### A5. YAML composition shape — `tags` and `estimatedEffortPerOccurrence` and `preconditions`
- **Status**: [add] missing-but-could-add
- **What we did**: `system-out-to-lombok.yml` had `name`/`displayName`/`description`/`recipeList` only.
- **Standard**: Real composed recipes (e.g. `slf4j.yml` in `rewrite-logging-frameworks`) add `tags:` and sometimes `preconditions:`. Both are optional but recommended.
- **Recommendation**: Add `tags: [logging, lombok, slf4j, log4j]` to each top-level composed recipe. `preconditions:` blocks are nice but lower priority — composed recipes are user-invoked, so the cost of a no-op cycle is trivial. `estimatedEffortPerOccurrence` is more about leaf recipes.

### A6. `@Option` nullability and required handling
- **Status**: [minor] mildly nonstandard
- **What we did**: Boolean options declared as primitive `boolean` with `required = false` and a default of `false` via overloaded zero-arg constructor.
- **Standard**: `SystemPrintToLogging` uses `@Nullable Boolean addLogger` — boxed type, nullable, no overloaded constructor. This makes "unset" distinguishable from "explicitly false," which YAML-composed recipes can rely on.
- **Recommendation**: Leave as-is. The semantics genuinely are boolean — there's no third "unset" state to distinguish. The overloaded constructor is fine. If you ever add an option where "unset means default-from-environment," migrate to boxed-nullable.

### A7. `@NullMarked` (JSpecify) vs the Lombok-style starter
- **Status**: [ok] standard, slightly ahead of curve
- **What we did**: `@NullMarked` from JSpecify on every recipe class.
- **Standard**: Starter declares `parserClasspath("org.jspecify:jspecify:1.0.0")`; `Log4jToSlf4j`-family recipes use `@Nullable` from JSpecify selectively but not blanket `@NullMarked`. Blanket-mark is more conservative.
- **Recommendation**: Keep. Strictly more rigorous than upstream.

### A8. `JavaIsoVisitor` vs `JavaVisitor`
- **Status**: [ok] standard
- **What we did**: All visitors use `JavaIsoVisitor` because transforms return the same-type tree.
- **Standard**: `recipe-writing-lessons.md`: "Use `JavaIsoVisitor` when returning the same LST element type; use `JavaVisitor` when transforming to different types." `Slf4jLogShouldBeConstant` uses `JavaVisitor` because it sometimes returns a different node from `visitMethodInvocation`.
- **Recommendation**: No change.

### A9. `JavaTemplate` `imports(...)` / `staticImports(...)` / `javaParser(...)`
- **Status**: [ok] standard
- **What we did**: Use `imports(SLF4J.fqn())` correctly in `AddLombokSlf4jAnnotation` and `ConvertManualLoggerToSlf4j` paired with `maybeAddImport`. No custom `javaParser(...)` — relying on context is fine because `log` and Lombok-generated symbols are resolved at runtime.
- **Standard**: Lessons doc: "Always declare imports when templates introduce types." Custom `javaParser(...)` is for when the template references a type the test parser can't see; the project handles that with `TypeValidation.none()` instead, which is also acceptable.
- **Recommendation**: No change. No `staticImports` opportunities visible — Lombok-generated `log` is a field, not a static import.

### A10. `MethodMatcher`
- **Status**: [ok] standard
- **What we did**: `private static final` matchers at class top, fully-qualified signatures.
- **Standard**: Identical pattern in `Slf4jLogShouldBeConstant`.
- **Recommendation**: One miss — `SystemOutToSlf4j.isSystemOutOrErr(...)` does string-comparison on `select.toString()` rather than using `MethodMatcher` against `java.io.PrintStream println(..)`. A real `MethodMatcher` would catch overloads correctly and would let you wire it as a `UsesMethod` precondition (see A2). Consider switching.

### A11. `JavaSourceSet` marker for classpath gating
- **Status**: [ok] standard
- **What we did**: `LombokClasspathGate` reads `JavaSourceSet` from compilation-unit markers and looks for `lombok.extern.slf4j.Slf4j` on the classpath.
- **Standard**: Documented mechanism. Multi-module awareness via `JavaProject` is the alternative when state needs aggregating; not needed here.
- **Recommendation**: No change.

### A12. `ScanningRecipe` for `UseCatalogReferenceForDependency`
- **Status**: [ok] standard, well-applied
- **What we did**: Two-phase scan/visit — scan checks for `libs.versions.toml`, visit phase no-ops (`TreeVisitor.noop()`) when absent.
- **Standard**: Canonical `ScanningRecipe<Acc>` shape.
- **Recommendation**: One observation — `Accumulator.catalogFound` is a `boolean`. The conventions doc warns "avoid boolean fields that should be per-project maps" for multi-module. In a multi-module Gradle build with one catalog at the root, the boolean is correct; but if a downstream user runs this on a workspace with multiple independent Gradle builds (composite or not), it can leak. Worth adding a `Map<JavaProject, Boolean>` if a smoke test ever surfaces this.

### A13. `TypeValidation.none()` in tests
- **Status**: [ok] standard escape hatch, used appropriately
- **What we did**: Tests set `TypeValidation.none()` because Lombok-generated `log` field can't be resolved by the test parser.
- **Standard**: FAQ recommends `afterTypeValidationOptions` for cases where only the post-recipe tree has unresolved types.
- **Recommendation**: Several tests use the broader `typeValidationOptions(TypeValidation.none())` (both before and after). Where the before-source has `@Slf4j` already declared and parses cleanly, narrow to `afterTypeValidationOptions(TypeValidation.none())`. Cosmetic; not a bug.

### A14. Build plugin choice: `vanniktech/gradle-maven-publish-plugin` vs `org.openrewrite.build.recipe-library-base`
- **Status**: [minor] superseded — `template/build-logic/` ships our own `recipe-library` convention plugin
- **What we did**: `com.vanniktech.maven.publish:0.36.0` for Maven Central, plus `gradle-versions-plugin`, `org.openrewrite.rewrite` for self-test. No OpenRewrite build plugins.
- **Standard**: Starter applies `org.openrewrite.build.recipe-library-base`, `org.openrewrite.build.publish` (Moderne Nexus), `nebula.release`, `org.openrewrite.build.recipe-repositories`. The recipe-library plugin sets compile target to Java 1.8 (per docs), wires `recipeDependencies { parserClasspath(...) }`, enables `createTypeTable` and `downloadRecipeDependencies` tasks, and embeds a `recipes.csv` for catalog discovery.
- **Outcome**: The scaffolder now ships its own convention plugin under `template/build-logic/src/main/kotlin/recipe-library.gradle.kts`. It handles toolchain, integrationTest/smokeTest source sets, jacoco, sign-onlyIf, and the pre-publish smokeTest gate. It does not yet provide `createTypeTable` or auto-generate `recipes.csv` — both would be additive features for a future iteration.

### A15. Java compile target (release=17)
- **Status**: [ok] standard for community recipes
- **What we did**: `release=17` for production code; tests at 25.
- **Standard**: Starter sets toolchain to JDK 25 with no explicit `release`, implying "newest." `recipe-library-base` per docs targets JDK 1.8.
- **Recommendation**: 17 is right. JDK 8 floor is overkill for a 2026 recipe targeting Lombok consumers (most of whom are on 17+ already).

### A16. CI workflow shape
- **Status**: [shipped] template ships `release.yml`; this repo runs four CI jobs
- **What we did originally**: One `gradle.yml` running `./gradlew build` on push and PR.
- **Outcome**: The scaffolded project ships `release.yml` (tag-triggered Maven Central publish), `wrapper-validation.yml`, and `gradle.yml`. This repo's CI runs four parallel jobs: `bash-scaffold`, `jbang-scaffold`, `harness` (in-repo TestKit), and `actionlint`.

### A17. `@RecipeDescriptor` / Refaster annotation processor
- **Status**: [ok] standard not-applicable
- **What we did originally**: Imperative recipes only.
- **Outcome**: Refaster is now a first-class scaffolder option (`add-recipe --type refaster` ships a holder class with one nested `@RecipeDescriptor` template-pair, idiomatic per `moderneinc/rewrite-recipe-starter`).

### A18. `recipes.csv` / Marketplace discoverability
- **Status**: [add] missing
- **What we did**: No `recipes.csv`, no community-recipes listing.
- **Standard**: Per Moderne docs, "Most OpenRewrite recipe modules now include an embedded recipes.csv file" (auto-generated by the recipe-library plugin). Community-recipes page is manual PR-submission to docs.openrewrite.org.
- **Recommendation**: Submit a PR to https://github.com/openrewrite/rewrite-website to add yourself to community-recipes once the library has settled. Adding `createTypeTable`-style auto-generated `recipes.csv` to our convention plugin is the additive path.

### A19. Idempotence / mutation discipline
- **Status**: [ok] standard
- **What we did**: All visitors are stateless; recipes are constructor-only; LST writes go through `with*` builders.
- **Recommendation**: One yellow flag — `SystemOutDetector` and `JulCallDetector` in `AddLombokSlf4jAnnotation` are inner visitors with mutable `boolean found` fields. They're scoped per-class-declaration and discarded immediately, so observably stateless. Pattern is fine. (If you want to be pedantic, replace with `Cursor` messaging or stream-based detection.)

### A20. `JavaTemplate.builder` vs `JavaTemplate.apply`
- **Status**: [ok] standard
- **What we did**: `JavaTemplate.builder(template).imports(...).build().apply(getCursor(), ...)` everywhere.
- **Recommendation**: No change.

### A21. Hand-constructed LST elements (`UseCatalogReferenceForDependency.buildCatalogReference`)
- **Status**: [minor] mildly nonstandard, with documented justification
- **What we did**: Build `J.FieldAccess` and `J.Identifier` by hand to produce `libs.lombok` because `JavaTemplate` struggles with bare expression fragments.
- **Standard**: Conventions doc: "Avoid hand-constructed LSTs. Use `JavaTemplate` or format-specific parsers instead."
- **Recommendation**: Known sharp edge — `JavaTemplate` can't produce a field-access expression in argument position cleanly. Leave the hand-built tree, keep the comment in the skill explaining why. If a future OpenRewrite version adds expression-only template support, migrate.

### A22. Repositories, version pinning, BOM usage
- **Status**: [ok] standard, [open] TOML pinning
- **What we did**: `implementation(platform(libs.openrewrite.recipe.bom))` then unversioned `implementation(libs.openrewrite.java)` etc — but the TOML pinned individual artifacts to `8.79.6` instead of letting the BOM manage them.
- **Standard**: Starter uses `latest.release` everywhere. The BOM's job is to align versions; pinning individual versions in the TOML duplicates that role and can drift.
- **Recommendation**: Drop `version.ref` from the rewrite-* entries the BOM manages. Keep the BOM version pin. This will also make `dependencyUpdates` quieter.

---

## Sources

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
