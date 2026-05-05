// Convention plugins (toolchain, gates, integrationTest, smokeTest, publishing
// boilerplate) live in build-logic/. Including it here makes its plugin id
// `recipe-library` available to build.gradle.kts.
pluginManagement {
    includeBuild("build-logic")
}

rootProject.name = "{{artifact}}"
