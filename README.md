# PassVault (Android)

KeePass-compatible password manager for Android. Opens and saves the same `.kdbx`
files as KeePass 2.x on Windows.

## Features
- Open / create KDBX databases (KDBX 3.1 and 4.x; AES-256, ChaCha20, Twofish; AES-KDF, Argon2d/id)
- Master password and/or key file (KeePass XML key files v1/v2, raw 32/64-byte, or any file)
- Groups: browse, create, rename, delete (recycle bin like desktop KeePass)
- Entries: view, add, edit (keeps history), delete, custom fields (protected or plain), tags
- Search over title, user name, URL, notes, tags and plain custom fields
- Password generator with strength meter
- Copy to clipboard (flagged sensitive) with auto-clear
- Auto-lock on inactivity / in background; screenshots blocked (FLAG_SECURE)
- Fingerprint unlock: master password sealed with an Android Keystore key that requires biometric auth

Files are opened through Android's file picker (Storage Access Framework), so local storage,
Google Drive, Dropbox, Syncthing folders etc. all work. Changes are written back to the same file.

## Build
Requires JDK 17+ and the Android SDK (path in `local.properties`).

    gradlew assembleRelease        # app/build/outputs/apk/release/app-release.apk
    gradlew testDebugUnitTest      # JVM tests for vault logic

The release build is signed with the debug key so it installs directly. Use your own keystore
before distributing it.

## Not included yet
Android Autofill service, attachments export/import, entry expiry editing, KDF tuning UI,
merge/sync conflict handling (last write wins — avoid editing the same file on two devices at once).
