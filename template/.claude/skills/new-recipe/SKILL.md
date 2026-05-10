---
name: new-recipe
description: Authoring a new OpenRewrite recipe — either in an existing recipe project or bootstrapping a recipe project from scratch. Covers the idiomatic visitor structure, `MethodMatcher` usage, YAML composition, the correct location for the recipe manifest, and tree-editing patterns (argument-level replacement that preserves Groovy/Kotlin markers). For anything test-related, see the companion `recipe-testing` skill. For generic Gradle project setup, see the `new-gradle-project` skill. Applies for tasks like "add a new recipe", "start a recipe project", "scaffold a recipe", "write an OpenRewrite visitor", or references to the moderneinc/rewrite-recipe-starter.
---

# Authoring a new OpenRewrite recipe

For testing, see the `recipe-testing` skill. For Gradle project setup, see `new-gradle-project`. This skill is the recipe-code side only.

## Bootstrapping a new recipe project

Start from the official template: https://github.com/moderneinc/rewrite-recipe-starter

Apply the `new-gradle-project` conventions (version catalog, condensed JUnit, Ben-Manes, gradle.properties), plus the recipe-specific items below.

## Recipe manifest location

Composed recipes (YAML) live at:

```
src/main/resources/META-INF/rewrite/<your-recipe>.yml
```

OpenRewrite auto-discovers recipes here. Do NOT put YAML at the project root — it won't be picked up, and you'll spend an hour wondering why.

## Visitor structure

Keep the top-level visitor method short and delegate to small, individually testable helpers. Every `if (!matches) return unchanged` early exit should be one line.

```java
@Override
public TreeVisitor<?, ExecutionContext> getVisitor() {
    return new JavaIsoVisitor<ExecutionContext>() {
        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
            if (!matchesCriteria(mi)) return mi;
            return transform(mi);
        }

        boolean matchesCriteria(J.MethodInvocation method) { /* ... */ }
        J.MethodInvocation transform(J.MethodInvocation method) { /* ... */ }
    };
}
```

Helpers that are meaningful on their own should be **package-private** (no modifier) so unit tests in the same package can call them directly. Do not make them `private` — that blocks unit testing and pushes everything into integration tests.

## `MethodMatcher` for type safety

Match methods by their fully qualified signature, not by name-string-comparison:

```java
private static final MethodMatcher PRINT_STACK_TRACE =
    new MethodMatcher("java.lang.Throwable printStackTrace(..)");

if (PRINT_STACK_TRACE.matches(method)) {
    // ...
}
```

Define the matcher as a `private static final` constant at the class top. One matcher per concept. This catches overloads, supertype matches, and generics correctly — a name-only check won't.

## Tree-editing: prefer argument-level replacement

When your transform changes an argument of a call, don't regenerate the whole `J.MethodInvocation` via `JavaTemplate.apply(..., method.getCoordinates().replace(), ...)`. That erases whatever markers the original carried — including DSL-specific markers like Groovy's `OmitParentheses` that tell the printer to emit `compileOnly libs.lombok` instead of `compileOnly(libs.lombok)`.

Instead, build the replacement `Expression` explicitly and swap it into the existing arguments container:

```java
Expression replacement = buildReplacement(catalogReference, literal.getPrefix())
        .withMarkers(literal.getMarkers()); // preserve OmitParentheses etc.
JContainer<Expression> newArgs = JContainer.withElements(
        visited.getPadding().getArguments(), List.of(replacement));
return visited.getPadding().withArguments(newArgs);
```

Build simple tree fragments by hand — for `libs.lombok` that's a `J.FieldAccess(J.Identifier("libs"), J.Identifier("lombok"))`. `JavaTemplate` struggles with bare expression fragments outside statement context.

## Recipe composition (YAML)

Keep individual Java/Kotlin recipes narrow and single-purpose. Compose them in YAML:

```yaml
---
type: specs.openrewrite.org/v1beta/recipe
name: com.yourorg.MyRecipe
displayName: My Recipe
description: Does something useful

recipeList:
  - com.yourorg.recipes.Step1
  - com.yourorg.recipes.Step2
```

Nested-form when a composed step needs an option:

```yaml
recipeList:
  - com.yourorg.recipes.Step1:
      someOption: true
```

When two steps share external behavior (e.g. `org.openrewrite.gradle.AddDependency` calls that touch the same `groupId`), note ordering requirements in a YAML comment — upstream dedup can silently drop the second call.

## Recipe options

Add configurable parameters via `@Option`:

```java
@Option(displayName = "Require Lombok on classpath",
        description = "When true, only add @Slf4j where the type is resolvable.",
        required = false)
private final boolean requireLombokOnClasspath;
```

- `required = false` with a sensible default keeps the zero-arg constructor valid for YAML `recipeList` entries that don't specify the option.
- Always provide matching `equals`, `hashCode`, and a getter — OpenRewrite caches recipe instances by equality and serializes them via getters.

## Recommended file layout

```
src/
├── main/
│   ├── java/com/yourorg/recipes/
│   │   ├── MyRecipe.java
│   │   └── MyHelperRecipe.java
│   └── resources/META-INF/rewrite/
│       └── my-recipe.yml          # composed pipeline, if any
└── test/
    └── java/com/yourorg/recipes/
        ├── MyRecipeTest.java       # see recipe-testing skill
        └── MyRecipeMethodTest.java # see recipe-testing skill
```

## Where the build gates live

If this project was scaffolded from `recipe-scaffold`, the reusable shape of the build (toolchain, source sets, test/integrationTest/smokeTest tasks, jacoco, javadoc, sign-onlyIf, pre-publish smokeTest gate) lives in [`build-logic/src/main/kotlin/recipe-library.gradle.kts`](../../../build-logic/src/main/kotlin/recipe-library.gradle.kts). Edit gates there, not in the project's `build.gradle.kts` — that file should stay narrow on identity (group/version) and POM coordinates.

Three opt-in quality gates flip on via `gradle.properties`:

- `recipeLibrary.minLineCoverage=0.70` — JaCoCo line-coverage minimum.
- `recipeLibrary.spotbugsStrict=true` — fail `check` on any SpotBugs finding.
- `recipeLibrary.failOnStaleDependencies=true` — `verifyDependencies` blocks `check` on any non-prerelease upgrade available on Maven Central.

All default off so a fresh project still builds. Tighten as the recipe library matures.

## Things that save time

- Start from the official template — it wires the plugin, BOM, and self-test loop (`rewrite(project)`) correctly.
- Write a failing `RewriteTest` first; let it drive the visitor. (See `recipe-testing` skill.)
- When in doubt about marker preservation, dump `method.getArguments().get(0).getMarkers()` for the before-source and check what DSL markers it carries.
- `@Option(required = false)` for backward-compatible recipe options with a sensible default.
