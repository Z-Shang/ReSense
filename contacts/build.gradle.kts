plugins { id("com.android.application") }
val generatedResources = layout.buildDirectory.dir("generated/htcPeople/res")
val generatePeopleAssets by tasks.registering(Exec::class) {
    inputs.file(rootProject.file("res/3.33.401.153.zip"))
    inputs.file(rootProject.file("tools/prepare_contacts_app.py"))
    outputs.dir(generatedResources)
    commandLine("python3", rootProject.file("tools/prepare_contacts_app.py"), "--rom", rootProject.file("res/3.33.401.153.zip"), "--output", generatedResources.get().asFile)
}
android {
    namespace = "dev.sense.contacts"
    compileSdk = 35
    defaultConfig { applicationId = "dev.sense.contacts"; minSdk = 35; targetSdk = 35; versionCode = 1; versionName = "0.1.0"; testInstrumentationRunner = "dev.sense.contacts.PeopleInstrumentation" }
    sourceSets.getByName("main").res.srcDir(generatedResources)
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
tasks.named("preBuild") { dependsOn(generatePeopleAssets) }

dependencies { implementation(project(":sense-framework")) }
