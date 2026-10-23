plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}

tasks.register("verifyPortableBoundaries") {
    group = "verification"
    description = "Prevent Android or JVM platform dependencies in portable production code."
    val sources = listOf("core/model", "core/game", "core/progress").map {
        fileTree("$it/src/main/kotlin") { include("**/*.kt") }
    }
    inputs.files(sources)
    doLast {
        val forbidden = Regex("(?m)^import (android\\.|androidx\\.|java\\.|javax\\.)")
        sources.flatMap { it.files }.forEach {
            check(!forbidden.containsMatchIn(it.readText())) { "Platform import in $it" }
        }
    }
}
