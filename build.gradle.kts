plugins {
    id("com.android.application") version "8.9.1" apply false
    id("com.android.library") version "8.9.1" apply false
}

// Resolve once so every APK and the shared packager use the same source snapshot.
val senseBuildVersion = java.util.Properties().apply {
    load(providers.exec {
        workingDir(rootDir)
        commandLine("python3", rootProject.file("tools/resolve_version.py"))
    }.standardOutput.asText.get().reader())
}
extra["senseVersionCode"] = senseBuildVersion.getProperty("senseVersionCode").toInt()
extra["senseVersionName"] = senseBuildVersion.getProperty("senseVersionName")
val writeBuildVersion by tasks.registering {
    inputs.properties(senseBuildVersion.entries.associate { it.key.toString() to it.value.toString() })
    val destination = layout.buildDirectory.file("version.properties")
    outputs.file(destination)
    doLast {
        destination.get().asFile.apply {
            parentFile.mkdirs()
            writeText(senseBuildVersion.entries.sortedBy { it.key.toString() }.joinToString("\n", postfix = "\n") { "${it.key}=${it.value}" })
        }
    }
}

val verifyHtcAssets by tasks.registering(Exec::class) {
    group = "verification"
    description = "Verify the build-ready HTC resource bundle without extracting the ROM"
    inputs.dir(layout.projectDirectory.dir("assets/htc"))
    commandLine("python3", rootProject.file("tools/verify_htc_assets.py"))
}

subprojects {
    tasks.matching { it.name == "preBuild" }.configureEach {
        dependsOn(rootProject.tasks.named("verifyHtcAssets"), rootProject.tasks.named("writeBuildVersion"))
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

 tasks.register<Copy>("assembleBundleDebug") {
    group = "build"
    description = "Build one bootstrap APK containing the complete shared Sense suite"
    dependsOn(":installer:assembleDebug")
    from(project(":installer").layout.buildDirectory.file("outputs/apk/debug/installer-debug.apk"))
    into(layout.buildDirectory.dir("outputs/bundle-debug"))
    rename { "sense-suite-debug.apk" }
}
