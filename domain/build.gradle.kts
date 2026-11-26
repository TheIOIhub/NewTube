plugins {
    kotlin("jvm")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    // PagingSource lives in the pure-Kotlin artifact; no Android runtime here.
    implementation("androidx.paging:paging-common:3.2.1")
    // Domain entities and use cases
}
