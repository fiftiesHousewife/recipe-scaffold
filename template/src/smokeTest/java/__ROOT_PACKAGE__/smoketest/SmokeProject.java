package __ROOT_PACKAGE__.smoketest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Writes a fresh, self-contained Gradle project to a target directory: wrapper,
 * settings, build, fixtures, and (when the variant calls for it) an empty
 * version catalog. The project resolves the recipe from {@code mavenLocal()}
 * via coordinates, so {@code smokeTest} depends on {@code publishToMavenLocal}.
 */
final class SmokeProject {

    private static final String WRAPPER_PROPERTIES_RESOURCE = "/gradle-wrapper.properties";

    private final Path projectRoot;
    private final Path projectDir;
    private final SmokeVariant variant;
    private final SmokeTestConfig config;

    SmokeProject(final Path projectDir, final SmokeVariant variant, final SmokeTestConfig config) {
        this.projectDir = projectDir;
        this.projectRoot = config.projectRoot();
        this.variant = variant;
        this.config = config;
    }

    void scaffold() {
        try {
            Files.createDirectories(projectDir);
            copyWrapper();
            writeSettings();
            writeBuild();
            writeFixtures();
            writeCatalogIfRequested();
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to scaffold smoke project at " + projectDir, e);
        }
    }

    private void copyWrapper() throws IOException {
        Files.createDirectories(projectDir.resolve("gradle/wrapper"));
        copyFromProject("gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.jar");
        copyFromProject("gradlew", "gradlew");
        copyFromProject("gradlew.bat", "gradlew.bat");
        projectDir.resolve("gradlew").toFile().setExecutable(true);

        try (final var in = SmokeProject.class.getResourceAsStream(WRAPPER_PROPERTIES_RESOURCE)) {
            if (in == null) {
                throw new IOException("Missing classpath resource " + WRAPPER_PROPERTIES_RESOURCE);
            }
            Files.copy(in, projectDir.resolve("gradle/wrapper/gradle-wrapper.properties"),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void copyFromProject(final String relSource, final String relDest) throws IOException {
        Files.copy(projectRoot.resolve(relSource),
                projectDir.resolve(relDest),
                StandardCopyOption.REPLACE_EXISTING);
    }

    private void writeSettings() throws IOException {
        Files.writeString(projectDir.resolve("settings.gradle.kts"),
                "rootProject.name = \"smoke\"\n");
    }

    private void writeBuild() throws IOException {
        final StringBuilder deps = new StringBuilder();
        for (final String dep : fixturePrerequisiteDeps()) {
            deps.append("    implementation(\"").append(dep).append("\")\n");
        }
        if (!variant.managesDependencies()) {
            // EDIT FOR YOUR RECIPE'S DEPS — start
            // These are the runtime deps the post-rewrite compile needs. The shipped
            // block is the SLF4J/Lombok set the template was extracted from; replace
            // with the deps your recipe assumes when it does not manage them itself.
            deps.append("""
                        compileOnly("org.projectlombok:lombok:1.18.44")
                        annotationProcessor("org.projectlombok:lombok:1.18.44")
                        implementation("org.slf4j:slf4j-api:2.0.17")
                        runtimeOnly("org.apache.logging.log4j:log4j-core:2.25.4")
                        runtimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.25.4")
                    """);
            // EDIT FOR YOUR RECIPE'S DEPS — end
        }

        Files.writeString(projectDir.resolve("build.gradle.kts"), """
                plugins {
                    java
                    id("org.openrewrite.rewrite") version "%s"
                }

                repositories {
                    mavenLocal()
                    mavenCentral()
                }

                dependencies {
                    rewrite("%s:{{artifact}}:%s")
                %s}

                rewrite {
                    activeRecipe("%s")
                }
                """.formatted(
                config.rewritePluginVersion(),
                config.projectGroup(),
                config.projectVersion(),
                deps,
                variant.recipeId()));
    }

    private java.util.Set<String> fixturePrerequisiteDeps() {
        return variant.fixtures().stream()
                .flatMap(f -> f.prerequisiteImplementationDeps().stream())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private void writeFixtures() throws IOException {
        for (final Fixture fixture : variant.fixtures()) {
            final Path target = projectDir.resolve(fixture.relativePath());
            Files.createDirectories(target.getParent());
            Files.writeString(target, fixture.body());
        }
    }

    private void writeCatalogIfRequested() throws IOException {
        if (variant.catalogMode() != SmokeVariant.CatalogMode.WITH_EMPTY_TOML) {
            return;
        }
        Files.createDirectories(projectDir.resolve("gradle"));
        Files.writeString(projectDir.resolve("gradle/libs.versions.toml"),
                "[versions]\n\n[libraries]\n");
    }
}
