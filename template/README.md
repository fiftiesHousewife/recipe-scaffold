# {{recipeName}}

{{recipeDescription}}

## Install

Add the recipe artifact to your `rewrite` configuration:

```kotlin
plugins {
    id("org.openrewrite.rewrite") version "{{rewritePluginVersion}}"
}

dependencies {
    rewrite("{{group}}:{{artifact}}:{{initialVersion}}")
}

rewrite {
    activeRecipe("{{rootPackage}}.ExampleRecipe")
}
```

Then run `./gradlew rewriteRun` against your project.

## Recipes

| Recipe ID | Description |
| --- | --- |
| `{{rootPackage}}.ExampleRecipe` | Placeholder no-op recipe. Replace once you've shipped your first transformation. |

## What this scaffold parses

The runtime classpath ships parsers for every language you are likely to rewrite alongside Java code:

| Language / file type | Parser artifact | Typical use |
| --- | --- | --- |
| Java (8, 11, 17, 21, 25) | [`rewrite-java`](https://docs.openrewrite.org/concepts-and-explanations/lst-examples/java-lst-examples) + per-version runtimes | Application code |
| Groovy | [`rewrite-groovy`](https://github.com/openrewrite/rewrite/tree/main/rewrite-groovy) | Standalone `.groovy` files |
| Gradle build scripts | [`rewrite-gradle`](https://docs.openrewrite.org/recipes/gradle) | Both Kotlin DSL (`.gradle.kts`) and Groovy DSL (`.gradle`); Groovy DSL is parsed via `rewrite-groovy` |
| TOML | [`rewrite-toml`](https://docs.openrewrite.org/recipes/toml) | Version catalogs (`gradle/libs.versions.toml`), `Cargo.toml`, etc. |
| Properties | [`rewrite-properties`](https://docs.openrewrite.org/recipes/properties) | `gradle.properties`, Spring config |
| YAML | [`rewrite-yaml`](https://docs.openrewrite.org/recipes/yaml) (transitive via the recipe BOM) | CI workflows, k8s manifests, Spring config |
| Refaster templates | [`rewrite-templating`](https://github.com/openrewrite/rewrite-templating) | `@BeforeTemplate`/`@AfterTemplate` recipe authoring |

Every parser is pinned in [`gradle/libs.versions.toml`](./gradle/libs.versions.toml) and resolved through the [`rewrite-recipe-bom`](https://github.com/openrewrite/rewrite-recipe-bom) so versions stay aligned.

## Supported project shapes

The `smokeTest` matrix scaffolds throwaway `/tmp` projects per cell and runs the recipe against each before publish. Extend the matrix when you add a new shape.

| Shape | Status |
| --- | --- |
| Single-module Kotlin DSL (`build.gradle.kts`) | covered by `smokeTest` |
| Multi-module Kotlin DSL | covered by `smokeTest` (project-shape matrix cell A) |
| Single-module Groovy DSL (`build.gradle`) | parser included; add a smoke cell when you ship a Groovy-aware recipe |

## Development

- `./gradlew check` — unit + integration tests, JaCoCo report.
- `./gradlew smokeTest` — pre-publish gate: scaffolds /tmp Gradle projects per matrix cell, runs the recipe, compiles the result.
- `./gradlew dependencyUpdates` — show outdated deps (Ben-Manes plugin).
- Versions are pinned in [`gradle/libs.versions.toml`](./gradle/libs.versions.toml). All parser/runtime/test deps go through the OpenRewrite recipe BOM, so bumping `openrewrite-bom` lifts the whole family at once.
- `SMOKE_TEST.md` — manual cells the automated runner can't reach.
- `BACKLOG.md` — Shipped / Queued / Active / Parked.
- `AGENTS.md` — project guidance for any AI coding agent (Claude, Cursor, Aider, Cline, Copilot CLI).
- `CLAUDE.md` — Claude-Code-specific notes; forwards to `AGENTS.md` for the canonical content.

## References

| Topic | Resource |
| --- | --- |
| **OpenRewrite** | [Docs home](https://docs.openrewrite.org), [Authoring recipes](https://docs.openrewrite.org/authoring-recipes), [Recipe testing](https://docs.openrewrite.org/authoring-recipes/recipe-testing), [`RewriteTest`](https://docs.openrewrite.org/authoring-recipes/recipe-testing#rewritetest), [Visitors](https://docs.openrewrite.org/concepts-and-explanations/visitors), [Scanning recipes](https://docs.openrewrite.org/concepts-and-explanations/recipes#scanning-recipes), [YAML composition](https://docs.openrewrite.org/reference/yaml-format-reference), [Refaster templates](https://github.com/openrewrite/rewrite-templating) |
| **Reference recipe libraries** | [`moderneinc/rewrite-recipe-starter`](https://github.com/moderneinc/rewrite-recipe-starter), [`rewrite-spring`](https://github.com/openrewrite/rewrite-spring), [`rewrite-static-analysis`](https://github.com/openrewrite/rewrite-static-analysis) |
| **Gradle** | [Convention plugins](https://docs.gradle.org/current/userguide/sharing_build_logic_between_subprojects.html), [Composite builds](https://docs.gradle.org/current/userguide/composite_builds.html), [Version catalogs](https://docs.gradle.org/current/userguide/platforms.html), [TestKit](https://docs.gradle.org/current/userguide/test_kit.html), [Tooling API](https://docs.gradle.org/current/userguide/third_party_integration.html#embedding) |
| **Build & publish** | [vanniktech/gradle-maven-publish-plugin](https://vanniktech.github.io/gradle-maven-publish-plugin/), [Maven Central Portal](https://central.sonatype.com), [Ben-Manes versions plugin](https://github.com/ben-manes/gradle-versions-plugin) |
| **Upstream scaffolder** | [`recipe-scaffold`](https://github.com/fiftiesHousewife/recipe-scaffold) — re-run `recipe-scaffold doctor` to see whether this project's vendored pieces are stale |

## License

Apache License, Version 2.0 — see [`LICENSE`](./LICENSE).
