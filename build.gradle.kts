plugins {
    id("com.android.application") version "8.9.1" apply false
    id("com.android.library") version "8.9.1" apply false
}

val verifyHtcAssets by tasks.registering(Exec::class) {
    group = "verification"
    description = "Verify the build-ready HTC resource bundle without extracting the ROM"
    inputs.dir(layout.projectDirectory.dir("assets/htc"))
    commandLine("python3", rootProject.file("tools/verify_htc_assets.py"))
}

subprojects {
    tasks.matching { it.name == "preBuild" }.configureEach {
        dependsOn(rootProject.tasks.named("verifyHtcAssets"))
    }
}

tasks.register<Exec>("extractHtcAssets") {
    group = "assets"
    description = "Explicitly regenerate assets/htc from the local stock ROM"
    inputs.file(layout.projectDirectory.file("res/3.33.401.153.zip"))
    commandLine("python3", rootProject.file("tools/extract_build_assets.py"), "--replace")
}

tasks.register<Exec>("assembleSharedDebug") {
    group = "build"
    description = "Build the framework library, framework services runtime and six dependent debug APKs"
    dependsOn(listOf("launcher", "weather", "phone", "contacts", "messages", "lockscreen", "sense-services").map { ":$it:assembleDebug" })
    commandLine("python3", rootProject.file("tools/build_shared_framework.py"))
}
