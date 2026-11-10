import java.util.zip.ZipFile

plugins {
    id("com.android.application")
}

// Personal Firebase configuration stays local. Generic/CI builds still open offline without it.
if (file("google-services.json").isFile) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "dev.bjamdort.opencode.remote"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.bjamdort.opencode.remote"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.2.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.15.0")
    implementation("androidx.core:core:1.17.0")
    implementation(platform("com.google.firebase:firebase-bom:35.1.0"))
    implementation("com.google.firebase:firebase-messaging")
    testImplementation("junit:junit:4.13.2")
}

val buildWeb by tasks.registering(Exec::class) {
    workingDir(rootProject.projectDir.resolve("../app"))
    environment("VITE_OPENCODE_SERVER_MODE", "none")
    commandLine("bun", "run", "build", "--mode", "android", "--logLevel", "warn", "--outDir",
        layout.buildDirectory.dir("web-dist").get().asFile.absolutePath, "--emptyOutDir")
    inputs.files(fileTree(rootProject.projectDir.parentFile) {
        exclude("**/node_modules/**", "**/dist/**", "**/build/**", "android/**", "**/.turbo/**")
    })
    inputs.files(rootProject.projectDir.resolve("../../package.json"), rootProject.projectDir.resolve("../../bun.lock"))
    outputs.dir(layout.buildDirectory.dir("web-dist"))
}

val bundleWebAssets by tasks.registering(Sync::class) {
    from(buildWeb)
    into(layout.buildDirectory.dir("generated/webAssets"))
}

android.sourceSets.getByName("main").assets.srcDir(bundleWebAssets)
tasks.named("preBuild") { dependsOn(bundleWebAssets) }

// Android can silently omit asset names (for example, directories starting with '_').
// Check the actual APK, not only the generated directory, so a partial UI cannot ship.
tasks.register("verifyBundledAssets") {
    dependsOn("assembleDebug")
    doLast {
        val assets = layout.buildDirectory.dir("generated/webAssets").get().asFile
        ZipFile(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk").get().asFile).use { apk ->
            fileTree(assets).files.forEach { asset ->
                val path = "assets/${asset.relativeTo(assets).invariantSeparatorsPath}"
                val entry = checkNotNull(apk.getEntry(path)) { "APK is missing bundled asset: $path" }
                check(asset.readBytes().contentEquals(apk.getInputStream(entry).use { it.readBytes() })) {
                    "APK contains a different bundled asset: $path"
                }
            }
        }
    }
}
