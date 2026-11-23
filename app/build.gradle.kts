import java.net.URI
import java.security.MessageDigest

plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val modelDirectory = layout.buildDirectory.dir("generated/rainy-model")
val modelBinary = modelDirectory.map { it.file("efficientdet_lite0.tflite") }
val checksum = rootProject.layout.projectDirectory.file("models.sha256")
val modelUrl = "https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/int8/1/efficientdet_lite0.tflite"

val prepareModel by tasks.registering {
    inputs.file(checksum)
    inputs.property("modelUrl", modelUrl)
    outputs.file(modelBinary)
    doLast {
        val expected = checksum.asFile.readText().trim()
        val target = modelBinary.get().asFile
        fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
            .digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        if (!target.exists() || sha256(target) != expected) {
            target.parentFile.mkdirs()
            val downloaded = File.createTempFile("model", ".download", target.parentFile)
            try {
                val connection = URI(modelUrl).toURL().openConnection().apply {
                    connectTimeout = 20_000
                    readTimeout = 60_000
                }
                connection.getInputStream().use { input ->
                    downloaded.outputStream().use { output -> input.copyTo(output) }
                }
                check(sha256(downloaded) == expected) { "EfficientDet-Lite0 checksum mismatch" }
                downloaded.copyTo(target, overwrite = true)
            } finally {
                downloaded.delete()
            }
        }
    }
}

android {
    namespace = "studio.rainy.realityview"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "studio.rainy.realityview"
        minSdk = 24
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 7
        versionName = "0.5.2"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    sourceSets.getByName("main").assets.srcDir(modelDirectory)

    val releaseStore = providers.environmentVariable("RAINY_STORE_FILE")
    if (releaseStore.isPresent) {
        signingConfigs.create("distribution") {
            storeFile = file(releaseStore.get())
            storePassword = providers.environmentVariable("RAINY_STORE_PASSWORD").get()
            keyAlias = providers.environmentVariable("RAINY_KEY_ALIAS").get()
            keyPassword = providers.environmentVariable("RAINY_KEY_PASSWORD").get()
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("distribution")
    }
}

tasks.named("preBuild") { dependsOn(prepareModel) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("io.github.mikimn:droiduvc-compose:0.2.1")
    implementation("androidx.media3:media3-common:1.5.1")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-rtsp:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("com.google.mediapipe:tasks-vision:0.10.26.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.7.0")
}
