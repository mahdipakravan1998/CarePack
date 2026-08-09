import org.gradle.api.GradleException
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val releaseTaskRequested =
    gradle.startParameter.taskNames.any { taskName ->
        taskName.contains("Release", ignoreCase = true)
    }

fun requiredReleaseEnvironment(
    name: String,
): String {
    val value =
        providers.environmentVariable(name)
            .orNull
            ?.trim()
            .orEmpty()

    if (releaseTaskRequested && value.isBlank()) {
        throw GradleException(
            "Required release signing environment is unavailable.",
        )
    }

    return value
}

val releaseStorePath =
    requiredReleaseEnvironment("CAREPACK_KEYSTORE_PATH")
val releaseStorePassword =
    requiredReleaseEnvironment("CAREPACK_KEYSTORE_PASSWORD")
val releaseKeyAlias =
    requiredReleaseEnvironment("CAREPACK_KEY_ALIAS")
val releaseKeyPassword =
    requiredReleaseEnvironment("CAREPACK_KEY_PASSWORD")

if (releaseTaskRequested && releaseStorePath.isNotBlank()) {
    val rootCanonical = rootDir.canonicalFile
    val storeCanonical = File(releaseStorePath).canonicalFile

    if (storeCanonical.toPath().startsWith(rootCanonical.toPath())) {
        throw GradleException(
            "Release signing material must be outside the repository.",
        )
    }

    if (!storeCanonical.isFile) {
        throw GradleException(
            "Release signing material is unavailable.",
        )
    }
}

android {
    namespace = "ir.carepack"
    compileSdk = 36

    signingConfigs {
        create("release") {
            if (releaseStorePath.isNotBlank()) {
                storeFile = file(releaseStorePath)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "ir.carepack"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }

        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt",
                ),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/AL2.0",
                "/META-INF/LGPL2.1",
            )
        }
    }

    sourceSets {
        getByName("androidTest") {
            assets.directories.add("$projectDir/schemas")
        }
    }

    testOptions {
        animationsDisabled = true
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
}

dependencyLocking {
    lockAllConfigurations()
}

tasks.withType<Test>().configureEach {
    reports.junitXml.required.set(true)
    reports.html.required.set(true)
}

val forbiddenDefinitiveWording =
    listOf(
        "مصرف شد",
        "مصرف نشد",
    )

val verifyForbiddenWording by tasks.registering {
    group = "verification"
    description =
        "Rejects presentation wording that claims proven consumption."

    doLast {
        val roots =
            listOf(
                file("src/main/java"),
                file("src/main/res"),
            )

        val violations =
            roots.flatMap { root ->
                if (!root.exists()) {
                    emptyList()
                } else {
                    root.walkTopDown()
                        .filter(File::isFile)
                        .filter { file ->
                            file.extension in setOf("kt", "xml")
                        }
                        .flatMap { file ->
                            file.readLines()
                                .mapIndexedNotNull { index, line ->
                                    forbiddenDefinitiveWording
                                        .firstOrNull(line::contains)
                                        ?.let { wording ->
                                            "${file.relativeTo(projectDir)}:${index + 1}:$wording"
                                        }
                                }
                        }
                        .toList()
                }
            }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Forbidden definitive wording found:\n" +
                        violations.joinToString("\n"),
            )
        }
    }
}

val verifyPackageBoundaries by tasks.registering {
    group = "verification"
    description =
        "Ensures pure domain sources do not import Android or Room APIs."

    doLast {
        val domainRoot = file("src/main/java/ir/carepack/domain")
        val forbiddenImports =
            listOf(
                "import android.",
                "import androidx.room.",
                "import ir.carepack.data.",
            )

        val violations =
            domainRoot.walkTopDown()
                .filter(File::isFile)
                .filter { it.extension == "kt" }
                .flatMap { source ->
                    source.readLines()
                        .mapIndexedNotNull { index, line ->
                            forbiddenImports
                                .firstOrNull(line::startsWith)
                                ?.let {
                                    "${source.relativeTo(projectDir)}:${index + 1}:$line"
                                }
                        }
                }
                .toList()

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Domain dependency boundary violations:\n" +
                        violations.joinToString("\n"),
            )
        }
    }
}

val verifyRoomSchemaCommitted by tasks.registering {
    group = "verification"
    description =
        "Verifies that the exported Room schema baseline is present."

    doLast {
        val schema =
            file(
                "schemas/ir.carepack.data.local.CarePackDatabase/1.json",
            )

        if (!schema.isFile || schema.length() == 0L) {
            throw GradleException(
                "Committed Room schema baseline is missing.",
            )
        }
    }
}

tasks.named("check") {
    dependsOn(
        verifyForbiddenWording,
        verifyPackageBoundaries,
        verifyRoomSchemaCommitted,
    )
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
