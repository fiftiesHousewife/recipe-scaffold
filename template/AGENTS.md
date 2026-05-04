# Agent guidance for {{artifact}}

Vendor-neutral project guidance for AI coding agents (Claude Code, Cursor, Aider, Cline, Copilot CLI, etc.). Claude Code reads `CLAUDE.md`, which forwards here for the canonical content.

> **Template provenance.** This project was scaffolded from
> `recipescaffold`. When the template upgrades and you want
> to pull in changes (build-script fixes, smoke-runner improvements, new
> skill content), see the README in that repo — there is no automatic sync.

## Project structure

```
src/
├── main/
│   ├── java/{{rootPackage}}/recipes/     # leaf recipes (one per transformation)
│   └── resources/META-INF/rewrite/       # composed top-level recipes (YAML)
├── test/
│   └── java/{{rootPackage}}/recipes/     # *Test.java RewriteTest integration tests
├── integrationTest/
│   └── java/{{rootPackage}}/recipes/     # withToolingApi() end-to-end tests
└── smokeTest/
    └── java/{{rootPackage}}/smoketest/   # pre-publish gate: scaffolds /tmp Gradle projects
```

Key files at repo root:

- `build.gradle.kts` + `gradle/libs.versions.toml` — build + version pinning
- `SMOKE_TEST.md` — the pre-release release gate
- `BACKLOG.md` — Shipped / Queued / Active / Parked
- `README.md` — user-facing

## Publication workflow

Before tagging and pushing a new version, run these in order. Skipping a step is how regressions ship.

1. **Quality gates**: `./gradlew check` — must be green (tests, JaCoCo report, integrationTest).
2. **Smoke tests**: `./gradlew smokeTest` — runs the full project-shape matrix end-to-end. Backed up by `SMOKE_TEST.md` for the manual cells the runner can't reach.
3. **Update `README.md`** if any recipe surface changed — new recipe, new option, new supported project shape.
4. **Update `BACKLOG.md`** — move whatever's shipping out of Active or Queued-for-next-release into Shipped with a new version heading.
5. **Bump `version` in `build.gradle.kts`** — `x.y` → `x.(y+1)` for additive changes, `(x+1).0` for source-incompatible.
6. **Commit + push** — one release commit is fine if the per-feature history is good. `git add` specific files, don't `-A`.
7. **Publish**: `./gradlew publishAndReleaseToMavenCentral`. The `smokeTest` task is a hard `dependsOn` so the gate is structural, not operator discipline.
8. **Tag**: `git tag v<version> && git push origin v<version>`.

## Coding standards (not covered by tools)

- **No comments in tests.** The method name is the documentation.
- **Helpers are package-private, not private.** Same-package tests call them directly.
- **Break complex visitor methods into named helpers.** A 30-line `visitMethodInvocation` is a refactor waiting to happen.
- **Explicit `@SuppressWarnings`.** Every warning is fixed or suppressed with a specific category — no blanket suppressions.
- **No emojis in source, docs, or commits** unless the user explicitly asks.
- **Prefer editing existing files over creating new ones.**
- **No abstractions ahead of need.** Three similar lines beats a premature helper.
- **No error handling for scenarios that can't happen.** Trust framework guarantees.
- **README stays concise and user-focused.** Internal "how it works" detail belongs in code comments at most.

## Build quirks worth knowing

- **`integrationTest` runs on JDK 21** because the embedded Gradle 8.14.3 (driven by `withToolingApi()`) cannot host a JDK newer than 21. The test source set excludes `sourceSets.test.output` and `rewrite-java-25` for the same reason — class file v69 poisons the embedded parser.
- **`smokeTest` forks a child Gradle 8.14.3** via `ProcessBuilder` for each variant. The runner pins the child's build JVM to JDK 21 via `JAVA_HOME` plus `GRADLE_OPTS=-Dorg.gradle.java.home=...` — `org.gradle.java.home` in the child's `gradle.properties` is parsed too late to keep Kotlin DSL evaluation off JDK 25.
- **`publishAndReleaseToMavenCentral` `dependsOn("smokeTest")`** by design. There is no path to Central that bypasses the smoke matrix.
