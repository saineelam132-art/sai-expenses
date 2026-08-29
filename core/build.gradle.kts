plugins {
    kotlin("jvm") version "2.0.21"
}

kotlin {
    // Pinned to the JDK actually available in CI/dev sandboxes; bump alongside app/build.gradle.kts
    // if you upgrade the installed JDK. Using a higher toolchain here would trigger an auto-download
    // that isn't guaranteed to have network access.
    jvmToolchain(21)
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
}
