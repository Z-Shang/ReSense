plugins { id("com.android.application") }
val prepareSuitePayload by tasks.registering(Exec::class) {
    dependsOn(rootProject.tasks.named("assembleSharedDebug"))
    commandLine("python3", rootProject.file("tools/prepare_suite_payload.py"))
}
android {
    namespace = "dev.sense.installer"
    compileSdk = 35
    defaultConfig { applicationId = "dev.sense.installer"; minSdk = 35; targetSdk = 35; versionCode = rootProject.extra["senseVersionCode"] as Int; versionName = rootProject.extra["senseVersionName"] as String }
    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/suite/assets"))
    androidResources { noCompress += "apk" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
tasks.named("preBuild") { dependsOn(prepareSuitePayload) }
