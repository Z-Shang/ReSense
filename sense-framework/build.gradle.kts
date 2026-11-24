plugins { id("com.android.library") }
val generatedResources = rootProject.file("assets/htc/sense-framework/res")
android {
    namespace = "dev.sense.framework"
    compileSdk = 35
    resourcePrefix = "sense_"
    defaultConfig { minSdk = 35 }
    sourceSets.getByName("main").res.srcDir(generatedResources)
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
