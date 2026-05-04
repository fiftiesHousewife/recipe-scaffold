# Snippets — recipe-skeleton fragments

Source-of-truth fragments for the `add-recipe` JBang subcommand. They get copied verbatim into every scaffolded project so `recipescaffold add-recipe MyRecipe` works the same in this repo's CI and in users' projects.

## Placeholder dialect

These templates use a deliberately small placeholder set, distinct from the project-wide `init`-time placeholders documented in the repo CLAUDE.md:

| Placeholder | Meaning |
| --- | --- |
| `{{package}}` | Java package the new recipe lives in (recipes go at `<package>.recipes`) |
| `{{recipeName}}` | Java class name (PascalCase, e.g. `RemoveStaleSuppression`) |
| `{{recipeDisplayName}}` | Short human-readable name returned by `getDisplayName()` |
| `{{recipeDescription}}` | One-sentence description returned by `getDescription()` |

`add-recipe` substitutes these via plain string replace. The repo's `init`-time substitutor explicitly skips `snippets/` so these `{{…}}` markers survive scaffolding intact.

## Files

- `recipe-class-java.template` — plain `JavaIsoVisitor` recipe with a TODO body.
- `recipe-test.template` — `RewriteTest` skeleton that asserts the no-op default leaves source unchanged.

Future additions queued in the repo's BACKLOG: `recipe-class-scanning.template`, `recipe-class-toml.template`, `yaml-composition-block.template`, `recipe-method-test.template`.
