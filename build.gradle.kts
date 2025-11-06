// Top-level build file where you can add configuration options common to all sub-projects/modules.
// Top-level build file
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.10" apply false
    id("androidx.navigation.safeargs.kotlin") version "2.9.3" apply false
}