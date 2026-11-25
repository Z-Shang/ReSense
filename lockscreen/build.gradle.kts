plugins { id("com.android.application") }
val recovered = rootProject.file("assets/htc/lockscreen/res")
android {
    namespace = "dev.sense.lockscreen"
    compileSdk = 35
    defaultConfig { applicationId = "dev.sense.lockscreen"; minSdk = 35; targetSdk = 35; versionCode = rootProject.extra["senseVersionCode"] as Int; versionName = rootProject.extra["senseVersionName"] as String; testInstrumentationRunner = "dev.sense.lockscreen.LockScreenInstrumentation" }
    sourceSets.getByName("main").res.srcDir(recovered)
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

dependencies { implementation(project(":sense-framework")) }
