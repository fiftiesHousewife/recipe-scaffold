package __ROOT_PACKAGE__.smoketest;

import java.util.List;

/**
 * One row of the §2a project-shape matrix. Six templates total (A–F) covering
 * multi-module, build-logic-as-subproject, and composite-build topologies in
 * both Kotlin and Groovy DSL flavours. Captures the topology + DSL axes plus
 * the list of subdirectories where {@code rewriteRun} must be invoked
 * (composite builds need one invocation per included build).
 */
record ProjectShapeVariant(String displayName,
                           String templateId,
                           String recipeId,
                           Topology topology,
                           Dsl dsl,
                           List<String> rewriteRunSubdirs) {

    enum Topology {
        MULTI_MODULE,
        BUILD_LOGIC_INCLUDE,
        COMPOSITE_INCLUDE_BUILD,
        RELEASE_SHAPED_CONSUMER
    }

    enum Dsl {
        KOTLIN,
        GROOVY
    }

    String safeDirName() {
        return ("template-" + templateId + "-" + topology + "-" + dsl).toLowerCase()
                .replace('_', '-');
    }
}
