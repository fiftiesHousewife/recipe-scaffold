# Claude Code session notes — openrewrite-recipe-template-fhw

This repo extracts the build conventions, test harnesses, and pre-publish smoke gate from `/Users/pippanewbold/Claude` (`io.github.fiftieshousewife:system-out-to-lombok-log4j`) into a reusable scaffold for new OpenRewrite recipe projects. It is the source-of-truth for what a fresh recipe project should look like — the upstream Claude project remains the canonical evolving recipe library, and changes flow push-based from there into here (see plan §B8).

## Read first

- **`JBANG_TEMPLATE_PLAN.md`** — the full Plan-agent report (Part A: 22 OpenRewrite best-practice findings against the upstream project; Part B: 11-section JBang+picocli scaffolder design). The header table tracks which Part A items have shipped upstream.
- **`README.md`** — what the repo is, placeholder list, JBang quickstart, manual fallback.

## Status

- **B11.1 done.** `template/` holds the parameterised scaffold. `tests/ci-smoke.sh` is the bash port that scaffolds + sed-substitutes + runs `./gradlew check smokeTest`.
- **B11.2 done (2026-05-04).** `jbang/RecipeScaffold.java` is the picocli `Init` port — same end-state as `ci-smoke.sh`, byte-identical scaffold tree (verified). Runs in two flavours: `jbang jbang/RecipeScaffold.java init …` (preferred) or `javac --release 17 -cp picocli.jar` for environments without JBang. `--verify` runs `./gradlew check smokeTest` after scaffolding.
- **B11.3 next.** `add-recipe <name>` subcommand and `template/snippets/*.template` fragments. Plan §B5 + §B6 for the design.
- **B11.4+ later.** `verify-gates` thin wrapper, end-to-end CI for the template repo, `--upgrade-skills`.

## Layout

```
.
├── README.md                     # repo-level: what it is, JBang quickstart, manual fallback
├── CLAUDE.md                     # this file — session bootstrap
├── JBANG_TEMPLATE_PLAN.md        # the source plan
├── .claude/skills/               # repo-level skills for working IN this repo
├── tests/ci-smoke.sh             # bash scaffold-and-build verifier (kept as v0 fallback)
├── jbang/RecipeScaffold.java     # picocli Init subcommand — the JBang flow
├── template/                     # WHAT GETS SCAFFOLDED into the user's new project
│   ├── AGENTS.md                 # vendor-neutral agent guidance (canonical)
│   ├── CLAUDE.md                 # Claude-Code-specific notes; forwards to AGENTS.md
│   ├── LICENSE                   # Apache 2.0
│   ├── .editorconfig
│   ├── .github/
│   │   ├── dependabot.yml
│   │   └── workflows/{gradle,release,wrapper-validation}.yml
│   ├── ...everything else that becomes their build...
│   └── .claude/skills/           # the four recipe-authoring skills the scaffolded project ships with
```

The `template/.claude/skills/` vs the repo-level `.claude/skills/` distinction matters: the former goes to the scaffolded user, the latter is for the maintainer of THIS repo.

## Placeholders the scaffold uses

| Placeholder | Meaning |
| --- | --- |
| `{{group}}`, `{{artifact}}`, `{{rootPackage}}` | Maven group + artifact + Java root package |
| `{{initialVersion}}` | First version of the scaffolded project |
| `{{recipeName}}`, `{{recipeDescription}}` | POM name + description |
| `{{githubOrg}}`, `{{githubRepo}}` | For SCM URLs |
| `{{authorId}}`, `{{authorName}}`, `{{authorEmail}}` | POM developer block |
| `{{javaTargetMain}}`, `{{javaTargetTests}}` | `release` for compileJava and compileTestJava |
| `{{rewritePluginVersion}}` | Snippet versions in template's docs |
| `__ROOT_PACKAGE__` | Literal directory marker — renamed at scaffold time |

The residual check is `(?<!\$)\{\{[a-zA-Z][a-zA-Z0-9]*\}\}` — anchored so GitHub Actions `${{ secrets.X }}` expressions in `release.yml` don't trip the gate.

`tests/ci-smoke.sh` and `jbang/RecipeScaffold.java` must stay in sync. When you add a new placeholder: extend both substitution lists and the table above.

## Skills available in this session

The four recipe-authoring skills (`new-gradle-project`, `new-recipe`, `recipe-testing`, `smoke-test`) plus the ten clean-code skills. The clean-code skills aren't enforced by build tools here (the template strips the cleancode plugin) — invoke them on judgment, not because tooling demands it.

## Conventions

- **No emojis in source, docs, or commits** unless the user explicitly asks.
- **Prefer editing `template/` files over creating new ones.** New files in `template/` propagate to every future scaffold — only add when the responsibility doesn't fit existing files.
- **Verify after every template change** by running both `tests/ci-smoke.sh` and the JBang script with `--verify`. Don't trust placeholder substitution by inspection.
- **Honest scope notes belong in `README.md`** — the smoke runner ships SLF4J/Lombok-specific dep blocks marked `EDIT FOR YOUR RECIPE'S DEPS`. Don't pretend it's fully generic.
- **AGENTS.md is canonical, CLAUDE.md is the stub** in the scaffolded project — vendor-neutral content goes in AGENTS.md.

## How to run the JBang script

The supported flow is JBang. Install it once (`brew install jbang` or `curl -Ls https://sh.jbang.dev | bash -s - app setup`), then:

```bash
jbang jbang/RecipeScaffold.java init --help
```

JBang handles compilation, dep resolution (`//DEPS info.picocli:picocli:4.7.7`), and caching. CI uses the same flow via `jbangdev/setup-jbang@main` in `.github/workflows/ci.yml`.

**Fallback only — when JBang is genuinely unavailable** (e.g. an air-gapped CI image), the script compiles cleanly with `javac --release 17` since `//DEPS` and `//JAVA` are Java line comments. Don't make this the default; install JBang.

```bash
PICOCLI=/tmp/picocli-cache/picocli-4.7.7.jar
[ -f "$PICOCLI" ] || curl -fsSL -o "$PICOCLI" \
  https://repo1.maven.org/maven2/info/picocli/picocli/4.7.7/picocli-4.7.7.jar
javac --release 17 -cp "$PICOCLI" -d /tmp/recipescaffold-build jbang/RecipeScaffold.java
java -cp /tmp/recipescaffold-build:"$PICOCLI" RecipeScaffold init --help
```

## Not yet wired

- `git init` and the GitHub remote `openrewrite-recipe-template-fhw` — defer until B11.3+ has settled the directory layout.
- `template/snippets/*.template` — B11.3 (`add-recipe` subcommand source-of-truth fragments).
- CI for THIS repo — runs `tests/ci-smoke.sh` (or the JBang flow) on every PR. Plan §B11.4.
