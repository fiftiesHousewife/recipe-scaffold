# Claude Code session notes

Vendor-neutral project guidance — structure, publication workflow, coding standards, build quirks — lives in [`AGENTS.md`](./AGENTS.md). Read that first; the rest of this file is Claude-Code-specific.

## Skills

Four committed project skills live at `.claude/skills/`. Their bodies are agent-neutral OpenRewrite/Gradle reference documentation — readable directly as docs by any agent or human. The Claude Code harness picks one based on the user's intent and the description in the YAML frontmatter.

| Skill | Covers |
| --- | --- |
| `new-gradle-project` | Bootstrapping a fresh Gradle build: TOML version catalog, condensed JUnit, Ben-Manes versions plugin, `gradle.properties` JVM args, build-file skeleton. |
| `new-recipe` | Authoring a new OpenRewrite recipe: visitor structure, `MethodMatcher`, YAML composition, manifest location, marker-preserving tree edits, `@Option` patterns. |
| `recipe-testing` | Writing tests for a recipe: integration vs. unit split, `RewriteTest` + `TypeValidation.none()`, multi-source `rewriteRun`, `GradleProject` marker injection, matrix-test layout. |
| `smoke-test` | Designing or extending the pre-release smoke-test procedure: `/tmp` project bootstrap, dryRun → Run → compile cycle, project-shape matrix, expected-outcomes tables, mavenLocal resolution check. |

The skill files in `.claude/skills/` are version-controlled — treat them as part of the project, not personal tool config. When the upstream scaffolder improves a skill, run `recipe-scaffold upgrade-skills` (or pull the change in by hand).

## Drift check at session start

This project was scaffolded by `recipe-scaffold` `v{{recipeScaffoldVersion}}`. On your first action in a session, read the `recipeScaffoldVersion` field of `.recipe-scaffold.yml` at the project root and compare it against the version above. If they differ, surface a one-line note recommending `recipe-scaffold upgrade-skills` and `recipe-scaffold upgrade-build-logic` (or `recipe-scaffold doctor` for an automated drift report). No network call required — the comparison is purely local. Do not block work on the drift; just flag it once and move on.

## Notes on collaboration

- Don't duplicate skill content into this file or into commit messages — refer to the skill and let the user see the same content.
- Project-wide rules (no emojis, package-private helpers, etc.) live in `AGENTS.md` § "Coding standards" — apply them silently, no need to acknowledge in chat.
- The template's `LICENSE` is Apache 2.0; new source files do not need per-file headers.
