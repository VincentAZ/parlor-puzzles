plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":core:game"))
    implementation(libs.serialization.json)
    testImplementation(libs.kotlin.test)
}
application { mainClass.set("com.parlor.tools.levels.MainKt") }
tasks.matching { it.name == "verifyPrototype" || it.name == "verifyCatalog" }.configureEach {
    mustRunAfter("generateWaveA", "generateWaveB", "generateWaveC")
}
tasks.register<JavaExec>("verifyPrototype") {
    group = "verification"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    args("verify", rootProject.file("app/src/main/assets/levels"), "prototype")
}
tasks.register<JavaExec>("verifyCatalog") {
    group = "verification"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    args("verify", rootProject.file("app/src/main/assets/levels"), "release")
}

tasks.register<JavaExec>("generateWaveA") {
    group = "content"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    args("generate-wave-a", rootProject.file("app/src/main/assets/levels"))
}

tasks.register<JavaExec>("generateWaveB") {
    group = "content"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    maxHeapSize = "2g"
    args("generate-wave-b", rootProject.file("app/src/main/assets/levels"))
}

tasks.register<JavaExec>("generateWaveC") {
    group = "content"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    maxHeapSize = "2g"
    args("generate-wave-c", rootProject.file("app/src/main/assets/levels"))
}
