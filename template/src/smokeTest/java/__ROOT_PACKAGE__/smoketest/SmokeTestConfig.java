package __ROOT_PACKAGE__.smoketest;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Captures the system properties the smokeTest Gradle task injects so the test
 * code doesn't reach into globals. Throws fast if any property is missing —
 * those are wiring bugs in build.gradle.kts, not user-facing failures.
 */
record SmokeTestConfig(String projectGroup,
                       String projectVersion,
                       String rewritePluginVersion,
                       Path projectRoot,
                       Path buildDir,
                       Path jdk21Home) {

    static SmokeTestConfig fromSystemProperties() {
        return new SmokeTestConfig(
                require("smokeTest.projectGroup"),
                require("smokeTest.projectVersion"),
                require("smokeTest.rewritePluginVersion"),
                Path.of(require("smokeTest.projectRoot")),
                Path.of(require("smokeTest.buildDir")),
                Path.of(require("smokeTest.jdk21Home")));
    }

    private static String require(final String key) {
        return Objects.requireNonNull(System.getProperty(key),
                () -> "Missing required system property: " + key + " — wired by build.gradle.kts");
    }
}
