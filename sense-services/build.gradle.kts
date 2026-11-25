plugins { id("com.android.application") }
val weatherSources=layout.buildDirectory.dir("generated/weather/java")
val generateWeatherSources by tasks.registering(Sync::class) {
    from(rootProject.file("sense-framework/src/shared/java")) {
        include("**/WeatherRepository.java")
        filter { line -> line.replace("package dev.sense.framework.weather;", "package dev.sense.framework.services;") }
    }
    into(weatherSources)
}
android {
    namespace="dev.sense.framework.services"
    compileSdk=35
    defaultConfig { applicationId="dev.sense.framework.services";minSdk=35;targetSdk=35;versionCode = rootProject.extra["senseVersionCode"] as Int;versionName = rootProject.extra["senseVersionName"] as String }
    sourceSets.getByName("main").java.srcDir(weatherSources)
    compileOptions { sourceCompatibility=JavaVersion.VERSION_17;targetCompatibility=JavaVersion.VERSION_17 }
}
tasks.named("preBuild") { dependsOn(generateWeatherSources) }
dependencies { implementation(project(":sense-framework")) }
