// Top level build file

plugins {
    id("org.jlleitschuh.gradle.ktlint") version "12.0.2" apply false
}

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.2.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.24")
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.47")
    }
}

// ktlint is applied to the modules only: the root project also contains the
// legacy `src/` folder, which is not part of any build.
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
}
