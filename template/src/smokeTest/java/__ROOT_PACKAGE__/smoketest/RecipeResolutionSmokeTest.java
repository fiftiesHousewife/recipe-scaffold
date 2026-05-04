package __ROOT_PACKAGE__.smoketest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fastest possible end-to-end check: scaffold a minimal Gradle project, list
 * the {@code rewrite} configuration, confirm the recipe coordinate resolves
 * from {@code mavenLocal()}. No {@code rewriteRun}, no {@code compileJava},
 * no Java fixtures. ~5 s instead of 15–35 s per full cell — isolates POM /
 * Gradle module metadata failures (broken transitive deps, wrong group
 * coordinates, missing classifier) from recipe-execution failures.
 */
@DisplayName("Pre-publish smoke test: recipe coordinate resolves from mavenLocal")
class RecipeResolutionSmokeTest {

    private static final String WRAPPER_PROPERTIES_RESOURCE = "/gradle-wrapper.properties";

    private final SmokeTestConfig config = SmokeTestConfig.fromSystemProperties();

    @Test
    @DisplayName("rewrite configuration resolves the published recipe artifact")
    void recipeArtifactResolves(@TempDir final Path tempDir) throws IOException {
        final Path projectDir = tempDir.resolve("resolution");
        scaffoldMinimalProject(projectDir);

        final GradleRunner.Result result = new GradleRunner(projectDir, config)
                .run("dependencies", "--configuration", "rewrite");

        assertThat(result.succeeded())
                .as("dependencies --configuration rewrite should succeed\nLog: %s\n%s",
                        result.logFile(), result.output())
                .isTrue();

        final String coordinate = config.projectGroup() + ":{{artifact}}:"
                + config.projectVersion();
        assertThat(result.output())
                .as("rewrite configuration should list the recipe coordinate %s\n%s",
                        coordinate, result.output())
                .contains(coordinate);
    }

    private void scaffoldMinimalProject(final Path projectDir) {
        try {
            Files.createDirectories(projectDir);
            copyWrapper(projectDir);
            Files.writeString(projectDir.resolve("settings.gradle.kts"),
                    "rootProject.name = \"resolution\"\n");
            Files.writeString(projectDir.resolve("build.gradle.kts"), """
                    plugins {
                        id("org.openrewrite.rewrite") version "%s"
                    }

                    repositories {
                        mavenLocal()
                        mavenCentral()
                    }

                    dependencies {
                        rewrite("%s:{{artifact}}:%s")
                    }
                    """.formatted(
                    config.rewritePluginVersion(),
                    config.projectGroup(),
                    config.projectVersion()));
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to scaffold resolution project at " + projectDir, e);
        }
    }

    private void copyWrapper(final Path targetDir) throws IOException {
        Files.createDirectories(targetDir.resolve("gradle/wrapper"));
        copyFromProject("gradle/wrapper/gradle-wrapper.jar",
                targetDir.resolve("gradle/wrapper/gradle-wrapper.jar"));
        copyFromProject("gradlew", targetDir.resolve("gradlew"));
        copyFromProject("gradlew.bat", targetDir.resolve("gradlew.bat"));
        targetDir.resolve("gradlew").toFile().setExecutable(true);

        try (final var in = RecipeResolutionSmokeTest.class.getResourceAsStream(WRAPPER_PROPERTIES_RESOURCE)) {
            if (in == null) {
                throw new IOException("Missing classpath resource " + WRAPPER_PROPERTIES_RESOURCE);
            }
            Files.copy(in, targetDir.resolve("gradle/wrapper/gradle-wrapper.properties"),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void copyFromProject(final String relSource, final Path target) throws IOException {
        Files.copy(config.projectRoot().resolve(relSource), target,
                StandardCopyOption.REPLACE_EXISTING);
    }
}
