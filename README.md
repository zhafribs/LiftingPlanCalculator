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

Those two files were committed to this repository once, back when it was private.
That history has since been rewritten to remove them, which is why `git log` will
not show them even though the working tree still has them, and why `.gitignore`
lists them. Never commit them again: a keystore committed once stays reachable in
every commit that came after it.

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

The key's restrictions are what protect it, not the repository's visibility: it is
scoped to this package name and signing certificate in the Google Cloud console, so
the file alone is useless without the matching key. This repository is **public**,
which means those restrictions are doing all of the work — check them periodically
and never loosen them.

---

# Releasing

The whole runbook, because most of it is the kind of thing you do twice a year and
cannot remember.

## 1. Bump the version

In `app/build.gradle.kts`:

- **`versionCode`** — must rise for every build you distribute. Play compares it and
  refuses a repeat, and an Android device uses it to decide whether an APK is an
  *upgrade* — so a stale one means the new file will not install over the old.
- **`versionName`** — what a person reads.
- **The APK filename**, in the `androidComponents` block at the foot of the same file.

The scheme is `major*10000 + minor*100 + patch`: v3.3.0 is `30300`, its first patch
`30301`.

## 2. Build, and verify the signature

```bash
./gradlew :app:assembleRelease

# apksigner needs a JDK on PATH
export JAVA_HOME=/opt/android-studio/jbr
apksigner verify --print-certs \
  app/build/outputs/apk/release/lifting-plan-calculator-v3.3.apk
```

**Do not skip the verify.** When `keystore.properties` is missing, the build still
succeeds — `app/build.gradle.kts` reads it defensively on purpose — and it produces
an *unsigned* APK that looks entirely normal until you try to install it.
`apksigner` either prints a certificate or tells you the file is not signed.

## 3. Publish

```bash
gh release create v3.3 \
  app/build/outputs/apk/release/lifting-plan-calculator-v3.3.apk \
  --title "v3.3" --notes "..."
```

## The signing-certificate trap

**Every key that signs a build needs its SHA-1 registered in Firebase, or Google
Sign-In fails in that build. It does not crash — it reports that sign-in is "not set
up for this build", which reads like a broken app rather than a missing setting.**

There are up to **three** keys, and they are all different:

| Key | Signs what | SHA-1 |
|---|---|---|
| Debug | `assembleDebug` on this machine | `fb4691e275601c8dcdad704b446102667b05fbc2` |
| Release | the APK in this repository's Releases | `f2430fbfd05160a9fedc7f5bfba0d59e09a91442` |
| Play App Signing | anything installed from Play | see Play Console → Setup → App signing |

Read one off a keystore with:

```bash
export JAVA_HOME=/opt/android-studio/jbr
keytool -list -v -keystore lifting-plan-release.jks    # prompts for the password
```

Then: **Firebase Console → Project settings → Your app → Add fingerprint**,
re-download `google-services.json`, replace `app/google-services.json`, and commit it.

Register all of them. That is the state you want: debug builds, sideloaded releases
and Play installs then all work, and none of them silently loses the Google button.

Email and password sign-in works regardless, so a build with a missing fingerprint is
usable — it is just missing its quickest route in.
