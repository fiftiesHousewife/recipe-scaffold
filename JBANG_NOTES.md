# JBang research notes

Persisted findings from 2026-05-04 research. Covers (1) JBang + picocli best practice and (2) JBang + OpenRewrite ecosystem. Sources cited inline.

## TL;DR — what to change in this repo

- Nothing structurally wrong. Single-file `RecipeScaffold.java` matches the dominant idiom.
- Two cheap wins worth doing now or in the next pass: ship a `jbang-catalog.json` so users can `jbang init@fiftiesHousewife` instead of pasting a long URL, and switch `//JAVA 17+` → `//JAVA 21` if we end up testing only on 21.
- One forward-looking note for B11.3: when we eventually bundle `template/` into the script for offline/installed-CLI use, `//FILES` supports directories (recursive copy). Until then, the on-disk `template/` lookup is fine.

## 1. File and class naming

JBang follows the standard Java rule: the public class name must match the filename. Both PascalCase (`RecipeScaffold.java`) and lowercase (`recipescaffold.java`) work; PascalCase is conventional Java.

The CLI binary name is set by `@Command(name = "...")` and is independent of the filename. Lowercase CLI binary names are conventional. We have `name = "recipescaffold"` on a class `RecipeScaffold` in `RecipeScaffold.java` — clean.

