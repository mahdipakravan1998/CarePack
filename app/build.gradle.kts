import org.gradle.api.GradleException
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val releaseTaskRequested =
    gradle.startParameter.taskNames.any { taskName ->
        taskName.contains(
            other = "Release",
            ignoreCase = true,
        )
    }

val keystorePropertiesFile =
    rootProject.file("keystore.properties")

val keystoreProperties =
    Properties().apply {
        if (keystorePropertiesFile.exists()) {
            keystorePropertiesFile.inputStream().use { input ->
                load(input)
            }
        }
    }

fun releaseSigningProperty(name: String): String {
    val value =
        keystoreProperties
            .getProperty(name)
            ?.trim()
            .orEmpty()

    if (value.isBlank() && releaseTaskRequested) {
        throw GradleException(
            "Missing release signing property '$name' in keystore.properties.",
        )
    }

    return value
}

android {
    namespace = "ir.carepack"
    compileSdk = 36

    signingConfigs {
        create("release") {
            val releaseStoreFile =
                releaseSigningProperty("storeFile")

            val releaseStorePassword =
                releaseSigningProperty("storePassword")

            val releaseKeyAlias =
                releaseSigningProperty("keyAlias")

            val releaseKeyPassword =
                releaseSigningProperty("keyPassword")

            if (
                releaseStoreFile.isNotBlank() &&
                releaseStorePassword.isNotBlank() &&
                releaseKeyAlias.isNotBlank() &&
                releaseKeyPassword.isNotBlank()
            ) {
                storeFile =
                    file(releaseStoreFile)

                storePassword =
                    releaseStorePassword

                keyAlias =
                    releaseKeyAlias

                keyPassword =
                    releaseKeyPassword
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
            signingConfig =
                signingConfigs.getByName("release")

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
            assets.directories.add(
                "$projectDir/schemas",
            )
        }
    }

    testOptions {
        animationsDisabled = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg(
        "room.schemaLocation",
        "$projectDir/schemas",
    )

    arg(
        "room.incremental",
        "true",
    )

    arg(
        "room.generateKotlin",
        "true",
    )
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(
        libs.androidx.lifecycle.runtime.ktx,
    )

    implementation(
        libs.androidx.lifecycle.runtime.compose,
    )

    implementation(
        libs.androidx.lifecycle.viewmodel.ktx,
    )

    implementation(
        libs.androidx.lifecycle.viewmodel.compose,
    )

    implementation(
        libs.androidx.navigation.compose,
    )

    implementation(
        libs.androidx.datastore.preferences,
    )

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)

    ksp(libs.androidx.room.compiler)

    implementation(
        libs.kotlinx.coroutines.android,
    )

    val composeBom =
        platform(libs.androidx.compose.bom)

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.compose.ui)

    implementation(
        libs.androidx.compose.ui.graphics,
    )

    implementation(
        libs.androidx.compose.ui.tooling.preview,
    )

    implementation(
        libs.androidx.compose.material3,
    )

    testImplementation(libs.junit)

    testImplementation(
        libs.kotlinx.coroutines.test,
    )

    androidTestImplementation(
        libs.androidx.test.core,
    )

    androidTestImplementation(
        libs.androidx.test.runner,
    )

    androidTestImplementation(
        libs.androidx.test.rules,
    )

    androidTestImplementation(
        libs.androidx.test.ext.junit,
    )

    androidTestImplementation(
        libs.androidx.test.espresso.core,
    )

    androidTestImplementation(
        libs.androidx.test.uiautomator,
    )

    androidTestImplementation(
        libs.androidx.room.testing,
    )

    androidTestImplementation(
        libs.kotlinx.coroutines.test,
    )

    androidTestImplementation(
        libs.androidx.compose.ui.test.junit4,
    )

    debugImplementation(
        libs.androidx.compose.ui.tooling,
    )

    debugImplementation(
        libs.androidx.compose.ui.test.manifest,
    )
}
