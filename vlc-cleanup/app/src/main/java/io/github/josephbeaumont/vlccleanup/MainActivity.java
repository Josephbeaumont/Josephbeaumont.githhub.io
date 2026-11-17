package io.github.josephbeaumont.vlccleanup;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.RecoverableSecurityException;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.provider.MediaStore;
import android.text.format.Formatter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Lists the videos on the phone. Tapping one opens it in VLC; when VLC closes
 * (the video ended or you backed out), this app asks whether to delete it.
 */
public class MainActivity extends Activity {

    private static final String VLC_PACKAGE = "org.videolan.vlc";

    private static final int REQ_PERMISSION = 1;
    private static final int REQ_VLC = 2;
    private static final int REQ_DELETE = 3;       // Android 11+ system delete dialog
    private static final int REQ_DELETE_RETRY = 4; // Android 10 "allow access" dialog

    // Result codes VLC returns (see VLC for Android's player intent docs)
    private static final int VLC_CONNECTION_FAILED = 2;
    private static final int VLC_PLAYBACK_ERROR = 3;
    private static final int VLC_HW_ACCEL_ERROR = 4;
    private static final int VLC_VIDEO_TRACK_LOST = 5;

    private static final int SORT_SIZE = 0, SORT_NEWEST = 1, SORT_OLDEST = 2, SORT_NAME = 3;
    private static final String[] SORT_LABELS = {"Biggest first", "Newest first", "Oldest first", "Name"};

    /** One video from the phone's media library. */
    static final class Video {
        final long id;
        final Uri uri;
        final String name;
        final long size;
        final long durationMs;
        final long dateAdded;
        final String folder;

        Video(long id, Uri uri, String name, long size, long durationMs, long dateAdded, String folder) {
            this.id = id;
            this.uri = uri;
            this.name = name;
            this.size = size;
            this.durationMs = durationMs;
            this.dateAdded = dateAdded;
            this.folder = folder;
        }
    }

    private final List<Video> videos = new ArrayList<>();
    private VideoAdapter adapter;
    private TextView header;
    private LinearLayout emptyView;
    private TextView emptyText;
    private Button emptyButton;

    /** The video handed to VLC, or waiting to be deleted. Survives the app being killed while VLC plays. */
    private Video current;
    private long freedThisSession;
    private int sortMode = SORT_SIZE;

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        sortMode = prefs.getInt("sort", SORT_SIZE);

        if (savedInstanceState != null) {
            freedThisSession = savedInstanceState.getLong("freed");
            if (savedInstanceState.containsKey("cur_id")) {
                current = new Video(
                        savedInstanceState.getLong("cur_id"),
                        Uri.parse(savedInstanceState.getString("cur_uri")),
                        savedInstanceState.getString("cur_name"),
                        savedInstanceState.getLong("cur_size"),
                        savedInstanceState.getLong("cur_dur"),
                        0, "");
            }
        }

        buildUi();

