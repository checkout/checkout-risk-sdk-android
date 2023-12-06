pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://maven.fpregistry.io/releases") }
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "risk-sdk-android"
include(":app")
include(":Risk")
