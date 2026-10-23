import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
import com.google.gms.googleservices.GoogleServicesTask
import groovy.json.JsonSlurper

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}
val proProductId = providers.gradleProperty("parlor.proProductId").orElse("parlor_pro").get()
val playPublicKey = providers.gradleProperty("parlor.playPublicKey").orElse("").get().filterNot(Char::isWhitespace)
require(proProductId.matches(Regex("[a-z0-9][a-z0-9._]{0,99}"))) { "Invalid Pro product ID" }
require(playPublicKey.matches(Regex("[A-Za-z0-9+/=]*"))) { "Play licensing public key must be Base64" }
val productionAds = listOf("app", "banner", "interstitial", "rewarded").associateWith {
    providers.gradleProperty("parlor.admob.$it").orElse("").get()
}
productionAds.forEach { (kind, value) ->
    require(value.isEmpty() || value.matches(Regex("ca-app-pub-[0-9]{16}${if (kind == "app") "~" else "/"}[0-9]{10}"))) {
        "Invalid AdMob $kind ID"
    }
}
val testAds = mapOf("app" to "ca-app-pub-3940256099942544~3347511713", "banner" to "ca-app-pub-3940256099942544/9214589741",
    "interstitial" to "ca-app-pub-3940256099942544/1033173712", "rewarded" to "ca-app-pub-3940256099942544/5224354917")
