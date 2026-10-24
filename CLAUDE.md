# Instructions for Claude (user's permanent preferences)

## Language
- Reply to the user and give all instructions in **English**.
- The user speaks **Urdu**, not Hindi. If the user's own words ever need to be written down or quoted, use **Urdu script**, never Devanagari (Hindi).
- Code, commit messages and technical names stay in English.

## Way of working
- Before starting any task, give the user a short plan:
  1. What I understood.
  2. What I am going to do next.
- Keep answers short and practical (Completed / Important Issues / Validation / Next Action).

## Project notes
- App: Artify Scanner (Android, Kotlin, Jetpack Compose).
- All scanning uses the Google ML Kit Document Scanner; the app has no camera of its own.
- Builds run in GitHub Actions (`.github/workflows/build-apk.yml`); the APK is an artifact of the run.
- CI signs debug builds with the fixed key `ci/debug.keystore` (SHA-1 FB:81:95:73:96:2D:5B:4C:8D:AD:EB:4C:89:7E:7B:D7:CA:D7:BA:A6) so Google sign-in keeps working.
