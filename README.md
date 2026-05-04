# recipescaffold

Scaffold an OpenRewrite recipe project with the build conventions, test harnesses, and pre-publish smoke gate from `system-out-to-lombok-log4j`.

> **Status: pre-alpha.** B11.3 (`add-recipe` for `--type java` + `--type scanning`) shipped 2026-05-04. `add-recipe --type yaml`/`refaster` and `verify-gates` are still queued — see [`BACKLOG.md`](./BACKLOG.md).

## Quickstart

Install [JBang](https://www.jbang.dev) once:

```bash
brew install jbang        # macOS
# or
curl -Ls https://sh.jbang.dev | bash -s - app setup
```

Then scaffold a fresh recipe project:

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

### Adding a recipe

Once scaffolded, drop a new recipe class + test in with:

```bash
cd acme-rewrite-recipes
jbang <recipescaffold-checkout>/jbang/RecipeScaffold.java add-recipe \
  --name RemoveStaleSuppression \
  --display-name "Remove @SuppressWarnings noise" \
  --description "Remove suppressions that no longer match a real warning."
./gradlew check
```

`add-recipe` walks upward looking for `.recipescaffold.yml`, so it works from any subdirectory. It writes `src/main/java/<rootPackage>/recipes/<Name>.java` and `src/test/java/<rootPackage>/recipes/<Name>Test.java` from `snippets/recipe-class-java.template` + `snippets/recipe-test.template`. The shipped recipe uses a `JavaIsoVisitor` no-op skeleton — fill in `matchesCriteria` / `transform` per the `.claude/skills/new-recipe/SKILL.md` guidance.

`--type` accepts `java` (default) or `scanning` (`ScanningRecipe<Acc>` with first-pass scan + second-pass visit). `yaml`/`refaster` are queued. `--no-tests` skips the test file. `--force` overwrites existing recipe/test files.

## What you get

- Gradle build with `vanniktech/gradle-maven-publish-plugin` wired to Maven Central, the openrewrite plugin for self-tests, the Ben-Manes versions plugin, and JaCoCo.
- Three source sets: `test` (unit + RewriteTest integration), `integrationTest` (`withToolingApi()` end-to-end), `smokeTest` (scaffolds /tmp Gradle projects per matrix cell, parallel cap=3).
- `publishAndReleaseToMavenCentral` structurally `dependsOn("smokeTest")` — there's no path to Central that skips the gate.
- Apache 2.0 `LICENSE`, `.editorconfig`, `dependabot.yml`, three GitHub Actions workflows (`gradle.yml` for CI, `wrapper-validation.yml` for wrapper-jar checksum, `release.yml` for tag-triggered Maven Central publish).
- `AGENTS.md` (vendor-neutral agent guidance) + `CLAUDE.md` stub forwarding to it. Four `.claude/skills/` files copied verbatim: `new-gradle-project`, `new-recipe`, `recipe-testing`, `smoke-test`.
- One `ExampleRecipe` no-op so a freshly scaffolded project's `./gradlew check smokeTest` is green from the first commit.

## What's parameterised

| Placeholder | Example | Meaning |
| --- | --- | --- |
| `{{group}}` | `io.github.acme` | Maven group |
| `{{artifact}}` | `acme-rewrite-recipes` | Maven artifact id |
| `{{rootPackage}}` | `io.github.acme` | Java root package; recipes live at `{{rootPackage}}.recipes` |
| `{{initialVersion}}` | `0.1` | First version |
| `{{recipeName}}` | `Acme Recipes` | POM name |
| `{{recipeDescription}}` | `Cleanup recipes for the Acme codebase` | POM description |
| `{{githubOrg}}`, `{{githubRepo}}` | `acme`, `acme-rewrite-recipes` | For SCM URLs |
| `{{authorId}}`, `{{authorName}}`, `{{authorEmail}}` | | For POM developer block |
| `{{javaTargetMain}}` | `17` | `release` for `compileJava` |
| `{{javaTargetTests}}` | `25` | `release` for `compileTestJava` and toolchain |
| `{{rewritePluginVersion}}` | `7.30.0` | for `id("org.openrewrite.rewrite") version "..."` snippets in docs |

`__ROOT_PACKAGE__` is a literal directory marker — the scaffolder renames it to the slashed form of `{{rootPackage}}` at scaffold time.

## What you'll need to edit afterwards

- The smoke runner is honest about its origins. `Fixture.EXAMPLE` is generic, but `SmokeProject.writeBuild` and `ProjectShapeScaffolder.GREETING_BODY` still ship the SLF4J/Lombok dep block + `System.out.println` fixture from the project this template was extracted from. Look for `EDIT FOR YOUR RECIPE'S DEPS` markers.

## Fallback paths (if you can't or don't want to use JBang)

**Bash + sed** — `tests/ci-smoke.sh` is the v0 path that does the same scaffolding without JBang. Edit the variables at the top, point it at a target dir, and it scaffolds + runs the gates:

```bash
./tests/ci-smoke.sh /path/to/target-project
```

**Manual `cp` + sed** — see the inline snippet in [`CLAUDE.md`](./CLAUDE.md#how-to-run-the-jbang-script) if you want full control.

## Verification

`tests/ci-smoke.sh` and the JBang script must produce byte-identical scaffold trees. After any change to `template/`, run **both**:

```bash
./tests/ci-smoke.sh /tmp/recipe-template-ci-smoke
jbang jbang/RecipeScaffold.java init … --verify
```

CI runs both in parallel on every PR — see `.github/workflows/ci.yml`.
