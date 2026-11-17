package io.github.josephbeaumont.vlccleanup;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.RecoverableSecurityException;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.MediaStore;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.Formatter;
import android.text.style.ForegroundColorSpan;
import android.util.LruCache;
import android.util.Size;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    // Colours
    private static final int C_BG = 0xFF121316;
    private static final int C_CARD = 0xFF1E2025;
    private static final int C_CARD_2 = 0xFF2A2D33;
    private static final int C_TEXT = 0xFFF2F2F2;
    private static final int C_TEXT_2 = 0xFFA0A4AB;
    private static final int C_ACCENT = 0xFFFF8800;
    private static final int C_OTHER = 0xFF6B7079;
    private static final int C_DANGER = 0xFFFF5A5F;
    private static final int C_GOOD = 0xFF4CD07D;

    /** One video from the phone's media library. */
    static final class Video {
        final long id;
        final Uri uri;
        final String name;
        final long size;
        final long durationMs;
        final long dateAdded; // seconds
        final String folder;     // folder name, e.g. "Camera"
        final String folderPath; // full folder location, so two folders with the same name stay apart

        Video(long id, Uri uri, String name, long size, long durationMs, long dateAdded, String folder, String folderPath) {
            this.id = id;
            this.uri = uri;
            this.name = name;
            this.size = size;
            this.durationMs = durationMs;
            this.dateAdded = dateAdded;
            this.folder = folder;
            this.folderPath = folderPath;
        }

        /** File name without the extension, which is what VLC shows and groups by. */
        String title() {
            int dot = name.lastIndexOf('.');
            return dot > 0 ? name.substring(0, dot) : name;
        }
    }

    /** A bundle of videos: one of VLC's name groups, or a folder. */
    static final class Group {
        final String key;
        String name;
        final List<Video> videos = new ArrayList<>();
        long size, newest, oldest = Long.MAX_VALUE;

        Group(String key) { this.key = key; }

        void add(Video v) {
            videos.add(v);
            size += v.size;
            newest = Math.max(newest, v.dateAdded);
            oldest = Math.min(oldest, v.dateAdded);
        }
    }

    private static final int GROUP_VLC = 0, GROUP_FOLDER = 1, GROUP_NONE = 2;
    private static final String[] GROUP_LABELS = {"Like VLC (by name)", "By folder", "Don't group"};

    private final List<Video> allVideos = new ArrayList<>();
    private final List<Object> shown = new ArrayList<>(); // Videos and Groups
    private int groupMode = GROUP_VLC;
    private int groupLen = 6;   // VLC groups videos whose names share this many first letters
    private String openGroup;   // key of the group being viewed, or null for the top level

    private VideoAdapter adapter;
    private ListView list;
    private LinearLayout emptyView;
    private TextView emptyText;
    private Button emptyButton;

    // Header views
    private View headerContent;
    private TextView totalSize, totalCount, legend, freedText;
    private StorageBar storageBar;

    // Thumbnails
    private final LruCache<Long, Bitmap> thumbs = new LruCache<Long, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 8)) {
        @Override protected int sizeOf(Long key, Bitmap b) { return b.getByteCount(); }
    };
    private final Set<Long> noThumb = new HashSet<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());

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
        groupMode = prefs.getInt("group", GROUP_VLC);
        groupLen = prefs.getInt("groupLen", 6);

        if (savedInstanceState != null) {
            freedThisSession = savedInstanceState.getLong("freed");
            openGroup = savedInstanceState.getString("openGroup");
            if (savedInstanceState.containsKey("cur_id")) {
                current = new Video(
                        savedInstanceState.getLong("cur_id"),
                        Uri.parse(savedInstanceState.getString("cur_uri")),
                        savedInstanceState.getString("cur_name"),
                        savedInstanceState.getLong("cur_size"),
                        savedInstanceState.getLong("cur_dur"),
                        0, "", "");
            }
        }

        if (getActionBar() != null) {
            getActionBar().setElevation(0);
            getActionBar().setBackgroundDrawable(new ColorDrawable(C_BG));
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
    protected void onDestroy() {
        super.onDestroy();
        pool.shutdownNow();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putLong("freed", freedThisSession);
        out.putString("openGroup", openGroup);
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
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(C_BG);

        list = new ListView(this);
        list.setDivider(null);
        list.setSelector(new ColorDrawable(0));
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, dp(16));
        list.addHeaderView(buildHeader(), null, false);
        adapter = new VideoAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> {
            Object o = p.getItemAtPosition(pos);
            if (o instanceof Video) playInVlc((Video) o);
            else if (o instanceof Group) openGroup(((Group) o).key);
        });
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            Object o = p.getItemAtPosition(pos);
            if (!(o instanceof Video)) return false;
            current = (Video) o;
            askToDelete(current, null);
            return true;
        });
        root.addView(list, new FrameLayout.LayoutParams(-1, -1));

        emptyView = new LinearLayout(this);
        emptyView.setOrientation(LinearLayout.VERTICAL);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(dp(32), dp(32), dp(32), dp(32));
        emptyView.setBackgroundColor(C_BG);
        emptyText = text(16, C_TEXT, false);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setLineSpacing(0, 1.2f);
        emptyView.addView(emptyText);
        emptyButton = new Button(this);
        emptyButton.setTextColor(0xFF000000);
        emptyButton.setBackground(pill(C_ACCENT));
        emptyButton.setPadding(dp(24), 0, dp(24), 0);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, dp(48));
        bp.topMargin = dp(24);
        emptyView.addView(emptyButton, bp);
        emptyView.setVisibility(View.GONE);
        root.addView(emptyView, new FrameLayout.LayoutParams(-1, -1));

        setContentView(root);
    }

    private View buildHeader() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.VERTICAL);
        h.setPadding(dp(16), dp(4), dp(16), dp(8));

        // Storage card
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(rounded(C_CARD, 20));

        LinearLayout big = new LinearLayout(this);
        big.setOrientation(LinearLayout.HORIZONTAL);
        big.setBaselineAligned(true);
        totalSize = text(30, C_TEXT, true);
        totalCount = text(14, C_TEXT_2, false);
        totalCount.setPadding(dp(8), 0, 0, 0);
        big.addView(totalSize);
        big.addView(totalCount);
        card.addView(big);

        storageBar = new StorageBar(this);
        LinearLayout.LayoutParams sb = new LinearLayout.LayoutParams(-1, dp(10));
        sb.topMargin = dp(14);
        card.addView(storageBar, sb);

        legend = text(12, C_TEXT_2, false);
        legend.setPadding(0, dp(10), 0, 0);
        card.addView(legend);

        freedText = text(13, C_GOOD, true);
        freedText.setPadding(0, dp(10), 0, 0);
        freedText.setVisibility(View.GONE);
        card.addView(freedText);

        h.addView(card, new LinearLayout.LayoutParams(-1, -2));

        TextView hint = text(13, C_TEXT_2, false);
        hint.setText("Tap a video to watch it in VLC. When you're done, you'll be asked whether to keep it.");
        hint.setPadding(dp(4), dp(12), dp(4), dp(2));
        h.addView(hint);

        // Wrapped so the whole header can be hidden while a group is open.
        headerContent = h;
        FrameLayout wrap = new FrameLayout(this);
        wrap.addView(h);
        return wrap;
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
        emptyView.setVisibility(View.VISIBLE);
    }

    private void updateHeader() {
        long videoBytes = 0;
        for (Video v : allVideos) videoBytes += v.size;

        totalSize.setText(size(videoBytes));
        totalCount.setText("of video · " + allVideos.size() + (allVideos.size() == 1 ? " file" : " files"));

        long free = 0, total = 0;
        try {
            StatFs fs = new StatFs(Environment.getExternalStorageDirectory().getPath());
            free = fs.getAvailableBytes();
            total = fs.getTotalBytes();
        } catch (Exception ignored) {
        }
        long other = Math.max(0, total - free - videoBytes);
        storageBar.set(videoBytes, other, free);

        SpannableStringBuilder sb = new SpannableStringBuilder();
        dot(sb, C_ACCENT); sb.append(" Videos   ");
        dot(sb, C_OTHER); sb.append(" Other   ");
        dot(sb, 0xFF4A4E56); sb.append(" Free ").append(size(free));
        legend.setText(sb);

        if (freedThisSession > 0) {
            freedText.setText("You've freed " + size(freedThisSession) + " so far");
            freedText.setVisibility(View.VISIBLE);
        } else {
            freedText.setVisibility(View.GONE);
        }
    }

    private static void dot(SpannableStringBuilder sb, int color) {
        int start = sb.length();
        sb.append("●");
        sb.setSpan(new ForegroundColorSpan(color),
                start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static String folderOf(Video v) {
        return v.folder == null || v.folder.isEmpty() ? "Other" : v.folder;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Sort")
                .setIcon(R.drawable.ic_sort)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(0, 3, 1, "Group videos…");
        menu.add(0, 4, 2, "Group name length…");
        menu.add(0, 2, 3, "Refresh");
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem len = menu.findItem(4);
        if (len != null) len.setVisible(groupMode == GROUP_VLC);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public void onBackPressed() {
        if (openGroup != null) closeGroup();
        else super.onBackPressed();
    }

    private void openGroup(String key) {
        openGroup = key;
        applyFilter();
        list.setSelection(0);
    }

    private void closeGroup() {
        openGroup = null;
        applyFilter();
        list.setSelection(0);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            new AlertDialog.Builder(this)
                    .setTitle("Sort videos")
                    .setSingleChoiceItems(SORT_LABELS, sortMode, (d, which) -> {
                        sortMode = which;
                        getPreferences(MODE_PRIVATE).edit().putInt("sort", sortMode).apply();
                        applyFilter();
                        list.setSelection(0);
                        d.dismiss();
                    })
                    .show();
            return true;
        } else if (item.getItemId() == android.R.id.home) {
            closeGroup();
            return true;
        } else if (item.getItemId() == 3) {
            new AlertDialog.Builder(this)
                    .setTitle("Group videos")
                    .setSingleChoiceItems(GROUP_LABELS, groupMode, (d, which) -> {
                        groupMode = which;
                        openGroup = null;
                        getPreferences(MODE_PRIVATE).edit().putInt("group", groupMode).apply();
                        applyFilter();
                        list.setSelection(0);
                        d.dismiss();
                    })
                    .show();
            return true;
        } else if (item.getItemId() == 4) {
            final String[] labels = new String[12];
            for (int i = 0; i < labels.length; i++) labels[i] = (i + 1) + (i == 5 ? " letters (VLC's default)" : i == 0 ? " letter" : " letters");
            new AlertDialog.Builder(this)
                    .setTitle("Group videos whose names start with the same…")
                    .setSingleChoiceItems(labels, groupLen - 1, (d, which) -> {
                        groupLen = which + 1;
                        openGroup = null;
                        getPreferences(MODE_PRIVATE).edit().putInt("groupLen", groupLen).apply();
                        applyFilter();
                        list.setSelection(0);
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

    /** Holds the views of one row. */
    private static final class Row {
        ImageView thumb;
        TextView duration, title, meta;
        ImageButton delete;
        TextView chevron;
        Video video;
    }

    private final class VideoAdapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int i) { return shown.get(i); }
        @Override public long getItemId(int i) { return i; }
        @Override public int getViewTypeCount() { return 1; }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            Row r;
            View view;
            if (convert == null) {
                r = new Row();
                view = buildRow(r);
                view.setTag(r);
            } else {
                view = convert;
                r = (Row) convert.getTag();
            }
            Object item = shown.get(i);
            if (item instanceof Group) {
                bindGroup(r, (Group) item);
                return view;
            }
            Video v = (Video) item;
            r.video = v;
            r.delete.setVisibility(View.VISIBLE);
            r.chevron.setVisibility(View.GONE);
            r.title.setText(v.name);
            r.duration.setText(v.durationMs > 0 ? time(v.durationMs) : "");
            r.duration.setVisibility(v.durationMs > 0 ? View.VISIBLE : View.GONE);

            SpannableStringBuilder meta = new SpannableStringBuilder(size(v.size));
            meta.setSpan(new ForegroundColorSpan(C_ACCENT), 0, meta.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            meta.append("  ·  ").append(folderOf(v));
            if (v.dateAdded > 0) {
                meta.append("\n").append(DateFormat.getMediumDateFormat(MainActivity.this).format(new Date(v.dateAdded * 1000)));
            }
            r.meta.setText(meta);
            loadThumb(v, r.thumb);
            return view;
        }
    }

    private void bindGroup(Row r, Group g) {
        r.video = null;
        r.delete.setVisibility(View.GONE);
        r.chevron.setVisibility(View.VISIBLE);
        r.title.setText(g.name);
        r.duration.setVisibility(View.VISIBLE);
        r.duration.setText("▤ " + g.videos.size());
        SpannableStringBuilder meta = new SpannableStringBuilder(size(g.size));
        meta.setSpan(new ForegroundColorSpan(C_ACCENT), 0, meta.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        meta.append("  ·  ").append(String.valueOf(g.videos.size())).append(" videos");
        r.meta.setText(meta);
        // Use the biggest video in the group for the picture.
        Video cover = g.videos.get(0);
        for (Video v : g.videos) if (v.size > cover.size) cover = v;
        loadThumb(cover, r.thumb);
    }

    private View buildRow(Row r) {
        FrameLayout outer = new FrameLayout(this);
        outer.setPadding(dp(16), dp(5), dp(16), dp(5));
        outer.setLayoutParams(new AbsListView.LayoutParams(-1, -2));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(10), dp(10), dp(4), dp(10));
        card.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), rounded(C_CARD, 18), null));
        card.setDuplicateParentStateEnabled(true); // show the ripple when the list row is pressed
        outer.addView(card, new FrameLayout.LayoutParams(-1, -2));

        // Thumbnail with a duration badge
        FrameLayout thumbBox = new FrameLayout(this);
        r.thumb = new ImageView(this);
        r.thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        r.thumb.setBackground(rounded(C_CARD_2, 12));
        clipRounded(r.thumb, 12);
        thumbBox.addView(r.thumb, new FrameLayout.LayoutParams(-1, -1));
        r.duration = text(11, 0xFFFFFFFF, true);
        r.duration.setPadding(dp(6), dp(2), dp(6), dp(2));
        r.duration.setBackground(rounded(0xB3000000, 6));
        FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        dl.setMargins(0, 0, dp(5), dp(5));
        thumbBox.addView(r.duration, dl);
        card.addView(thumbBox, new LinearLayout.LayoutParams(dp(128), dp(72)));

        // Name and details
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        r.title = text(15, C_TEXT, true);
        r.title.setMaxLines(2);
        r.title.setEllipsize(TextUtils.TruncateAt.END);
        r.meta = text(13, C_TEXT_2, false);
        r.meta.setPadding(0, dp(4), 0, 0);
        r.meta.setLineSpacing(0, 1.1f);
        texts.addView(r.title);
        texts.addView(r.meta);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1);
        tl.leftMargin = dp(12);
        card.addView(texts, tl);

        // Delete button
        r.delete = new ImageButton(this);
        r.delete.setImageResource(R.drawable.ic_delete);
        r.delete.setBackground(borderlessRipple());
        r.delete.setFocusable(false);
        r.delete.setContentDescription("Delete");
        r.delete.setOnClickListener(v -> {
            if (r.video == null) return;
            current = r.video;
            askToDelete(current, null);
        });
        card.addView(r.delete, new LinearLayout.LayoutParams(dp(48), dp(48)));

        // Arrow shown on groups instead of the delete button
        r.chevron = text(28, C_TEXT_2, false);
        r.chevron.setText("›");
        r.chevron.setGravity(Gravity.CENTER);
        r.chevron.setVisibility(View.GONE);
        card.addView(r.chevron, new LinearLayout.LayoutParams(dp(40), dp(48)));

        return outer;
    }

    // ---------------------------------------------------------------- thumbnails

    private void loadThumb(Video v, ImageView iv) {
        iv.setTag(v.id);
        Bitmap cached = thumbs.get(v.id);
        if (cached != null) {
            iv.setImageBitmap(cached);
            return;
        }
        iv.setImageDrawable(null);
        if (noThumb.contains(v.id)) return;
        pool.execute(() -> {
            Bitmap bm = null;
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    bm = getContentResolver().loadThumbnail(v.uri, new Size(384, 216), null);
                } else {
                    bm = MediaStore.Video.Thumbnails.getThumbnail(getContentResolver(), v.id,
                            MediaStore.Video.Thumbnails.MINI_KIND, null);
                }
            } catch (Exception ignored) {
            }
            final Bitmap result = bm;
            main.post(() -> {
                if (result == null) {
                    noThumb.add(v.id);
                    return;
                }
                thumbs.put(v.id, result);
                if (Long.valueOf(v.id).equals(iv.getTag())) iv.setImageBitmap(result);
            });
        });
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
        if (hasReadPermission()) {
            loadVideos();
        } else {
            showEmpty("Without access to your videos this app can't list or delete them.\n\n"
                            + "If the button doesn't bring up a prompt, allow \"Videos\" (or \"Files and media\") "
                            + "for VLC Cleanup in your phone's Settings → Apps.",
                    "Try again", v -> requestReadPermission());
        }
    }

    // ---------------------------------------------------------------- loading

    private void loadVideos() {
        allVideos.clear();
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
                Build.VERSION.SDK_INT >= 29 ? MediaStore.Video.Media.RELATIVE_PATH : MediaStore.Video.Media.DATA,
        };
        try (Cursor c = getContentResolver().query(collection, projection, null, null, null)) {
            if (c != null) {
                while (c.moveToNext()) {
                    long id = c.getLong(0);
                    String name = c.getString(1);
                    allVideos.add(new Video(
                            id,
                            ContentUris.withAppendedId(collection, id),
                            name != null ? name : "(unnamed video)",
                            c.getLong(2),
                            c.getLong(3),
                            c.getLong(4),
                            c.getString(5),
                            folderPathOf(c.getString(6))));
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't read videos: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        if (allVideos.isEmpty()) {
            showEmpty("No videos found on this phone.", null, null);
            return;
        }
        emptyView.setVisibility(View.GONE);
        applyFilter();
        updateHeader();
    }

    /** On Android 10+ this is already the folder path; on older versions it's the file path. */
    private static String folderPathOf(String s) {
        if (s == null) return "";
        if (Build.VERSION.SDK_INT >= 29) return s;
        int slash = s.lastIndexOf('/');
        return slash > 0 ? s.substring(0, slash) : s;
    }

    /** The key VLC would group this video under: the first few letters of its name, ignoring case and a leading "The ". */
    private String vlcGroupKey(Video v) {
        String t = v.title().trim().toLowerCase(Locale.ROOT);
        if (t.startsWith("the ")) t = t.substring(4);
        return t.length() > groupLen ? t.substring(0, groupLen) : t;
    }

    /** Builds what's on screen: the groups (plus loose videos), or the videos inside the open group. */
    private void applyFilter() {
        shown.clear();

        Map<String, Group> groups = new LinkedHashMap<>();
        if (groupMode != GROUP_NONE) {
            for (Video v : allVideos) {
                String key = groupMode == GROUP_FOLDER ? "f:" + v.folderPath + "/" + folderOf(v) : "n:" + vlcGroupKey(v);
                Group g = groups.get(key);
                if (g == null) groups.put(key, g = new Group(key));
                g.add(v);
            }
            for (Group g : groups.values()) g.name = groupMode == GROUP_FOLDER ? folderOf(g.videos.get(0)) : commonPrefix(g.videos);
        }

        Group open = openGroup != null ? groups.get(openGroup) : null;
        if (openGroup != null && open == null) openGroup = null; // group no longer exists (e.g. emptied)

        List<Video> videos = new ArrayList<>();
        if (open != null) {
            videos.addAll(open.videos);
            shown.addAll(videos);
        } else if (groupMode == GROUP_NONE) {
            shown.addAll(allVideos);
        } else {
            for (Group g : groups.values()) {
                // Like VLC, a "group" of one by name is just shown as the video.
                if (g.videos.size() == 1 && groupMode == GROUP_VLC) shown.add(g.videos.get(0));
                else shown.add(g);
            }
        }

        Comparator<Object> cmp;
        switch (sortMode) {
            case SORT_NEWEST: cmp = (a, b) -> Long.compare(newestOf(b), newestOf(a)); break;
            case SORT_OLDEST: cmp = (a, b) -> Long.compare(oldestOf(a), oldestOf(b)); break;
            case SORT_NAME:   cmp = (a, b) -> nameOf(a).compareToIgnoreCase(nameOf(b)); break;
            default:          cmp = (a, b) -> Long.compare(sizeOf(b), sizeOf(a)); break;
        }
        Collections.sort(shown, cmp);
        adapter.notifyDataSetChanged();

        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(open != null);
            if (open != null) {
                getActionBar().setTitle(open.name);
                getActionBar().setSubtitle(open.videos.size() + " videos · " + size(open.size));
            } else {
                getActionBar().setTitle("VLC Cleanup");
                getActionBar().setSubtitle(SORT_LABELS[sortMode]);
            }
        }
        headerContent.setVisibility(open != null ? View.GONE : View.VISIBLE);
    }

    /** VLC names a group after the beginning that all its videos' names share. */
    private static String commonPrefix(List<Video> vs) {
        String p = vs.get(0).title();
        for (Video v : vs) {
            String t = v.title();
            int n = 0, max = Math.min(p.length(), t.length());
            while (n < max && Character.toLowerCase(p.charAt(n)) == Character.toLowerCase(t.charAt(n))) n++;
            p = p.substring(0, n);
        }
        p = p.trim();
        return p.isEmpty() ? vs.get(0).title() : p;
    }

    private static long sizeOf(Object o) { return o instanceof Group ? ((Group) o).size : ((Video) o).size; }
    private static long newestOf(Object o) { return o instanceof Group ? ((Group) o).newest : ((Video) o).dateAdded; }
    private static long oldestOf(Object o) { return o instanceof Group ? ((Group) o).oldest : ((Video) o).dateAdded; }
    private static String nameOf(Object o) { return o instanceof Group ? ((Group) o).name : ((Video) o).name; }

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
                progress = "You stopped at " + time(position) + " of " + time(duration);
            }
            askToDelete(current, progress);

        } else if (requestCode == REQ_DELETE) {
            // Android 11+: the system dialog did the delete if the user tapped Allow.
            if (resultCode == RESULT_OK) onDeleted(current);
            else Toast.makeText(this, "Kept it", Toast.LENGTH_SHORT).show();

        } else if (requestCode == REQ_DELETE_RETRY) {
            // Android 10: user granted access, so try deleting again.
            if (resultCode == RESULT_OK && current != null) deleteVideo(current);
        }
    }

    // ---------------------------------------------------------------- deleting

    private void askToDelete(Video v, String progress) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), dp(4));

        ImageView thumb = new ImageView(this);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setBackground(rounded(C_CARD_2, 14));
        clipRounded(thumb, 14);
        loadThumb(v, thumb);
        box.addView(thumb, new LinearLayout.LayoutParams(-1, dp(170)));

        TextView name = text(16, C_TEXT, true);
        name.setText(v.name);
        name.setMaxLines(3);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setPadding(0, dp(14), 0, 0);
        box.addView(name);

        if (progress != null) {
            TextView p = text(14, C_TEXT_2, false);
            p.setText(progress);
            p.setPadding(0, dp(4), 0, 0);
            box.addView(p);
        }

        TextView frees = text(15, C_ACCENT, true);
        frees.setText("Frees " + size(v.size));
        frees.setPadding(0, dp(12), 0, 0);
        box.addView(frees);

        TextView warn = text(13, C_TEXT_2, false);
        warn.setText("Deleted for good — it won't go to a recycle bin.");
        warn.setPadding(0, dp(2), 0, 0);
        box.addView(warn);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Delete this video?")
                .setView(box)
                .setPositiveButton("Delete", (d, w) -> deleteVideo(v))
                .setNegativeButton("Keep", (d, w) -> current = null)
                .setOnCancelListener(d -> current = null)
                .create();
        dialog.show();
        Button del = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (del != null) del.setTextColor(C_DANGER);
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
            thumbs.remove(v.id);
            Toast.makeText(this, "Deleted · freed " + size(v.size), Toast.LENGTH_SHORT).show();
        }
        current = null;
        loadVideos();
    }

    // ---------------------------------------------------------------- helpers

    /** Bar showing videos / other / free space. */
    static final class StorageBar extends View {
        private long videos, other, free;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clip = new Path();

        StorageBar(Context c) { super(c); }

        void set(long videos, long other, long free) {
            this.videos = videos;
            this.other = other;
            this.free = free;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            clip.reset();
            clip.addRoundRect(0, 0, w, h, h / 2, h / 2, Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            paint.setColor(0xFF2E3138);
            c.drawRect(0, 0, w, h, paint);
            long total = videos + other + free;
            if (total > 0) {
                float wv = w * videos / total;
                float wo = w * other / total;
                paint.setColor(C_ACCENT);
                c.drawRect(0, 0, wv, h, paint);
                paint.setColor(C_OTHER);
                c.drawRect(wv, 0, wv + wo, h, paint);
            }
            c.restore();
        }
    }

    private TextView text(int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private GradientDrawable pill(int color) {
        return rounded(color, 100);
    }

    private void clipRounded(View v, int radiusDp) {
        final float r = dp(radiusDp);
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
            }
        });
        v.setClipToOutline(true);
    }

    private Drawable borderlessRipple() {
        TypedArray a = obtainStyledAttributes(new int[]{android.R.attr.selectableItemBackgroundBorderless});
        Drawable d = a.getDrawable(0);
        a.recycle();
        return d;
    }

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
