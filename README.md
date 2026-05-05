# recipescaffold

Scaffold an OpenRewrite recipe project with the build conventions, test harnesses, and pre-publish smoke gate from `system-out-to-lombok-log4j` — plus a CLI that adds new recipes, runs the gate chain, and refreshes the bundled agent skills.

> **Status: beta (2026-05-05).** All four recipe `--type` values (`java`, `scanning`, `yaml`, `refaster`), both `--test-style` variants (`block`, `method`), and four subcommands (`init`, `add-recipe`, `verify-gates`, `upgrade-skills`) shipped. CI exercises the full scaffold-and-build chain in three parallel jobs plus actionlint. See [`BACKLOG.md`](./BACKLOG.md) for queued items.

Repo: <https://github.com/fiftiesHousewife/recipescaffold>

## Quickstart

Install [JBang](https://www.jbang.dev) once:

```bash
brew install jbang        # macOS
# or
curl -Ls https://sh.jbang.dev | bash -s - app setup
```

Scaffold a fresh recipe project:

```bash
jbang jbang/RecipeScaffold.java init \
  --group=io.github.acme \
  --artifact=acme-rewrite-recipes \
  --package=io.github.acme \
  --recipe-name="Acme Recipes" \
  --recipe-description="OpenRewrite recipes for the Acme codebase" \
  --github-org=acme \
  --github-repo=acme-rewrite-recipes \
  --author-id=acmebot \
  --author-name="Acme Bot" \
  --author-email=bot@acme.example \
  --directory=./acme-rewrite-recipes \
  --verify
```

`--verify` runs `./gradlew check smokeTest` against the freshly scaffolded project as a sanity check. Drop it for a faster scaffold-only run. `jbang jbang/RecipeScaffold.java init --help` lists every option.

The result is a normal Gradle project rooted at `--directory`, with a `.recipescaffold.yml` dropfile at the root that captures the project's identity for subsequent tooling. From there: `cd acme-rewrite-recipes && ./gradlew check`.

## Subcommands

The CLI exposes four subcommands; `--help` on any of them lists every option.

### `init` — scaffold a new project

The Quickstart above. Required: `--group`, `--artifact`, `--package`, `--recipe-name`, `--recipe-description`, `--github-org`, `--github-repo`, `--author-id`, `--author-name`, `--author-email`. Optional: `--initial-version` (default `0.1`), `--java-target-main` (default `17`), `--java-target-tests` (default `25`), `--rewrite-plugin-version` (default `7.30.0`), `--directory` (default `./<artifact>`), `--template-dir` (default: walks upward), `--force`, `--verify`. Writes `.recipescaffold.yml` at the output root for subsequent commands.

### `add-recipe` — drop a new recipe in

```bash
cd acme-rewrite-recipes
jbang <recipescaffold-checkout>/jbang/RecipeScaffold.java add-recipe \
  --name RemoveStaleSuppression \
  --display-name "Remove @SuppressWarnings noise" \
  --description "Remove suppressions that no longer match a real warning."
./gradlew check
```

Walks upward looking for `.recipescaffold.yml`, so it works from any subdirectory.

**`--type` (default `java`):**

| Type | Output | Default test scaffold |
| --- | --- | --- |
| `java` | `src/main/java/<pkg>/<Name>.java` — `JavaIsoVisitor` no-op skeleton | `recipe-test.template` (single-arg `java(...)`, asserts no-op) |
| `scanning` | `src/main/java/<pkg>/<Name>.java` — `ScanningRecipe<Acc>` two-pass skeleton | same as `java` |
| `yaml` | `src/main/resources/META-INF/rewrite/<kebab>.yml` — composed-recipe manifest with placeholder `recipeList: []` | `recipe-test-yaml.template` (uses `Environment.builder().scanRuntimeClasspath().build().activateRecipes(...)`) |
| `refaster` | `src/main/java/<pkg>/<Name>.java` — outer holder with one nested `@RecipeDescriptor` template-pair | `recipe-test-refaster.template` (instantiates the *generated* `<Name>Recipes` aggregate) |

**`--test-style` (default `block`):** `block` is the multi-line text-block test scaffold; `method` swaps in a one-line `java(...)` over `Math.max(1, 2)` with a commented-out before/after hint — tighter for argument-level transforms. `method` is currently restricted to `--type java|scanning`.

**Other flags:** `--display-name`, `--description`, `--package` (default `<rootPackage>.recipes`), `-d/--directory`, `--no-tests`, `--force`.

The shipped recipe uses a no-op skeleton — fill in `matchesCriteria` / `transform` per the `.claude/skills/new-recipe/SKILL.md` guidance.

### `verify-gates` — run the full gate chain

```bash
cd acme-rewrite-recipes
jbang <recipescaffold-checkout>/jbang/RecipeScaffold.java verify-gates
```

Runs `./gradlew check integrationTest smokeTest`. The three tasks are listed explicitly so all run even when `check` is up-to-date. Refuses to run in non-recipescaffold projects (no dropfile = no `smokeTest` task to invoke). Accepts `--directory`.

### `upgrade-skills` — refresh the bundled agent skills

```bash
cd acme-rewrite-recipes
jbang <recipescaffold-checkout>/jbang/RecipeScaffold.java upgrade-skills [--dry-run]
```

Replaces each subdir of the project's `.claude/skills/` with the corresponding upstream copy from `template/.claude/skills/`. Iterates only over upstream subdirs, so any user-added skill is left alone. `--dry-run` previews. Accepts `--directory`, `--template-dir`.

## What you get

- Gradle build with `vanniktech/gradle-maven-publish-plugin` wired to Maven Central, the openrewrite plugin for self-tests, the Ben-Manes versions plugin, and JaCoCo.
- Refaster recipe support pre-wired: `org.openrewrite:rewrite-templating` annotation processor + `com.google.errorprone:error_prone_core` (with the canonical `auto-service-annotations` and `dataflow-errorprone` excludes); `compileJava` adds `-Arewrite.javaParserClasspathFrom=resources`.
- Three source sets: `test` (unit + `RewriteTest` integration), `integrationTest` (`withToolingApi()` end-to-end), `smokeTest` (scaffolds `/tmp` Gradle projects per matrix cell).
- `publishAndReleaseToMavenCentral` structurally `dependsOn("smokeTest")` — there's no path to Central that skips the gate.
- Apache 2.0 `LICENSE`, `.editorconfig`, `dependabot.yml`, three GitHub Actions workflows (`gradle.yml` for CI, `wrapper-validation.yml` for wrapper-jar checksum, `release.yml` for tag-triggered Maven Central publish).
- `AGENTS.md` (vendor-neutral agent guidance) + `CLAUDE.md` stub forwarding to it. Four `.claude/skills/` shipped: `new-gradle-project`, `new-recipe`, `recipe-testing`, `smoke-test`.
- One `ExampleRecipe` no-op so a freshly scaffolded project's `./gradlew check smokeTest` is green from the first commit.
- `snippets/` — copies of the `add-recipe` source-of-truth fragments. Carried into the scaffolded project so `add-recipe` resolves them locally; the init-time substitutor explicitly skips this directory so the snippet-time `{{...}}` markers survive.

## Placeholders

Two distinct dialects:

**Init-time** — substituted by `init` (and `tests/ci-smoke.sh`):

| Placeholder | Example | Meaning |
| --- | --- | --- |
| `{{group}}` | `io.github.acme` | Maven group |
| `{{artifact}}` | `acme-rewrite-recipes` | Maven artifact id |
| `{{rootPackage}}` | `io.github.acme` | Java root package; recipes live at `<rootPackage>.recipes` |
| `{{initialVersion}}` | `0.1` | First version |
| `{{recipeName}}` | `Acme Recipes` | POM name |
| `{{recipeDescription}}` | `Cleanup recipes for the Acme codebase` | POM description |
| `{{githubOrg}}`, `{{githubRepo}}` | `acme`, `acme-rewrite-recipes` | For SCM URLs |
| `{{authorId}}`, `{{authorName}}`, `{{authorEmail}}` | | For POM developer block |
| `{{javaTargetMain}}` | `17` | `release` for `compileJava` |
| `{{javaTargetTests}}` | `25` | `release` for `compileTestJava` and toolchain |
| `{{rewritePluginVersion}}` | `7.30.0` | for `id("org.openrewrite.rewrite") version "..."` snippets in docs |

`__ROOT_PACKAGE__` is a literal directory marker — the scaffolder renames it to the slashed form of `{{rootPackage}}` at scaffold time.

**Snippet-time** — substituted by `add-recipe`, only inside `snippets/*.template`:

| Placeholder | Meaning |
| --- | --- |
| `{{package}}` | Java package the recipe (or its test) lives in. Default: `<rootPackage>.recipes`. |
| `{{recipeName}}` | Recipe class name (PascalCase). |
| `{{recipeDisplayName}}` | Returned by `getDisplayName()`. Default: humanised `--name`. |
| `{{recipeDescription}}` | Returned by `getDescription()`. |
| `{{recipeId}}` | OpenRewrite recipe id. For yaml: `<rootPackage>.<recipeName>` (root namespace per the example.yml convention). For java/scanning/refaster: `<package>.<recipeName>`. |
| `{{recipeKebab}}` | kebab-case form of `{{recipeName}}`, used for the YAML manifest filename. |

## What you'll need to edit afterwards

The smoke runner is honest about its origins. `Fixture.EXAMPLE` is generic, but `SmokeProject.writeBuild` and `ProjectShapeScaffolder.GREETING_BODY` still ship the SLF4J/Lombok dep block and `System.out.println` fixture from the project this template was extracted from. Look for `EDIT FOR YOUR RECIPE'S DEPS` markers.

## Fallback paths (when JBang isn't available)

**Bash + sed** — `tests/ci-smoke.sh` is the v0 path that does the same scaffolding without JBang. Edit the variables at the top, point it at a target dir, and it scaffolds + runs the gates:

```bash
./tests/ci-smoke.sh /path/to/target-project
```

**Manual `javac`** — the JBang script compiles cleanly with `javac --release 17` since `//DEPS` and `//JAVA` are Java line comments:

```bash
PICOCLI=/tmp/picocli-cache/picocli-4.7.7.jar
[ -f "$PICOCLI" ] || curl -fsSL -o "$PICOCLI" \
  https://repo1.maven.org/maven2/info/picocli/picocli/4.7.7/picocli-4.7.7.jar
javac --release 17 -cp "$PICOCLI" -d /tmp/recipescaffold-build jbang/RecipeScaffold.java
java -cp /tmp/recipescaffold-build:"$PICOCLI" recipescaffold.RecipeScaffold init --help
```

## Verification

CI runs four parallel jobs on every push and PR (`.github/workflows/ci.yml`):

| Job | Runs |
| --- | --- |
| `bash-scaffold` | `tests/ci-smoke.sh` end-to-end (scaffold + `./gradlew check smokeTest`), then `add-recipe` once per `--type`/`--test-style` cell, then `./gradlew check`. |
| `jbang-scaffold` | `jbang init --verify` (scaffold + `./gradlew check smokeTest`), then the same `add-recipe` cells, then `./gradlew check`. |
| `harness` | `./gradlew test` — the in-repo TestKit harness (`src/test/java/recipescaffold/ScaffoldHarnessTest.java`) drives `Init` + `AddRecipe` (all five cells) into a `@TempDir`, then runs `GradleRunner` on the result. Sandbox-friendly: redirects Gradle home and Maven local to tmp dirs. |
| `actionlint` | Lints this repo's workflow plus the three workflows shipped into scaffolded projects after a `jbang init`. |

Locally, `tests/ci-smoke.sh` and `jbang init --verify` should produce byte-identical scaffold trees. After any change to `template/`, run **both** plus the harness:

```bash
./tests/ci-smoke.sh /tmp/recipe-template-ci-smoke
jbang jbang/RecipeScaffold.java init … --verify
./gradlew test
```

## Layout

```
.
├── README.md                     # this file
├── CLAUDE.md                     # session bootstrap for Claude Code
├── AGENTS.md                     # vendor-neutral agent guidance (forwarded from CLAUDE.md)
├── BACKLOG.md                    # what's shipped + what's queued
├── JBANG_TEMPLATE_PLAN.md        # the source plan (Part A: upstream findings; Part B: scaffolder design)
├── build.gradle.kts              # repo-root Gradle build for the TestKit harness
├── settings.gradle.kts
├── gradle/                       # libs.versions.toml + wrapper for the harness build
├── gradlew, gradlew.bat
├── jbang/RecipeScaffold.java     # picocli — the four subcommands
├── src/test/java/recipescaffold/ # ScaffoldHarnessTest — Init+AddRecipe@TempDir+GradleRunner
├── tests/ci-smoke.sh             # bash scaffold-and-build verifier (kept as v0 fallback)
├── template/                     # WHAT GETS SCAFFOLDED into the user's new project
│   ├── AGENTS.md, CLAUDE.md, LICENSE, .editorconfig
│   ├── .github/workflows/        # gradle.yml, release.yml, wrapper-validation.yml
│   ├── snippets/                 # source-of-truth recipe-skeleton fragments (read by add-recipe)
│   ├── src/                      # main + test + integrationTest + smokeTest
│   └── .claude/skills/           # the four recipe-authoring skills shipped with the scaffold
└── .github/workflows/ci.yml      # bash-scaffold + jbang-scaffold + harness + actionlint
```

## License

Apache 2.0. The scaffolded project also ships under Apache 2.0.
