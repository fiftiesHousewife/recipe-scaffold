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
 * Drives the project-shape matrix end-to-end. One JUnit test per template:
 * scaffold a /tmp project of the chosen topology + DSL, invoke
 * {@code rewriteRun} in each {@link ProjectShapeVariant#rewriteRunSubdirs() subdir}
 * (one for non-composite shapes, two for {@code includeBuild} composites),
 * then {@code compileJava}.
 *
 * <p><b>EDIT THIS FILE for your recipe.</b> The shipped matrix has one cell
 * (multi-module Kotlin DSL) so a freshly scaffolded project's smokeTest
 * passes. Add cells per topology + DSL combination you want to gate releases
 * on. If your recipe expects a specific transformation marker in the output
 * (e.g. an annotation it added), add a post-rewrite assertion here — silent
 * no-op recipes will still pass {@code compileJava}.
 */
@DisplayName("Pre-publish smoke tests: project-shape matrix (multi-module / build-logic / composite)")
class ProjectShapeSmokeTest {

    private final SmokeTestConfig config = SmokeTestConfig.fromSystemProperties();

    static Stream<Arguments> matrix() {
        return Stream.of(
                cell(new ProjectShapeVariant(
                        "A — multi-module Kotlin DSL",
                        "A",
                        "{{rootPackage}}.ExampleRecipe",
                        ProjectShapeVariant.Topology.MULTI_MODULE,
                        ProjectShapeVariant.Dsl.KOTLIN,
                        List.of(""))));
    }

    private static Arguments cell(final ProjectShapeVariant variant) {
        return Arguments.of(variant.displayName(), variant);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("matrix")
    void shapeRewritesAndCompiles(final String displayName,
                                  final ProjectShapeVariant variant,
                                  @TempDir final Path tempDir) {
        final Path projectDir = tempDir.resolve(variant.safeDirName());
        new ProjectShapeScaffolder(projectDir, variant, config).scaffold();

        for (final String subdir : variant.rewriteRunSubdirs()) {
            final Path runDir = subdir.isEmpty() ? projectDir : projectDir.resolve(subdir);
            final GradleRunner.Result rewriteRun = new GradleRunner(runDir, config).run("rewriteRun");
            assertThat(rewriteRun.succeeded())
                    .as("rewriteRun should succeed for %s in %s\nLog: %s\n%s",
                            displayName, subdir.isEmpty() ? "<root>" : subdir,
                            rewriteRun.logFile(), rewriteRun.output())
                    .isTrue();
        }

        final GradleRunner.Result compileJava = new GradleRunner(projectDir, config).run("compileJava");
        assertThat(compileJava.succeeded())
                .as("compileJava should succeed after rewrite for %s\nLog: %s\n%s",
                        displayName, compileJava.logFile(), compileJava.output())
                .isTrue();
    }
}
