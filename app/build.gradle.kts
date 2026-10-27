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
android {
    namespace = "com.parlor.puzzles"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.parlor.puzzles"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Network-dependent SDK qualification runs explicitly in a fresh process; ordinary tests stay deterministic.
        buildConfigField("boolean", "CRASH_REPORTING_ENABLED", "false")
        manifestPlaceholders["crashReportingEnabled"] = "false"
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"; versionNameSuffix = "-dev"
        }
        release {
            buildConfigField("boolean", "CRASH_REPORTING_ENABLED", "true")
            manifestPlaceholders["crashReportingEnabled"] = "true"
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("benchmark") {
            initWith(getByName("release"))
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
    constraints {
        // Removing the ad SDK changes transitive version selection. Keep the
        // annotation-only dependency on a version already covered by our checksums.
        implementation("com.google.errorprone:error_prone_annotations:2.26.1")
    }
    implementation(project(":core:design"))
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:platform"))
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
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test)
    androidTestImplementation(libs.coroutines.test)
    androidTestImplementation(libs.uiautomator)
    androidTestImplementation(libs.androidx.window.testing)
    debugImplementation(libs.compose.test.manifest)
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(":tools:levels:verifyCatalog") }
tasks.matching { it.name in setOf("preDebugBuild", "preBenchmarkBuild", "preCrashTestBuild") }.configureEach {
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
    description = "Reject advertising, billing, consent, Analytics and measurement SDKs in every application variant."
    doLast {
        android.buildTypes.forEach { variant ->
            val resolved = configurations.getByName("${variant.name}RuntimeClasspath").incoming.resolutionResult
            val unresolved = resolved.allDependencies.filterIsInstance<org.gradle.api.artifacts.result.UnresolvedDependencyResult>()
            require(unresolved.isEmpty()) { "Cannot audit unresolved dependencies in ${variant.name}: ${unresolved.joinToString { it.attempted.displayName }}" }
            val forbidden = resolved.allComponents
                .mapNotNull { it.moduleVersion }
                .filter { it.group in setOf("com.android.billingclient", "com.google.android.libraries.ads.mobile.sdk", "com.google.android.ump") ||
                    (it.group == "com.google.android.gms" && it.name.startsWith("play-services-ads")) ||
                    (it.group == "com.google.firebase" && it.name.startsWith("firebase-analytics")) ||
                    (it.group == "com.google.android.gms" && it.name.startsWith("play-services-measurement")) }
            require(forbidden.isEmpty()) { "Advertising, billing, consent and analytics SDKs must not ship: ${forbidden.joinToString()}" }
        }
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(verifyNoAnalytics) }
// Explicit regeneration and assembly may share an invocation. Consume the freshly
// generated files even when Gradle schedules asset work before other app tasks.
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    mustRunAfter(":tools:levels:generateWaveA", ":tools:levels:generateWaveB", ":tools:levels:generateWaveC")
}
