rootProject.name = "prod-access"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include("common", "runner", "control", "web")
