// Top-level build file where you can add configuration options common to all sub-projects/modules.
// AGP 9 has built-in Kotlin support, so the kotlin-android plugin is deliberately absent.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false

    // The Google services plugin, applied in :app rather than here.  It reads
    // `app/google-services.json` at build time and generates the resource the
    // Firebase SDKs initialise themselves from.  Declaring it here with
    // `apply false` is how a version is shared with the module without applying
    // it to the root project, which has no Android app of its own.
    alias(libs.plugins.google.services) apply false
}
