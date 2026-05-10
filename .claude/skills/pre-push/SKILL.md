---
name: pre-push
description: Run the local equivalents of every CI job before pushing to recipe-scaffold. Catches shellcheck SC2086 in workflow yaml, harness regressions from refactors that shifted reflection paths, and bash-scaffold breakage — three classes of failure that have each cost a CI cycle. Applies whenever you are about to `git push` a commit that touched `jbang/RecipeScaffold.java`, `template/`, `tests/ci-smoke.sh`, `src/test/java/`, `.github/workflows/`, `gradle/libs.versions.toml`, or `template/build-logic/`.
---

# Pre-push checklist for recipe-scaffold

Run all three checks. Push only on green.

## 1. Lint workflows the way CI does

```bash
PATH=$TMPDIR:$PATH actionlint .github/workflows/*.yml
```

`actionlint` alone is silent on `SC2086` (unquoted `${GITHUB_WORKSPACE}` etc.); CI runs it with `shellcheck` on PATH and surfaces every shell-injection-class warning. Install both into `$TMPDIR` first if missing:

```bash
[ -x $TMPDIR/actionlint ] || {
  curl -fsSL -o $TMPDIR/al.tgz "https://github.com/rhysd/actionlint/releases/download/v1.7.7/actionlint_1.7.7_$(uname -s | tr A-Z a-z)_$(uname -m | sed 's/x86_64/amd64/;s/arm64/arm64/').tar.gz"
  tar -xzf $TMPDIR/al.tgz -C $TMPDIR actionlint && chmod +x $TMPDIR/actionlint
}
[ -x $TMPDIR/shellcheck ] || {
  curl -fsSL -o $TMPDIR/sc.tar.xz "https://github.com/koalaman/shellcheck/releases/download/v0.10.0/shellcheck-v0.10.0.$(uname -s | tr A-Z a-z).$(uname -m).tar.xz"
  tar -xJf $TMPDIR/sc.tar.xz -C $TMPDIR
  mv $TMPDIR/shellcheck-v0.10.0/shellcheck $TMPDIR/shellcheck && chmod +x $TMPDIR/shellcheck
}
```

## 2. Run the harness

```bash
./gradlew test
```

This is the in-repo equivalent of CI's `harness` job. `RecipeScaffoldUnitTest` exercises the pure-function helpers; `ScaffoldHarnessTest` scaffolds a full project into a tmpdir and runs `./gradlew check` against it via TestKit. Catches:

- broken `setField(...)` reflection after `@Mixin`/field renames,
- regressions in `template/build-logic/` (e.g. missing `compileOnly` deps for the Refaster annotation processor),
- YAML manifest changes that break `Environment.builder().load(...)`.

If the inner Gradle's network is blocked (e.g. inside a sandbox) and the cache is warm, fall back to `HARNESS_OFFLINE=1 ./gradlew test --rerun-tasks` to surface real failures instead of `UnknownHostException`. The harness already passes `-Dkotlin.compiler.execution.strategy=in-process` to the inner runner.

## 3. Run the bash scaffold path

```bash
./tests/ci-smoke.sh /tmp/recipe-template-prepush
```

Mirrors CI's `bash-scaffold` job up to the placeholder-residual check; the trailing `./gradlew check smokeTest` exercises the same gate chain CI does. Skip if you only touched test code or top-level docs.

## Order and bail-out

Run cheap-to-expensive: actionlint, then `./gradlew test`, then `tests/ci-smoke.sh`. If anything fails, fix locally and re-run that step before continuing — do not push partial fixes hoping CI catches the rest.

## What does NOT repro locally

- The `jbang-scaffold` job's network-dependent dep resolution can cold-start in CI but not in a sandbox without DNS for forked daemons. CI is authoritative for that path.
- The full release flow (`publishAndReleaseToMavenCentral`) only runs from `release.yml` on tag push; never run it from a branch.

## When to skip

If the diff is `README.md`/`AGENTS.md`/`BACKLOG.md`/`CHANGELOG.md` only and no other file changed, you can skip steps 2 and 3 — but still run step 1 if any file under `.github/workflows/` touched.