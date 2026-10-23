plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(libs.serialization.json)
    api(project(":core:model"))
    implementation(project(":core:game"))
    implementation(libs.datetime)
    testImplementation(libs.kotlin.test)
}
