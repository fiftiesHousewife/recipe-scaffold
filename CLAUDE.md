# Claude Code session notes — recipe-scaffold

Vendor-neutral guidance — repo layout, placeholder dialects, conventions, how to run the scaffolder, the TestKit harness — lives in [`AGENTS.md`](./AGENTS.md). Read that first; the rest of this file is Claude-Code-specific.

## Read first

- [`AGENTS.md`](./AGENTS.md) — canonical project guidance.
- [`README.md`](./README.md) — user-facing: install paths, subcommand reference, command cheatsheet.
- [`BACKLOG.md`](./BACKLOG.md) — Shipped / Queued / Active / Parked.
- [`CHANGELOG.md`](./CHANGELOG.md) — release log.

## Skills available in this session

The four recipe-authoring skills (`new-gradle-project`, `new-recipe`, `recipe-testing`, `smoke-test`) plus the ten clean-code skills. The clean-code skills aren't enforced by build tools here (the template strips the cleancode plugin) — apply them on judgment, not because tooling demands it.

## Notes on collaboration

- Project-wide rules (no emojis, package-private helpers, `template/` over new files, etc.) live in [`AGENTS.md`](./AGENTS.md) — apply silently, no need to acknowledge in chat.
- Don't duplicate skill content into responses or commit messages — invoke the skill and let the user see the same content.
- New files in `template/` propagate to every future scaffold; weigh that before adding rather than editing.
