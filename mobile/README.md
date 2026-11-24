# JSM mobile apps (Capacitor)

The Android and iOS apps are the existing JSM pages packaged in a native
WebView shell. The job seeker, employer and admin pages, the design system and
`assets/js/api.js` are shared with the web build — there is no second copy of
the UI to maintain.

The apps talk to the same production API (`https://enuvex.onrender.com/api`),
so accounts, jobs and applications are the same data as the website.

## Requirements

- Node 20 or newer
- **Android:** Android Studio with JDK 21 and an Android SDK
- **iOS:** macOS with Xcode 16+ and CocoaPods

## Build the Android app

```bash
cd mobile
npm install
npm run sync:android   # rebuilds www/ from the shared pages, then copies it into android/
npm run open:android   # opens the project in Android Studio
```

In Android Studio press **Run ▶** on a device or emulator. For a Play Store
upload use **Build → Generate Signed App Bundle**, which produces a `.aab`.

## Build the iOS app

```bash
cd mobile
npm install
npx cap add ios        # once, on macOS
npm run sync:ios
npm run open:ios       # opens the workspace in Xcode
```

Xcode needs a signing team under **Signing & Capabilities**; then archive and
upload to App Store Connect as usual.

## How the pages get into the app

`scripts/copy-web.mjs` rebuilds `mobile/www/` from the repository root:
`*.html`, `assets/`, `job-seeker/`, `employer/` and `admin/` — the same set
`backend/pom.xml` copies into the server jar.

`www/` and `node_modules/` are generated and git-ignored. Do not edit `www/`
by hand: after changing any page or asset at the repository root, run one of
the `sync` scripts again.

## API access and CORS

`assets/js/api.js` already points at the production Render API. A Capacitor
WebView reports the origin `https://localhost` on Android and
`capacitor://localhost` on iOS, and both are allowed in
`backend/src/main/java/com/jobskills/config/SecurityConfig.java`. The backend
must be deployed with that change before the packaged apps can sign in.

## Not included

Push notifications, native plugins (camera, biometrics, geolocation) and store
screenshots. Each of those is an additive plugin or asset; the shared web pages
need no changes for them.

## App identity

The launcher icon, adaptive-icon layers (navy background, JSM mark inside the
64dp safe zone) and splash screens are the JSM brand from `assets/brand/`.
`strings.xml` and `capacitor.config.json` set the launcher label to **JSM**; the
application id stays `com.enuvex.app` so existing installs and the package
history are preserved.

Build a debug APK without Android Studio:

```bash
cd mobile
npm install && npx cap sync android
cd android && ./gradlew assembleDebug   # app/build/outputs/apk/debug/app-debug.apk
```
