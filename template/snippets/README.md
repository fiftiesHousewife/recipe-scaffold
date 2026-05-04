# Snippets — recipe-skeleton fragments

Source-of-truth fragments for the `add-recipe` JBang subcommand. They get copied verbatim into every scaffolded project so `recipescaffold add-recipe MyRecipe` works the same in this repo's CI and in users' projects.

## Placeholder dialect

These templates use a deliberately small placeholder set, distinct from the project-wide `init`-time placeholders documented in the repo CLAUDE.md:

| Placeholder | Meaning |
| --- | --- |
| `{{package}}` | Java package the new recipe (or its test) lives in. Default: `<rootPackage>.recipes`. |
| `{{recipeName}}` | Java class name (PascalCase, e.g. `RemoveStaleSuppression`) |
| `{{recipeDisplayName}}` | Short human-readable name returned by `getDisplayName()` |
| `{{recipeDescription}}` | One-sentence description returned by `getDescription()` |
| `{{recipeId}}` | OpenRewrite recipe identifier. For YAML compositions: `<rootPackage>.<recipeName>` (root namespace). For Java/scanning: `<package>.<recipeName>`. |
| `{{recipeKebab}}` | kebab-case form of `{{recipeName}}` (e.g. `remove-stale-suppression`). Used for YAML manifest filenames. |

`add-recipe` substitutes these via plain string replace. The repo's `init`-time substitutor explicitly skips `snippets/` so these `{{…}}` markers survive scaffolding intact.

## Files

- `recipe-class-java.template` — plain `JavaIsoVisitor` recipe with a TODO body.
- `recipe-class-scanning.template` — `ScanningRecipe<Acc>` two-pass skeleton (`getInitialValue` / `getScanner` / `getVisitor` + nested `Acc`).
- `recipe-class-refaster.template` — Refaster outer-holder class with one nested `@RecipeDescriptor` template-pair. The `rewrite-templating` annotation processor generates a sibling `<recipeName>Recipes` aggregate at compile time.
- `yaml-composition-block.template` — `META-INF/rewrite/<kebab>.yml` composed-recipe manifest.
- `recipe-test.template` — `RewriteTest` skeleton for Java/scanning recipes (asserts the no-op default leaves source unchanged).
- `recipe-test-yaml.template` — `RewriteTest` skeleton for YAML compositions; uses `Environment.builder().scanRuntimeClasspath().build().activateRecipes(...)` to load the manifest.
- `recipe-test-refaster.template` — `RewriteTest` skeleton for Refaster recipes; instantiates the *generated* `<recipeName>Recipes` (plural) and runs a before/after pair that the default template body satisfies.

Future additions queued in the repo's BACKLOG: `recipe-method-test.template`.
