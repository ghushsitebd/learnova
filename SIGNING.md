# Learnova Signing & Install/Update Notes

## Current CI build

The GitHub Actions workflow creates a temporary CI release keystore for each run. This is suitable for testing the generated APK/AAB, but it is **not** a permanent production signing identity.

### Important consequence

An APK signed by one CI run may not update an APK signed by a later CI run, because Android requires an update to use the same signing identity.

The current workflow therefore should be treated as a **test/share build**, not a stable production update channel.

## Before Play Store release

Use a permanent upload key and enable Google Play App Signing. Keep the private key and passwords outside the repository, preferably in GitHub Actions secrets or another secure secret manager.

Do not commit a production keystore, private key, or signing password to this repository.

## Current verification

The release workflow successfully produces:

- `Learnova.apk`
- `Learnova.aab`
- SHA-256 checksum files

The AAB is intended for Google Play distribution. The APK is intended for direct testing/sharing.

## Safe next step

When a permanent signing key is ready, configure the workflow to read it from protected CI secrets. Do not replace the current test key with a newly generated key on every run.
