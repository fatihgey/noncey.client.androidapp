# noncey — Android App

SMS OTP feeder for the noncey system. Receives SMS messages, matches them against
active noncey configurations, and forwards matching messages to the daemon via
`POST /api/sms/ingest`.

See `ARCHITECTURE.md` for the full design.

---

## Prerequisites

| Requirement | Version | Notes |
|---|---|---|
| JDK | 17 or higher | [Adoptium](https://adoptium.net) recommended |
| Android SDK | API 26+ (Android 8) | Via Android Studio or standalone SDK |
| `ANDROID_HOME` | — | Set to your SDK root, or install Android Studio |

The Gradle wrapper (`gradlew` / `gradlew.bat`) is included and downloads Gradle
automatically. If `gradle/wrapper/gradle-wrapper.jar` is missing (first clone),
the build scripts will attempt to regenerate it using `gradle wrapper`; in that
case `gradle` must be on your PATH, or open the project in Android Studio once
instead.

---

## Building

### Option A — Android Studio (recommended)

1. Open Android Studio → **Open** → select this directory.
2. Android Studio installs the SDK, wrapper, and dependencies automatically.
3. **Run** (▶) installs directly to a connected device or emulator.
4. **Build → Generate Signed Bundle / APK** for a release build (see signing below).

### Option B — Command line

**Debug APK** (no signing required, for development/testing):

```bash
# Linux / macOS
./build.sh

# Windows
build.bat
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

**Release APK** (requires a keystore — see [Signing](#signing)):

```bash
# Linux / macOS
./build.sh release

# Windows
build.bat release
```

Output: `app/build/outputs/apk/release/app-release.apk`

You can also call Gradle directly after the wrapper is bootstrapped:

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

---

## Signing

Release builds must be signed. The signing config is kept outside the repo in
`keystore.properties` (gitignored).

### 1. Generate a keystore (one-time)

```bash
keytool -genkey -v \
  -keystore noncey.keystore \
  -alias noncey \
  -keyalg RSA -keysize 2048 -validity 10000
```

Store `noncey.keystore` somewhere safe — losing it means you cannot update the
app on devices that already have it installed. **Do not commit it to git.**

### 2. Create `keystore.properties`

Copy the example and fill in your values:

```bash
cp keystore.properties.example keystore.properties
```

```properties
storeFile=../noncey.keystore   # path relative to the app/ directory
storePassword=your_keystore_password
keyAlias=noncey
keyPassword=your_key_password
```

### 3. Build

```bash
./build.sh release   # or build.bat release on Windows
```

The signed APK is in `app/build/outputs/apk/release/app-release.apk`.

---

## Distribution

### Sideloading (direct install)

The simplest option — no app store required.

**Via ADB (developer/internal use):**

```bash
adb install app/build/outputs/apk/release/app-release.apk
```

**Via file transfer:**

1. Copy the APK to the device (USB, cloud storage, email).
2. On the device: **Settings → Apps → Special app access → Install unknown apps** →
   allow the file manager or browser you'll use to open the APK.
3. Tap the APK file to install.

### Firebase App Distribution (recommended for team rollout)

Free internal distribution with install prompts and version tracking.

1. Create a Firebase project at [console.firebase.google.com](https://console.firebase.google.com).
2. Add an Android app with package name `com.noncey.android`.
3. Install the Firebase CLI:
   ```bash
   npm install -g firebase-tools
   firebase login
   ```
4. Upload a release APK:
   ```bash
   ./build.sh release
   firebase appdistribution:distribute app/build/outputs/apk/release/app-release.apk \
     --app YOUR_FIREBASE_APP_ID \
     --groups "testers"
   ```
5. Testers receive an email with a one-tap install link.

### Google Play (public / managed distribution)

For production. Requires a [Google Play Developer account](https://play.google.com/console) ($25 one-time fee).

1. Build a release APK (or AAB for Play — preferred):
   ```bash
   ./gradlew bundleRelease
   ```
   Output: `app/build/outputs/bundle/release/app-release.aab`

2. In Play Console: **Create app → Production → Create new release → Upload AAB**.
3. Fill in store listing, screenshots, privacy policy.
4. Submit for review (typically 1–3 days for new apps).

For internal-only use within an organisation, **Google Play Internal Testing** track
allows up to 100 testers without a full review.

---

## First-time setup on device

After installing the app:

1. Open **noncey** → enter your daemon URL and credentials → **Log in**.
2. Grant **SMS** permissions when prompted.
3. The app runs a foreground service; keep battery optimisation disabled for it
   (**Settings → Battery → noncey → Unrestricted**) to ensure reliable forwarding.

---

## License

MIT — see [LICENSE](LICENSE).
