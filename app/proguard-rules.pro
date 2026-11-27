# Release builds use R8 with AGP's proguard-android-optimize.txt defaults.
# No app-specific keep rules are needed: Room/Hilt/Dagger code is generated at
# compile time and kotlinx.serialization uses generated serializers, not reflection.
