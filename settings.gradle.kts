pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "SenseLauncher"
include(":lockscreen", ":messages", ":launcher", ":weather", ":phone", ":contacts", ":sense-framework")
include(":sense-services")

include(":installer")
