# Viewing Himangshu Music user counts

The GMS app already includes Firebase Analytics. When Firebase is configured, its automatic app/session events let you see **Active users** in the Firebase Console; no account sign-in or personal user ID is added by this setup.

## Set up Firebase

1. Create or choose a Firebase project and enable Google Analytics for it.
2. Register an Android app with package name `echo.music.iad1tya` and download its `google-services.json`.
3. For a local build, put the file at `app/google-services.json`. It is gitignored; do not commit it.
4. For signed APKs built by `.github/workflows/build-release-apk.yml`, base64-encode that JSON and save the output as the GitHub Actions repository secret `FIREBASE_GOOGLE_SERVICES_JSON_BASE64`. The workflow decodes it only on the signed-release path and checks the Android package before building. Do not send the value in chat.

Linux: `base64 -w0 app/google-services.json`

macOS: `base64 app/google-services.json | tr -d '\n'`

If the secret is absent, the workflow still builds the APK, but Firebase Analytics will not receive app activity from that build. The unsigned debug-preview path intentionally skips Firebase configuration.

## Where to see the number

In Firebase Console, open the project, choose **Analytics → Dashboard**, and read **Active users** for the date range you want. Data starts only after people install and open a build that contains this Firebase configuration; GitHub release downloads are not counted as users, and earlier activity is not backfilled.

**Active users are an analytics estimate of active app instances, not a verified count of unique people or all-time installs.** People using multiple devices, reinstalling, or disabling analytics can affect the count. FOSS builds do not include Firebase Analytics.
