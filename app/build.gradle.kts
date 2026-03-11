import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
val hasReleaseKeystore = keystorePropertiesFile.exists().also { exists ->
    if (exists) {
        keystorePropertiesFile.inputStream().use(keystoreProperties::load)
    }
} && listOf("storeFile", "storePassword", "keyAlias", "keyPassword").all {
    !keystoreProperties.getProperty(it).isNullOrBlank()
}
val resolvedVersionCode = (findProperty("VERSION_CODE") as String?)
    ?.toIntOrNull()
    ?: 1
val resolvedVersionName = (findProperty("VERSION_NAME") as String?)
    ?.trim()
    ?.ifBlank { null }
    ?: "1.0"
val testAdMobAppId = "ca-app-pub-3940256099942544~3347511713"
val testBannerAdUnitId = "ca-app-pub-3940256099942544/6300978111"
val testInterstitialAdUnitId = "ca-app-pub-3940256099942544/1033173712"
val resolvedAdMobAppId = (findProperty("ADMOB_APP_ID") as String?)
    ?.trim()
    ?.ifBlank { null }
    ?: testAdMobAppId
val resolvedBannerAdUnitId = (findProperty("ADMOB_BANNER_AD_UNIT_ID") as String?)
    ?.trim()
    ?.ifBlank { null }
    ?: testBannerAdUnitId
val resolvedInterstitialAdUnitId = (findProperty("ADMOB_INTERSTITIAL_AD_UNIT_ID") as String?)
    ?.trim()
    ?.ifBlank { null }
    ?: testInterstitialAdUnitId

android {
    namespace = "com.hijitoko.notihook"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.hijitoko.notihook"
        minSdk = 29
        targetSdk = 36
        versionCode = resolvedVersionCode
        versionName = resolvedVersionName
        manifestPlaceholders["adMobAppId"] = resolvedAdMobAppId
        buildConfigField("String", "ADMOB_BANNER_AD_UNIT_ID", "\"$resolvedBannerAdUnitId\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_AD_UNIT_ID", "\"$resolvedInterstitialAdUnitId\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

val apkBaseName = providers.provider {
    "NotiHook-v${resolvedVersionName}"
}

fun renameApkTaskName(buildType: String) = "rename${buildType.replaceFirstChar { it.uppercase() }}Apk"

listOf("debug", "release").forEach { buildType ->
    val assembleTask = "assemble${buildType.replaceFirstChar { it.uppercase() }}"
    val renameTask = renameApkTaskName(buildType)

    tasks.register(renameTask) {
        dependsOn(assembleTask)
        doLast {
            val apkDir = layout.buildDirectory.dir("outputs/apk/$buildType").get().asFile
            val targetName = when (buildType) {
                "release" -> if (hasReleaseKeystore) {
                    "${apkBaseName.get()}-release.apk"
                } else {
                    "${apkBaseName.get()}-release-unsigned.apk"
                }
                else -> "${apkBaseName.get()}-$buildType.apk"
            }
            val apks = apkDir.listFiles()?.filter { it.isFile && it.extension == "apk" }.orEmpty()

            apks.forEach { apk ->
                if (apk.name != targetName) {
                    val target = apkDir.resolve(targetName)
                    if (target.exists()) target.delete()
                    if (!apk.renameTo(target)) {
                        apk.copyTo(target, overwrite = true)
                        apk.delete()
                    }
                }
            }
        }
    }

    afterEvaluate {
        tasks.named(assembleTask) {
            finalizedBy(renameTask)
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.okhttp)
    implementation(libs.google.play.services.ads)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
