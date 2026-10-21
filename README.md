# SoftAlarm

Simple Android alarm that wakes you gently (volume ramps up). Kotlin, minSdk 24, targetSdk 34.

## Build
- Android Studio: open this folder and press Run.
- Command line (JDK 17 + Android SDK): `./gradlew assembleDebug`
  -> `app/build/outputs/apk/debug/app-debug.apk`
- GitHub: push this folder as the repo root to `main`; the workflow in `.github/workflows` builds the APK.

## Notes
- The alarm is not restored after a phone reboot (no boot receiver yet).
- Android 13+ asks for notification permission on first launch; allow it so the alarm screen can show.
