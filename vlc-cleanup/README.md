# VLC Cleanup

A small Android app for freeing up space: watch a video in VLC, and when you close it
(or it ends) you get asked **"Delete this video?"**

## How it works

1. Open **VLC Cleanup**. It lists every video on your phone, biggest first, with the
   free space shown at the top.
2. Tap a video. It plays in **VLC**.
3. When the video ends or you exit the player, VLC returns you to VLC Cleanup, which asks
   whether to delete the video. Tap **Delete** or **Keep**.
   - On Android 11 and newer, Android shows its own "Allow VLC Cleanup to delete this video?"
     prompt as well. Apps can't skip that prompt. Tap **Allow**.
   - Deleting is permanent. The file doesn't go to a recycle bin.
4. Tap the trash icon on a video (or long-press it) to delete it without watching it.

Use the sort button (top right) to sort by size, newest, oldest or name, and the folder
chips (Camera, Download, …) to show one folder at a time.

## Install

1. Install **VLC for Android** from the Play Store if you don't have it.
2. On your phone, open this repository's **Releases** page on GitHub and download
   `VLC-Cleanup.apk` from the newest *VLC Cleanup* release.
3. Open the downloaded file. Android asks you to allow installing apps from your browser
   (or Files app) — allow it, then tap **Install**.
4. Open VLC Cleanup and allow access to your videos.

New builds install over the old one, so updating is just downloading the new APK.

## Building

GitHub Actions builds the app (`.github/workflows/vlc-cleanup.yml`) on every push that
changes this folder. Each build publishes a release with the APK. To build it locally with
the Android SDK installed: `gradle assembleRelease` in this folder.

`app/sideload.keystore` is a throwaway signing key, so every build can update the
previous one on your phone. It's only meant for installing on your own phone.
