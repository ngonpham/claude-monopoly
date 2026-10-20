pluginManagement {
    repositories {
        google()
        // Mirror Maven Central của Google — đỡ bị lỗi 429 khi Maven Central giới hạn tần suất
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}
rootProject.name = "CoTyPhuVietNam"
include(":core", ":app")
