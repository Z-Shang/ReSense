plugins { id("com.android.library") }
val generatedResources = layout.buildDirectory.dir("generated/htcSense/res")
val generateSenseAssets by tasks.registering(Exec::class) {
    inputs.file(rootProject.file("res/3.33.401.153.zip"))
    inputs.file(rootProject.file("tools/prepare_sense_framework.py"))
    outputs.dir(generatedResources)
    commandLine("python3",rootProject.file("tools/prepare_sense_framework.py"),"--rom",rootProject.file("res/3.33.401.153.zip"),"--output",generatedResources.get().asFile)
}
android {
    namespace = "dev.sense.framework"
    compileSdk = 35
    resourcePrefix = "sense_"
    defaultConfig { minSdk = 35 }
    sourceSets.getByName("main").res.srcDir(generatedResources)
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
tasks.named("preBuild") { dependsOn(generateSenseAssets) }