        if (!hasReadPermission()) {
            showEmpty("VLC Cleanup needs access to your videos so it can list them and offer to delete the ones you're done with.",
                    "Allow access", v -> requestReadPermission());
            requestReadPermission();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasReadPermission()) loadVideos();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putLong("freed", freedThisSession);
        if (current != null) {
            out.putLong("cur_id", current.id);
            out.putString("cur_uri", current.uri.toString());
            out.putString("cur_name", current.name);
            out.putLong("cur_size", current.size);
            out.putLong("cur_dur", current.durationMs);
        }
    }

    // ---------------------------------------------------------------- UI

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        header = new TextView(this);
        header.setPadding(dp(16), dp(12), dp(16), dp(12));
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        ListView list = new ListView(this);
        adapter = new VideoAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> playInVlc(videos.get(pos)));
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            current = videos.get(pos);
            askToDelete(current, null);
            return true;
        });

        emptyView = new LinearLayout(this);
        emptyView.setOrientation(LinearLayout.VERTICAL);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(dp(32), dp(32), dp(32), dp(32));
        emptyText = new TextView(this);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        emptyView.addView(emptyText);
        emptyButton = new Button(this);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2);
        bp.topMargin = dp(16);
        emptyView.addView(emptyButton, bp);

        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(emptyView, new LinearLayout.LayoutParams(-1, 0, 1));
        list.setEmptyView(emptyView);

        setContentView(root);
    }

    private void showEmpty(String message, String buttonLabel, View.OnClickListener onClick) {
        emptyText.setText(message);
        if (buttonLabel == null) {
            emptyButton.setVisibility(View.GONE);
        } else {
            emptyButton.setVisibility(View.VISIBLE);
            emptyButton.setText(buttonLabel);
            emptyButton.setOnClickListener(onClick);
        }
        header.setText(freeSpaceText());
    }

    private void updateHeader() {
        long total = 0;
        for (Video v : videos) total += v.size;
        StringBuilder sb = new StringBuilder();
        sb.append(videos.size()).append(videos.size() == 1 ? " video · " : " videos · ")
                .append(size(total)).append('\n').append(freeSpaceText());
        if (freedThisSession > 0) sb.append(" · freed ").append(size(freedThisSession)).append(" so far");
        sb.append("\nTap to watch in VLC · long-press to delete now");
        header.setText(sb);
    }

    private String freeSpaceText() {
        try {
            StatFs fs = new StatFs(Environment.getExternalStorageDirectory().getPath());
            return size(fs.getAvailableBytes()) + " free on phone";
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Sort: " + SORT_LABELS[sortMode]).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        menu.add(0, 2, 1, "Refresh");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            new AlertDialog.Builder(this)
                    .setTitle("Sort videos")
                    .setSingleChoiceItems(SORT_LABELS, sortMode, (d, which) -> {
                        sortMode = which;
                        getPreferences(MODE_PRIVATE).edit().putInt("sort", sortMode).apply();
                        sortVideos();
                        adapter.notifyDataSetChanged();
                        invalidateOptionsMenu();
                        d.dismiss();
                    })
                    .show();
            return true;
        } else if (item.getItemId() == 2) {
            if (hasReadPermission()) loadVideos();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private final class VideoAdapter extends BaseAdapter {
        @Override public int getCount() { return videos.size(); }
        @Override public Object getItem(int i) { return videos.get(i); }
        @Override public long getItemId(int i) { return videos.get(i).id; }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            LinearLayout row;
            TextView title, detail;
            if (convert == null) {
                row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(16), dp(12), dp(16), dp(12));
                title = new TextView(MainActivity.this);
                title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                title.setTypeface(Typeface.DEFAULT_BOLD);
                title.setMaxLines(2);
                detail = new TextView(MainActivity.this);
                detail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                detail.setAlpha(0.7f);
                row.addView(title);
                row.addView(detail);
            } else {
                row = (LinearLayout) convert;
                title = (TextView) row.getChildAt(0);
                detail = (TextView) row.getChildAt(1);
            }
            Video v = videos.get(i);
            title.setText(v.name);
            StringBuilder sb = new StringBuilder(size(v.size));
            if (v.durationMs > 0) sb.append(" · ").append(time(v.durationMs));
            if (v.folder != null && !v.folder.isEmpty()) sb.append(" · ").append(v.folder);
            detail.setText(sb);
            return row;
        }
    }

    // ---------------------------------------------------------------- permissions

    private String readPermission() {
        return Build.VERSION.SDK_INT >= 33
                ? "android.permission.READ_MEDIA_VIDEO"
                : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    private boolean hasReadPermission() {
        if (Build.VERSION.SDK_INT < 23) return true;
        if (checkSelfPermission(readPermission()) != PackageManager.PERMISSION_GRANTED) return false;
        // Android 9 and older also need write access to delete files.
        return Build.VERSION.SDK_INT > 28
                || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestReadPermission() {
        if (Build.VERSION.SDK_INT < 23) return;
        String[] perms = Build.VERSION.SDK_INT <= 28
                ? new String[]{readPermission(), Manifest.permission.WRITE_EXTERNAL_STORAGE}
                : new String[]{readPermission()};
        requestPermissions(perms, REQ_PERMISSION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_PERMISSION) return;
        if (!hasReadPermission()) {
            showEmpty("Without access to your videos this app can't list or delete them.\n\n"
                            + "If the button doesn't bring up a prompt, allow \"Videos\" (or \"Files and media\") "
                            + "for VLC Cleanup in your phone's Settings → Apps.",
                    "Try again", v -> requestReadPermission());
        }
    }

    // ---------------------------------------------------------------- loading

    private void loadVideos() {
        videos.clear();
        Uri collection = Build.VERSION.SDK_INT >= 29
                ? MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                : MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
        };
        try (Cursor c = getContentResolver().query(collection, projection, null, null, null)) {
            if (c != null) {
                while (c.moveToNext()) {
                    long id = c.getLong(0);
                    String name = c.getString(1);
                    videos.add(new Video(
                            id,
                            ContentUris.withAppendedId(collection, id),
                            name != null ? name : "(unnamed video)",
                            c.getLong(2),
                            c.getLong(3),
                            c.getLong(4),
                            c.getString(5)));
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't read videos: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        sortVideos();
        adapter.notifyDataSetChanged();
        if (videos.isEmpty()) {
            showEmpty("No videos found on this phone.", null, null);
        } else {
            updateHeader();
        }
    }

    private void sortVideos() {
        Comparator<Video> cmp;
        switch (sortMode) {
            case SORT_NEWEST: cmp = (a, b) -> Long.compare(b.dateAdded, a.dateAdded); break;
            case SORT_OLDEST: cmp = (a, b) -> Long.compare(a.dateAdded, b.dateAdded); break;
            case SORT_NAME:   cmp = (a, b) -> a.name.compareToIgnoreCase(b.name); break;
            default:          cmp = (a, b) -> Long.compare(b.size, a.size); break;
        }
        Collections.sort(videos, cmp);
    }

    // ---------------------------------------------------------------- VLC

    private void playInVlc(Video v) {
        current = v;
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setPackage(VLC_PACKAGE);
        intent.setDataAndType(v.uri, "video/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.putExtra("title", v.name);
        try {
            // VLC sends a result back when the player closes, which lands in onActivityResult.
            startActivityForResult(intent, REQ_VLC);
        } catch (ActivityNotFoundException e) {
            current = null;
            new AlertDialog.Builder(this)
                    .setTitle("VLC isn't installed")
                    .setMessage("This app plays your videos in VLC for Android. Install it from the Play Store, then come back.")
                    .setPositiveButton("Open Play Store", (d, w) -> {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + VLC_PACKAGE)));
                        } catch (ActivityNotFoundException ex) {
                            startActivity(new Intent(Intent.ACTION_VIEW,
                                    Uri.parse("https://play.google.com/store/apps/details?id=" + VLC_PACKAGE)));
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    private static long longExtra(Intent data, String key) {
        if (data == null || data.getExtras() == null) return -1;
        Object o = data.getExtras().get(key);
        return o instanceof Number ? ((Number) o).longValue() : -1;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_VLC) {
            if (current == null) return;
            if (resultCode == VLC_CONNECTION_FAILED || resultCode == VLC_PLAYBACK_ERROR
                    || resultCode == VLC_HW_ACCEL_ERROR || resultCode == VLC_VIDEO_TRACK_LOST) {
                Toast.makeText(this, "VLC had trouble playing that video.", Toast.LENGTH_LONG).show();
            }
            long position = longExtra(data, "extra_position");
            long duration = longExtra(data, "extra_duration");
            if (duration <= 0) duration = current.durationMs;

            String progress = null;
            if (position > 0 && duration > 0 && position < duration * 0.9) {
                progress = "You stopped at " + time(position) + " of " + time(duration) + ".";
            }
            askToDelete(current, progress);

        } else if (requestCode == REQ_DELETE) {
            // Android 11+: the system dialog did the delete if the user tapped Allow.
            if (resultCode == RESULT_OK) onDeleted(current);
            else Toast.makeText(this, "Kept " + (current != null ? current.name : "video"), Toast.LENGTH_SHORT).show();

        } else if (requestCode == REQ_DELETE_RETRY) {
            // Android 10: user granted access, so try deleting again.
            if (resultCode == RESULT_OK && current != null) deleteVideo(current);
        }
    }

    // ---------------------------------------------------------------- deleting

    private void askToDelete(Video v, String progress) {
        StringBuilder msg = new StringBuilder();
        msg.append(v.name).append("\n\n");
        if (progress != null) msg.append(progress).append("\n\n");
        msg.append("Deleting it frees ").append(size(v.size))
                .append(". The file is removed from your phone for good, not moved to a recycle bin.");

        new AlertDialog.Builder(this)
                .setTitle("Delete this video?")
                .setMessage(msg)
                .setPositiveButton("Delete", (d, w) -> deleteVideo(v))
                .setNegativeButton("Keep", (d, w) -> current = null)
                .setOnCancelListener(d -> current = null)
                .show();
    }

    private void deleteVideo(Video v) {
        current = v;
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                // Android 11+ only lets an app delete another app's files through this system prompt.
                PendingIntent pi = MediaStore.createDeleteRequest(getContentResolver(), Collections.singletonList(v.uri));
                startIntentSenderForResult(pi.getIntentSender(), REQ_DELETE, null, 0, 0, 0);
                return;
            }
            int rows = getContentResolver().delete(v.uri, null, null);
            if (rows > 0) onDeleted(v);
            else Toast.makeText(this, "Couldn't delete that video (it may already be gone).", Toast.LENGTH_LONG).show();
        } catch (SecurityException e) {
            if (Build.VERSION.SDK_INT >= 29 && e instanceof RecoverableSecurityException) {
                try {
                    IntentSender sender = ((RecoverableSecurityException) e).getUserAction().getActionIntent().getIntentSender();
                    startIntentSenderForResult(sender, REQ_DELETE_RETRY, null, 0, 0, 0);
                    return;
                } catch (IntentSender.SendIntentException ignored) {
                }
            }
            Toast.makeText(this, "Android didn't allow deleting that video.", Toast.LENGTH_LONG).show();
        } catch (IntentSender.SendIntentException e) {
            Toast.makeText(this, "Couldn't open the delete prompt.", Toast.LENGTH_LONG).show();
        }
    }

    private void onDeleted(Video v) {
        if (v != null) {
            freedThisSession += v.size;
            Toast.makeText(this, "Deleted · freed " + size(v.size), Toast.LENGTH_SHORT).show();
        }
        current = null;
        loadVideos();
    }

    // ---------------------------------------------------------------- helpers

    private String size(long bytes) {
        return Formatter.formatShortFileSize(this, bytes);
    }

    private static String time(long ms) {
        long s = ms / 1000, h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0
                ? String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
                : String.format(Locale.US, "%d:%02d", m, sec);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
