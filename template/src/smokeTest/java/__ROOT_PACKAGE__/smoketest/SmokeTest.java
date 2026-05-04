package __ROOT_PACKAGE__.smoketest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One JUnit test per cell in the smoke matrix: each top-level recipe in the
 * YAML, paired with the catalog axis (with/without toml) where the recipe
 * manages dependencies, or a single cell with pre-declared deps for the
 * {@code *NoDeps} variants. Each cell scaffolds a fresh /tmp project, runs
 * the recipe end-to-end via a nested Gradle process, and confirms the
 * post-rewrite Java compiles.
 *
 * <p><b>EDIT THIS FILE for your recipe.</b> Replace the single example cell
 * below with one cell per top-level recipe + axis combination you want to
 * gate releases on. Cells run in parallel (see
 * {@code src/smokeTest/resources/junit-platform.properties}); cap is 3 by
 * default.
 *
 * <p>Slow (~40s/cell with parallelism=3) — these are the pre-publish gate,
 * not part of {@code ./gradlew check}.
 */
@DisplayName("Pre-publish smoke tests: scaffold a project, run the recipe, compile the result")
class SmokeTest {

    private final SmokeTestConfig config = SmokeTestConfig.fromSystemProperties();

    static Stream<Arguments> matrix() {
        return Stream.of(
                cell(new SmokeVariant("ExampleRecipe + no toml",
                        "{{rootPackage}}.ExampleRecipe",
                        SmokeVariant.CatalogMode.WITHOUT_TOML,
                        true,
                        List.of(Fixture.EXAMPLE))));
    }

    private static Arguments cell(final SmokeVariant variant) {
        return Arguments.of(variant.displayName(), variant);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("matrix")
    void variantRewritesAndCompiles(final String displayName,
                                    final SmokeVariant variant,
                                    @TempDir final Path tempDir) {
        final Path projectDir = tempDir.resolve(variant.safeDirName());
        new SmokeProject(projectDir, variant, config).scaffold();

        final GradleRunner runner = new GradleRunner(projectDir, config);
        final GradleRunner.Result rewriteRun = runner.run("rewriteRun");
        assertThat(rewriteRun.succeeded())
                .as("rewriteRun should succeed for variant %s\nLog: %s\n%s",
                        displayName, rewriteRun.logFile(), rewriteRun.output())
                .isTrue();

        final GradleRunner.Result compileJava = runner.run("compileJava");
        assertThat(compileJava.succeeded())
                .as("compileJava should succeed after rewrite for variant %s\nLog: %s\n%s",
                        displayName, compileJava.logFile(), compileJava.output())
                .isTrue();
    }
}
