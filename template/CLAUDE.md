# Claude Code session notes

Project guidance for any AI coding agent — structure, publication workflow, coding standards, build quirks — lives in [`AGENTS.md`](./AGENTS.md). Read that first; the rest of this file is Claude-Code-specific.

## Skills

Four committed project skills live at `.claude/skills/`. Invoke them instead of re-deriving their content; descriptions are tuned so the model picks the right one when the user's intent matches.

| Skill | Invoke when the user asks to … |
| --- | --- |
| `new-gradle-project` | Bootstrap a fresh Gradle build: TOML version catalog, condensed JUnit, Ben-Manes versions plugin, `gradle.properties` JVM args, build-file skeleton. |
| `new-recipe` | Author a new OpenRewrite recipe: visitor structure, `MethodMatcher`, YAML composition, manifest location, marker-preserving tree edits, `@Option` patterns. |
| `recipe-testing` | Write tests for a recipe: integration vs. unit split, `RewriteTest` + `TypeValidation.none()`, multi-source `rewriteRun`, `GradleProject` marker injection, matrix-test layout. |
| `smoke-test` | Design or extend the pre-release smoke-test procedure: `/tmp` project bootstrap, dryRun → Run → compile cycle, project-shape matrix, expected-outcomes tables, mavenLocal resolution check. |

The skill files in `.claude/skills/` are version-controlled — treat them as part of the project, not personal config. When the upstream template repo improves a skill, pull the change in by hand (no auto-sync).

## Notes on collaboration

- Do not duplicate skill content into this file or into commit messages — invoke the skill and let the user see the same content you used.
- Project-wide rules (no emojis, package-private helpers, etc.) live in `AGENTS.md` § "Coding standards" — apply them silently, no need to acknowledge in chat.
- The template's `LICENSE` is Apache 2.0; new source files do not need per-file headers.
