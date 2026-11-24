plugins { id("com.android.application") }
val generatedResources = layout.buildDirectory.dir("generated/htcWeather/res")
val sharedSources = layout.buildDirectory.dir("generated/sharedWeather/java")
val sharedNames = listOf("WeatherCodes.java", "HtcWeatherScene.java", "WeatherProjection.java", "WeatherTransform.java")
val generateSharedWeather by tasks.registering(Sync::class) {
    from(rootProject.file("launcher/src/main/java/dev/sense/launcher")) { include(sharedNames); filter { line -> line.replace("package dev.sense.launcher;", "package dev.sense.weather;") } }
    from(rootProject.file("sense-framework/src/shared/java")) { include("**/WeatherRepository.java");filter { line -> line.replace("package dev.sense.framework.weather;", "package dev.sense.weather;") } }
    into(sharedSources.map { it.dir("dev/sense/weather") })
}
val generateWeatherAssets by tasks.registering(Exec::class) {
    inputs.file(rootProject.file("res/3.33.401.153.zip"))
    inputs.files(rootProject.fileTree("tools") { include("*.py") })
    outputs.dir(generatedResources)
    commandLine("python3", rootProject.file("tools/prepare_weather_app.py"), "--rom", rootProject.file("res/3.33.401.153.zip"), "--output", generatedResources.get().asFile)
}
android {
    namespace = "dev.sense.weather"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.sense.weather"
        minSdk = 35
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "dev.sense.weather.WeatherInstrumentation"
    }
    sourceSets.getByName("main").res.srcDir(generatedResources)
    sourceSets.getByName("main").java.srcDir(sharedSources)
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
tasks.named("preBuild") { dependsOn(generateWeatherAssets, generateSharedWeather) }

dependencies { implementation(project(":sense-framework")) }
