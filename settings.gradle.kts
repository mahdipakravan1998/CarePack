import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    repositories {
        mavenLocal()

        maven {
            name = "MyketMirror"
            url = uri("https://maven.myket.ir")
        }

        maven {
            name = "IranMavenCentralMirror"
            url = uri(
                "https://archive.ito.gov.ir/gradle/maven-central"
            )
        }

        maven {
            name = "IranGradlePluginMirror"
            url = uri(
                "https://archive.ito.gov.ir/gradle/maven-plugin"
            )
        }
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
                                "gradle:$pluginVersion"
                    )
                }
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(
        RepositoriesMode.PREFER_SETTINGS
    )

    repositories {
        mavenLocal()

        maven {
            name = "MyketMirror"
            url = uri("https://maven.myket.ir")
        }

        maven {
            name = "IranMavenCentralMirror"
            url = uri(
                "https://archive.ito.gov.ir/gradle/maven-central"
            )
        }

        maven {
            name = "IranGradlePluginMirror"
            url = uri(
                "https://archive.ito.gov.ir/gradle/maven-plugin"
            )
        }
    }
}

rootProject.name = "CarePack"

include(":app")