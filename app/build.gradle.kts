import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Versions read YEAR.RELEASE, from version.txt at the project root, the one place to change it: two digits for the year, then the release of that year counting up from 1, so 26.1 is the first release of 2026 and 26.2 the second. Android also wants a whole number that only ever grows, so it is built from the same two parts: 26.1 becomes 26000001.
val appVersion = providers.fileContents(rootProject.layout.projectDirectory.file("version.txt")).asText.get().trim()
val versionPattern = Regex("""(\d{2})\.(\d{1,4})""")
val versionParts = requireNotNull(versionPattern.matchEntire(appVersion)) {
    "version.txt must read YEAR.RELEASE, for example 26.1, but says '$appVersion'"
}.destructured.toList().map { it.toInt() }
val (versionYear, versionRelease) = versionParts
require(versionRelease >= 1) {
    "version.txt counts releases from 1, but says '$appVersion'"
}

android {
    namespace = "com.agustincantero.mxiangcameraviewer"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.agustincantero.mxiangcameraviewer"
        minSdk = 30
        targetSdk = 37
        versionCode = versionYear * 1_000_000 + versionRelease
        versionName = appVersion

    }

    // The release key is read from keystore.properties at the project root, which stays out of version control, so no password ever lives in the build script.
    val keystoreFile = rootProject.file("keystore.properties")
    val keystore = Properties().apply {
        if (keystoreFile.exists()) keystoreFile.inputStream().use { load(it) }
    }
    signingConfigs {
        create("release") {
            storeFile = keystore.getProperty("storeFile")?.let { file(it) }
            storePassword = keystore.getProperty("storePassword")
            keyAlias = keystore.getProperty("keyAlias")
            keyPassword = keystore.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

// The release file is named after the app and its version, MxiangCameraViewer_v26.1.apk, the way it is attached to a GitHub release.
androidComponents {
    onVariants { variant ->
        if (variant.buildType == "release") {
            variant.outputs.forEach { it.outputFileName.set("MxiangCameraViewer_v$appVersion.apk") }
        }
    }
}

dependencies {
    // The Go protocol code, built from bridge/mobile with gomobile bind: run ./gradlew buildBridge.
    implementation(files("libs/xmbridge.aar"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// Builds the Go bridge into libs/xmbridge.aar. Run it once, and again whenever the bridge changes. It needs Go, gomobile and the Android NDK; ARM 32-bit is not optional, since many TV sticks run a 32-bit userland.
tasks.register<Exec>("buildBridge") {
    val localProperties = Properties().apply { rootProject.file("local.properties").inputStream().use { load(it) } }
    val sdk = requireNotNull(localProperties.getProperty("sdk.dir")) { "local.properties has no sdk.dir" }
    val goBin = File(System.getProperty("user.home"), "go/bin")

    workingDir = rootProject.file("bridge/mobile")
    environment("ANDROID_HOME", sdk)
    environment("ANDROID_NDK_HOME", "$sdk/ndk/27.2.12479018")
    environment("PATH", goBin.path + File.pathSeparator + System.getenv("PATH"))
    commandLine(
        "gomobile", "bind",
        "-target=android/arm64,android/arm,android/amd64,android/386",
        "-androidapi", "30",
        // Segments aligned to 16 KB, which Google Play asks of TV apps; the NDK r27 aligns to 4 KB unless told to.
        "-ldflags=-extldflags=-Wl,-z,max-page-size=16384",
        "-o", file("libs/xmbridge.aar").path,
        ".",
    )
}
