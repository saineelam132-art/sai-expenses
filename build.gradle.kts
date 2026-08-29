// Intentionally empty: each subproject declares its own plugins (with versions) so that
// running `:core:test` (a pure-JVM module) never needs to resolve the Android Gradle Plugin
// or Google's Maven repo. Combined with org.gradle.configureondemand=true in gradle.properties,
// this keeps the core module buildable/testable in environments without Android SDK/network
// access to dl.google.com.
