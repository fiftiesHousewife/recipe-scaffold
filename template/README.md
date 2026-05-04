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

## Supported project shapes

| Shape | Status |
| --- | --- |
| Single-module Kotlin DSL | covered by `smokeTest` |
| Multi-module Kotlin DSL | covered by `smokeTest` (project-shape matrix cell A) |

Add rows as you extend the smoke matrix.

## Development

- `./gradlew check` — unit + integration tests, JaCoCo report.
- `./gradlew smokeTest` — pre-publish gate: scaffolds /tmp Gradle projects per matrix cell, runs the recipe, compiles the result.
- `./gradlew dependencyUpdates` — show outdated deps (Ben-Manes plugin).
- `SMOKE_TEST.md` — manual cells the automated runner can't reach.
- `BACKLOG.md` — Shipped / Queued / Active / Parked.
- `AGENTS.md` — project guidance for any AI coding agent (Claude, Cursor, Aider, Cline, Copilot CLI).
- `CLAUDE.md` — Claude-Code-specific notes; forwards to `AGENTS.md` for the canonical content.

## License

Apache License, Version 2.0 — see [`LICENSE`](./LICENSE).
