plugins {
    `kotlin-dsl`
}

// Plugin classpath for the convention scripts under src/main/kotlin/. Versions
// are pulled from gradle/libs.versions.toml at the parent level so the plugin
// versions used here track the same catalog the recipes consume at runtime.
dependencies {
    implementation("org.openrewrite:plugin:${libs.versions.openrewrite.get()}")
    implementation("com.github.ben-manes:gradle-versions-plugin:${libs.versions.versions.get()}")
    implementation("com.vanniktech:gradle-maven-publish-plugin:${libs.versions.maven.publish.get()}")
}
