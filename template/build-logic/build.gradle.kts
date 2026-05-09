plugins {
    `kotlin-dsl`
}

// Plugin classpath for the convention scripts under src/main/kotlin/. Versions
// are pulled from gradle/libs.versions.toml at the parent level so the plugin
// versions used here track the same catalog the recipes consume at runtime.
//
// build-logic's own build.gradle.kts does not get the typed `libs` accessor
// (it's generated for the build's *consumers*, and build-logic is its own
// build). The programmatic API below is the documented workaround.
val libs = the<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")
fun ver(alias: String) = libs.findVersion(alias)
        .orElseThrow { IllegalStateException("missing version: $alias") }
        .requiredVersion

dependencies {
    implementation("org.openrewrite:plugin:${ver("openrewrite")}")
    implementation("com.github.ben-manes:gradle-versions-plugin:${ver("versions")}")
    implementation("com.vanniktech:gradle-maven-publish-plugin:${ver("maven-publish")}")
    implementation("com.github.spotbugs.snom:spotbugs-gradle-plugin:${ver("spotbugs-plugin")}")
}
