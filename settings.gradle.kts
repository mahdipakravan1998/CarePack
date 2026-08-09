import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    val useMavenLocal =
        providers
            .gradleProperty("carepackUseMavenLocal")
            .map(String::toBooleanStrict)
            .orElse(false)
            .get()

    repositories {
        if (useMavenLocal) {
            mavenLocal()
        }

        maven {
            name = "MyketMirror"
            url = uri("https://maven.myket.ir")
        }

        maven {
            name = "IranGradlePluginMirror"
            url =
                uri(
                    "https://archive.ito.gov.ir/gradle/maven-plugin",
                )
        }

        maven {
            name = "IranMavenCentralMirror"
            url =
                uri(
                    "https://archive.ito.gov.ir/gradle/maven-central",
                )
        }

        gradlePluginPortal()
        google()
        mavenCentral()
    }

    resolutionStrategy {
        eachPlugin {
            val pluginVersion =
                requested.version ?: return@eachPlugin

            when (requested.id.id) {
                "com.android.application",
                "com.android.library",
                "com.android.test",
                "com.android.dynamic-feature" -> {
                    useModule(
                        "com.android.tools.build:" +
                                "gradle:$pluginVersion",
                    )
                }
            }
        }
    }
}

val useMavenLocal =
    providers
        .gradleProperty("carepackUseMavenLocal")
        .map(String::toBooleanStrict)
        .orElse(false)
        .get()

dependencyResolutionManagement {
    repositoriesMode.set(
        RepositoriesMode.FAIL_ON_PROJECT_REPOS,
    )

    repositories {
        if (useMavenLocal) {
            mavenLocal()
        }

        maven {
            name = "MyketMirror"
            url = uri("https://maven.myket.ir")
        }

        maven {
            name = "IranMavenCentralMirror"
            url =
                uri(
                    "https://archive.ito.gov.ir/gradle/maven-central",
                )
        }

        google()
        mavenCentral()
    }
}

rootProject.name = "CarePack"

include(":app")
