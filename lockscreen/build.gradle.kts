plugins { id("com.android.application") }
val recovered = rootProject.file("assets/htc/lockscreen/res")
android {
    namespace = "dev.sense.lockscreen"
    compileSdk = 35
    defaultConfig { applicationId = "dev.sense.lockscreen"; minSdk = 35; targetSdk = 35; versionCode = 1; versionName = "0.1.0"; testInstrumentationRunner = "dev.sense.lockscreen.LockScreenInstrumentation" }
    sourceSets.getByName("main").res.srcDir(recovered)
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

dependencies { implementation(project(":sense-framework")) }
