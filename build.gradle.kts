// Top level build file

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.0.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.0")
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.47")
    }
}

pluginManagement {
    repositories {
        google()
        mavenCentral()
    }
    resolutionStrategy {
        eachPlugin {
            // force specific plugin versions if needed
        }
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
