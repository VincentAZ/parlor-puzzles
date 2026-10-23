pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "ParlorPuzzles"
include(":app", ":core:model", ":core:game", ":core:progress", ":core:data", ":core:platform")
include(":feature:play", ":feature:library", ":tools:levels")
include(":benchmark")
include(":core:design")
