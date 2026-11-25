plugins { id("com.android.application") }
val generatedResources = rootProject.file("assets/htc/messages/res")
android {
    namespace = "dev.sense.messages"
    compileSdk = 35
    defaultConfig { applicationId = "dev.sense.messages"; minSdk = 35; targetSdk = 35; versionCode = rootProject.extra["senseVersionCode"] as Int; versionName = rootProject.extra["senseVersionName"] as String; testInstrumentationRunner = "dev.sense.messages.MessagesInstrumentation" }
    sourceSets.getByName("main").res.srcDir(generatedResources)
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

dependencies { implementation(project(":sense-framework")) }
