# VidyaAI

An Android AI tutor app for students, built with Jetpack Compose and Material 3.

## What is in here

```
VidyaAI/
├─ settings.gradle.kts
├─ build.gradle.kts
└─ app/
   ├─ build.gradle.kts
   └─ src/main/
      ├─ AndroidManifest.xml
      ├─ res/values/strings.xml
      └─ java/com/vidya/ai/
         ├─ VidyaApp.kt
         ├─ MainActivity.kt
         ├─ core/NetworkMonitor.kt
         └─ ui/
            ├─ theme/Theme.kt
            ├─ components/AiOrb.kt
            ├─ navigation/VidyaNavHost.kt
            ├─ home/HomeViewModel.kt
            ├─ home/HomeScreen.kt
            └─ common/ComingSoonScreen.kt
```

A few supporting resource files are included so the project actually builds:
`res/values/colors.xml`, `res/values/themes.xml` and launcher icons under `res/mipmap-*`.

## Build

1. Open the `VidyaAI` folder in Android Studio.
2. Let Studio generate the Gradle wrapper, or run `gradle wrapper --gradle-version 8.7` once.
3. `./gradlew :app:assembleDebug`

Toolchain: AGP 8.5.2, Kotlin 1.9.24, Compose compiler 1.5.14, Compose BOM 2024.06.00,
compileSdk 34, targetSdk 34, minSdk 24, JDK 17, Gradle 8.7 or newer.

## Notes

- `NetworkMonitor` registers a `ConnectivityManager.NetworkCallback` in `VidyaApp.onCreate()`
  and exposes a `StateFlow<Boolean>`; `HomeScreen` shows an offline banner from it.
- `AiOrb` is a pure-Canvas animated orb with four states (Idle, Listening, Thinking, Speaking).
  Tap the orb on Home to cycle the states.
- Home data is sample/demo data inside `HomeViewModel`; swap it for a real source (API, Room)
  when the backend is ready.
