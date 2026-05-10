# recipe-scaffold

Scaffold and maintain an [OpenRewrite](https://docs.openrewrite.org) recipe project from a single-file CLI. The same script that emits the project also ships subcommands that add recipes, run the local gate chain, and re-sync the bundled agent skills as the upstream evolves.

Repo: <https://github.com/fiftiesHousewife/recipe-scaffold>
Latest release notes: [`CHANGELOG.md`](./CHANGELOG.md)

## Why this exists

Most project templates do one thing: stamp out a starting point. `cookiecutter`, `yeoman`, and the Maven archetype share the same arc — fork the template, substitute placeholders, hand the result to the user. That works for day one. By month three, every scaffolded project tends to carry a private divergence of the build script: a plugin bumped here, a transitive-dep workaround there, an undocumented fix copied from somewhere. The template that produced them is now ahead by a year, behind by a year, or both, with no upgrade path.

OpenRewrite recipe libraries amplify the cost. The interesting moving parts are not in the recipe code; they are in the gate machinery — a smokeTest matrix scaffolding `/tmp` projects per cell, an `integrationTest` source set pinning a JDK 21 launcher to keep `withToolingApi()` happy on JDK 25 outer toolchains, a publish-gate wiring `publishAndReleaseToMavenCentral` to depend on `smokeTest`, the Refaster annotation processor's classpath quirks. When any of these breaks, the fix has to land in every consumer that ever ran the template, by hand.

`recipe-scaffold` keeps the template and the upgrade tool as the same binary. The dropfile (`.recipe-scaffold.yml`) `init` writes is a contract: every later subcommand reads it back so the project's identity is decided once. `upgrade-skills` and (queued) `upgrade-build-logic` close the loop by re-syncing the vendored pieces — agent skills today, the convention plugin tomorrow — without churning the consumer's `build.gradle.kts`.

## JBang and picocli

The CLI is one Java file at [`jbang/RecipeScaffold.java`](./jbang/RecipeScaffold.java). Two pieces of plumbing make that practical:

- **[JBang](https://www.jbang.dev)** is a launcher for self-describing Java scripts. The first three lines of the file are `///usr/bin/env jbang`, `//JAVA 17+`, and `//DEPS info.picocli:picocli:4.7.7`. JBang reads those, downloads the dependency, picks a JDK that matches, compiles, runs `main`, and caches the compiled jar. Users invoke the script via a JBang catalog reference (`jbang recipe-scaffold@fiftiesHousewife/recipe-scaffold`) without cloning anything.
- **[picocli](https://picocli.info)** turns the file into a real CLI: `@Command` declares each subcommand, `@Option` declares its flags, `@Mixin` shares `--directory` across `add-recipe`/`verify-gates`/`upgrade-skills`. The same compiled bytecode runs as a JBang script, a `./gradlew run`, an `installDist` shell launcher, or a fat jar — picocli handles all four equivalently.

Compared with `cookiecutter` (Python, language-substitution only), `yeoman` (Node, generator scripts), or `maven-archetype-plugin` (a Maven plugin, awkward to extend with non-init subcommands), this combination gives one launcher, one binary, and a single place to host post-init upgrade subcommands.

## Quickstart

```bash
jbang recipe-scaffold@fiftiesHousewife/recipe-scaffold init \
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

`--verify` runs `./gradlew check smokeTest` against the freshly scaffolded project. Drop it for a faster scaffold-only pass. `--help` on any subcommand lists every option.

The result is a Gradle project rooted at `--directory`, with a `.recipe-scaffold.yml` dropfile at the root capturing the project's identity for later subcommands.

### Argfile support

`init` takes a lot of options. picocli accepts an `@argfile` so you can stash them in a properties-style file and point the CLI at it:

```bash
cat > acme.argfile <<'EOF'
--group=io.github.acme
--artifact=acme-rewrite-recipes
--package=io.github.acme
--recipe-name=Acme Recipes
--recipe-description=OpenRewrite recipes for the Acme codebase
--github-org=acme
--github-repo=acme-rewrite-recipes
--author-id=acmebot
--author-name=Acme Bot
--author-email=bot@acme.example
EOF

jbang recipe-scaffold@fiftiesHousewife/recipe-scaffold init @acme.argfile --verify
```

One option per line, no quoting required. See the [picocli documentation on argfiles](https://picocli.info/#AtFiles) for the full grammar.

## Five ways to run it

Same code, five distribution paths. Pick whichever your environment makes cheapest.

| Path | One-time setup | Invocation | Best for |
| --- | --- | --- | --- |
| **JBang catalog** | install JBang once | `jbang recipe-scaffold@fiftiesHousewife/recipe-scaffold init …` | individual devs |
| **JBang direct** | install JBang + clone | `jbang jbang/RecipeScaffold.java init …` | hacking on the scaffolder |
| **`./gradlew run`** | clone only | `./gradlew run --args="init --group=…"` | one-off, no install |
| **`./gradlew installDist`** *(no JBang)* | `./gradlew installDist` | `build/install/recipe-scaffold/bin/recipe-scaffold init …` | corporate envs that block `brew` |
| **Fat jar** *(no JBang)* | `./gradlew jar` | `java -jar build/libs/recipe-scaffold.jar init …` | shipping into another build/CI image |

A bash fallback (`tests/ci-smoke.sh`) reproduces just the `init` step without JBang or Java; see [Fallback paths](#fallback-paths) below.

### Installing JBang

```bash
brew install jbang                                     # macOS
sdk install jbang                                      # via SDKMAN!
choco install jbang                                    # Windows
curl -Ls https://sh.jbang.dev | bash -s - app setup    # POSIX universal
./gradlew downloadJbang                                # corporate / no package manager
```

The `app setup` form drops `jbang` into `~/.jbang/bin/` and adds it to `PATH` in shell-rc files without sudo. The `./gradlew downloadJbang` path is for environments that block `brew`/`curl | bash`/etc.: it downloads the official JBang release zip from [GitHub](https://github.com/jbangdev/jbang/releases) (pinned in `gradle/libs.versions.toml`) and extracts it under `build/jbang/jbang-<version>/`.

### Upgrading recipe-scaffold itself

Each install path has its own upgrade trigger. `recipe-scaffold --version` reports the running CLI version; compare against [the latest release](https://github.com/fiftiesHousewife/recipe-scaffold/releases) to know whether you are behind.

| Install path | Upgrade command |
| --- | --- |
| JBang catalog (tracks `main`) | `jbang cache clear && jbang recipe-scaffold@fiftiesHousewife/recipe-scaffold --version` |
| JBang catalog pinned to a tag | Change the tag in your invocations (`@…/v0.3.0` → `@…/v0.4.0`). |
| `jbang app install` | `jbang app install --force recipe-scaffold@fiftiesHousewife/recipe-scaffold` |
| JBang direct from a clone | `git pull` |
| `./gradlew run` from a clone | `git pull` (Gradle re-runs from sources next invocation) |
| `./gradlew installDist` | `git pull && ./gradlew installDist` |
| Fat jar | `git pull && ./gradlew jar` |

A queued `recipe-scaffold doctor` subcommand (see [`BACKLOG.md`](./BACKLOG.md)) will collapse this table by detecting the install path and printing the right command.

After upgrading the CLI itself, **existing scaffolded projects** still need to refresh their copies of `template/.claude/skills/` (run `recipe-scaffold upgrade-skills`) and — once `upgrade-build-logic` ships — their copy of `template/build-logic/`. The migration recipe in the latest [CHANGELOG entry](./CHANGELOG.md) covers the manual `curl` until then.

## Subcommands

| Subcommand | One-liner | Typical use |
| --- | --- | --- |
| [`init`](#init) | Scaffold a fresh recipe project from `template/`. | First-time bootstrap; once per project. |
| [`add-recipe`](#add-recipe) | Drop a new recipe (and test) into an existing scaffolded project. | Every new recipe; works from any subdirectory. |
| [`verify-gates`](#verify-gates) | Run `./gradlew check integrationTest smokeTest`. | Pre-push and pre-publish sanity check. |
| [`upgrade-skills`](#upgrade-skills) | Refresh the project's `.claude/skills/` from the upstream copy. | After `recipe-scaffold` itself releases. |
| [`upgrade-build-logic`](#upgrade-build-logic) | Refresh the project's vendored `build-logic/` from the upstream copy. | After upstream convention-plugin fixes. |
| [`doctor`](#doctor) | Report version drift and the right upgrade command for your install path. | Anytime; replaces the manual upgrade table below. |

`--help` on any subcommand lists every option.

### `init`

Required and optional flags:

| Flag | Required | Default | Purpose |
| --- | --- | --- | --- |
| `--group` | yes | — | Maven group, e.g. `io.github.acme` |
| `--artifact` | yes | — | Maven artifact id |
| `--package` / `--root-package` | yes | — | Java root package; recipes default to `<rootPackage>.recipes` |
| `--recipe-name` | yes | — | POM `<name>` |
| `--recipe-description` | yes | — | POM `<description>` |
| `--github-org`, `--github-repo` | yes | — | SCM URLs |
| `--author-id`, `--author-name`, `--author-email` | yes | — | POM developer block |
| `--initial-version` | no | `0.1` | First version of the scaffolded project |
| `--java-target-main` | no | `17` | `release` for `compileJava` |
| `--java-target-tests` | no | `25` | `release` for `compileTestJava` and toolchain |
| `--rewrite-plugin-version` | no | `7.32.1` | Version cited in template docs |
| `-d` / `--directory` | no | `./<artifact>` | Output directory |
| `--template-dir` | no | walks upward | Template source directory |
| `--force` | no | off | Overwrite an existing output directory |
| `--verify` | no | off | Run `./gradlew check smokeTest` after scaffolding |

Writes `.recipe-scaffold.yml` at the output root with `recipeScaffoldVersion`, `group`, `artifact`, `rootPackage`, `javaTargetMain`, `javaTargetTests`. Hand-edit `recipePackage:` into that file if you want recipes outside the default `<rootPackage>.recipes`.

### `add-recipe`

```bash
cd acme-rewrite-recipes
jbang recipe-scaffold@fiftiesHousewife/recipe-scaffold add-recipe \
  --name RemoveStaleSuppression \
  --display-name "Remove @SuppressWarnings noise" \
  --description "Remove suppressions that no longer match a real warning."
```

Walks upward looking for `.recipe-scaffold.yml`, so it works from any subdirectory.

| Flag | Default | Purpose |
| --- | --- | --- |
| `--name` | — | Recipe class name in PascalCase (required) |
| `--type` | `java` | One of `java`, `scanning`, `yaml`, `refaster` |
| `--test-style` | `block` | `block` (multi-line) or `method` (one-line); `method` requires `--type=java\|scanning` |
| `--display-name` | humanised `--name` | Override `getDisplayName()` |
| `--description` | TODO sentence | Override `getDescription()` |
| `--package` | dropfile `recipePackage`, else `<rootPackage>.recipes` | Override the recipe's package |
| `-d` / `--directory` | walk upward | Project root |
| `--no-tests` | off | Skip the test file |
| `--force` | off | Overwrite an existing recipe / test |

Recipe-type output paths:

| `--type` | Main file | Test scaffold |
| --- | --- | --- |
| `java` | `src/main/java/<pkg>/<Name>.java` ([`JavaIsoVisitor`](https://docs.openrewrite.org/concepts-and-explanations/visitors) skeleton) | `recipe-test.template` (single-arg `java(...)`) |
| `scanning` | `src/main/java/<pkg>/<Name>.java` ([`ScanningRecipe<Acc>`](https://docs.openrewrite.org/concepts-and-explanations/recipes#scanning-recipes)) | as `java` |
| `yaml` | `src/main/resources/META-INF/rewrite/<kebab>.yml` ([composed-recipe manifest](https://docs.openrewrite.org/reference/yaml-format-reference)) | `recipe-test-yaml.template` (asserts manifest shape) |
| `refaster` | `src/main/java/<pkg>/<Name>.java` (outer holder + nested [`@RecipeDescriptor`](https://github.com/openrewrite/rewrite-templating)) | `recipe-test-refaster.template` (instantiates generated `<Name>Recipes`) |

The shipped recipe is a no-op; fill in `matchesCriteria` / `transform` per `.claude/skills/new-recipe/SKILL.md`.

### `verify-gates`

```bash
cd acme-rewrite-recipes
jbang recipe-scaffold@fiftiesHousewife/recipe-scaffold verify-gates
```

Runs `./gradlew check integrationTest smokeTest`. The three tasks are listed explicitly so all run even when `check` is up-to-date. Refuses to run in non-recipe-scaffold projects (no dropfile = no `smokeTest` task to invoke). Accepts `--directory`.

### `upgrade-skills`

```bash
cd acme-rewrite-recipes
jbang recipe-scaffold@fiftiesHousewife/recipe-scaffold upgrade-skills [--dry-run]
```

Replaces each subdirectory of the project's `.claude/skills/` with the corresponding upstream copy from `template/.claude/skills/`. Iterates only over upstream subdirectories, so any user-added skill is left alone. Accepts `--directory`, `--template-dir`, `--dry-run`.

### `upgrade-build-logic`

```bash
cd acme-rewrite-recipes
jbang recipe-scaffold@fiftiesHousewife/recipe-scaffold upgrade-build-logic [--dry-run]
```

Replaces the project's vendored `build-logic/` tree with the upstream copy from `template/build-logic/`. The whole tree is scaffold-managed, so the refresh is wholesale — don't keep local edits to `recipe-library.gradle.kts`. `gradle/libs.versions.toml` is **not** refreshed; if `./gradlew check` reports missing catalog entries after an upgrade, diff `template/gradle/libs.versions.toml` against your project's copy by hand. Accepts `--directory`, `--template-dir`, `--dry-run`.

### `doctor`

```bash
recipe-scaffold doctor [--no-network]
```

Reports the running CLI version, the project's `recipeScaffoldVersion` (if invoked from inside a scaffolded project), the latest tag on the upstream repo (single GitHub API call, cached for 24h at `~/.cache/recipe-scaffold/latest-release.txt`), and a heuristic for which install path the CLI was launched from. When drift is detected, prints the upgrade command appropriate to your install path. `--no-network` skips the API call for offline use. Accepts `--directory`.

## What you get in the scaffolded project

| Aspect | What ships |
| --- | --- |
| **Build logic** | `build-logic/` [included build](https://docs.gradle.org/current/userguide/composite_builds.html) hosts the [`recipe-library` convention plugin](./template/build-logic/src/main/kotlin/recipe-library.gradle.kts) — toolchain, source sets, jacoco, javadoc, sign-onlyIf, pre-publish smoke gate. The project's `build.gradle.kts` shrinks to identity and POM. |
| **Plugins** | [vanniktech/gradle-maven-publish-plugin](https://vanniktech.github.io/gradle-maven-publish-plugin/), [OpenRewrite Gradle plugin](https://docs.openrewrite.org/reference/gradle-plugin-configuration), [Ben-Manes versions plugin](https://github.com/ben-manes/gradle-versions-plugin), [SpotBugs Gradle plugin](https://github.com/spotbugs/spotbugs-gradle-plugin), [JaCoCo](https://www.jacoco.org/jacoco/). |
| **Refaster wiring** | [`org.openrewrite:rewrite-templating`](https://github.com/openrewrite/rewrite-templating) annotation processor + [Errorprone Refaster](https://errorprone.info/docs/refaster), with canonical `auto-service-annotations` and `dataflow-errorprone` excludes; `compileJava` adds `-Arewrite.javaParserClasspathFrom=resources`. |
| **Source sets** | `test` ([JUnit 5](https://junit.org/junit5/) + [`RewriteTest`](https://docs.openrewrite.org/authoring-recipes/recipe-testing)), `integrationTest` ([Tooling API `withToolingApi()`](https://docs.gradle.org/current/userguide/third_party_integration.html#embedding)), `smokeTest` (scaffolds throwaway `/tmp` Gradle projects per matrix cell). |
| **Publishing gate** | `publishAndReleaseToMavenCentral` structurally `dependsOn("smokeTest")` — no path to Central skips the gate. |
| **Quality gates** | [Three opt-in `gradle.properties` keys](#quality-gates) — coverage, SpotBugs strictness, stale-dependency block. All default off. |
| **Files** | [Apache 2.0 LICENSE](https://www.apache.org/licenses/LICENSE-2.0), `.editorconfig`, three GitHub Actions workflows (`gradle.yml`, `wrapper-validation.yml`, `release.yml`). |
| **Agent setup** | `AGENTS.md` + `CLAUDE.md` stub. Four `.claude/skills/`: `new-gradle-project`, `new-recipe`, `recipe-testing`, `smoke-test`. |
| **Sample code** | One `ExampleRecipe` no-op so a freshly scaffolded project's `./gradlew check smokeTest` is green from the first commit. |
| **Snippets** | `snippets/` carries the `add-recipe` source-of-truth fragments into the scaffolded project; the init-time substitutor skips this directory so snippet-time `{{...}}` markers survive. |
| **Other deps** | [Lombok](https://projectlombok.org), [JSpecify](https://jspecify.dev), [AssertJ](https://assertj.github.io/doc/). |

### Quality gates

The `recipe-library` convention plugin ships three opt-in gates. All default off so a fresh scaffold builds without configuration; flip them on in `gradle.properties` as the recipe library matures.

| `gradle.properties` key | Effect |
| --- | --- |
| `recipeLibrary.minLineCoverage=0.70` | Enforces minimum line-coverage ratio via `jacocoTestCoverageVerification` (wired into `check`). Unset = no rule. |
| `recipeLibrary.spotbugsStrict=true` | Makes any SpotBugs finding fail `check`. Default false: findings are reported under `build/reports/spotbugs/` but non-blocking. |
| `recipeLibrary.failOnStaleDependencies=true` | Wires `verifyDependencies` into `check`; any non-prerelease upgrade available on Maven Central blocks the build. Prereleases (alpha/beta/rc/milestone/preview/snapshot) are filtered out. |
| `recipeLibrary.javaTargetMain=17` | `release` for `compileJava`. |
| `recipeLibrary.javaTargetTests=25` | `release` for `compileTestJava` and the test/integrationTest/smokeTest toolchain. |

## Day-to-day commands in the scaffolded project

### Build & test

| Command | What it does |
| --- | --- |
| `./gradlew check` | Unit tests + integration tests. Default verification gate. |
| `./gradlew test` | Unit / `RewriteTest` suite only. Fastest feedback. |
| `./gradlew test --tests "MyRecipeTest"` | Single test class. |
| `./gradlew integrationTest` | Embedded-Gradle suite; pulls a real `GradleProject` marker. |
| `./gradlew smokeTest` | Scaffolds throwaway `/tmp` projects per matrix cell, runs the recipe, verifies output compiles. The pre-publish gate. |
| `./gradlew jacocoTestReport` | HTML coverage under `build/reports/jacoco/`. |

### Dependency hygiene

| Command | What it does |
| --- | --- |
| `./gradlew dependencyUpdates` | List dependencies with newer releases (Ben-Manes). |
| `./gradlew dependencies` | Resolved dependency tree. |
| `./gradlew :buildEnvironment` | Gradle plugin classpath (catches plugin-version drift). |

### Publishing

| Command | What it does |
| --- | --- |
| `./gradlew publishToMavenLocal` | Stage to `~/.m2`. |
| `./gradlew publishAndReleaseToMavenCentral` | Full release. Structurally `dependsOn("smokeTest")`. |
| `git tag -a vX.Y.Z -m "..." && git push --tags` | Triggers `release.yml` for an automated tag-driven release. |

### Wrapper hygiene

| Command | What it does |
| --- | --- |
| `./gradlew wrapper --gradle-version=9.4.1` | Bump the Gradle wrapper. |
| `./gradlew wrapper --gradle-version=9.5.0 --distribution-type=bin` | Same, smaller download. |

## Placeholders

Two dialects share `{{name}}` syntax.

**Init-time** — substituted by `init` (and `tests/ci-smoke.sh`):

| Placeholder | Example | Meaning |
| --- | --- | --- |
| `{{group}}`, `{{artifact}}`, `{{rootPackage}}` | `io.github.acme`, `acme-rewrite-recipes`, `io.github.acme` | Maven group / artifact / Java root package |
| `{{initialVersion}}` | `0.1` | First version |
| `{{recipeName}}`, `{{recipeDescription}}` | `Acme Recipes`, `Cleanup recipes` | POM name / description |
| `{{githubOrg}}`, `{{githubRepo}}` | `acme`, `acme-rewrite-recipes` | SCM URLs |
| `{{authorId}}`, `{{authorName}}`, `{{authorEmail}}` | | POM developer block |
| `{{javaTargetMain}}`, `{{javaTargetTests}}` | `17`, `25` | `release` for `compileJava` and `compileTestJava` |
| `{{rewritePluginVersion}}` | `7.32.1` | Snippet versions in the scaffolded `README.md` |
| `{{recipeScaffoldVersion}}` | `0.3.0` | The CLI version that scaffolded the project; baked into `template/CLAUDE.md` so an agent can drift-check |
| `__ROOT_PACKAGE__` | | Literal directory marker, renamed at scaffold time |

**Snippet-time** — substituted by `add-recipe`, only inside `snippets/*.template`:

| Placeholder | Meaning |
| --- | --- |
| `{{package}}` | Java package the recipe (or its test) lives in. Default: `recipePackage` from the dropfile if set, else `<rootPackage>.recipes`. `--package=` overrides both. |
| `{{recipeName}}` | Recipe class name (PascalCase) |
| `{{recipeDisplayName}}` | Returned by `getDisplayName()`. Default: humanised `--name`. |
| `{{recipeDescription}}` | Returned by `getDescription()` |
| `{{recipeId}}` | OpenRewrite recipe id. yaml: `<rootPackage>.<recipeName>` (root namespace per example.yml convention); java/scanning/refaster: `<package>.<recipeName>` |
| `{{recipeKebab}}` | kebab-case form of `{{recipeName}}`, used for the YAML manifest filename |

## Verification

CI runs four parallel jobs on every push and PR (`.github/workflows/ci.yml`):

| Job | Runs |
| --- | --- |
| `bash-scaffold` | `tests/ci-smoke.sh` end-to-end, then `add-recipe` once per `--type` × `--test-style` cell, then `./gradlew check`. |
| `jbang-scaffold` | `jbang init --verify`, then the same `add-recipe` cells, then `./gradlew check`. |
| `harness` | `./gradlew test` — the in-repo [Gradle TestKit](https://docs.gradle.org/current/userguide/test_kit.html) harness drives `Init` + `AddRecipe` (all five cells) into a `@TempDir`, then runs `GradleRunner`. |
| `actionlint` | Lints this repo's workflow plus the three workflows shipped into scaffolded projects after a `jbang init`. |

Locally, `tests/ci-smoke.sh` and `jbang init --verify` produce byte-identical scaffold trees. After any change to `template/`, run both plus the harness:

```bash
./tests/ci-smoke.sh /tmp/recipe-template-ci-smoke
jbang jbang/RecipeScaffold.java init … --verify
./gradlew test
```

A `pre-push` skill at `.claude/skills/pre-push/SKILL.md` lists the exact local commands an agent should run before `git push`.

## Fallback paths

### Fat jar (no JBang)

```bash
./gradlew jar
java -jar build/libs/recipe-scaffold.jar init …
```

Bundles picocli; ~430 KB. Same CLI surface as the JBang form. Fine for CI images that don't ship JBang.

### Bash + sed (no JDK at scaffold time)

```bash
./tests/ci-smoke.sh /path/to/target-project
```

The v0 path. Edits the variables at the top of the script, runs the substitution, then runs `./gradlew check smokeTest` against the result (which still needs a JDK — bash is only doing file-shuffling).

### Manual `javac`

If neither the fat jar nor JBang are available but the script is on disk:

```bash
PICOCLI=/tmp/picocli-cache/picocli-4.7.7.jar
[ -f "$PICOCLI" ] || curl -fsSL -o "$PICOCLI" \
  https://repo1.maven.org/maven2/info/picocli/picocli/4.7.7/picocli-4.7.7.jar
javac --release 17 -cp "$PICOCLI" -d /tmp/recipe-scaffold-build jbang/RecipeScaffold.java
java -cp /tmp/recipe-scaffold-build:"$PICOCLI" recipescaffold.RecipeScaffold init --help
```

## Layout

```
.
├── README.md, AGENTS.md, BACKLOG.md, CHANGELOG.md, CLAUDE.md
├── jbang-catalog.json            # JBang catalog entry — `jbang recipe-scaffold@fiftiesHousewife/recipe-scaffold`
├── build.gradle.kts              # repo-root build for the TestKit harness + fat jar
├── settings.gradle.kts
├── gradle/                       # libs.versions.toml + wrapper
├── gradlew, gradlew.bat
├── jbang/RecipeScaffold.java     # picocli — the four subcommands
├── src/test/java/recipescaffold/ # in-repo TestKit harness
├── tests/ci-smoke.sh             # bash scaffold-and-build verifier
├── template/                     # everything below this is what `init` scaffolds into the user's project
│   ├── AGENTS.md, CLAUDE.md, LICENSE, .editorconfig
│   ├── build.gradle.kts          # project identity + maven-publish coords/POM
│   ├── settings.gradle.kts       # pluginManagement.includeBuild("build-logic")
│   ├── gradle.properties         # toolchain + opt-in quality-gate flags
│   ├── build-logic/              # included build — `recipe-library` convention plugin
│   ├── .github/workflows/        # gradle.yml, release.yml, wrapper-validation.yml
│   ├── snippets/                 # source-of-truth `add-recipe` fragments
│   ├── src/                      # main + test + integrationTest + smokeTest
│   └── .claude/skills/           # the four recipe-authoring skills
└── .github/workflows/ci.yml      # bash-scaffold + jbang-scaffold + harness + actionlint
```

## References

| Topic | Resource |
| --- | --- |
| **OpenRewrite** | [Docs](https://docs.openrewrite.org), [Recipe testing](https://docs.openrewrite.org/authoring-recipes/recipe-testing), [`moderneinc/rewrite-recipe-starter`](https://github.com/moderneinc/rewrite-recipe-starter), [`rewrite-templating`](https://github.com/openrewrite/rewrite-templating), [`rewrite-spring`](https://github.com/openrewrite/rewrite-spring), [`rewrite-static-analysis`](https://github.com/openrewrite/rewrite-static-analysis) |
| **JBang** | [Docs](https://www.jbang.dev/documentation/jbang/latest/usage.html), [`@argfile` syntax](https://picocli.info/#AtFiles) |
| **picocli** | [User manual](https://picocli.info), [GitHub](https://github.com/remkop/picocli) |
| **Gradle** | [Composite builds](https://docs.gradle.org/current/userguide/composite_builds.html), [Convention plugins](https://docs.gradle.org/current/userguide/sharing_build_logic_between_subprojects.html), [Version catalogs](https://docs.gradle.org/current/userguide/platforms.html), [TestKit](https://docs.gradle.org/current/userguide/test_kit.html) |
| **Build & publishing** | [vanniktech maven-publish-plugin](https://vanniktech.github.io/gradle-maven-publish-plugin/), [Maven Central Portal](https://central.sonatype.com), [Ben-Manes versions plugin](https://github.com/ben-manes/gradle-versions-plugin), [SpotBugs](https://github.com/spotbugs/spotbugs-gradle-plugin), [JaCoCo](https://www.jacoco.org/jacoco/), [actionlint](https://github.com/rhysd/actionlint) |
| **This project** | [`AGENTS.md`](./AGENTS.md), [`BACKLOG.md`](./BACKLOG.md), [`CHANGELOG.md`](./CHANGELOG.md), [`recipe-library.gradle.kts`](./template/build-logic/src/main/kotlin/recipe-library.gradle.kts) |

## License

[Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0). The scaffolded project also ships under Apache 2.0.
