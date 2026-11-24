plugins { id("com.android.application") }
val generatedResources = layout.buildDirectory.dir("generated/htcMessages/res")
val generateMessagesAssets by tasks.registering(Exec::class) {
    inputs.file(rootProject.file("res/3.33.401.153.zip"))
    inputs.file(rootProject.file("tools/prepare_messages_app.py"))
    outputs.dir(generatedResources)
    commandLine("python3", rootProject.file("tools/prepare_messages_app.py"), "--rom", rootProject.file("res/3.33.401.153.zip"), "--output", generatedResources.get().asFile)
}
android {
    namespace = "dev.sense.messages"
    compileSdk = 35
    defaultConfig { applicationId = "dev.sense.messages"; minSdk = 35; targetSdk = 35; versionCode = 1; versionName = "0.1.0"; testInstrumentationRunner = "dev.sense.messages.MessagesInstrumentation" }
    sourceSets.getByName("main").res.srcDir(generatedResources)
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
tasks.named("preBuild") { dependsOn(generateMessagesAssets) }
dependencies { implementation(project(":sense-framework")) }
