package recipescaffold;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

// In-repo TestKit harness. Mirrors the GitHub Actions bash- and JBang-
// scaffold jobs, with two differences that make it sandbox-friendly:
//   1. -g <tmpHome> redirects the Gradle user home so we don't pollute
//      ~/.gradle (and so a sandbox that blocks ~/.gradle writes still
//      works).
//   2. -Dmaven.repo.local=<tmpM2> redirects mavenLocal so the scaffolded
//      project's publishMavenPublicationToMavenLocal step (which
//      smokeTest depends on) lands somewhere we can write.
//
// The harness drives Init / AddRecipe directly as Java methods; the
// JBang form is exercised by the GitHub Actions jbang-scaffold job.
// Pattern: Initializr's ProjectGeneratorTester + Maven Archetype's
// archetype:integration-test, ported to Gradle TestKit.
class ScaffoldHarnessTest {

    @Test
    void initThenAddRecipeForEveryTypeBuildsGreen(
            @TempDir Path projectDir,
            @TempDir Path mavenLocal) throws Exception {

        recipescaffold.RecipeScaffold.Init init = new recipescaffold.RecipeScaffold.Init();
        setField(init, "group", "io.github.acme");
        setField(init, "artifact", "acme-rewrite-recipes");
        setField(init, "rootPackage", "io.github.acme");
        setField(init, "initialVersion", "0.1");
        setField(init, "recipeName", "Acme Recipes");
        setField(init, "recipeDescription", "Harness scaffold");
        setField(init, "githubOrg", "acme");
        setField(init, "githubRepo", "acme-rewrite-recipes");
        setField(init, "authorId", "acmebot");
        setField(init, "authorName", "Acme Bot");
        setField(init, "authorEmail", "bot@acme.example");
        setField(init, "javaTargetMain", "17");
        setField(init, "javaTargetTests", "25");
        setField(init, "rewritePluginVersion", "7.30.0");
        setField(init, "outputDir", projectDir);
        setField(init, "force", true);

        assertThat(init.call()).isZero();

        addRecipe(projectDir, "SmokeJavaRecipe", "java", "block");
        addRecipe(projectDir, "SmokeScanRecipe", "scanning", "block");
        addRecipe(projectDir, "SmokeYamlRecipe", "yaml", "block");
        addRecipe(projectDir, "SmokeRefasterRecipe", "refaster", "block");
        addRecipe(projectDir, "SmokeMethodTestRecipe", "java", "method");

        // Inherit the parent's ~/.gradle so plugins and deps come from the
        // already-warm cache. Forcing -g <tmp> would mean a cold daemon that
        // re-downloads everything, which breaks under sandboxed networking
        // because the proxy does not extend to deeply-nested Gradle JVMs.
        // -Dmaven.repo.local stays redirected so we don't pollute ~/.m2 with
        // the scaffolded project's publishMavenPublicationToMavenLocal output.
        // Set HARNESS_OFFLINE=1 in env to repro CI-style failures without DNS:
        // the inner Gradle then refuses network and surfaces actual build errors
        // rather than UnknownHostException. Default is online so CI exercises
        // a real cold build path.
        List<String> args = new java.util.ArrayList<>();
        args.add("-Dmaven.repo.local=" + mavenLocal);
        // In-process Kotlin compile avoids the daemon-spawn permission issue
        // some sandboxes hit when the daemon tries to write its temp file
        // under ~/Library or /root.
        args.add("-Dkotlin.compiler.execution.strategy=in-process");
        if ("1".equals(System.getenv("HARNESS_OFFLINE"))) {
            args.add("--offline");
        }
        args.add("--stacktrace");
        args.add("check");

        BuildResult result = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withArguments(args)
                .forwardOutput()
                .build();

        assertThat(Objects.requireNonNull(result.task(":check")).getOutcome())
                .as("scaffolded project's check task")
                .isIn(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE);
    }

    private static void addRecipe(Path projectDir, String name, String type, String testStyle) throws Exception {
        recipescaffold.RecipeScaffold.AddRecipe add = new recipescaffold.RecipeScaffold.AddRecipe();
        setField(add, "name", name);
        setField(add, "type", type);
        setField(add, "testStyle", testStyle);
        recipescaffold.RecipeScaffold.ProjectDirectoryMixin mixin = new recipescaffold.RecipeScaffold.ProjectDirectoryMixin();
        setField(mixin, "projectDir", projectDir);
        setField(add, "projectDirectory", mixin);
        assertThat(add.call())
                .as("add-recipe %s --type=%s --test-style=%s", name, type, testStyle)
                .isZero();
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        // Init / AddRecipe expose their picocli-bound state as
        // package-private fields; the harness replays the parsed-args
        // form so we don't need to round-trip through CommandLine.
        Field f = findField(target.getClass(), name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Field findField(Class<?> cls, String name) throws NoSuchFieldException {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getName().equals(name)) {
                    return f;
                }
            }
        }
        throw new NoSuchFieldException(name + " on " + cls);
    }

    @SuppressWarnings("unused")
    private static List<String> describe(BuildResult r) {
        return r.getTasks().stream().map(t -> t.getPath() + " " + t.getOutcome()).toList();
    }
}