Sources: [JBang script-directives docs](https://www.jbang.dev/documentation/jbang/latest/script-directives.html), [Twilio JBang+picocli tutorial](https://www.twilio.com/en-us/blog/developers/tutorials/building-blocks/cli-app-java-jbang-picocli).

## 2. Directives

| Directive | Form | Notes |
| --- | --- | --- |
| `//DEPS` | `groupId:artifactId:version` | Pin exact versions for installable scripts. Floating syntax (`+`, `LATEST`, `RELEASE`) exists but is a footgun — a transitively broken release silently breaks every user's install. Classifier and `@pom` (BOM) and `@fatjar` extensions supported. |
| `//JAVA` | `17` or `17+` | `N` requires exact version; `N+` is minimum. JBang auto-downloads matching JDK if missing. Pin to a specific LTS (e.g. `21`) when you actually test only one. We currently say `17+` which is permissive and fine. |
| `//SOURCES` | `Foo.java Bar.java` (or globs) | Relative to the main script. `//SOURCES` in included files is honored recursively. Only the main script's `//DEPS`, `//JAVA`, etc. are read. |
| `//FILES` | `path1 path2` (directories supported, recursive) | Bundles resources alongside the script. Read at runtime via classpath. Useful when distributing a script that needs a payload tree (relevant for B11.3 if we want to bundle `template/`). |
| `//JAVAC_OPTIONS` | e.g. `-parameters -Xlint:unchecked` | Compiler args. Picocli benefits from `-parameters` for richer reflection. |
| `//RUNTIME_OPTIONS` | e.g. `-Xmx2g` | JVM flags. Use sparingly; defaults are usually right. |

Sources: [JBang script-directives docs](https://www.jbang.dev/documentation/jbang/latest/script-directives.html), [JBang File Organization](https://www.jbang.dev/documentation/jbang/latest/organizing.html), [Baeldung JBang Guide](https://www.baeldung.com/jbang-guide).

## 3. Single-file vs multi-file

Stay single-file until you cross ~500–800 lines or have more than two or three picocli `@Command` subclasses. Triggers to split:

- A second subcommand (`AddRecipe`) that needs to share helpers with `Init`.
- Helper classes ≥ 50 lines that warrant their own file for readability.
- Wanting to unit-test helpers without invoking the whole CLI.

Standard multi-file picocli + JBang shape:

```java
//SOURCES Init.java AddRecipe.java VerifyGates.java

@Command(subcommands = { Init.class, AddRecipe.class, VerifyGates.class })
public class RecipeScaffold { ... }
```

Sibling files don't get their own `//DEPS` — the main script declares everything. `jbang app install` follows `//SOURCES` transitively, so the install UX stays a one-liner.

For our repo: this trigger fires at B11.3 (`add-recipe`). Until then, single-file.

## 4. Distribution

Two patterns, in order of polish:

**A. Direct GitHub URL — `jbang app install <blob-url>`.** Tag-pinned URLs are reproducible:

```
jbang app install https://github.com/fiftiesHousewife/recipescaffold/blob/v0.1/jbang/RecipeScaffold.java
```

`main` / `HEAD` URLs work but drift. Document the one-time `jbang trust add https://github.com/<org>/` line so first-run users don't see a surprise prompt.

**B. `jbang-catalog.json` at repo root** — better UX once you have more than one alias or want a short name. Schema confirmed from `jbangdev/jbang-catalog`:

```json
{
  "aliases": {
    "init": {
      "script-ref": "jbang/RecipeScaffold.java",
      "description": "Scaffold a new OpenRewrite recipe project"
    }
  }
}
```

Users then run `jbang init@fiftiesHousewife/recipescaffold init …`. The `script-ref` can be a relative file (resolved against the catalog's branch/tag), a Maven coordinate, a fatjar URL, or another catalog alias.

**Recommendation for v0.1:** ship the `jbang-catalog.json` from the start, even with one alias. Costs ~10 lines, gains a clean install UX and forward-compatibility for B11.3's `add-recipe` alias.

Sources: [JBang App Installation docs](https://www.jbang.dev/documentation/jbang/latest/app-installation.html), [jbangdev/jbang-catalog](https://github.com/jbangdev/jbang-catalog).

## 5. Testing

There is no great native testing story for JBang scripts. Real-world patterns:

- **Black-box CI** (what we already have via `.github/workflows/ci.yml`). Spawns the script, scaffolds a project, runs `./gradlew check smokeTest`. High coverage of *behavior*, low coverage of *helper logic*. Sufficient for v0.1.
- **Extract logic to plain classes in a `tooling/` Gradle module; keep the JBang file as a thin launcher.** This is the dominant pattern once a script grows past ~200 lines. The launcher does `//DEPS` + `main()` + delegate; tests live in a normal Gradle project.
- **`//SOURCES` + JUnit Console Launcher in JBang.** Works (`//DEPS org.junit.platform:junit-platform-console-launcher:1.x`), but rarely seen for production CLIs because debugging and refactoring tooling is poor.

For our repo: keep the black-box CI. Move helpers to a Gradle module when B11.3 doubles the line count. Recorded as a Parked item in `BACKLOG.md`.

## 6. Picocli + JBang interaction

- The picocli annotation processor (`picocli-codegen`) does **not** run under JBang's default `javac` invocation. JBang doesn't typically wire annotation processors. Picocli's reflection-based runtime works without it; you only lose codegen-based help/version generation and GraalVM native-image config. For a scaffolder, this loss is negligible.
- `picocli-shell-jline3` (REPL/autocomplete) is overkill for a one-shot `init` CLI. Skip.
- `Callable<Integer>` on each subcommand + `System.exit(new CommandLine(...).execute(args))` from `main` is the right shape. We already do this.

Sources: [picocli](https://github.com/remkop/picocli), [Twilio JBang+picocli tutorial](https://www.twilio.com/en-us/blog/developers/tutorials/building-blocks/cli-app-java-jbang-picocli).

## 7. Pitfalls

- **Working-directory assumptions.** A scaffolder's natural target is `pwd`, not the script location. We use `Paths.get("").toAbsolutePath()` for cwd anchoring; `getClass().getResource(...)` for bundled classpath resources. Don't mix.
- **`System.exit` sprinkled through subcommand bodies.** Use `Callable<Integer>` returns and let picocli's exit-code mechanism handle it. We already do.
- **Wrong `//JAVA` pinning.** `//JAVA 25` would exclude users on JDK 21 LTS. `//JAVA 17+` is the most permissive useful floor; `//JAVA 21` is the right pin if we test only one.
- **JBang trust prompts.** First-run from a new GitHub URL prompts the user. Document `jbang trust add https://github.com/<org>/` in the README install snippet.
- **Floating dep versions.** Pin everything in installable scripts.
- **Hidden state in `~/.jbang/cache`.** During iterative development, `jbang cache clear` is the fix when behavior seems stale.
- **`//FILES` resource access.** When you bundle a tree, read it via `getClass().getResourceAsStream` (classpath), not `Files.walk` on a real path — the script's runtime location is `~/.jbang/cache/...`, not the source tree.

## 8. JBang + OpenRewrite ecosystem

- **[`maxandersen/rewrite-jbang`](https://github.com/maxandersen/rewrite-jbang)** — the canonical reference. JBang-distributed *runner* for OpenRewrite recipes (~97% of its source mirrors `rewrite-maven-plugin`). Single-file `rewrite.java`, installable via `jbang app install <blob-url>`, subcommands `discover` and `--recipes <id>`. Validates our single-file picocli pattern. Different scope from us: they run recipes, we scaffold the project that authors them.
- **[`openrewrite/rewrite` discussion #4265](https://github.com/openrewrite/rewrite/discussions/4265)** — confirms that standalone OpenRewrite without Maven/Gradle is genuinely hard (classpath setup, LST serialization is a Moderne differentiator and not open-source, visitor isolation assumes recipe lifecycles). The framework deliberately assumes integration with build tools. Implication for us: **irrelevant** — our scope is scaffolding a recipe-DEVELOPMENT project, which is exactly what JBang is good for.
- **[`jtama/openrewrite-refactoring-as-code`](https://github.com/jtama/openrewrite-refactoring-as-code)** — Maven-based demo of writing OpenRewrite recipes; not a JBang scaffolder. Mentioned only because GitHub search surfaced it.

## 9. Reference implementations worth re-reading

- **[`maxandersen/rewrite-jbang`](https://github.com/maxandersen/rewrite-jbang)** — single-file, mirrors a Maven plugin's CLI surface in JBang.
- **[`jbangdev/jbang-catalog`](https://github.com/jbangdev/jbang-catalog)** — official catalog, many small picocli scripts; canonical `jbang-catalog.json` shape.
- **[Apache Camel JBang](https://camel.apache.org/manual/camel-jbang.html)** — production-grade JBang CLI shipped by an Apache project. Worth studying for the "thin JBang launcher, real Maven module behind" pattern.
- **[`jython/jbang-catalog`](https://github.com/jython/jbang-catalog)** — community catalog example.
- **[picocli](https://github.com/remkop/picocli)** — `Callable<Integer>` skeletons that translate one-to-one to JBang.

## 10. Decisions for this repo

| Question | Decision | Rationale |
| --- | --- | --- |
| File/class name | `RecipeScaffold.java` / `RecipeScaffold` | Java convention; CLI binary name `recipescaffold` is independent. |
| `//JAVA` pin | Keep `17+` for now | We don't yet test on a specific JDK; permissive floor matches our user base. |
| `//DEPS` pin | Exact (`info.picocli:picocli:4.7.7`) | Already done; never go floating for installable scripts. |
| `//FILES` use | Defer to B11.3 | Until we ship a binary CLI, on-disk `template/` lookup is right. |
| `//SOURCES` split | Defer to B11.3 | Single-file is right for one subcommand. |
| `jbang-catalog.json` | Add when GitHub remote is created | One-alias catalog is ~10 lines and gives clean install UX. |
| Testing | Black-box CI only for now | Module extraction parked until B11.3 doubles complexity. |
| `picocli-codegen` | Skip | Reflection-based picocli is enough for an `init` CLI. |

Tracked as backlog items in [`BACKLOG.md`](./BACKLOG.md).
