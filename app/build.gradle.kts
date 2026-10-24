import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // Reads `app/google-services.json` and generates the resource the Firebase
    // SDKs initialise from.  It has to sit in **this** module's directory, not
    // the project root: the plugin looks beside the module's build file, and a
    // config at the root produces a build failure that names a missing file
    // rather than a misplaced one.
    alias(libs.plugins.google.services)
}

// Release signing credentials live outside version control, in
// keystore.properties at the project root.
//
// Read defensively: a checkout without that file still builds.  It simply
// produces an unsigned release APK, which is the honest outcome -- the
// alternative is a build that fails with a complaint about a secret the person
// building it was never given.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.zhafrisbs.liftingplancalculator"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // Unchanged on purpose.  The applicationId *is* the app's identity to
        // Android, so this keeps the native build the same app as the earlier
        // Kivy one rather than introducing a second launcher icon.
        applicationId = "com.zhafrisbs.liftingplancalculator"
        minSdk = 24
        targetSdk = 37
        // `versionCode` is the number Play compares and **must rise for every
        // upload**; `versionName` is the one a person reads.  The scheme is
        // major*10000 + minor*100 + patch, so v3.3.0 is 30300 and its first patch
        // is 30301 -- no one has to remember what the last release used, and a
        // larger number is never handed to an older build.
        versionCode = 30400
        versionName = "3.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
            // v1 (the old JAR signature) is left off: it is only needed for
            // API < 24, and this app's minSdk is 24.  v3 *is* turned on, and the
            // reason is the one thing that cannot be undone later -- it records a
            // proof-of-rotation lineage, so the key can be replaced one day
            // without every installed copy refusing the update.
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            // R8 + resource shrinking: this is a calculation app with no
            // reflection and no serialization, so nothing here needs to survive
            // obfuscation except the Activity, which AGP keeps automatically.
            optimization {
                enable = true
            }
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // Generated off by default since AGP 8, and needed for `BuildConfig`.
        //
        // The About tab prints the version from it rather than reading it out of the
        // PackageManager, which is the other way: `getPackageInfo(String, Int)` is
        // deprecated from API 33, and the replacement takes a `PackageInfoFlags` that
        // only exists from API 33 -- so the "modern" version needs a version check to
        // call it.  A generated constant needs neither.
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

// The name the release APK is published under.
//
// **Only the release build is renamed.**  A debug APK called
// `lifting-plan-calculator-v3.2.1.apk` is a file somebody will send to somebody, and
// the one way to be sure it is not the wrong build is for it not to carry the
// release name.  Debug keeps AGP's own `app-debug.apk`.
//
// The version in this name is written out rather than read from `versionName`, even
// though the two now agree at 3.3.  Bumping a release means editing the version in
// two places, and a mismatch is visible in the file name -- which is where it does
// least harm -- rather than being silently absent from one of them.
//
// (`androidComponents` rather than the old `applicationVariants`, which AGP 9
// removed.)
androidComponents {
    onVariants { variant ->
        if (variant.buildType == "release") {
            variant.outputs.forEach { output ->
                output.outputFileName.set("lifting-plan-calculator-v3.3.1.apk")
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Firebase, for the sign-in gate and the email allowlist.
    //
    // The BoM pins every Firebase artifact to one tested set, so the two below
    // carry no version of their own.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    // Firestore holds the allowlist, and it is the only server-side state this
    // app has.  It exists for one reason: a check compiled into the APK is a
    // check anyone can delete.
    implementation(libs.firebase.firestore)

    // Google Sign-In, through Credential Manager -- the current API, where the old
    // `GoogleSignInClient` is deprecated.  It hands back a Google ID token, which
    // Firebase exchanges for a session; it is not a second sign-in system, just a
    // second way to get one.
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // **No `firebase-analytics`, deliberately.**  The console's setup snippet
    // offers it, and it would ship telemetry about a lifting-plan app used by a
    // handful of named people -- while also pulling in the AD_ID permission that
    // Play then demands a declaration for.  Nothing in this app reads it back.

    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
