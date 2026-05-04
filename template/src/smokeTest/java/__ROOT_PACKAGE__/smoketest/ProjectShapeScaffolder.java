package __ROOT_PACKAGE__.smoketest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Writes a multi-module / build-logic-include / composite-build smoke project
 * to a target directory. Branches on {@link ProjectShapeVariant#topology()}
 * and {@link ProjectShapeVariant#dsl()}. The single-module sibling lives in
 * {@link SmokeProject}.
 *
 * <p><b>EDIT THIS FILE for your recipe.</b> {@code GREETING_BODY} is a
 * {@code System.out.println} fixture and the inline Gradle build templates
 * declare Lombok + SLF4J + log4j2 so the post-rewrite Java compiles after the
 * SLF4J migration recipe this template was extracted from. Replace the
 * fixture body with one your recipe transforms, and the dep block with the
 * deps your recipe assumes (or remove it if your recipe manages its own).
 */
final class ProjectShapeScaffolder {

    private static final String WRAPPER_PROPERTIES_RESOURCE = "/gradle-wrapper.properties";

    private static final String GREETING_BODY = """
            package com.example;

            public class Greeting {
                public void say(String name) {
                    System.out.println("Hello, " + name);
                }
            }
            """;

    private static final String HELPER_BODY = """
            package com.example;

            public class Helper {
                public void log(String msg) {
                    System.out.println("helper: " + msg);
                }
            }
            """;

    private final Path projectDir;
    private final ProjectShapeVariant variant;
    private final SmokeTestConfig config;

    ProjectShapeScaffolder(final Path projectDir,
                           final ProjectShapeVariant variant,
                           final SmokeTestConfig config) {
        this.projectDir = projectDir;
        this.variant = variant;
        this.config = config;
    }

    void scaffold() {
        try {
            Files.createDirectories(projectDir);
            copyWrapper(projectDir);
            switch (variant.topology()) {
                case MULTI_MODULE -> scaffoldMultiModule();
                case BUILD_LOGIC_INCLUDE -> scaffoldBuildLogicInclude();
                case COMPOSITE_INCLUDE_BUILD -> scaffoldCompositeIncludeBuild();
                case RELEASE_SHAPED_CONSUMER -> scaffoldReleaseShapedConsumer();
            }
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to scaffold project at " + projectDir, e);
        }
    }

    private void scaffoldMultiModule() throws IOException {
        writeSettings(projectDir, "smoke-multi", "include(\"app\", \"lib\")", "include 'app', 'lib'");
        writeRootBuildWithSubprojectJavaPlugin();
        writeEmptyCatalog(projectDir);
        writeSubprojectBuild(projectDir.resolve("app"));
        writeSubprojectBuild(projectDir.resolve("lib"));
        writeJava(projectDir.resolve("app"), "Greeting.java", GREETING_BODY);
        writeJava(projectDir.resolve("lib"), "Helper.java", HELPER_BODY);
    }

    private void scaffoldBuildLogicInclude() throws IOException {
        writeSettings(projectDir, "smoke-buildlogic",
                "include(\"app\", \"build-logic\")",
                "include 'app', 'build-logic'");
        writeRootBuildWithSubprojectJavaPlugin();
        writeEmptyCatalog(projectDir);
        writeSubprojectBuild(projectDir.resolve("app"));
        writeSubprojectBuild(projectDir.resolve("build-logic"));
        writeJava(projectDir.resolve("app"), "Greeting.java", GREETING_BODY);
        writeJava(projectDir.resolve("build-logic"), "Helper.java", HELPER_BODY);
    }

    private void scaffoldReleaseShapedConsumer() throws IOException {
        if (variant.dsl() != ProjectShapeVariant.Dsl.KOTLIN) {
            throw new UnsupportedOperationException("RELEASE_SHAPED_CONSUMER only implemented for Kotlin DSL");
        }
        Files.writeString(projectDir.resolve("settings.gradle.kts"),
                "rootProject.name = \"release-shaped-consumer\"\n");
        Files.writeString(projectDir.resolve("build.gradle.kts"), """
                plugins {
                    java
                    id("org.openrewrite.rewrite") version "%s"
                    id("com.github.ben-manes.versions") version "0.53.0"
                }

                repositories {
                    mavenLocal()
                    mavenCentral()
                }

                dependencies {
                    rewrite("%s:{{artifact}}:%s")

                    compileOnly("org.projectlombok:lombok:1.18.44")
                    annotationProcessor("org.projectlombok:lombok:1.18.44")
                }

                rewrite {
                    activeRecipe("%s")
                }
                """.formatted(
                config.rewritePluginVersion(),
                config.projectGroup(),
                config.projectVersion(),
                variant.recipeId()));
        writeJava(projectDir, "foo/Foo.java", """
                package com.example.foo;

                public class Foo {
                    public void greet(String name) {
                        System.out.println("foo: " + name);
                    }
                }
                """);
        writeJava(projectDir, "bar/Bar.java", """
                package com.example.bar;

                public class Bar {
                    public void announce(String topic) {
                        System.out.println("bar: " + topic);
                    }
                }
                """);
    }

    private void scaffoldCompositeIncludeBuild() throws IOException {
        writeSettings(projectDir, "smoke-composite",
                "includeBuild(\"build-logic\")",
                "includeBuild 'build-logic'");
        writeStandaloneRootBuild(projectDir);
        writeEmptyCatalog(projectDir);
        writeJava(projectDir, "Greeting.java", GREETING_BODY);

        final Path included = projectDir.resolve("build-logic");
        Files.createDirectories(included);
        copyWrapper(included);
        writeSettings(included, "build-logic", "", "");
        writeStandaloneRootBuild(included);
        writeEmptyCatalog(included);
        writeJava(included, "Helper.java", HELPER_BODY);
    }

    private void writeSettings(final Path dir,
                               final String rootName,
                               final String kotlinIncludes,
                               final String groovyIncludes) throws IOException {
        if (variant.dsl() == ProjectShapeVariant.Dsl.KOTLIN) {
            Files.writeString(dir.resolve("settings.gradle.kts"),
                    "rootProject.name = \"" + rootName + "\"\n"
                    + (kotlinIncludes.isEmpty() ? "" : kotlinIncludes + "\n"));
        } else {
            Files.writeString(dir.resolve("settings.gradle"),
                    "rootProject.name = '" + rootName + "'\n"
                    + (groovyIncludes.isEmpty() ? "" : groovyIncludes + "\n"));
        }
    }

    private void writeRootBuildWithSubprojectJavaPlugin() throws IOException {
        if (variant.dsl() == ProjectShapeVariant.Dsl.KOTLIN) {
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

                    rewrite {
                        activeRecipe("%s")
                    }

                    subprojects {
                        apply(plugin = "java")

                        repositories {
                            mavenCentral()
                        }
                    }
                    """.formatted(
                    config.rewritePluginVersion(),
                    config.projectGroup(),
                    config.projectVersion(),
                    variant.recipeId()));
        } else {
            Files.writeString(projectDir.resolve("build.gradle"), """
                    plugins {
                        id 'org.openrewrite.rewrite' version '%s'
                    }

                    repositories {
                        mavenLocal()
                        mavenCentral()
                    }

                    dependencies {
                        rewrite '%s:{{artifact}}:%s'
                    }

                    rewrite {
                        activeRecipe '%s'
                    }

                    subprojects {
                        apply plugin: 'java'

                        repositories {
                            mavenCentral()
                        }
                    }
                    """.formatted(
                    config.rewritePluginVersion(),
                    config.projectGroup(),
                    config.projectVersion(),
                    variant.recipeId()));
        }
    }

    private void writeStandaloneRootBuild(final Path dir) throws IOException {
        if (variant.dsl() == ProjectShapeVariant.Dsl.KOTLIN) {
            Files.writeString(dir.resolve("build.gradle.kts"), """
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
                    }

                    rewrite {
                        activeRecipe("%s")
                    }
                    """.formatted(
                    config.rewritePluginVersion(),
                    config.projectGroup(),
                    config.projectVersion(),
                    variant.recipeId()));
        } else {
            Files.writeString(dir.resolve("build.gradle"), """
                    plugins {
                        id 'java'
                        id 'org.openrewrite.rewrite' version '%s'
                    }

                    repositories {
                        mavenLocal()
                        mavenCentral()
                    }

                    dependencies {
                        rewrite '%s:{{artifact}}:%s'
                    }

                    rewrite {
                        activeRecipe '%s'
                    }
                    """.formatted(
                    config.rewritePluginVersion(),
                    config.projectGroup(),
                    config.projectVersion(),
                    variant.recipeId()));
        }
    }

    private void writeSubprojectBuild(final Path subprojectDir) throws IOException {
        Files.createDirectories(subprojectDir);
        if (variant.dsl() == ProjectShapeVariant.Dsl.KOTLIN) {
            Files.writeString(subprojectDir.resolve("build.gradle.kts"), "");
        } else {
            Files.writeString(subprojectDir.resolve("build.gradle"), "");
        }
    }

    private void writeJava(final Path moduleDir, final String relativePath, final String body) throws IOException {
        final Path target = moduleDir.resolve("src/main/java/com/example").resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, body);
    }

    private void writeEmptyCatalog(final Path dir) throws IOException {
        Files.createDirectories(dir.resolve("gradle"));
        Files.writeString(dir.resolve("gradle/libs.versions.toml"),
                "[versions]\n\n[libraries]\n");
    }

    private void copyWrapper(final Path targetDir) throws IOException {
        Files.createDirectories(targetDir.resolve("gradle/wrapper"));
        copyFromProject("gradle/wrapper/gradle-wrapper.jar",
                targetDir.resolve("gradle/wrapper/gradle-wrapper.jar"));
        copyFromProject("gradlew", targetDir.resolve("gradlew"));
        copyFromProject("gradlew.bat", targetDir.resolve("gradlew.bat"));
        targetDir.resolve("gradlew").toFile().setExecutable(true);

        try (final var in = ProjectShapeScaffolder.class.getResourceAsStream(WRAPPER_PROPERTIES_RESOURCE)) {
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
