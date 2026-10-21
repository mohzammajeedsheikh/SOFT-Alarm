# SoftAlarm

A gentle Android alarm clock with alarms, a timer and a stopwatch. Alarms can ring with
recorded "voices" (Mother, Teacher, Azan, ...) in English and Urdu. Written in Kotlin using only
the Android framework (no extra libraries).

## Build the APK on GitHub (no Android Studio needed)

1. Create a new GitHub repository and push this folder to the `main` branch.
2. Open the **Actions** tab. The **Build APK** workflow runs on every push, or run it by hand with **Run workflow**.
3. When it finishes, open the run and download **SoftAlarm-debug-apk** from *Artifacts*. Unzip it and install `app-debug.apk` on your phone.

## Build locally

Requires JDK 17, the Android SDK and Gradle 8.9+.

```bash
gradle :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

To add the Gradle wrapper (optional): `gradle wrapper --gradle-version 8.9`, then commit `gradlew`, `gradlew.bat` and `gradle/`.

## Voices

Put your mp3 files in `app/src/main/assets/audio/`. File names are listed in
`app/src/main/assets/audio/README.txt`. A missing file falls back to the phone's alarm sound.
Which voices are free or Pro is set in `Voices.kt`.

## Pro unlock (optional)

See [PRO_SETUP.md](PRO_SETUP.md) for the Paddle payment backend (`backend-stub/`).
Until `BASE_URL` in `ProManager.kt` is set, the Buy button shows "not set up yet".

## Project layout

```
.github/workflows/build-apk.yml   CI that builds the debug APK
app/                              Android app (package com.example.hello)
backend-stub/                     Small Node 18+ server for Paddle payments
```
