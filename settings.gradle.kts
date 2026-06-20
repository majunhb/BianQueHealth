pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolution {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "BianQueHealth"

include(":app")
include(":base")
include(":module_face")
include(":module_tongue")
include(":module_blood_pressure")
include(":module_pulse")
include(":module_health_engine")