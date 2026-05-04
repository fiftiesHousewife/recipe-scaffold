package __ROOT_PACKAGE__.smoketest;

import java.util.List;

/**
 * One row of the §2 smoke matrix: a top-level recipe id, the catalog axis
 * (with/without/n-a), the fixtures the recipe should transform, and whether
 * the variant manages dependencies (so the throwaway project knows whether
 * to pre-declare Lombok / SLF4J / log4j2 itself).
 */
record SmokeVariant(String displayName,
                    String recipeId,
                    CatalogMode catalogMode,
                    boolean managesDependencies,
                    List<Fixture> fixtures) {

    enum CatalogMode {
        WITHOUT_TOML,
        WITH_EMPTY_TOML,
        NOT_APPLICABLE
    }

    String safeDirName() {
        return displayName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
