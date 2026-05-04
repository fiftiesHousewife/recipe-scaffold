package __ROOT_PACKAGE__.smoketest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs {@code ./gradlew <tasks>} in a scaffolded smoke project. Pins the nested
 * Gradle's build JVM to a JDK 21 install: {@code JAVA_HOME} for the wrapper
 * launcher and {@code -Dorg.gradle.java.home} (via {@code GRADLE_OPTS}) for the
 * forked build process — gradle.properties is parsed too late to keep the
 * Kotlin DSL evaluator off JDK 25, which Gradle 8.14.3's bundled Kotlin can't
 * parse. Captures combined stdout/stderr to a per-cell log under
 * {@code build/smoke-tmp/<cell>/run.log} so failures surface with the actual
 * Gradle output, not just an exit code.
 */
final class GradleRunner {

    private static final long TIMEOUT_SECONDS = 600;

    private final Path projectDir;
    private final SmokeTestConfig config;

    GradleRunner(final Path projectDir, final SmokeTestConfig config) {
        this.projectDir = projectDir;
        this.config = config;
    }

    Result run(final String... gradleTasks) {
        final List<String> command = buildCommand(gradleTasks);
        final ProcessBuilder builder = new ProcessBuilder(command)
                .directory(projectDir.toFile())
                .redirectErrorStream(true);
        final String jdk21 = config.jdk21Home().toString();
        builder.environment().put("JAVA_HOME", jdk21);
        builder.environment().merge("GRADLE_OPTS",
                "-Dorg.gradle.java.home=" + jdk21,
                (existing, added) -> existing + " " + added);

        try {
            final Process process = builder.start();
            final byte[] output = process.getInputStream().readAllBytes();
            final boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException(
                        "Gradle process timed out after " + TIMEOUT_SECONDS + "s in " + projectDir);
            }
            final Path log = projectDir.resolve("run.log");
            Files.write(log, output);
            return new Result(process.exitValue(), new String(output, StandardCharsets.UTF_8), log);
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to launch gradlew in " + projectDir, e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted waiting for gradlew", e);
        }
    }

    private List<String> buildCommand(final String[] gradleTasks) {
        final List<String> command = new ArrayList<>();
        command.add(projectDir.resolve("gradlew").toString());
        command.add("--no-daemon");
        command.add("--stacktrace");
        for (final String task : gradleTasks) {
            command.add(task);
        }
        return command;
    }

    record Result(int exitCode, String output, Path logFile) {
        boolean succeeded() {
            return exitCode == 0;
        }
    }
}
