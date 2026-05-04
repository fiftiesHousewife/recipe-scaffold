---
name: recipe-testing
description: Use this skill when writing or restructuring tests for an OpenRewrite recipe — whether single-source `RewriteTest` cases, helper-method unit tests, multi-source Gradle-aware `rewriteRun` setups, or test-matrix scaffolding across DSL/topology/deps-style combinations. Covers when to split integration vs unit tests, how to handle Lombok-generated types with `TypeValidation.none()`, how to inject `GradleProject` markers to simulate multi-module topology, and when RewriteTest is an approximation that a /tmp smoke test needs to back up. Invoke for phrases like "test this recipe", "write a RewriteTest", "how do I test the Groovy path", "add a matrix test", or whenever a user complains that a recipe's behavior is hard to pin down from unit tests alone.
---

# Testing OpenRewrite recipes

## The two-layer strategy

Every non-trivial recipe gets two kinds of tests, living in the same package:

| File | Purpose | Uses |
| --- | --- | --- |
| `MyRecipeTest.java` | Integration — full recipe fires on before/after source fixtures | `RewriteTest`, `rewriteRun(...)` |
| `MyRecipeMethodTest.java` | Unit — exercise package-private helpers in isolation | Plain JUnit + AssertJ, no `rewriteRun` |

The integration test proves the visitor + template produce the expected diff. The unit test proves the helpers (type matching, tree inspection, name predicates) behave correctly in isolation, with precise failure messages. When an integration test fails with a surprising diff, the unit tests tell you which helper is wrong.

This is why helpers are **package-private** rather than `private` — the unit test in the same package needs to call them directly.

## `RewriteTest` integration skeleton

```java
class MyRecipeTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MyRecipe())
            .parser(JavaParser.fromJavaVersion())
            .typeValidationOptions(TypeValidation.none()); // see below
    }

    @Test
    void convertsSimpleCase() {
        rewriteRun(
            java(
                """
                // before
                """,
                """
                // after
                """
            )
        );
    }

    @Test
    void leavesUnrelatedCodeAlone() {
        rewriteRun(
            java("""
                // single-arg form: asserts no change
                """)
        );
    }
}
```

One text block `java(before, after)` asserts a diff; one `java(before)` asserts no change. Use both — the "no change" case is where regressions hide.

## `TypeValidation.none()` for Lombok-aware recipes

Lombok generates symbols at compile time that the OpenRewrite test parser can't resolve (e.g. the `log` field produced by `@Slf4j`). Without relaxing validation, any recipe that adds Lombok annotations will fail parse validation on the "after" source:

```java
spec.recipe(new MyRecipe())
    .afterTypeValidationOptions(TypeValidation.none());
```

`.afterTypeValidationOptions` turns off validation only on the post-recipe tree. `.typeValidationOptions(TypeValidation.none())` disables both before and after — use that when even the input has unresolved types. Documented in OpenRewrite's FAQ.

## Multi-source tests

`rewriteRun` accepts any combination of source types in a single call. Use this for recipes that touch more than one file type:

```java
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.gradle.Assertions.buildGradle;       // Groovy DSL
import static org.openrewrite.gradle.Assertions.buildGradleKts;    // Kotlin DSL
import static org.openrewrite.gradle.Assertions.settingsGradle;
import static org.openrewrite.gradle.Assertions.settingsGradleKts;
import static org.openrewrite.toml.Assertions.toml;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.xml.Assertions.xml;
```

```java
rewriteRun(
    toml(CATALOG_STUB, spec -> spec.path("gradle/libs.versions.toml")),
    buildGradleKts(beforeBuild, afterBuild),
    java(beforeJava, afterJava)
);
```

**Path matters.** Each source should set its own path via `spec.path(...)` so recipes that filter by path (e.g. the scan phase of `UseCatalogReferenceForDependency` looks for `libs.versions.toml`) behave the same way they do against a real project.

## Simulating multi-module topology

OpenRewrite's test harness treats each source file independently unless you attach `GradleProject` markers yourself. For a two-module setup:

```java
GradleProject appMarker = GradleProject.builder()
    .id(Tree.randomId())
    .group("com.example").name("app").version("0.1.0").path(":app")
    .plugins(List.of()).mavenRepositories(List.of())
    .mavenPluginRepositories(List.of()).nameToConfiguration(Map.of())
    .build();

rewriteRun(
    buildGradleKts(
        beforeApp,
        afterApp,
        spec -> spec.path("app/build.gradle.kts").markers(appMarker)
    ),
    buildGradleKts(
        beforeLib,
        afterLib,
        spec -> spec.path("lib/build.gradle.kts").markers(libMarker)
    )
);
```

The markers are approximations — they encode just enough for recipes that read `GradleProject` to behave correctly. Anything that actually needs Gradle dependency resolution (like the full `org.openrewrite.gradle.AddDependency` pipeline) still needs a real `/tmp` smoke test.

## The matrix-test pattern

When a recipe's behavior depends on multiple axes (build-script DSL × project topology × dependency style), sweep the matrix in a dedicated package:

```
src/test/java/<pkg>/matrix/
├── MatrixTestSupport.java      // shared CATALOG_STUB, GradleProject builder, etc.
├── KotlinDslMatrixTest.java    // one @Test per meaningful cell
└── GroovyDslMatrixTest.java
```

Each cell gets a `@DisplayName` describing the axes it covers. Don't enumerate every combination of every axis — 14 well-chosen cells beat 64 mechanical ones. Pick the combinations where the axes interact meaningfully; skip the orthogonal ones that add no signal.

## When `RewriteTest` is an approximation

These situations require a real `/tmp` Gradle smoke test backing them up:

- Recipes that call `org.openrewrite.gradle.AddDependency` end-to-end. Without `withToolingApi()` (which pulls non-Maven-Central deps), the full resolution model isn't available — tests using the recipe individually may pass while the composed YAML silently fails.
- Composite builds (`includeBuild(...)`). OpenRewrite's harness doesn't stitch included builds together; each is processed independently.
- Anything whose proof-of-correctness is "the resulting project compiles and its tests pass". That's a Gradle concern, not a RewriteTest concern.

Treat the RewriteTest matrix as the inner-loop signal; treat the /tmp smoke tests as the authoritative release gate. See the companion `smoke-test` skill for designing the latter.

## Suppression patterns in tests

Compiler / analyzer noise that's unavoidable in test code, annotated narrowly:

```java
@Test
@SuppressWarnings("unchecked")
void visitor_getsTypedAccess() {
    JavaIsoVisitor<ExecutionContext> visitor =
        (JavaIsoVisitor<ExecutionContext>) recipe.getVisitor();
    // ...
}

@Test
@SuppressWarnings({"unchecked", "DataFlowIssue"})
void tolerates_nullArgsInConstructor() {
    parts.add(new J.Literal(null, null, null, "text", "\"text\"", null, null));
}
```

Always suppress the narrowest possible category, and only on the specific method. No class-level suppressions and no `@SuppressWarnings("all")` — if a test needs that, it's wrong.

## Fast feedback loop

Run a single test class during development:

```bash
./gradlew test --tests 'com.yourorg.recipes.MyRecipeTest'
```

Or a single method:

```bash
./gradlew test --tests 'com.yourorg.recipes.MyRecipeTest.convertsSimpleCase'
```

The full `./gradlew check` adds JaCoCo + SpotBugs on top and is the pre-commit signal, not the inner loop.
