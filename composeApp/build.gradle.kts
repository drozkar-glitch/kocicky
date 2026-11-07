import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
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
            implementation(libs.androidx.activity.compose)
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

val keystoreProps = Properties()
val keystorePropsFile = rootProject.file("keystore.properties").let {
    if (it.exists()) it else rootProject.file("android_keystore.props")
}
if (keystorePropsFile.exists()) {
    keystoreProps.load(FileInputStream(keystorePropsFile))
}

fun getSigningValue(envKeys: List<String>, propKeys: List<String>): String? {
    for (k in envKeys) {
        val v = System.getenv(k)?.trim()
        if (!v.isNullOrEmpty()) return v
    }
    for (k in propKeys) {
        val v = keystoreProps.getProperty(k)?.trim()
        if (!v.isNullOrEmpty()) return v
    }
    return null
}

val resolvedKeystorePath = getSigningValue(
    listOf("CM_KEYSTORE_PATH", "STORE_FILE", "KEYSTORE_FILE", "ANDROID_KEYSTORE_PATH"),
    listOf("storeFile", "CM_KEYSTORE_PATH", "STORE_FILE")
)
val resolvedKeystorePassword = getSigningValue(
    listOf("CM_KEYSTORE_PASSWORD", "STORE_PASSWORD", "KEYSTORE_PASSWORD", "ANDROID_KEYSTORE_PASSWORD"),
    listOf("storePassword", "CM_KEYSTORE_PASSWORD", "STORE_PASSWORD")
)
val resolvedKeyAlias = getSigningValue(
    listOf("CM_KEY_ALIAS", "KEY_ALIAS", "ANDROID_KEY_ALIAS"),
    listOf("keyAlias", "CM_KEY_ALIAS", "KEY_ALIAS")
)
val resolvedKeyPassword = getSigningValue(
    listOf("CM_KEY_PASSWORD", "KEY_PASSWORD", "ANDROID_KEY_PASSWORD"),
    listOf("keyPassword", "CM_KEY_PASSWORD", "KEY_PASSWORD")
) ?: resolvedKeystorePassword

val ciBuildNumber = System.getenv("BUILD_NUM")?.toIntOrNull()
    ?: System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.plus(100)
    ?: 101
val appVer = System.getenv("APP_VERSION")?.takeIf { it.isNotBlank() } ?: "1.0"

android {
    namespace = "cz.mares.kocicky"
    compileSdk = 36

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    sourceSets["main"].resources.srcDirs("src/commonMain/resources")

    defaultConfig {
        applicationId = "cz.mares.kocicky"
        minSdk = 24
        targetSdk = 36
        versionCode = ciBuildNumber
        versionName = "$appVer.$ciBuildNumber"
    }

    signingConfigs {
        create("release") {
            val ksFile = resolvedKeystorePath?.let { File(it) }
            if (ksFile != null && ksFile.exists() && !resolvedKeystorePassword.isNullOrEmpty() && !resolvedKeyAlias.isNullOrEmpty()) {
                storeFile = ksFile
                storePassword = resolvedKeystorePassword
                keyAlias = resolvedKeyAlias
                keyPassword = resolvedKeyPassword
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null && releaseSigning.storeFile!!.exists()) {
                signingConfig = releaseSigning
            }
        }
        getByName("debug") {
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null && releaseSigning.storeFile!!.exists()) {
                signingConfig = releaseSigning
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
