// Top-level build file
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.10" apply false
    id("androidx.navigation.safeargs.kotlin") version "2.9.3" apply false
    id("com.google.gms.google-services") version "4.4.4" apply false
}