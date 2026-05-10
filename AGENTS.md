# Agent guidance — recipescaffold

Vendor-neutral project guidance for any AI coding agent (or human contributor) working on this repo. Tool-specific notes live in `CLAUDE.md` (Claude Code) or in equivalent files for other agents; the substantive content is here.

## What this repo is

A reusable scaffold for new OpenRewrite recipe projects: build conventions, three-tier test harnesses (`test` / `integrationTest` / `smokeTest`), and a pre-publish smoke gate that hard-blocks Maven Central on a real downstream `./gradlew check`. The scaffolded project is the source-of-truth for what a fresh recipe project should look like; this repo is the source-of-truth for the scaffolder itself.

## Layout

```
.
├── README.md                     # user-facing
├── AGENTS.md                     # this file — canonical project guidance
├── CLAUDE.md                     # tool-specific (Claude Code) stub forwarding here
├── BACKLOG.md                    # what's shipped + queued
├── JBANG_TEMPLATE_PLAN.md        # the source plan (Part A: review findings; Part B: scaffolder design)
├── jbang-catalog.json            # JBang catalog entry
├── build.gradle.kts              # repo-root Gradle build (TestKit harness, fat jar, application plugin)
├── settings.gradle.kts
├── gradle/                       # libs.versions.toml + wrapper for the harness build
├── gradlew, gradlew.bat          # wrapper scripts (kept in sync with template/)
├── jbang/RecipeScaffold.java     # picocli — the four subcommands
├── src/test/java/recipescaffold/ # ScaffoldHarnessTest + RecipeScaffoldUnitTest
├── tests/ci-smoke.sh             # bash scaffold-and-build verifier (kept as v0 fallback)
├── template/                     # WHAT GETS SCAFFOLDED into the user's new project
│   ├── AGENTS.md                 # vendor-neutral agent guidance (canonical for scaffolded users)
│   ├── CLAUDE.md                 # Claude Code stub; forwards to template/AGENTS.md
│   ├── LICENSE, .editorconfig
│   ├── .github/workflows/        # gradle.yml, release.yml, wrapper-validation.yml
│   ├── snippets/                 # source-of-truth recipe-skeleton fragments (read by add-recipe)
│   ├── src/                      # main + test + integrationTest + smokeTest
│   └── .claude/skills/           # four recipe-authoring skills shipped with the scaffold
└── .github/workflows/ci.yml      # bash-scaffold + jbang-scaffold + harness + actionlint
```

After `init`, the scaffolded project root holds a `.recipescaffold.yml` dropfile (`recipescaffoldVersion`, `group`, `artifact`, `rootPackage`, `javaTargetMain`, `javaTargetTests`; optional `recipePackage` for projects that want recipes outside the default `<rootPackage>.recipes`). `add-recipe`, `verify-gates`, and `upgrade-skills` all walk upward from cwd looking for it.

The `template/.claude/skills/` (ships to scaffolded users) vs the repo-level `.claude/skills/` (for working IN this repo) distinction matters; they are separate directories with different lifecycles.

## Placeholder dialects

Two distinct dialects share the `{{name}}` syntax.

**Init-time placeholders** — substituted by `tests/ci-smoke.sh` and `jbang init`:

| Placeholder | Meaning |
| --- | --- |
| `{{group}}`, `{{artifact}}`, `{{rootPackage}}` | Maven group + artifact + Java root package |
| `{{initialVersion}}` | First version of the scaffolded project |
| `{{recipeName}}`, `{{recipeDescription}}` | POM name + description |
| `{{githubOrg}}`, `{{githubRepo}}` | For SCM URLs |
| `{{authorId}}`, `{{authorName}}`, `{{authorEmail}}` | POM developer block |
| `{{javaTargetMain}}`, `{{javaTargetTests}}` | `release` for compileJava and compileTestJava |
| `{{rewritePluginVersion}}` | Snippet versions in template's docs |
| `{{recipescaffoldVersion}}` | The CLI version that scaffolded the project. Burned into `template/CLAUDE.md` at init time so a Claude Code session can drift-check against the dropfile. |
| `__ROOT_PACKAGE__` | Literal directory marker — renamed at scaffold time |

**Snippet-time placeholders** — substituted by `add-recipe`, only inside `template/snippets/*.template`:

| Placeholder | Meaning |
| --- | --- |
| `{{package}}` | Java package the recipe (or its test) lives in. Default: `recipePackage` from the dropfile if set, else `<rootPackage>.recipes`. `--package=` overrides both. |
| `{{recipeName}}` | Recipe class name (PascalCase) |
| `{{recipeDisplayName}}` | Returned by `getDisplayName()` |
| `{{recipeDescription}}` | Returned by `getDescription()` |
| `{{recipeId}}` | OpenRewrite recipe identifier — for yaml: `<rootPackage>.<recipeName>` (root namespace per example.yml convention); for java/scanning/refaster: `<package>.<recipeName>`. |
| `{{recipeKebab}}` | kebab-case form of `{{recipeName}}`, used for the YAML manifest filename |

Init-time substitution and the residual check both **skip files under `<root>/snippets/`** so the snippet-time markers survive scaffolding into the user's project intact.

