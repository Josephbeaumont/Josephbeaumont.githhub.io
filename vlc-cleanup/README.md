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

Videos are grouped the way VLC groups them: videos whose names start with the same
6 letters go together (e.g. `20161104_192357.mp4` and `20161104_191935.mp4` → "20161104_19").
Tap a group to open it. In the ⋮ menu you can group by folder instead, turn grouping off,
or change the number of letters to match VLC's own setting. Groups you made by hand in VLC
can't be copied, because VLC keeps them private.

### Making a group from a Google Photos album

Google Photos doesn't let other apps read albums, so pick the videos instead: in VLC Cleanup
tap **⋮ → Add a Google Photos album… → Pick from Google Photos**, open the album under
**Albums**, select its videos, tap **Done**, name the group and tap **Save**. (Sharing the videos
from Google Photos to VLC Cleanup also works where the share menu offers it.) To add more videos
later, pick them the same way and choose **Add to existing group**. VLC Cleanup matches them to the videos on your phone. Long-press the group to
rename or remove it (removing a group never deletes videos). This is also in the ⋮ menu.

The **My groups** tab shows only the groups you made, with a button to make a new one.

Use the sort button (top right) to sort by size, newest, oldest or name.

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
