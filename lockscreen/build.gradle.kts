plugins { id("com.android.application") }
val recovered = layout.buildDirectory.dir("generated/htcLockscreen/res")
val recoverLockscreen by tasks.registering(Exec::class) {
    inputs.file(rootProject.file("res/3.33.401.153.zip"))
    inputs.files(rootProject.files("tools/prepare_lockscreen.py", "tools/mode10.py", "tools/decode_navbar.py", "tools/inspect_weather_playback.py"))
    outputs.dir(recovered)
    commandLine("python3",rootProject.file("tools/prepare_lockscreen.py"),"--rom",rootProject.file("res/3.33.401.153.zip"),"--output",recovered.get().asFile)
}
android {
    namespace = "dev.sense.lockscreen"
    compileSdk = 35
    defaultConfig { applicationId = "dev.sense.lockscreen"; minSdk = 35; targetSdk = 35; versionCode = 1; versionName = "0.1.0"; testInstrumentationRunner = "dev.sense.lockscreen.LockScreenInstrumentation" }
    sourceSets.getByName("main").res.srcDir(recovered)
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
tasks.named("preBuild") { dependsOn(recoverLockscreen) }
dependencies { implementation(project(":sense-framework")) }