The residual check regex is `(?<!\$)\{\{[a-zA-Z][a-zA-Z0-9]*\}\}` — anchored so GitHub Actions `${{ secrets.X }}` expressions in `release.yml` don't trip the gate.

`tests/ci-smoke.sh` and `jbang/RecipeScaffold.java` must stay in sync. When adding a new init-time placeholder: extend both substitution lists and the table above. When adding a new snippet, drop it in `template/snippets/` and `add-recipe` picks it up by file name (no script edit needed unless adding a new `--type`).

## How the scaffolder runs

The supported primary flow is JBang:

```bash
jbang jbang/RecipeScaffold.java init --help
jbang jbang/RecipeScaffold.java add-recipe --help
jbang jbang/RecipeScaffold.java verify-gates --help
jbang jbang/RecipeScaffold.java upgrade-skills --help
```

JBang handles compilation, dep resolution (`//DEPS info.picocli:picocli:4.7.7`), and caching. CI uses the same flow via `jbangdev/setup-jbang@main`.

Four other distribution paths exist for environments where JBang isn't available (corporate-managed images, air-gapped CI, etc.): `./gradlew run --args="..."`, `./gradlew installDist` (produces `build/install/recipescaffold/bin/recipescaffold`), `./gradlew jar` (fat jar), and the bash `tests/ci-smoke.sh` v0 fallback. See README for the full table.

Typical sequence for a fresh project:

```bash
jbang jbang/RecipeScaffold.java init --group=… --artifact=… --package=… [...] -d ./acme-rewrite-recipes --verify
cd acme-rewrite-recipes
jbang <path-to-recipescaffold>/jbang/RecipeScaffold.java add-recipe --name RemoveStaleSuppression
./gradlew check
```

## TestKit harness

`./gradlew test` runs the in-repo TestKit harness from `src/test/java/recipescaffold/`. Two test classes:

- **`RecipeScaffoldUnitTest`** — pure-function tests for the helpers (`kebabCase`, `humanise`, `isPascalCase`, `applySubstitutions`, `Init.buildReplacements`).
- **`ScaffoldHarnessTest`** — drives `Init` + `AddRecipe` (one cell per `--type` × `--test-style`) into a `@TempDir`, then runs `GradleRunner` against the resulting scaffolded project. Pattern: Initializr's `ProjectGeneratorTester` shape + Maven Archetype's `archetype:integration-test`, ported to Gradle TestKit.

The harness is the in-repo equivalent of Maven's `archetype:integration-test` and runs as a parallel CI job alongside `bash-scaffold` and `jbang-scaffold`. Some sandboxed environments restrict network access for deeply-nested Gradle daemons; in those cases CI is the authoritative gate.

### Reproducing CI failures locally

The harness forwards the inner Gradle's stdout/stderr (`forwardOutput()`) and the root build has `testLogging.showStandardStreams = true`, so a failed `./gradlew test` prints the inner build's full log inline. Two extra knobs:

- **`HARNESS_OFFLINE=1`** — appends `--offline` to the inner `GradleRunner` so a warm cache surfaces real build errors instead of `UnknownHostException` when the outer environment blocks DNS for forked daemons. Run once online to populate the cache, then re-run with `HARNESS_OFFLINE=1 ./gradlew test --rerun-tasks` for fast offline iteration.
- **`-Dkotlin.compiler.execution.strategy=in-process`** — already passed to the inner runner so the Kotlin compile daemon does not try to write under `~/Library/Application Support/kotlin/` (which some sandboxes block). No action needed.

Pulling CI logs without `gh`:

```bash
curl -fsSL -H "Authorization: Bearer $GITHUB_TOKEN" \
  -L "https://api.github.com/repos/<owner>/<repo>/actions/jobs/<job-id>/logs" \
  -o /tmp/job.log
```

The job id is in `https://api.github.com/repos/<owner>/<repo>/actions/runs/<run-id>/jobs`.

## Conventions

- **No emojis in source, docs, or commits** unless the user explicitly asks.
- **Prefer editing `template/` files over creating new ones.** New files in `template/` propagate to every future scaffold — only add when the responsibility doesn't fit existing files.
- **Verify after every template change** by running both `tests/ci-smoke.sh` and the JBang script with `--verify`, plus `./gradlew test`. Don't trust placeholder substitution by inspection.
- **Honest scope notes belong in `README.md`** — the smoke runner ships SLF4J/Lombok-specific dep blocks marked `EDIT FOR YOUR RECIPE'S DEPS`. Don't pretend it's fully generic.
- **`AGENTS.md` is canonical, `CLAUDE.md` is the stub** in the scaffolded project — vendor-neutral content goes in AGENTS.md.
- **Wrapper assets at the repo root mirror those under `template/`.** `./gradlew syncWrappersFromTemplate` copies; `./gradlew check` includes a parity check that fails if they drift.

## Skills

Four committed recipe-authoring skills ship with the scaffolded project under `template/.claude/skills/`. They are version-controlled as part of the project, not personal tool config: `new-gradle-project`, `new-recipe`, `recipe-testing`, `smoke-test`. The repo-level `.claude/skills/` contains tool-specific copies for working IN this scaffolder repo and is not part of the scaffold output.

The skill bodies are agent-neutral OpenRewrite/Gradle documentation; agents that don't recognize the skill-file convention can read the markdown bodies directly as reference docs.
