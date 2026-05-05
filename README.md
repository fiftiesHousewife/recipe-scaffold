# recipescaffold

Scaffold an [OpenRewrite](https://docs.openrewrite.org) recipe project with modern build conventions, three-tier test harnesses, and a pre-publish smoke gate that hard-blocks [Maven Central](https://central.sonatype.com) on a real downstream `./gradlew check` — plus a CLI that adds new recipes, runs the gate chain, and refreshes the bundled agent skills.

> **Status: beta (2026-05-05).** All four recipe `--type` values (`java`, `scanning`, `yaml`, [`refaster`](https://errorprone.info/docs/refaster)), both `--test-style` variants (`block`, `method`), and four subcommands (`init`, `add-recipe`, `verify-gates`, `upgrade-skills`) shipped. CI exercises the full scaffold-and-build chain in three parallel jobs plus [actionlint](https://github.com/rhysd/actionlint). See [`BACKLOG.md`](./BACKLOG.md) for queued items.

Repo: <https://github.com/fiftiesHousewife/recipescaffold>

## Five ways to run it

The CLI is a [picocli](https://picocli.info) script at `jbang/RecipeScaffold.java`. Same code, five distribution paths — pick whichever your environment makes cheapest:

| Path | One-time setup | Invocation | Best for |
| --- | --- | --- | --- |
| **[JBang](https://www.jbang.dev) catalog** | install JBang once | `jbang recipescaffold@fiftiesHousewife/recipescaffold init …` | individual devs |
| **JBang direct** | install JBang once + clone repo | `jbang jbang/RecipeScaffold.java init …` | hacking on the scaffolder itself |
| **`./gradlew run`** | clone repo only | `./gradlew run --args="init --group=…"` | trying it once without installing anything global |
| **`./gradlew installDist`** *(no JBang)* | `./gradlew installDist` once | `build/install/recipescaffold/bin/recipescaffold init …` | corporate/managed envs that block `brew` |
| **Fat jar** *(no JBang)* | `./gradlew jar` once | `java -jar build/libs/recipescaffold.jar init …` | shipping into another build/CI image |

A bash fallback (`tests/ci-smoke.sh`) reproduces just the `init` step without JBang **or** Java. See [Fallback paths](#fallback-paths).

### Installing JBang

```bash
brew install jbang                                     # macOS
sdk install jbang                                      # via SDKMAN!
choco install jbang                                    # Windows
curl -Ls https://sh.jbang.dev | bash -s - app setup    # POSIX universal
```

The `app setup` form drops `jbang` into `~/.jbang/bin/`, prepends it to your `PATH` in shell-rc files, and works without sudo.

### What JBang gives you

JBang is a launcher for single-file Java/Kotlin/Groovy scripts. The first line of `jbang/RecipeScaffold.java`:

```java
///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17+
//DEPS info.picocli:picocli:4.7.7
```

is interpreted by JBang to mean: minimum JDK 17, this single dep on the runtime classpath, then run the file's `main` method. The `///usr/bin/env` shebang lets you `chmod +x` and run the file directly. The `//DEPS`, `//JAVA`, and shebang lines are valid Java line comments, so the same file compiles cleanly with `javac --release 17 -cp picocli.jar`.

**Catalogs.** A [`jbang-catalog.json`](./jbang-catalog.json) at a repo root maps short aliases to scripts. Resolution: `jbang <alias>@<user>/<repo>` fetches `https://github.com/<user>/<repo>/blob/main/jbang-catalog.json` and looks up the alias. We ship one alias, `recipescaffold`, pointing at `jbang/RecipeScaffold.java`. JBang caches the resolved script under `~/.jbang/cache/` so subsequent invocations are fast.

**Trust.** First-time invocation of a remote script prompts for trust. Pre-trust with:

```bash
jbang trust add https://github.com/fiftiesHousewife/recipescaffold
```

**Permanent install.** Drop `recipescaffold` into your `PATH` so you can call it without `jbang`:

```bash
jbang app install recipescaffold@fiftiesHousewife/recipescaffold
recipescaffold init --group=io.github.acme …
```

**Pinning to a version.** Catalog refs default to `main`. To pin to a release tag:

```bash
jbang recipescaffold@fiftiesHousewife/recipescaffold/v0.2.0 init …
```

**JBang vs. fat jar — when to use which.** JBang wins when you want one-line install and short invocations. The fat jar (`./gradlew jar` → `java -jar …`) wins when you're embedding the scaffolder into another tool, shipping it into a Docker image, or running in environments where you can't `curl` install scripts. Both invoke the same compiled bytecode; choose by ergonomics.

**Other JBang directives** the script *could* use (we don't currently):
- `//SOURCES` — additional source files to compile alongside (would let us split into multiple files when refactoring §A14 lands).
- `//FILES` — non-Java resources to bundle.
- `//RUNTIME_OPTIONS` — JVM args.
- `//JAVAC_OPTIONS` — compiler flags.
- `//NATIVE` — request native-image build via GraalVM.

Reference: [JBang documentation](https://www.jbang.dev/documentation/jbang/latest/usage.html).

## Quickstart

```bash
jbang recipescaffold@fiftiesHousewife/recipescaffold init \
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

`--verify` runs `./gradlew check smokeTest` against the freshly scaffolded project as a sanity check. Drop it for a faster scaffold-only run. Append `--help` to any subcommand for the full option list.

The result is a normal [Gradle](https://gradle.org) project rooted at `--directory`, with a `.recipescaffold.yml` dropfile at the root that captures the project's identity for subsequent tooling. From there: `cd acme-rewrite-recipes && ./gradlew check`.

## Subcommands

The CLI exposes four subcommands; `--help` on any of them lists every option. Examples below use the JBang catalog form; substitute `jbang jbang/RecipeScaffold.java` or `java -jar build/libs/recipescaffold.jar` if you prefer.

### `init` — scaffold a new project

The Quickstart above. **Required:** `--group`, `--artifact`, `--package`, `--recipe-name`, `--recipe-description`, `--github-org`, `--github-repo`, `--author-id`, `--author-name`, `--author-email`. **Optional:** `--initial-version` (default `0.1`), `--java-target-main` (default `17`), `--java-target-tests` (default `25`), `--rewrite-plugin-version` (default `7.32.1`), `--directory` (default `./<artifact>`), `--template-dir` (default: walks upward), `--force`, `--verify`. Writes `.recipescaffold.yml` at the output root for subsequent commands.

### `add-recipe` — drop a new recipe in

```bash
cd acme-rewrite-recipes
jbang recipescaffold@fiftiesHousewife/recipescaffold add-recipe \
  --name RemoveStaleSuppression \
  --display-name "Remove @SuppressWarnings noise" \
  --description "Remove suppressions that no longer match a real warning."
./gradlew check
```

Walks upward looking for `.recipescaffold.yml`, so it works from any subdirectory.

**`--type` (default `java`):**

| Type | Output | Default test scaffold |
| --- | --- | --- |
| `java` | `src/main/java/<pkg>/<Name>.java` — [`JavaIsoVisitor`](https://docs.openrewrite.org/concepts-and-explanations/visitors) no-op skeleton | `recipe-test.template` (single-arg `java(...)`, asserts no-op) |
| `scanning` | `src/main/java/<pkg>/<Name>.java` — [`ScanningRecipe<Acc>`](https://docs.openrewrite.org/concepts-and-explanations/recipes#scanning-recipes) two-pass skeleton | same as `java` |
| `yaml` | `src/main/resources/META-INF/rewrite/<kebab>.yml` — [composed-recipe manifest](https://docs.openrewrite.org/reference/yaml-format-reference) with placeholder `recipeList: []` | `recipe-test-yaml.template` (uses `Environment.builder().scanRuntimeClasspath().build().activateRecipes(...)`) |
| `refaster` | `src/main/java/<pkg>/<Name>.java` — outer holder with one nested [`@RecipeDescriptor`](https://github.com/openrewrite/rewrite-templating) template-pair | `recipe-test-refaster.template` (instantiates the *generated* `<Name>Recipes` aggregate) |

**`--test-style` (default `block`):** `block` is the multi-line text-block test scaffold; `method` swaps in a one-line `java(...)` over `Math.max(1, 2)` with a commented-out before/after hint — tighter for argument-level transforms. `method` is currently restricted to `--type java|scanning`.

**Other flags:** `--display-name`, `--description`, `--package` (default `<rootPackage>.recipes`), `-d/--directory`, `--no-tests`, `--force`.

The shipped recipe uses a no-op skeleton — fill in `matchesCriteria` / `transform` per the `.claude/skills/new-recipe/SKILL.md` guidance.

### `verify-gates` — run the full gate chain

```bash
cd acme-rewrite-recipes
jbang recipescaffold@fiftiesHousewife/recipescaffold verify-gates
```

Runs `./gradlew check integrationTest smokeTest`. The three tasks are listed explicitly so all run even when `check` is up-to-date. Refuses to run in non-recipescaffold projects (no dropfile = no `smokeTest` task to invoke). Accepts `--directory`.

### `upgrade-skills` — refresh the bundled agent skills

```bash
cd acme-rewrite-recipes
jbang recipescaffold@fiftiesHousewife/recipescaffold upgrade-skills [--dry-run]
```

Replaces each subdir of the project's `.claude/skills/` with the corresponding upstream copy from `template/.claude/skills/`. Iterates only over upstream subdirs, so any user-added skill is left alone. `--dry-run` previews. Accepts `--directory`, `--template-dir`.

## Cheatsheet for the scaffolded project

Once you're inside `<your-project>` and have run `init`, these are the commands you'll use day-to-day. The first column is what you run; the second is what to expect.

### Build & test

| Command | What it does |
| --- | --- |
| `./gradlew check` | Run unit tests (`test`) + integration tests (`integrationTest`, embedded Gradle). Default verification gate. |
| `./gradlew test` | Just the unit / `RewriteTest` suite. Fastest feedback loop. |
| `./gradlew test --tests "MyRecipeTest"` | Single test class. Useful while iterating. |
| `./gradlew integrationTest` | Just the embedded-Gradle suite. Slower; pulls a real `GradleProject` marker. |
| `./gradlew smokeTest` | Scaffolds throwaway `/tmp` projects per matrix cell, runs the recipe, verifies output compiles. The pre-publish gate. |
| `./gradlew jacocoTestReport` | HTML coverage report under `build/reports/jacoco/`. |
| `./gradlew clean` | Wipe `build/`. |

### Recipe authoring

| Command | What it does |
| --- | --- |
| `jbang recipescaffold@fiftiesHousewife/recipescaffold add-recipe --name MyRecipe` | Add a Java recipe + test. |
| `… add-recipe --name MyRecipe --type scanning` | Add a `ScanningRecipe<Acc>` two-pass skeleton. |
| `… add-recipe --name MyRecipe --type yaml` | Add a YAML composition manifest under `META-INF/rewrite/`. |
| `… add-recipe --name MyRecipe --type refaster` | Add a Refaster template-pair holder; the AP generates `MyRecipeRecipes`. |
| `… add-recipe --name MyRecipe --test-style method` | Use the one-line `java(...)` test scaffold (java/scanning only). |
| `… add-recipe --name MyRecipe --no-tests` | Skip the test file. |
| `… verify-gates` | Run `check integrationTest smokeTest` — full local pre-publish gate. |
| `… upgrade-skills [--dry-run]` | Refresh the bundled `.claude/skills/` from the upstream scaffolder. |

### Dependency hygiene

| Command | What it does |
| --- | --- |
| `./gradlew dependencyUpdates` | Ben-Manes versions plugin: list dependencies with newer releases. |
| `./gradlew dependencies` | Show the resolved dependency tree. |
| `./gradlew :buildEnvironment` | Show the Gradle plugin classpath (catches plugin-version drift). |

### Publishing

| Command | What it does |
| --- | --- |
| `./gradlew publishToMavenLocal` | Stage to `~/.m2`. Local consumers can resolve. |
| `./gradlew publishAndReleaseToMavenCentral` | Full release path. Structurally `dependsOn("smokeTest")` — no path skips the gate. |
| `git tag -a vX.Y.Z -m "..." && git push --tags` | Triggers `release.yml` for an automated tag-driven release. |

### Wrapper hygiene

| Command | What it does |
| --- | --- |
| `./gradlew wrapper --gradle-version=9.4.1` | Bump the Gradle wrapper. |
| `./gradlew wrapper --gradle-version=9.5.0 --distribution-type=bin` | Same, with `-bin` (smaller download than the default `-all`). |

## What you get

- Gradle build with [`vanniktech/gradle-maven-publish-plugin`](https://vanniktech.github.io/gradle-maven-publish-plugin/) wired to Maven Central, the [OpenRewrite Gradle plugin](https://docs.openrewrite.org/reference/gradle-plugin-configuration) for self-tests, the [Ben-Manes versions plugin](https://github.com/ben-manes/gradle-versions-plugin), and [JaCoCo](https://www.jacoco.org/jacoco/).
- Refaster recipe support pre-wired: [`org.openrewrite:rewrite-templating`](https://github.com/openrewrite/rewrite-templating) annotation processor + [`com.google.errorprone:error_prone_core`](https://errorprone.info) (with the canonical `auto-service-annotations` and `dataflow-errorprone` excludes); `compileJava` adds `-Arewrite.javaParserClasspathFrom=resources`.
- Three source sets: `test` ([JUnit 5](https://junit.org/junit5/) + [`RewriteTest`](https://docs.openrewrite.org/authoring-recipes/recipe-testing) integration), `integrationTest` ([Gradle Tooling API `withToolingApi()`](https://docs.gradle.org/current/userguide/third_party_integration.html#embedding) end-to-end), `smokeTest` (scaffolds `/tmp` Gradle projects per matrix cell).
- `publishAndReleaseToMavenCentral` structurally `dependsOn("smokeTest")` — there's no path to Central that skips the gate.
- [Apache 2.0 LICENSE](https://www.apache.org/licenses/LICENSE-2.0), `.editorconfig`, three GitHub Actions workflows (`gradle.yml` for CI, `wrapper-validation.yml` for wrapper-jar checksum, `release.yml` for tag-triggered Maven Central publish).
- `AGENTS.md` (vendor-neutral agent guidance) + `CLAUDE.md` stub forwarding to it. Four `.claude/skills/` shipped: `new-gradle-project`, `new-recipe`, `recipe-testing`, `smoke-test`.
- One `ExampleRecipe` no-op so a freshly scaffolded project's `./gradlew check smokeTest` is green from the first commit.
- `snippets/` — copies of the `add-recipe` source-of-truth fragments. Carried into the scaffolded project so `add-recipe` resolves them locally; the init-time substitutor explicitly skips this directory so the snippet-time `{{...}}` markers survive.
- Other deps wired by default: [Lombok](https://projectlombok.org), [JSpecify](https://jspecify.dev), [AssertJ](https://assertj.github.io/doc/).

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
| `{{rewritePluginVersion}}` | `7.32.1` | for `id("org.openrewrite.rewrite") version "..."` snippets in docs |

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

## Fallback paths

### Fat jar (no JBang)

Build once, run anywhere with a JDK ≥ 17:

```bash
./gradlew jar
java -jar build/libs/recipescaffold.jar init …
```

Bundles picocli; ~430 KB. Same CLI surface as the JBang form; differs only in invocation. Fine for CI images that don't ship JBang.

### Bash + sed (no JDK at scaffold time)

`tests/ci-smoke.sh` is the v0 path that does just the init step without JBang **or** Java. Edit the variables at the top, point it at a target dir:

```bash
./tests/ci-smoke.sh /path/to/target-project
```

It scaffolds the project and runs `./gradlew check smokeTest` against the result (which still needs a JDK — bash is only doing the file-shuffling).

### Manual `javac`

If neither the fat jar nor JBang are available but the JBang script is on disk, it compiles cleanly with `javac --release 17` since `//DEPS` and `//JAVA` are Java line comments:

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
| `harness` | `./gradlew test` — the in-repo [Gradle TestKit](https://docs.gradle.org/current/userguide/test_kit.html) harness (`src/test/java/recipescaffold/ScaffoldHarnessTest.java`) drives `Init` + `AddRecipe` (all five cells) into a `@TempDir`, then runs `GradleRunner` on the result. Sandbox-friendly: redirects Gradle home and Maven local to tmp dirs. |
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
├── jbang-catalog.json            # JBang catalog entry — `jbang recipescaffold@fiftiesHousewife/recipescaffold`
├── build.gradle.kts              # repo-root Gradle build for the TestKit harness + fat jar
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

[Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0). The scaffolded project also ships under Apache 2.0.
