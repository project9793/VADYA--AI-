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

1. Open the `VidyaAI` folder in Android Studio (Ladybug or newer).
2. Let Studio generate the Gradle wrapper, or run `gradle wrapper --gradle-version 8.9` once.
3. `./gradlew :app:assembleDebug`

Toolchain: AGP 8.7.2, Kotlin 2.0.21, Compose BOM 2024.10.01, compileSdk 35, minSdk 24, JDK 17.

## Notes

- `NetworkMonitor` registers a `ConnectivityManager.NetworkCallback` in `VidyaApp.onCreate()`
  and exposes a `StateFlow<Boolean>`; `HomeScreen` shows an offline banner from it.
- `AiOrb` is a pure-Canvas animated orb with four states (Idle, Listening, Thinking, Speaking).
  Tap the orb on Home to cycle the states.
- Home data is sample/demo data inside `HomeViewModel`; swap it for a real source (API, Room)
  when the backend is ready.
