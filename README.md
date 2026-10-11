# WordCraft Android (unofficial) — APK builder

This is an **unofficial Android WebView wrapper** for the open-source [WordCraft](https://github.com/storytold/wordcraft) Rust word processor. It packages **the actual WordCraft 0.4.0 WebAssembly build locally** inside the APK; it is not a remote website bookmark. You can launch and render WordCraft without an Internet connection after installation.

The upstream app currently publishes Windows/macOS/Linux/BSD and web releases, not an Android APK. This Android wrapper uses Android WebView + JavaScript/WebAssembly. It is experimental, untested on a physical Android device, and **not** a native Rust/Android port. Desktop-size ribbon controls may be small on a phone. WebGPU may be unavailable in WebView; the launcher forces the documented `?webgl` backend. A compatible WebGL2 GPU/WebView is required.

**This ZIP contains buildable source files, not an APK.** The app's published web release is intentionally downloaded during build, so we don't distribute a stale/upstream binary in source control. Build steps below produce the installable, debug-signed `app-debug.apk`.

## Easiest way: GitHub Actions (no Android Studio needed)

1. Create a new GitHub repository under your account, e.g. `wordcraft-android`.
2. Unzip this project, then upload its **contents** to that repository, preserving `.github/workflows/build-apk.yml` and all subdirectories. Alternatively use `git init`, `git add .`, `git commit`, and `git push` to upload it.
3. On GitHub, open **Actions** → **Build Android APK** → **Run workflow**. (A push to `main` also triggers a build.) Enable Actions for the repo if GitHub prompts you.
4. After the workflow succeeds, open its **Artifacts** and download `wordcraft-android-v0.4.0-debug-apk`.
5. Unzip that build artifact to obtain `app-debug.apk`; copy it to your Android phone or tablet and open it to install. Android may ask you to allow installation from this source.

The workflow fetches WordCraft v0.4.0 from its official GitHub release, bundles the WebAssembly assets, downloads MIT/Apache notices, installs Android build tools and generates an Android debug APK. You do not need Rust or a WebAssembly compiler.

## Build on your Windows computer with Android Studio

1. Install Android Studio (with Android SDK platform 35, build tools, and JDK 17).
2. In PowerShell, run `./scripts/prepare-web.ps1` from this project directory. The script downloads the official 0.4.0 WordCraft web release. If execution policies prevent it, use `powershell -ExecutionPolicy Bypass -File .\scripts\prepare-web.ps1`.
3. Open the **project root directory** in Android Studio. Let Gradle sync; it will need the Internet to get the Android Gradle plugin if not cached.
4. Select **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
5. The file will be at `app/build/outputs/apk/debug/app-debug.apk`.

On Linux/macOS, install GitHub CLI (`gh`), `curl`, and `unzip`; run `bash scripts/prepare-web.sh`, then `gradle :app:assembleDebug` with Gradle 8.10.2.

## Compatibility and caveats

- Android 10 (API 29) and newer, 64-bit or 32-bit devices supporting modern Android WebView. The APK itself carries architecture-neutral WebAssembly, not ABI-specific native binaries.
- Both phones and tablets; a **landscape tablet with an external keyboard/mouse** is a more practical fit for the desktop-oriented ribbon interface. No responsive layout changes to upstream UI are claimed.
- App launches to locally packaged `https://appassets.androidplatform.net/wordcraft/index.html?webgl` through a request interceptor, enabling secure-context web APIs. No remote web host is needed.
- File Open uses Android's system file picker. Save/Export tries to catch browser Blob downloads, and writes files to `Downloads/WordCraft/` via Android MediaStore; large exports and all document formats **have not yet been device-tested**.
- Android WebView compatibility with WebGL2, keyboard shortcuts, copy/paste, IME, and PDF export will vary by device/OS version.
- The APK is automatically signed with the Android **debug** key by Gradle, suitable for sideloading/tests. It is **not a production-signed app**, and not published to Google Play.
- The app is an independent, unofficial wrapper. Do not mistake it for an Android release endorsed by the WordCraft developers.

## Updating WordCraft

The Bash workflow accepts `WORDCRAFT_VERSION=v0.x.y` so long as a matching web release archive exists. Also update `versionName`/`versionCode` in `app/build.gradle`. The PowerShell helper currently pins v0.4.0.

## Licences and credits

WordCraft copyright © ArtCraft Team and contributors, licensed MIT OR Apache-2.0. `scripts/prepare-web.*` fetch the upstream MIT/Apache licence files, `NOTICE` and `ATTRIBUTION.md` into the bundled assets. Do not copy or alter the project's protected logos and brand assets. The Android wrapper uses an original generic document icon.

Upstream: https://github.com/storytold/wordcraft

This scaffold was generated for personal experimentation; review Android security, signing, accessibility, file handling, and attribution before distributing it.
