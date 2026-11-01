plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "com.justinlin099.xperiarecentsrootless"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.justinlin099.xperiarecentsrootless"
        minSdk = 30
        targetSdk = 36
        versionCode = 30
        versionName = "0.30-test"
    }
    buildFeatures { aidl = true }
    buildTypes { release { isMinifyEnabled = false } }
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
