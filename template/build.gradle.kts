// Project identity. The reusable build (toolchain, gates, integration and
// smoke tests, publishing scaffolding) lives in the `recipe-library`
// convention plugin under build-logic/. Edit gates there, not here.
plugins {
    id("recipe-library")
}

group = "{{group}}"
version = "{{initialVersion}}"

// Override javaTargetMain / javaTargetTests by editing gradle.properties:
//   recipeLibrary.javaTargetMain=17
//   recipeLibrary.javaTargetTests=25
// Defaults match the values picked at scaffold time.

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    coordinates(group.toString(), "{{artifact}}", version.toString())

    pom {
        name.set("{{recipeName}}")
        description.set("{{recipeDescription}}")
        url.set("https://github.com/{{githubOrg}}/{{githubRepo}}")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("{{authorId}}")
                name.set("{{authorName}}")
                email.set("{{authorEmail}}")
            }
        }
        scm {
            connection.set("scm:git:git://github.com/{{githubOrg}}/{{githubRepo}}.git")
            developerConnection.set("scm:git:ssh://github.com/{{githubOrg}}/{{githubRepo}}.git")
            url.set("https://github.com/{{githubOrg}}/{{githubRepo}}")
        }
    }
}
