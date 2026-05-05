// Included build that produces the `recipe-library` convention plugin.
// Reuses the parent project's version catalog so plugin classpath versions
// stay aligned with the runtime versions used by the recipes themselves.
dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
