import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) {
        FileInputStream(file).use { load(it) }
    }
}

fun gitOutput(vararg args: String): String? = runCatching {
    providers.exec {
        commandLine("git", *args)
        workingDir = rootDir
    }.standardOutput.asText.get().trim()
}.getOrNull()?.takeIf { it.isNotBlank() }

val latestTag = gitOutput("describe", "--tags", "--abbrev=0")
val exactTag = gitOutput("describe", "--tags", "--exact-match")
val commitCount = gitOutput("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 1
val baseVersionName = latestTag?.removePrefix("v")?.takeIf { it.isNotBlank() } ?: "0.0.0"
val autoVersionName = if (exactTag != null) baseVersionName else "$baseVersionName-dev"

android {
    namespace = "com.microauto"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("signing/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            val storePath = System.getenv("RELEASE_KEYSTORE_PATH")
                ?: keystoreProperties.getProperty("storeFile")
            if (!storePath.isNullOrBlank()) {
                val store = file(storePath)
                if (store.exists()) {
                    storeFile = store
                    storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                        ?: keystoreProperties.getProperty("storePassword")
                    keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                        ?: keystoreProperties.getProperty("keyAlias")
                    keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
                        ?: keystoreProperties.getProperty("keyPassword")
                }
            }
        }
    }

    defaultConfig {
        applicationId = "com.microauto"
        minSdk = 26
        targetSdk = 36
        versionCode = commitCount.coerceAtLeast(1)
        versionName = autoVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }

    applicationVariants.all {
        val variantVersionName = versionName ?: "0.0.0"
        outputs.all {
            (this as com.android.build.gradle.api.ApkVariantOutput).outputFileName =
                "MicroAuto-$variantVersionName.apk"
        }
    }
}

val releaseSigningConfig = android.signingConfigs.getByName("release")

gradle.taskGraph.whenReady {
    val releaseArtifactTasks = setOf(
        "assembleRelease",
        "bundleRelease",
        "packageRelease",
        "installRelease"
    )
    val requestsRelease = allTasks.any { task ->
        task.project == project && task.name in releaseArtifactTasks
    }
    if (requestsRelease && releaseSigningConfig.storeFile == null) {
        throw org.gradle.api.GradleException(
            "release keystore 未配置：请设置 RELEASE_KEYSTORE_PATH、" +
                "RELEASE_KEYSTORE_PASSWORD、RELEASE_KEY_ALIAS、RELEASE_KEY_PASSWORD，" +
                "或在 keystore.properties 中配置签名信息。release 构建不再回退 debug 签名。"
        )
    }
}

dependencies {
    // 1.19.1 requires compileSdk 37 / AGP 9.1; keep the latest 36-compatible release.
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.mozilla:rhino:1.7.15")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.test:core-ktx:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}
