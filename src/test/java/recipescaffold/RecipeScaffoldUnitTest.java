package recipescaffold;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RecipeScaffoldUnitTest {

    @ParameterizedTest
    @CsvSource({
            "Foo, foo",
            "FooBar, foo-bar",
            "RemoveStaleSuppression, remove-stale-suppression",
            "ABC, a-b-c",
            "X, x"
    })
    void kebabCase_lowercasesAndHyphenatesEveryUpperRunBoundary(String input, String expected) {
        assertThat(RecipeScaffold.kebabCase(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "Foo, Foo",
            "FooBar, Foo bar",
            "RemoveStaleSuppression, Remove stale suppression"
    })
    void humanise_inserts_space_before_internal_uppers_lowercase_only_those(String input, String expected) {
        assertThat(RecipeScaffold.humanise(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Foo", "FooBar", "F", "F1", "Foo123Bar"})
    void isPascalCase_acceptsValidIdentifiers(String name) {
        assertThat(RecipeScaffold.isPascalCase(name)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"foo", "fooBar", "1Foo", "Foo Bar", "Foo-Bar", "Foo_Bar", ""})
    void isPascalCase_rejectsInvalidIdentifiers(String name) {
        assertThat(RecipeScaffold.isPascalCase(name)).isFalse();
    }

    @Test
    void isPascalCase_rejectsNull() {
        assertThat(RecipeScaffold.isPascalCase(null)).isFalse();
    }

    @Test
    void applySubstitutions_replacesEveryKey() {
        Map<String, String> repl = new LinkedHashMap<>();
        repl.put("{{a}}", "X");
        repl.put("{{b}}", "Y");
        assertThat(RecipeScaffold.applySubstitutions("[{{a}}][{{b}}][{{a}}]", repl))
                .isEqualTo("[X][Y][X]");
    }

    @Test
    void applySubstitutions_preservesUnknownKeys() {
        Map<String, String> repl = Map.of("{{a}}", "X");
        assertThat(RecipeScaffold.applySubstitutions("{{a}} {{b}}", repl))
                .isEqualTo("X {{b}}");
    }

    @Test
    void applySubstitutions_appliesInOrder() {
        // Order matters when one replacement is a prefix of another's value.
        Map<String, String> repl = new LinkedHashMap<>();
        repl.put("X", "Y");
        repl.put("Y", "Z");
        assertThat(RecipeScaffold.applySubstitutions("X", repl)).isEqualTo("Z");
    }

    @Test
    void initBuildReplacements_includesAllInitTimePlaceholdersAndMarker() throws Exception {
        RecipeScaffold.Init init = new RecipeScaffold.Init();
        setField(init, "group", "io.github.acme");
        setField(init, "artifact", "acme-rewrite-recipes");
        setField(init, "rootPackage", "io.github.acme");
        setField(init, "initialVersion", "0.1");
        setField(init, "recipeName", "Acme Recipes");
        setField(init, "recipeDescription", "Cleanup recipes");
        setField(init, "githubOrg", "acme");
        setField(init, "githubRepo", "acme-rewrite-recipes");
        setField(init, "authorId", "acmebot");
        setField(init, "authorName", "Acme Bot");
        setField(init, "authorEmail", "bot@acme.example");
        setField(init, "javaTargetMain", "17");
        setField(init, "javaTargetTests", "25");
        setField(init, "rewritePluginVersion", "7.30.0");

        Map<String, String> repl = init.buildReplacements();

        assertThat(repl)
                .containsEntry("{{group}}", "io.github.acme")
                .containsEntry("{{artifact}}", "acme-rewrite-recipes")
                .containsEntry("{{rootPackage}}", "io.github.acme")
                .containsEntry("{{initialVersion}}", "0.1")
                .containsEntry("{{recipeName}}", "Acme Recipes")
                .containsEntry("{{recipeDescription}}", "Cleanup recipes")
                .containsEntry("{{githubOrg}}", "acme")
                .containsEntry("{{githubRepo}}", "acme-rewrite-recipes")
                .containsEntry("{{authorId}}", "acmebot")
                .containsEntry("{{authorName}}", "Acme Bot")
                .containsEntry("{{authorEmail}}", "bot@acme.example")
                .containsEntry("{{javaTargetMain}}", "17")
                .containsEntry("{{javaTargetTests}}", "25")
                .containsEntry("{{rewritePluginVersion}}", "7.30.0")
                .containsEntry(RecipeScaffold.MARKER_DIR, "io.github.acme");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "build.gradle.kts", "settings.gradle.kts", "libs.versions.toml",
            "Foo.java", "ci.yml", "README.md", "gradle.properties",
            "ci-smoke.sh", ".gitignore", ".editorconfig"
    })
    void isTextFile_acceptsKnownExtensionsAndNames(String name) {
        assertThat(RecipeScaffold.isTextFile(Path.of(name))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "gradle-wrapper.jar", "logo.png", "binary", "noext", "data.bin"
    })
    void isTextFile_rejectsBinariesAndExtensionlessFiles(String name) {
        assertThat(RecipeScaffold.isTextFile(Path.of(name))).isFalse();
    }

    @Test
    void isUnderSnippets_truthyOnlyWhenFirstSegmentIsSnippets() {
        Path root = Path.of("/proj");
        assertThat(RecipeScaffold.isUnderSnippets(root, root.resolve("snippets/recipe.template"))).isTrue();
        assertThat(RecipeScaffold.isUnderSnippets(root, root.resolve("snippets"))).isTrue();
        assertThat(RecipeScaffold.isUnderSnippets(root, root.resolve("src/main/java/Foo.java"))).isFalse();
        assertThat(RecipeScaffold.isUnderSnippets(root, root.resolve("src/snippets/inner"))).isFalse();
    }

    @Test
    void readDropfile_parsesQuotedAndUnquotedAndSkipsCommentsBlanks(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve(".recipescaffold.yml");
        Files.writeString(file, String.join("\n",
                "# leading comment",
                "",
                "recipescaffoldVersion: \"0.2.0\"",
                "  group:    io.example  ",
                "artifact: \"demo\"",
                "rootPackage: io.example.demo",
                "# trailing comment",
                "javaTargetMain: 17",
                "javaTargetTests: \"25\"",
                "malformed-no-colon",
                ":leadingColonIsIgnored",
                ""
        ), StandardCharsets.UTF_8);

        Map<String, String> drop = RecipeScaffold.readDropfile(file);

        assertThat(drop)
                .containsEntry("recipescaffoldVersion", "0.2.0")
                .containsEntry("group", "io.example")
                .containsEntry("artifact", "demo")
                .containsEntry("rootPackage", "io.example.demo")
                .containsEntry("javaTargetMain", "17")
                .containsEntry("javaTargetTests", "25")
                .doesNotContainKey("malformed-no-colon")
                .doesNotContainKey("");
    }

    @Test
    void addRecipe_rejectsUnknownType(@TempDir Path tmp) throws Exception {
        Path project = newSyntheticProject(tmp);
        ExecResult r = runScaffold(project, "add-recipe", "--name", "Foo", "--type", "made-up");
        assertThat(r.exitCode).isEqualTo(2);
        assertThat(r.stderr).contains("--type=made-up");
    }

    @Test
    void addRecipe_rejectsLowerCaseName(@TempDir Path tmp) throws Exception {
        Path project = newSyntheticProject(tmp);
        ExecResult r = runScaffold(project, "add-recipe", "--name", "fooBar");
        assertThat(r.exitCode).isEqualTo(2);
        assertThat(r.stderr).contains("PascalCase");
    }

    @Test
    void addRecipe_rejectsBadTestStyle(@TempDir Path tmp) throws Exception {
        Path project = newSyntheticProject(tmp);
        ExecResult r = runScaffold(project, "add-recipe", "--name", "Foo", "--test-style", "weird");
        assertThat(r.exitCode).isEqualTo(2);
        assertThat(r.stderr).contains("--test-style=weird");
    }

    @Test
    void addRecipe_rejectsMethodStyleWithYaml(@TempDir Path tmp) throws Exception {
        Path project = newSyntheticProject(tmp);
        ExecResult r = runScaffold(project, "add-recipe",
                "--name", "Foo", "--type", "yaml", "--test-style", "method");
        assertThat(r.exitCode).isEqualTo(2);
        assertThat(r.stderr).contains("--test-style=method");
        assertThat(r.stderr).contains("--type=yaml");
    }

    @Test
    void addRecipe_failsWhenDropfileMissingRootPackage(@TempDir Path tmp) throws Exception {
        Path project = newSyntheticProject(tmp);
        Files.writeString(project.resolve(".recipescaffold.yml"),
                "group: io.example\nartifact: demo\n", StandardCharsets.UTF_8);
        ExecResult r = runScaffold(project, "add-recipe", "--name", "Foo");
        assertThat(r.exitCode).isEqualTo(3);
        assertThat(r.stderr).contains("rootPackage");
    }

    @Test
    void addRecipe_writesJavaRecipeAndTest(@TempDir Path tmp) throws Exception {
        Path project = newSyntheticProject(tmp);
        ExecResult r = runScaffold(project, "add-recipe", "--name", "FooBar");
        assertThat(r.exitCode).isZero();
        Path main = project.resolve("src/main/java/io/example/demo/recipes/FooBar.java");
        Path test = project.resolve("src/test/java/io/example/demo/recipes/FooBarTest.java");
        assertThat(main).exists();
        assertThat(test).exists();
        String body = Files.readString(main, StandardCharsets.UTF_8);
        assertThat(body).contains("package io.example.demo.recipes;");
        assertThat(body).contains("public class FooBar");
    }

    @Test
    void addRecipe_yamlEmitsManifestUnderResources(@TempDir Path tmp) throws Exception {
        Path project = newSyntheticProject(tmp);
        ExecResult r = runScaffold(project, "add-recipe", "--name", "MyComp", "--type", "yaml");
        assertThat(r.exitCode).isZero();
        assertThat(project.resolve("src/main/resources/META-INF/rewrite/my-comp.yml")).exists();
        assertThat(project.resolve("src/test/java/io/example/demo/recipes/MyCompTest.java")).exists();
    }

    @Test
    void addRecipe_refusesOverwriteWithoutForce(@TempDir Path tmp) throws Exception {
        Path project = newSyntheticProject(tmp);
        assertThat(runScaffold(project, "add-recipe", "--name", "Foo").exitCode).isZero();
        ExecResult r = runScaffold(project, "add-recipe", "--name", "Foo");
        assertThat(r.exitCode).isEqualTo(2);
        assertThat(r.stderr).contains("refusing to overwrite");
    }

    @Test
    void addRecipe_skipsTestWhenRequested(@TempDir Path tmp) throws Exception {
        Path project = newSyntheticProject(tmp);
        assertThat(runScaffold(project, "add-recipe", "--name", "Foo", "--no-tests").exitCode).isZero();
        assertThat(project.resolve("src/main/java/io/example/demo/recipes/Foo.java")).exists();
        assertThat(project.resolve("src/test/java/io/example/demo/recipes/FooTest.java")).doesNotExist();
    }

    @Test
    void addRecipe_failsWhenSnippetsDirMissing(@TempDir Path tmp) throws Exception {
        Path project = newSyntheticProject(tmp);
        deleteRecursively(project.resolve("snippets"));
        ExecResult r = runScaffold(project, "add-recipe", "--name", "Foo");
        assertThat(r.exitCode).isEqualTo(3);
        assertThat(r.stderr).contains("snippets dir not found");
    }

    @Test
    void verifyGates_exits2WhenNoDropfile(@TempDir Path tmp) throws Exception {
        ExecResult r = runScaffold(tmp, "verify-gates", "--directory", tmp.toString());
        assertThat(r.exitCode).isEqualTo(2);
        assertThat(r.stderr).contains(".recipescaffold.yml not found");
    }

    @Test
    void upgradeSkills_exits2WhenNoDropfile(@TempDir Path tmp) throws Exception {
        ExecResult r = runScaffold(tmp, "upgrade-skills", "--directory", tmp.toString());
        assertThat(r.exitCode).isEqualTo(2);
        assertThat(r.stderr).contains(".recipescaffold.yml not found");
    }

    private static Path newSyntheticProject(Path tmp) throws Exception {
        Path project = tmp.resolve("synth");
        Files.createDirectories(project);
        copyTree(repoRoot().resolve("template/snippets"), project.resolve("snippets"));
        Files.writeString(project.resolve(".recipescaffold.yml"), String.join("\n",
                "recipescaffoldVersion: \"0.2.0\"",
                "group: \"io.example\"",
                "artifact: \"demo\"",
                "rootPackage: \"io.example.demo\"",
                "javaTargetMain: \"17\"",
                "javaTargetTests: \"25\"",
                ""
        ), StandardCharsets.UTF_8);
        return project;
    }

    private static Path repoRoot() {
        Path here = Path.of("").toAbsolutePath();
        for (Path p = here; p != null; p = p.getParent()) {
            if (Files.isDirectory(p.resolve("template/snippets"))) {
                return p;
            }
        }
        throw new IllegalStateException("could not locate repo root from " + here);
    }

    private static void copyTree(Path src, Path dst) throws Exception {
        Files.walk(src).forEach(p -> {
            try {
                Path target = dst.resolve(src.relativize(p));
                if (Files.isDirectory(p)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(p, target);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static void deleteRecursively(Path p) throws Exception {
        if (!Files.exists(p)) {
            return;
        }
        Files.walk(p)
                .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                .forEach(x -> {
                    try {
                        Files.delete(x);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private record ExecResult(int exitCode, String stdout, String stderr) {}

    private static ExecResult runScaffold(Path cwd, String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        String[] effective = needsDirectory(args) ? withDirectory(args, cwd) : args;
        PrintStream origOut = System.out;
        PrintStream origErr = System.err;
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
            int rc = new CommandLine(new RecipeScaffold()).execute(effective);
            return new ExecResult(rc, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
        } finally {
            System.setOut(origOut);
            System.setErr(origErr);
        }
    }

    private static boolean needsDirectory(String[] args) {
        for (String a : args) {
            if ("--directory".equals(a) || "-d".equals(a)) {
                return false;
            }
        }
        return args.length > 0 && (
                "add-recipe".equals(args[0]) || "verify-gates".equals(args[0]) || "upgrade-skills".equals(args[0]));
    }

    private static String[] withDirectory(String[] args, Path dir) {
        String[] out = new String[args.length + 2];
        out[0] = args[0];
        out[1] = "--directory";
        out[2] = dir.toString();
        System.arraycopy(args, 1, out, 3, args.length - 1);
        return out;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