android {
    namespace = "com.parlor.puzzles"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.parlor.puzzles"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Network-dependent SDK qualification runs explicitly in a fresh process; ordinary tests stay deterministic.
        testInstrumentationRunnerArguments["notAnnotation"] = "com.parlor.puzzles.AdNetworkQualification"
        buildConfigField("String", "PRO_PRODUCT_ID", "\"$proProductId\"")
        buildConfigField("String", "PLAY_PUBLIC_KEY", "\"$playPublicKey\"")
        buildConfigField("boolean", "CRASH_REPORTING_ENABLED", "false")
        manifestPlaceholders["crashReportingEnabled"] = "false"
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"; versionNameSuffix = "-dev"
            testAds.forEach { (kind, value) -> buildConfigField("String", "ADMOB_${kind.uppercase()}", "\"$value\"") }
            manifestPlaceholders["admobAppId"] = testAds.getValue("app")
        }
        create("adTest") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".adtest"
            versionNameSuffix = "-ad-test"
            matchingFallbacks += listOf("debug")
            // This isolated preview cannot make purchases, even with owner properties configured.
            buildConfigField("String", "PLAY_PUBLIC_KEY", "\"\"")
        }
        create("storeTest") {
            initWith(getByName("debug"))
            // License testers need the package configured in Play Console.
            applicationIdSuffix = null
            versionNameSuffix = "-store-test"
            matchingFallbacks += listOf("debug")
        }
        release {
            buildConfigField("boolean", "CRASH_REPORTING_ENABLED", "true")
            manifestPlaceholders["crashReportingEnabled"] = "true"
            productionAds.forEach { (kind, value) -> buildConfigField("String", "ADMOB_${kind.uppercase()}", "\"$value\"") }
            manifestPlaceholders["admobAppId"] = productionAds.getValue("app")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("benchmark") {
            initWith(getByName("release"))
            testAds.forEach { (kind, value) -> buildConfigField("String", "ADMOB_${kind.uppercase()}", "\"$value\"") }
            manifestPlaceholders["admobAppId"] = testAds.getValue("app")
            applicationIdSuffix = ".benchmark"
            versionNameSuffix = "-benchmark"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            buildConfigField("boolean", "CRASH_REPORTING_ENABLED", "false")
            manifestPlaceholders["crashReportingEnabled"] = "false"
        }
        create("crashTest") {
            initWith(getByName("benchmark"))
            applicationIdSuffix = ".crashtest"
            versionNameSuffix = "-crash-test"
            buildConfigField("boolean", "CRASH_REPORTING_ENABLED", "true")
            manifestPlaceholders["crashReportingEnabled"] = "true"
        }
        configureEach {
            val uploadsEnabled = name in setOf("release", "crashTest")
            configure<CrashlyticsExtension> {
                mappingFileUploadEnabled = uploadsEnabled
            }
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true; buildConfig = true }
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":core:design"))
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:platform"))
    implementation(libs.work.runtime)
    implementation(project(":feature:play"))
    implementation(project(":feature:library"))
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.activity.compose)
    implementation(libs.profileinstaller)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    // The application owns the Firebase provider removal in its manifest.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.common)
    "crashTestImplementation"(libs.firebase.crashlytics)
    debugImplementation(libs.compose.tooling)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.room.runtime)
    androidTestImplementation(libs.serialization.json)
    androidTestImplementation(libs.mobile.ads)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test)
    androidTestImplementation(libs.coroutines.test)
    androidTestImplementation(libs.uiautomator)
    androidTestImplementation(libs.androidx.window.testing)
    debugImplementation(libs.compose.test.manifest)
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(":tools:levels:verifyCatalog") }
val verifyReleaseBilling by tasks.registering {
    doLast {
        require(playPublicKey.isNotBlank()) { "Configure parlor.playPublicKey before building release or storeTest." }
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(playPublicKey)))
    }
}
tasks.matching { it.name == "preReleaseBuild" || it.name == "preStoreTestBuild" }.configureEach { dependsOn(verifyReleaseBilling) }
val verifyReleaseAds by tasks.registering {
    doLast {
        require(productionAds.values.all { it.isNotBlank() && !it.startsWith("ca-app-pub-3940256099942544") }) {
            "Configure production parlor.admob.app, banner, interstitial and rewarded IDs before building release."
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(verifyReleaseAds) }
tasks.matching { it.name in setOf("preDebugBuild", "preAdTestBuild", "preBenchmarkBuild", "preStoreTestBuild", "preCrashTestBuild") }.configureEach {
    dependsOn(":tools:levels:verifyPrototype")
}

// Use only the configuration for this exact variant. A root or test-project JSON
// must never accidentally configure production or an ordinary development build.
androidComponents.onVariants { variant ->
    tasks.named<GoogleServicesTask>("process${variant.name.replaceFirstChar(Char::uppercaseChar)}GoogleServices") {
        val reportingVariant = variant.name in setOf("release", "crashTest")
        enabled = reportingVariant
        googleServicesJsonFiles.set(if (reportingVariant) listOf(file("src/${variant.name}/google-services.json")) else emptyList())
        googleServicesJsonFiles.disallowChanges()
    }
}
for ((variant, packageName) in mapOf("Release" to "com.parlor.puzzles", "CrashTest" to "com.parlor.puzzles.crashtest")) {
    val config = layout.projectDirectory.file("src/${variant.replaceFirstChar(Char::lowercaseChar)}/google-services.json")
    val verify = tasks.register("verify${variant}CrashReporting") {
        group = "verification"
        doLast {
            require(config.asFile.isFile) { "Configure ${config.asFile.relativeTo(projectDir)} from the owner's Firebase project before building $variant." }
            val json = try { JsonSlurper().parse(config.asFile) as? Map<*, *> }
                catch (_: Exception) { null }
            requireNotNull(json) { "Invalid Firebase configuration JSON" }
            val project = json["project_info"] as? Map<*, *> ?: error("Missing Firebase project information")
            val clients = json["client"] as? List<*> ?: error("Missing Firebase Android app registration")
            val client = clients.mapNotNull { it as? Map<*, *> }.singleOrNull {
                val info = it["client_info"] as? Map<*, *>
                (info?.get("android_client_info") as? Map<*, *>)?.get("package_name") == packageName
            } ?: error("Firebase configuration must contain exactly one registration for $packageName")
            val appId = (client["client_info"] as? Map<*, *>)?.get("mobilesdk_app_id") as? String ?: ""
            require(appId.matches(Regex("1:[0-9]+:android:[a-fA-F0-9]+"))) { "Invalid Firebase Android app ID" }
            require(project["project_number"]?.toString() == appId.split(':')[1]) { "Firebase app and project numbers disagree" }
            require((project["project_id"] as? String)?.matches(Regex("[a-z][a-z0-9-]{4,28}[a-z0-9]")) == true) { "Invalid Firebase project ID" }
            val keys = client["api_key"] as? List<*> ?: emptyList<Any>()
            require(keys.any { (it as? Map<*, *>)?.get("current_key")?.toString()?.matches(Regex("AIza[A-Za-z0-9_-]{35}")) == true }) {
                "Missing or invalid Firebase client API key"
            }
        }
    }
    tasks.matching { it.name == "pre${variant}Build" || it.name == "process${variant}GoogleServices" }.configureEach { dependsOn(verify) }
}
val verifyNoAnalytics by tasks.registering {
    group = "verification"
    description = "Reject Analytics and measurement SDKs in every application variant."
    doLast {
        android.buildTypes.forEach { variant ->
            val resolved = configurations.getByName("${variant.name}RuntimeClasspath").incoming.resolutionResult
            val unresolved = resolved.allDependencies.filterIsInstance<org.gradle.api.artifacts.result.UnresolvedDependencyResult>()
            require(unresolved.isEmpty()) { "Cannot audit unresolved dependencies in ${variant.name}: ${unresolved.joinToString { it.attempted.displayName }}" }
            val forbidden = resolved.allComponents
                .mapNotNull { it.moduleVersion }
                .filter { (it.group == "com.google.firebase" && it.name.startsWith("firebase-analytics")) ||
                    (it.group == "com.google.android.gms" && it.name.startsWith("play-services-measurement")) }
            require(forbidden.isEmpty()) { "Analytics SDKs are outside v1 scope: ${forbidden.joinToString()}" }
        }
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(verifyNoAnalytics) }
// Explicit regeneration and assembly may share an invocation. Consume the freshly
// generated files even when Gradle schedules asset work before other app tasks.
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    mustRunAfter(":tools:levels:generateWaveA", ":tools:levels:generateWaveB", ":tools:levels:generateWaveC")
}
