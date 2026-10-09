# Lifting Plan Calculator

An Android app that sizes a crane lift and prints the working: the load at the hook,
the crane checked against its own load chart, and the slings for uniform, off-centre
and two-crane lifts. Every figure is printed with the formula and the values that
produced it, so a reviewer can check the arithmetic rather than take it on trust.

A port of a Kivy desktop application, and it keeps that project's wording, section
order and arithmetic.

## Requirements

- Android Studio, with JDK 11 or newer
- Android SDK 37 (`compileSdk`/`targetSdk` are both 37; `minSdk` is 24)

## Setup

```bash
git clone https://github.com/zhafribs/LiftingPlanCalculator.git
```

Open the folder in Android Studio. `local.properties` (the SDK path) is per-machine
and generated on first sync.

**For a release build** you also need the signing material, which is deliberately
**not** in this repository:

```
keystore.properties        storeFile / storePassword / keyAlias / keyPassword
lifting-plan-release.jks   the keystore itself
```

`app/build.gradle.kts` reads both defensively: without them the build still
succeeds, it simply produces an unsigned release APK. Restore them from your
backup before cutting a release — **losing the keystore means you can never update
the app under its existing Play listing again.**

## Build and test

```bash
./gradlew :app:assembleDebug       # debug APK
./gradlew :app:assembleRelease     # release APK  -> app/build/outputs/apk/release/
./gradlew :app:testDebugUnitTest   # unit tests
```

The release APK is named `lifting-plan-calculator-v<version>.apk` by `app/build.gradle.kts`.

## Layout

```
core/       the calculations, with no Android or Compose imports -- so they are
            unit-testable, and most of the test suite lives against them
ui/         Compose screens; one file per tab
assets/katex/   the bundled maths renderer, for the printed report
firestore.rules the email allowlist, which is what actually enforces access
```

## Access

The app is limited to approved email addresses. That is enforced by **Firestore
security rules**, not by the app — a check compiled into an APK can be deleted by
anyone willing to unzip it, so the server is what refuses.

- `firestore.rules` holds the rules; deploy them to the Firebase project.
- To approve someone, add a document to the `allowed` collection whose **ID is their
  lowercased email**. Nothing else is needed.
- The rules require a **verified** address. Without that, anyone could sign up using
  an address on the list without controlling it.

## A note on `app/google-services.json`

It **is** committed, because Google's guidance is that it is not a secret and
because the build cannot run without it. The API key inside it is restricted by
package name and signing certificate.

That makes this repository's visibility a decision: keep it **private**, or restrict
the key in the Google Cloud console.
