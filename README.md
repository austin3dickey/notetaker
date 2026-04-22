# notetaker

An Android app for shareable checklist notes. See `CLAUDE.md` for the feature
spec and `ROADMAP.md` for what's shipped and what's next.

## Testing on your Android device

There's no release signing config yet (tracked in ROADMAP M6), so distribution
is debug-APK sideload only. Two ways to get the app onto your phone.

**Prerequisites (both options).** Gradle needs `JAVA_HOME` and `ANDROID_HOME`
in the environment; they live in `~/.zshenv` so fresh zsh shells already
have them. From a different shell, export them first — see the "Running
the build locally" section of `CLAUDE.md`.

### Option 1 — adb install (fastest if you already have adb)

1. Enable **Developer options** on your phone (tap Build number 7 times in
   Settings → About phone) and turn on **USB debugging**.
2. Plug the phone in and approve the RSA fingerprint prompt on the device.
3. Build and install:

    ```bash
    ./gradlew installDebug
    ```

    `installDebug` builds `app-debug.apk` and pushes it in one step. You can
    also build first (`./gradlew assembleDebug`) and install by hand with
    `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
4. Launch **notetaker** from the app drawer.

`adb devices` should list your phone before `installDebug` — if it says
`unauthorized`, re-approve the RSA prompt on the device.

### Option 2 — build + transfer + sideload (no cable)

1. Build the APK:

    ```bash
    ./gradlew assembleDebug
    ```

    The output lands at `app/build/outputs/apk/debug/app-debug.apk`.
2. Move the APK to your phone (AirDrop to a Mac-connected Files app, Google
   Drive, email, etc.).
3. On the phone, open the APK. Android will ask you to allow installs from
   that source the first time. Approve and install.

### Updating to a newer build

Re-run `./gradlew installDebug` (or `adb install -r …`) — `-r` / `installDebug`
both reinstall over the existing app and preserve its data. If you need a clean
database (for testing schema fallback, for example), uninstall first:

```bash
adb uninstall com.notetaker
```

### Troubleshooting

- **`adb` not found** — Android Studio installs it at
  `~/Library/Android/sdk/platform-tools/adb` on macOS. If you use the Homebrew
  command-line tools, it's at
  `/opt/homebrew/share/android-commandlinetools/platform-tools/adb`. Either
  add that directory to `PATH` or use the full path.
- **"App not installed" on the device** — usually means an older signed copy
  of `com.notetaker` is already installed. Uninstall it first.
- **"INSTALL_FAILED_INSUFFICIENT_STORAGE"** — free up some space and retry.

### Release builds on-device

`./gradlew assembleRelease` produces `app/build/outputs/apk/release/app-release-unsigned.apk`
with R8 + resource shrinking applied. Because it's unsigned, it **won't install**
until a signing config lands (ROADMAP M6). For now, stick to the debug variant
on-device.

## Running the build locally

See the "Running the build locally" section of `CLAUDE.md` — same commands
whether you're running tests by hand or asking an agent to.
