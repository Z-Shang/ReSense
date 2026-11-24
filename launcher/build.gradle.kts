plugins { id("com.android.application") }
val weatherSources=layout.buildDirectory.dir("generated/sharedWeather/java")
val generateSharedWeather by tasks.registering(Sync::class) {
    from(rootProject.file("sense-framework/src/shared/java")) {
        include("**/WeatherRepository.java")
        filter { line -> line.replace("package dev.sense.framework.weather;", "package dev.sense.launcher;") }
    }
    into(weatherSources)
}
val dockResources = layout.buildDirectory.dir("generated/senseDock/res")
val generateDockAssets by tasks.registering(Exec::class) {
    val rom = rootProject.layout.projectDirectory.file("res/3.33.401.153.zip")
    inputs.file(rom)
    inputs.file(rootProject.file("tools/prepare_weather_widgets.py"))
    inputs.files(rootProject.files("tools/prepare_dock_assets.py", "tools/extract_fullscreen_weather.py", "tools/extract_clock_flip.py", "tools/extract_clock_digits.py", "tools/extract_dock_icons.py", "tools/extract_dock_motion.py", "tools/extract_leap.py", "tools/extract_music.py", "tools/extract_search.py", "tools/extract_weather_assets.py", "tools/extract_weather_catalog.py", "tools/inspect_weather_playback.py", "tools/decode_navbar.py", "tools/mode10.py"))
    outputs.dir(dockResources)
    commandLine("python3", rootProject.file("tools/prepare_dock_assets.py").absolutePath,
        "--rom", rom.asFile.absolutePath, "--output", dockResources.get().asFile.absolutePath)
}
android {
    buildFeatures { aidl = true }
    namespace = "dev.sense.launcher"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.sense.launcher"
        minSdk = 35
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "dev.sense.launcher.WeatherSceneInstrumentation"
    }
    sourceSets.getByName("main").res.srcDir(dockResources)
    sourceSets.getByName("main").java.srcDir(weatherSources)
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.named("preBuild") { dependsOn(generateDockAssets,generateSharedWeather) }

dependencies { implementation(project(":sense-framework")) }
