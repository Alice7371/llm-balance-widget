// Aliyun mirrors are fastest locally (CN) but unreliable from GitHub runners
// (502s), so CI (CI=true) uses google()/mavenCentral() directly. Note: each
// block below has its own scope, hence the repeated val.
pluginManagement {
    val inCi = System.getenv("CI") == "true"
    repositories {
        if (!inCi) {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/gradle-plugin")
            maven("https://maven.aliyun.com/repository/public")
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    val inCi = System.getenv("CI") == "true"
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (!inCi) {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "MyApp"
include(":app")
