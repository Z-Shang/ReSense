plugins { id("com.android.application") }
val weatherSources=layout.buildDirectory.dir("generated/sharedWeather/java")
val generateSharedWeather by tasks.registering(Sync::class) {
    from(rootProject.file("sense-framework/src/shared/java")) {
        include("**/WeatherRepository.java")
        filter { line -> line.replace("package dev.sense.framework.weather;", "package dev.sense.launcher;") }
    }
    into(weatherSources)
}
val dockResources = rootProject.file("assets/htc/launcher/res")
android {
    buildFeatures { aidl = true }
    namespace = "dev.sense.launcher"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.sense.launcher"
        minSdk = 35
        targetSdk = 35
        versionCode = rootProject.extra["senseVersionCode"] as Int
        versionName = rootProject.extra["senseVersionName"] as String
        testInstrumentationRunner = "dev.sense.launcher.WeatherSceneInstrumentation"
    }
    sourceSets.getByName("main").res.srcDir(dockResources)
    sourceSets.getByName("main").java.srcDir(weatherSources)
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.named("preBuild") { dependsOn(generateSharedWeather) }

dependencies { implementation(project(":sense-framework")) }
