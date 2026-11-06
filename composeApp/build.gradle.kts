import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import java.io.FileInputStream

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.application")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation("androidx.activity:activity-compose:1.9.3")
            implementation("androidx.core:core-ktx:1.13.1")
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
        }
    }
}

android {
    namespace = "cz.mares.kocicky"
    compileSdk = 36

    defaultConfig {
        applicationId = "cz.mares.kocicky"
        minSdk = 24
        targetSdk = 36

        val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        val ciBuildNumber = System.getenv("BUILD_NUM")?.toIntOrNull()
            ?: if (runNumber != null) (runNumber + 100) else 101
        val appVer = System.getenv("APP_VERSION")?.takeIf { it.isNotBlank() } ?: "1.0"

        versionCode = ciBuildNumber
        versionName = "$appVer.$ciBuildNumber"
    }

    signingConfigs {
        create("release") {
            val props = Properties()
            val propFiles = listOf(
                rootProject.file("keystore.properties"),
                rootProject.file("android_keystore.props"),
                file("keystore.properties")
            )
            for (pf in propFiles) {
                if (pf.exists()) {
                    FileInputStream(pf).use { props.load(it) }
                    break
                }
            }

            val envKeystorePath = System.getenv("CM_KEYSTORE_PATH")
                ?: System.getenv("ANDROID_KEYSTORE_PATH")
                ?: System.getenv("KEYSTORE_FILE")
                ?: System.getenv("STORE_FILE")
                ?: props.getProperty("storeFile")

            if (!envKeystorePath.isNullOrBlank()) {
                val ksFile = file(envKeystorePath)
                val rootKsFile = rootProject.file(envKeystorePath)
                storeFile = when {
                    ksFile.exists() -> ksFile
                    rootKsFile.exists() -> rootKsFile
                    else -> ksFile
                }
                storePassword = System.getenv("CM_KEYSTORE_PASSWORD")
                    ?: System.getenv("KEYSTORE_PASSWORD")
                    ?: System.getenv("STORE_PASSWORD")
                    ?: props.getProperty("storePassword")
                keyAlias = System.getenv("CM_KEY_ALIAS")
                    ?: System.getenv("KEY_ALIAS")
                    ?: props.getProperty("keyAlias")
                keyPassword = System.getenv("CM_KEY_PASSWORD")
                    ?: System.getenv("KEY_PASSWORD")
                    ?: props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            val releaseConfig = signingConfigs.getByName("release")
            signingConfig = if (releaseConfig.storeFile != null && releaseConfig.storeFile!!.exists()) {
                releaseConfig
            } else {
                signingConfigs.getByName("debug")
            }
        }
        getByName("debug") {
            val releaseConfig = signingConfigs.getByName("release")
            if (releaseConfig.storeFile != null && releaseConfig.storeFile!!.exists()) {
                signingConfig = releaseConfig
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
