plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// CI supplies signing credentials only to the release build step.
// Ordinary local/debug builds do not need a private signing key.
val releaseSigningNames = listOf(
    "ANDROID_KEYSTORE_PATH", "ANDROID_KEYSTORE_PASSWORD", "ANDROID_KEY_ALIAS", "ANDROID_KEY_PASSWORD"
)
val releaseSigningValues = releaseSigningNames.associateWith { providers.environmentVariable(it).orNull }
val hasReleaseSigning = releaseSigningValues.values.all { !it.isNullOrBlank() }
require(releaseSigningValues.values.all { it.isNullOrBlank() } || hasReleaseSigning) {
    "Release signing requires all four ANDROID_KEYSTORE_PATH / PASSWORD / KEY_ALIAS / KEY_PASSWORD variables."
}

android {
    namespace = "com.justinlin099.xperiarecentsrootless"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.justinlin099.xperiarecentsrootless"
        minSdk = 30
        targetSdk = 36
        versionCode = 32
        versionName = "1.0.0"
    }
    buildFeatures { aidl = true; buildConfig = true }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseSigningValues["ANDROID_KEYSTORE_PATH"]))
                storePassword = releaseSigningValues["ANDROID_KEYSTORE_PASSWORD"]
                keyAlias = releaseSigningValues["ANDROID_KEY_ALIAS"]
                keyPassword = releaseSigningValues["ANDROID_KEY_PASSWORD"]
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            test.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED")
            test.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
            listOf("http.proxyHost", "http.proxyPort", "https.proxyHost", "https.proxyPort").forEach { key ->
                System.getProperty(key)?.let { test.systemProperty(key, it) }
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }
}
dependencies {
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
}

// Keep the AndroidX components on compatible, explicit versions.
configurations.configureEach {
    resolutionStrategy.eachDependency {
        when (requested.group) {
            "androidx.lifecycle" -> useVersion("2.6.2")
            "androidx.core" -> if (requested.name != "core-viewtree") useVersion("1.17.0")
            "androidx.annotation" -> if (requested.name != "annotation-experimental") useVersion("1.8.1")
            "androidx.collection" -> useVersion("1.4.2")
            "org.jetbrains.kotlinx" -> if (requested.name.startsWith("kotlinx-coroutines")) useVersion("1.8.1")
            "org.jetbrains.kotlin" -> if (requested.name == "kotlin-stdlib" || requested.name == "kotlin-stdlib-common") useVersion("2.0.21")
        }
    }
}
