# Phone-only deployment using GitHub Actions

Same proven flow as the PIMS scanner. You do not need Android Studio or a computer.

## What you need
- Your Samsung phone
- A free GitHub account
- This project's files

## Part 1 — Create the GitHub repository
1. Open https://github.com in Chrome on your Samsung.
2. Sign in.
3. Tap your profile menu, then **Your repositories**.
4. Tap **New**.
5. Repository name: `PTX7-Cycle-Count-Scanner`
6. Choose **Private**.
7. Tap **Create repository**.

## Part 2 — Upload the project files
Upload these top-level items, preserving folder structure:
- `.github`
- `app`
- `build.gradle.kts`
- `gradle.properties`
- `settings.gradle.kts`
- `gradlew`
- `gradlew.bat`

If the GitHub website is awkward on a phone, use the GitHub app, Termux, or a Git
client that supports folder commits — the same method you used for PIMS.

## Part 3 — Run the cloud build
1. Open the repository.
2. Tap **Actions**.
3. Select **Build Android APK**.
4. Tap **Run workflow**, then the green **Run workflow** button.

## Part 4 — Download the APK
1. Wait for the green checkmark.
2. Open the completed run.
3. Scroll to **Artifacts**.
4. Download `PTX7-Cycle-Count-Scanner-debug`.
5. Open the ZIP in Samsung **My Files** and extract `app-debug.apk`.

## Part 5 — Install on Samsung
1. Tap `app-debug.apk`.
2. If prompted, allow **Install unknown apps**.
3. Tap **Install**.

## First test
1. Open **PTX7 Cycle Count**.
2. Tap **SCAN A PAC** and scan a known item label (e.g. `PAC13180`).
3. Confirm the correct item appears with its Min / Max.
4. Enter a count and tap **SAVE COUNT**.
5. On Home, confirm progress increased and the item moved to "Already counted".
6. Tap **EXPORT TODAY'S COUNTS** and try **Copy counts column**.

## Notes
- No Amazon credentials are stored. No network connection is used.
- The camera is handled by Google Play Services; the app itself requests no
  camera permission in the manifest.
