plugins {
    id("com.android.application") version "8.9.1" apply false
    id("com.android.library") version "8.9.1" apply false
}

tasks.register<Exec>("assembleSharedDebug") {
    group = "build"
    description = "Build the framework library, framework services runtime and six dependent debug APKs"
    dependsOn(listOf("launcher", "weather", "phone", "contacts", "messages", "lockscreen", "sense-services").map { ":$it:assembleDebug" })
    commandLine("python3", rootProject.file("tools/build_shared_framework.py"))
}
