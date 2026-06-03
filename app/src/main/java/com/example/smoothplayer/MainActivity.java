package com.example.smoothplayer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.IBinder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import rikka.shizuku.Shizuku;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements TextureView.SurfaceTextureListener {
    private static final String TAG = "SmoothPlayer";
    private static final int REQUEST_PICK_VIDEO = 1001;
    private static final int REQUEST_READ_STORAGE = 1002;
    private static final long CONTROLS_HIDE_DELAY_MS = 3200L;
    private static final int SEEK_STEP_MS = 10_000;
    private static final int SEEK_TRIPLE_STEP_MS = 60_000;
    private static final int SWIPE_SWITCH_MIN_DISTANCE_DP = 96;
    private static final int SWIPE_SWITCH_MAX_DURATION_MS = 800;
    private static final String PREFS = "smooth_player";
    private static final String KEY_LAST_PATH = "last_path";
    private static final String KEY_LAST_POSITION = "last_position";
    private static final String KEY_LAST_SPEED = "last_speed";
    private static final String KEY_LAST_FILL = "last_fill";
    private static final String KEY_VIDEO_SCALE_MODE = "video_scale_mode";
    private static final String KEY_PRIVACY_ACCEPTED = "privacy_accepted";
    private static final String KEY_AUTO_NEXT = "auto_next";
    private static final String KEY_PLAYBACK_MODE = "playback_mode";
    private static final String KEY_VIDEO_ONLY = "video_only";
    private static final String KEY_LAST_SHIZUKU_DIR = "last_shizuku_dir";
    private static final String KEY_FAVORITES = "favorites";
    private static final String ROOT_CACHE_DIR = "root-cache";
    private static final String ROOT_MOUNT_DIR = "root-mount";
    private static final int PREVIEW_CACHE_LIMIT = 80;
    private static final int PLAYBACK_MODE_SINGLE = 0;
    private static final int PLAYBACK_MODE_SEQUENCE = 1;
    private static final int PLAYBACK_MODE_LIST_LOOP = 2;
    private static final int PLAYBACK_MODE_ONE_LOOP = 3;
    private static final int SCALE_MODE_FIT = 0;
    private static final int SCALE_MODE_FILL = 1;
    private static final int SCALE_MODE_STRETCH = 2;
    private static final int SCALE_MODE_ORIGINAL = 3;
    private static final int SORT_NAME = 0;
    private static final int SORT_SIZE = 1;
    private static final int SORT_TIME = 2;
    private static final int SORT_RANDOM = 3;
    private static final int GESTURE_NONE = 0;
    private static final int GESTURE_VOLUME = 1;
    private static final int GESTURE_BRIGHTNESS = 2;
    private static final int REQUEST_SHIZUKU_PERMISSION = 1003;
    private static final long TAP_DELAY_MS = 260L;
    private static final long LONG_PRESS_DELAY_MS = 420L;
    private static final String UPDATE_INFO_URL =
            "https://raw.githubusercontent.com/zhouhaoran-TJU/VibeReplay/main/dist/version.json";
    private static final String[] SPEED_LABELS = {
            "0.5x", "0.75x", "1x", "1.25x", "1.5x", "2x", "2.5x", "3x"
    };
    private static final float[] SPEED_VALUES = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 2.5f, 3f};
    private static final String[] PICKER_MIME_TYPES = {
            "video/*",
            "audio/*",
            "application/octet-stream",
            "application/*",
            "text/*"
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Map<String, Bitmap> previewCache = new LinkedHashMap<String, Bitmap>(PREVIEW_CACHE_LIMIT, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) {
            if (size() <= PREVIEW_CACHE_LIMIT) {
                return false;
            }
            Bitmap bitmap = eldest.getValue();
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
            return true;
        }
    };
    private final Set<String> previewLoading = new HashSet<>();
    private final Runnable progressTick = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            handler.postDelayed(this, 350L);
        }
    };
    private final Runnable hideControlsRunnable = new Runnable() {
        @Override
        public void run() {
            setControlsVisible(false);
        }
    };
    private final Runnable singleTapRunnable = new Runnable() {
        @Override
        public void run() {
            if (!controlsLocked) {
                setControlsVisible(!controlsVisible);
            } else {
                showFeedback("控制已锁定");
            }
        }
    };
    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            startLongPressSpeed();
        }
    };
    private final Shizuku.UserServiceArgs shizukuServiceArgs =
            new Shizuku.UserServiceArgs(new ComponentName(
                    BuildConfig.APPLICATION_ID,
                    RestrictedFileService.class.getName()))
                    .daemon(false)
                    .processNameSuffix("restricted_file")
                    .debuggable(BuildConfig.DEBUG)
                    .version(3);
    private final ServiceConnection shizukuConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            restrictedFileService = IRestrictedFileService.Stub.asInterface(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            restrictedFileService = null;
        }
    };
    private final Shizuku.OnRequestPermissionResultListener shizukuPermissionListener =
            new Shizuku.OnRequestPermissionResultListener() {
                @Override
                public void onRequestPermissionResult(int requestCode, int grantResult) {
                    if (requestCode != REQUEST_SHIZUKU_PERMISSION) {
                        return;
                    }
                    if (grantResult == PackageManager.PERMISSION_GRANTED && pendingShizukuPath != null) {
                        String path = pendingShizukuPath;
                        pendingShizukuPath = null;
                        if (new File(path).isDirectory()) {
                            showShizukuDirectory(path);
                        } else {
                            openRestrictedPathWithShizuku(path);
                        }
                    } else {
                        pendingShizukuPath = null;
                        Toast.makeText(MainActivity.this, "Shizuku 未授权", Toast.LENGTH_SHORT).show();
                    }
                }
            };

    private FrameLayout root;
    private TextureView textureView;
    private View dimLayer;
    private LinearLayout topBar;
    private LinearLayout bottomBar;
    private EditText pathInput;
    private Button playButton;
    private ImageButton centerPlayButton;
    private Button fitButton;
    private Button lockButton;
    private Button unlockButton;
    private Button favoriteButton;
    private Button previousButton;
    private Button nextButton;
    private Button autoNextButton;
    private SeekBar seekBar;
    private TextView timeText;
    private TextView titleText;
    private TextView feedbackText;
    private FrameLayout shizukuBrowserOverlay;
    private TextView browserPathText;
    private ListView browserListView;
    private TextView browserEmptyText;
    private Button browserSortButton;
    private Button browserDirectionButton;
    private Button browserFilterButton;
    private ProgressBar loading;
    private Spinner speedSpinner;

    private MediaPlayer player;
    private Surface surface;
    private Uri currentUri;
    private Uri pendingUri;
    private boolean pendingAutoPlay = true;
    private int pendingSeekMs;
    private boolean surfaceReady;
    private boolean prepared;
    private boolean userSeeking;
    private boolean controlsVisible = true;
    private boolean controlsLocked;
    private boolean fillMode;
    private boolean autoNext = true;
    private int playbackMode = PLAYBACK_MODE_SEQUENCE;
    private int videoScaleMode = SCALE_MODE_FIT;
    private boolean videoOnlyBrowsing = true;
    private int shizukuSortMode;
    private boolean shizukuSortAscending = true;
    private long shizukuRandomSeed = System.nanoTime();
    private int previewGeneration;
    private boolean resumePlaying;
    private int videoWidth;
    private int videoHeight;
    private float playbackSpeed = 1f;
    private float speedBeforeLongPress = 1f;
    private long lastTapTime;
    private float lastTapX;
    private float touchDownX;
    private float touchDownY;
    private long touchDownTime;
    private boolean touchMoved;
    private int activeVerticalGesture;
    private float gestureStartBrightness = -1f;
    private int gestureStartVolume;
    private boolean longPressActive;
    private boolean longPressTriggered;
    private int tapCount;
    private IRestrictedFileService restrictedFileService;
    private ParcelFileDescriptor activeShizukuFd;
    private String pendingShizukuPath;
    private String currentBrowserPath;
    private String[] currentBrowserRawEntries;
    private boolean browserVisible;
    private final List<PlaybackItem> playbackQueue = new ArrayList<>();
    private int currentQueueIndex = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        restoreUiState(savedInstanceState);
        buildLayout();
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener, handler);
        showPrivacyNoticeIfNeeded();
        requestStoragePermissionIfNeeded();

        Uri launchUri = getIntent() == null ? null : getIntent().getData();
        if (launchUri != null) {
            setPendingVideo(launchUri, 0, true);
            pathInput.setText(launchUri.toString());
            saveLastPath(launchUri.toString());
            persistReadPermission(launchUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else if (savedInstanceState != null) {
            String savedUri = savedInstanceState.getString(KEY_LAST_PATH, "");
            if (!savedUri.isEmpty()) {
                setPendingVideo(Uri.parse(savedUri), savedInstanceState.getInt(KEY_LAST_POSITION, 0),
                        savedInstanceState.getBoolean("was_playing", false));
                pathInput.setText(savedUri);
            }
        } else {
            String lastPath = getPreferences().getString(KEY_LAST_PATH, "");
            pathInput.setText(lastPath);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        handler.removeCallbacks(progressTick);
        handler.post(progressTick);
    }

    @Override
    protected void onStop() {
        savePlaybackState();
        handler.removeCallbacks(progressTick);
        if (player != null && player.isPlaying()) {
            resumePlaying = true;
            player.pause();
            updatePlayButton();
        }
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        savePlaybackState();
        outState.putString(KEY_LAST_PATH, currentUri == null ? pathInput.getText().toString() : currentUri.toString());
        outState.putInt(KEY_LAST_POSITION, currentPosition());
        outState.putBoolean("was_playing", player != null && prepared && player.isPlaying());
        outState.putFloat(KEY_LAST_SPEED, playbackSpeed);
        outState.putBoolean(KEY_LAST_FILL, fillMode);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        Uri uri = intent == null ? null : intent.getData();
        if (uri != null) {
            pathInput.setText(uri.toString());
            saveLastPath(uri.toString());
            persistReadPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            openVideo(uri, 0, true);
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (feedbackText != null) {
            feedbackText.animate().cancel();
        }
        releasePlayer();
        if (surface != null) {
            surface.release();
            surface = null;
        }
        closeActiveShizukuFd();
        unbindShizukuService();
        hideShizukuBrowser();
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener);
        ioExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (browserVisible) {
            hideShizukuBrowser();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        surfaceReady = true;
        surface = new Surface(surfaceTexture);
        if (pendingUri != null) {
            Uri uri = pendingUri;
            int seekMs = pendingSeekMs;
            boolean autoPlay = pendingAutoPlay;
            pendingUri = null;
            pendingSeekMs = 0;
            openVideo(uri, seekMs, autoPlay);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
        applyVideoTransform();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        Uri reopenUri = currentUri;
        int reopenPosition = currentPosition();
        boolean wasPlaying = player != null && prepared && player.isPlaying();
        savePlaybackState();
        surfaceReady = false;
        if (surface != null) {
            surface.release();
            surface = null;
        }
        releasePlayer();
        if (reopenUri != null) {
            pendingUri = reopenUri;
            pendingSeekMs = reopenPosition;
            pendingAutoPlay = wasPlaying;
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_VIDEO && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                persistReadPermission(uri, data.getFlags());
                pathInput.setText(uri.toString());
                saveLastPath(uri.toString());
                clearPlaybackQueue();
                openVideo(uri, 0, true);
            }
        }
    }

    private void configureWindow() {
        Window window = getWindow();
        window.requestFeature(Window.FEATURE_NO_TITLE);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.BLACK);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemBars();
    }

    private void restoreUiState(Bundle savedInstanceState) {
        SharedPreferences prefs = getPreferences();
        playbackSpeed = savedInstanceState == null ? prefs.getFloat(KEY_LAST_SPEED, 1f)
                : savedInstanceState.getFloat(KEY_LAST_SPEED, 1f);
        fillMode = savedInstanceState == null ? prefs.getBoolean(KEY_LAST_FILL, false)
                : savedInstanceState.getBoolean(KEY_LAST_FILL, false);
        autoNext = prefs.getBoolean(KEY_AUTO_NEXT, true);
        playbackMode = prefs.getInt(KEY_PLAYBACK_MODE, autoNext ? PLAYBACK_MODE_SEQUENCE : PLAYBACK_MODE_SINGLE);
        videoScaleMode = prefs.getInt(KEY_VIDEO_SCALE_MODE, fillMode ? SCALE_MODE_FILL : SCALE_MODE_FIT);
        fillMode = videoScaleMode == SCALE_MODE_FILL;
        videoOnlyBrowsing = prefs.getBoolean(KEY_VIDEO_ONLY, true);
    }

    private void buildLayout() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(5, 7, 10));
        setContentView(root);

        textureView = new TextureView(this);
        textureView.setSurfaceTextureListener(this);
        textureView.setOpaque(false);
        root.addView(textureView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        dimLayer = new View(this);
        dimLayer.setBackgroundColor(Color.argb(72, 0, 0, 0));
        root.addView(dimLayer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        loading = new ProgressBar(this);
        loading.setIndeterminate(true);
        loading.setVisibility(View.GONE);
        root.addView(loading, new FrameLayout.LayoutParams(dp(52), dp(52), Gravity.CENTER));

        feedbackText = makeText(18, Color.WHITE, true);
        feedbackText.setGravity(Gravity.CENTER);
        feedbackText.setBackgroundResource(R.drawable.bg_feedback);
        feedbackText.setVisibility(View.GONE);
        root.addView(feedbackText, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        centerPlayButton = makeCenterButton();
        centerPlayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                togglePlay();
            }
        });
        FrameLayout.LayoutParams centerParams = new FrameLayout.LayoutParams(dp(104), dp(104), Gravity.CENTER);
        root.addView(centerPlayButton, centerParams);

        unlockButton = makeCompactButton("解");
        unlockButton.setVisibility(View.GONE);
        unlockButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                controlsLocked = false;
                updateLockState();
                revealControls();
            }
        });
        FrameLayout.LayoutParams unlockParams = new FrameLayout.LayoutParams(dp(44), dp(36),
                Gravity.TOP | Gravity.RIGHT);
        unlockParams.setMargins(0, dp(8), dp(8), 0);
        root.addView(unlockButton, unlockParams);

        buildTopBar();
        buildBottomBar();
        buildShizukuBrowserOverlay();
        bindGestures();
        setSpeedSpinnerSelection(playbackSpeed);
        setControlsVisible(true);
    }

    private void buildTopBar() {
        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(10), dp(6), dp(8), dp(6));
        topBar.setBackgroundResource(R.drawable.bg_panel);

        titleText = makeText(15, Color.WHITE, true);
        titleText.setText("Smooth Player");
        titleText.setSingleLine(true);
        titleText.setEllipsize(TextUtils.TruncateAt.END);
        titleText.setGravity(Gravity.CENTER_VERTICAL);
        topBar.addView(titleText, new LinearLayout.LayoutParams(0,
                dp(34), 1f));

        favoriteButton = makeCompactButton("☆");
        favoriteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleFavoriteCurrent();
            }
        });
        LinearLayout.LayoutParams favoriteParams = new LinearLayout.LayoutParams(dp(42), dp(34));
        favoriteParams.leftMargin = dp(8);
        topBar.addView(favoriteButton, favoriteParams);

        Button favoriteListButton = makeCompactButton("收藏");
        favoriteListButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showFavoriteList();
            }
        });
        LinearLayout.LayoutParams favoriteListParams = new LinearLayout.LayoutParams(dp(56), dp(34));
        favoriteListParams.leftMargin = dp(6);
        topBar.addView(favoriteListButton, favoriteListParams);

        Button browseButton = makeCompactButton("浏览");
        browseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showBrowseOptions();
            }
        });
        LinearLayout.LayoutParams browseParams = new LinearLayout.LayoutParams(dp(50), dp(34));
        browseParams.leftMargin = dp(6);
        topBar.addView(browseButton, browseParams);

        Button moreButton = makeCompactButton("⋮");
        moreButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showMoreOptions();
            }
        });
        LinearLayout.LayoutParams moreParams = new LinearLayout.LayoutParams(dp(42), dp(34));
        moreParams.leftMargin = dp(6);
        topBar.addView(moreButton, moreParams);

        pathInput = new EditText(this);
        pathInput.setSingleLine(true);
        pathInput.setTextColor(Color.WHITE);
        pathInput.setHintTextColor(Color.rgb(150, 162, 176));
        pathInput.setTextSize(14);
        pathInput.setHint("输入路径或选择文件");
        pathInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        pathInput.setSelectAllOnFocus(false);
        pathInput.setBackgroundColor(Color.argb(36, 255, 255, 255));
        pathInput.setPadding(dp(12), 0, dp(12), 0);

        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        topParams.setMargins(dp(8), dp(8), dp(8), 0);
        root.addView(topBar, topParams);
    }

    private void showMoreOptions() {
        new AlertDialog.Builder(this)
                .setTitle("操作")
                .setItems(new CharSequence[]{"检查更新", "文件访问权限"},
                        (dialog, which) -> {
                            if (which == 0) {
                                checkForUpdates(true);
                            } else if (which == 1) {
                                showAccessOptions();
                            }
                        })
                .show();
    }

    private void showBrowseOptions() {
        new AlertDialog.Builder(this)
                .setTitle("浏览")
                .setItems(new CharSequence[]{"Shizuku 浏览", "系统浏览"}, (dialog, which) -> {
                    if (which == 0) {
                        showShizukuPickOptions();
                    } else {
                        pickVideo();
                    }
                })
                .show();
    }

    private void toggleFavoriteCurrent() {
        FavoriteItem item = currentFavoriteItem();
        if (item == null) {
            Toast.makeText(this, "当前没有可收藏的视频", Toast.LENGTH_SHORT).show();
            return;
        }
        List<FavoriteItem> favorites = favoriteItems();
        int existingIndex = favoriteIndexOf(favorites, item.key);
        if (existingIndex >= 0) {
            favorites.remove(existingIndex);
            saveFavoriteItems(favorites);
            Toast.makeText(this, "已取消收藏", Toast.LENGTH_SHORT).show();
        } else {
            favorites.add(0, item);
            saveFavoriteItems(favorites);
            Toast.makeText(this, "已收藏", Toast.LENGTH_SHORT).show();
        }
        updateFavoriteButton();
    }

    private void showFavoriteList() {
        List<FavoriteItem> favorites = favoriteItems();
        if (favorites.isEmpty()) {
            Toast.makeText(this, "暂无收藏", Toast.LENGTH_SHORT).show();
            return;
        }
        int generation = ++previewGeneration;
        ListView listView = new ListView(this);
        listView.setDividerHeight(0);
        FavoriteItemAdapter adapter = new FavoriteItemAdapter(favorites, generation);
        listView.setAdapter(adapter);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("收藏")
                .setView(listView)
                .setNegativeButton("关闭", null)
                .setNeutralButton("管理", (ignored, which) -> showFavoriteManageList())
                .create();
        listView.setOnItemClickListener((parent, view, which, id) -> {
            dialog.dismiss();
            openFavoriteItem(favorites.get(which));
        });
        listView.setOnItemLongClickListener((parent, view, which, id) -> {
            FavoriteItem item = favorites.get(which);
            new AlertDialog.Builder(this)
                    .setTitle("移出收藏")
                    .setMessage(item.title)
                    .setNegativeButton("取消", null)
                    .setPositiveButton("移出", (confirmDialog, confirmWhich) -> {
                        removeFavoriteForKey(item.key);
                        favorites.remove(which);
                        adapter.notifyDataSetChanged();
                        if (favorites.isEmpty()) {
                            dialog.dismiss();
                        }
                        Toast.makeText(this, "已移出收藏", Toast.LENGTH_SHORT).show();
                    })
                    .show();
            return true;
        });
        dialog.setOnDismissListener(dialogInterface -> previewGeneration++);
        dialog.show();
    }

    private void showFavoriteManageList() {
        List<FavoriteItem> favorites = favoriteItems();
        if (favorites.isEmpty()) {
            Toast.makeText(this, "暂无收藏", Toast.LENGTH_SHORT).show();
            return;
        }
        CharSequence[] labels = new CharSequence[favorites.size()];
        boolean[] checked = new boolean[favorites.size()];
        for (int i = 0; i < favorites.size(); i++) {
            labels[i] = favorites.get(i).title;
            checked[i] = false;
        }
        new AlertDialog.Builder(this)
                .setTitle("移出收藏")
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton("取消", null)
                .setPositiveButton("移出", (dialog, which) -> {
                    List<FavoriteItem> latest = favoriteItems();
                    Set<String> removing = new HashSet<>();
                    for (int i = 0; i < checked.length && i < favorites.size(); i++) {
                        if (checked[i]) {
                            removing.add(favorites.get(i).key);
                        }
                    }
                    if (removing.isEmpty()) {
                        return;
                    }
                    List<FavoriteItem> kept = new ArrayList<>();
                    for (FavoriteItem favorite : latest) {
                        if (!removing.contains(favorite.key)) {
                            kept.add(favorite);
                        }
                    }
                    saveFavoriteItems(kept);
                    updateFavoriteButton();
                    Toast.makeText(this, "已移出收藏", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void openFavoriteItem(FavoriteItem item) {
        if (item == null) {
            return;
        }
        clearPlaybackQueue();
        pathInput.setText(item.path);
        saveLastPath(item.path);
        if (item.shizuku) {
            openVideo(Uri.fromParts("shizuku", item.path, null), 0, true);
        } else if (item.contentUri) {
            openVideo(Uri.parse(item.path), 0, true);
        } else {
            openVideo(Uri.fromFile(new File(item.path)), 0, true);
        }
    }

    private FavoriteItem currentFavoriteItem() {
        if (currentUri == null) {
            String raw = pathInput == null ? "" : pathInput.getText().toString().trim();
            if (raw.isEmpty()) {
                return null;
            }
            if (raw.startsWith("content://")) {
                return new FavoriteItem(raw, displayTitle(Uri.parse(raw)), raw, false, true);
            }
            return new FavoriteItem("file:" + raw, new File(raw).getName(), raw, false, false);
        }
        if ("shizuku".equals(currentUri.getScheme())) {
            String path = currentUri.getSchemeSpecificPart();
            return new FavoriteItem("shizuku:" + path, new File(path).getName(), path, true, false);
        }
        if ("content".equals(currentUri.getScheme())) {
            String uri = currentUri.toString();
            return new FavoriteItem(uri, displayTitle(currentUri), uri, false, true);
        }
        if ("file".equals(currentUri.getScheme())) {
            String path = currentUri.getPath();
            return new FavoriteItem("file:" + path, new File(path).getName(), path, false, false);
        }
        String uri = currentUri.toString();
        return new FavoriteItem(uri, displayTitle(currentUri), uri, false, uri.startsWith("content://"));
    }

    private List<FavoriteItem> favoriteItems() {
        List<FavoriteItem> items = new ArrayList<>();
        String raw = getPreferences().getString(KEY_FAVORITES, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            Set<String> seen = new LinkedHashSet<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) {
                    continue;
                }
                String key = object.optString("key", "");
                String path = object.optString("path", "");
                if (key.isEmpty() || path.isEmpty() || seen.contains(key)) {
                    continue;
                }
                seen.add(key);
                String title = object.optString("title", "");
                if (title.isEmpty()) {
                    title = new File(path).getName();
                }
                items.add(new FavoriteItem(key, title, path,
                        object.optBoolean("shizuku", false),
                        object.optBoolean("contentUri", false)));
            }
        } catch (Exception exception) {
            Log.w(TAG, "Failed to parse favorites", exception);
        }
        return items;
    }

    private void saveFavoriteItems(List<FavoriteItem> items) {
        JSONArray array = new JSONArray();
        Set<String> seen = new HashSet<>();
        for (FavoriteItem item : items) {
            if (item == null || item.key.isEmpty() || item.path.isEmpty() || seen.contains(item.key)) {
                continue;
            }
            seen.add(item.key);
            JSONObject object = new JSONObject();
            try {
                object.put("key", item.key);
                object.put("title", item.title);
                object.put("path", item.path);
                object.put("shizuku", item.shizuku);
                object.put("contentUri", item.contentUri);
                array.put(object);
            } catch (Exception exception) {
                Log.w(TAG, "Failed to save favorite item", exception);
            }
        }
        getPreferences().edit().putString(KEY_FAVORITES, array.toString()).apply();
    }

    private int favoriteIndexOf(List<FavoriteItem> favorites, String key) {
        for (int i = 0; i < favorites.size(); i++) {
            if (favorites.get(i).key.equals(key)) {
                return i;
            }
        }
        return -1;
    }

    private void updateFavoriteButton() {
        if (favoriteButton == null) {
            return;
        }
        FavoriteItem item = currentFavoriteItem();
        boolean favorite = item != null && favoriteIndexOf(favoriteItems(), item.key) >= 0;
        favoriteButton.setText(favorite ? "★" : "☆");
    }

    private void removeFavoriteForPath(String path, boolean shizuku, boolean contentUri) {
        if (path == null || path.isEmpty()) {
            return;
        }
        String key = contentUri ? path : (shizuku ? "shizuku:" + path : "file:" + path);
        removeFavoriteForKey(key);
    }

    private void removeFavoriteForKey(String key) {
        if (key == null || key.isEmpty()) {
            return;
        }
        List<FavoriteItem> favorites = favoriteItems();
        List<FavoriteItem> kept = new ArrayList<>();
        boolean removed = false;
        for (FavoriteItem favorite : favorites) {
            if (key.equals(favorite.key)) {
                removed = true;
            } else {
                kept.add(favorite);
            }
        }
        if (removed) {
            saveFavoriteItems(kept);
            updateFavoriteButton();
        }
    }

    private void showScaleModeMenu() {
        new AlertDialog.Builder(this)
                .setTitle("画面模式")
                .setItems(new CharSequence[]{"适配", "填充", "拉伸", "原始"}, (dialog, which) -> {
                    videoScaleMode = which;
                    fillMode = videoScaleMode == SCALE_MODE_FILL;
                    getPreferences().edit()
                            .putInt(KEY_VIDEO_SCALE_MODE, videoScaleMode)
                            .putBoolean(KEY_LAST_FILL, fillMode)
                            .apply();
                    fitButton.setText(scaleModeLabel());
                    applyVideoTransform();
                    revealControls();
                })
                .show();
    }

    private String scaleModeLabel() {
        if (videoScaleMode == SCALE_MODE_FILL) {
            return "填充";
        }
        if (videoScaleMode == SCALE_MODE_STRETCH) {
            return "拉伸";
        }
        if (videoScaleMode == SCALE_MODE_ORIGINAL) {
            return "原始";
        }
        return "适配";
    }

    private void showPlaybackModeMenu() {
        new AlertDialog.Builder(this)
                .setTitle("播放模式")
                .setItems(new CharSequence[]{"单集", "顺序", "列表循环", "单集循环"}, (dialog, which) -> {
                    playbackMode = which;
                    autoNext = playbackMode != PLAYBACK_MODE_SINGLE;
                    getPreferences().edit()
                            .putInt(KEY_PLAYBACK_MODE, playbackMode)
                            .putBoolean(KEY_AUTO_NEXT, autoNext)
                            .apply();
                    autoNextButton.setText(playbackModeLabel());
                    revealControls();
                })
                .show();
    }

    private String playbackModeLabel() {
        if (playbackMode == PLAYBACK_MODE_ONE_LOOP) {
            return "单循";
        }
        if (playbackMode == PLAYBACK_MODE_LIST_LOOP) {
            return "循环";
        }
        if (playbackMode == PLAYBACK_MODE_SEQUENCE) {
            return "顺序";
        }
        return "单集";
    }

    private void buildBottomBar() {
        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.VERTICAL);
        bottomBar.setPadding(dp(10), dp(6), dp(10), dp(6));
        bottomBar.setBackgroundResource(R.drawable.bg_panel);

        LinearLayout progressRow = new LinearLayout(this);
        progressRow.setGravity(Gravity.CENTER_VERTICAL);
        progressRow.setOrientation(LinearLayout.HORIZONTAL);

        seekBar = new SeekBar(this);
        seekBar.setMax(1000);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            seekBar.setProgressDrawable(getResources().getDrawable(R.drawable.progress_layer, getTheme()));
        } else {
            seekBar.setProgressDrawable(getResources().getDrawable(R.drawable.progress_layer));
        }
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser && prepared && player != null) {
                    int target = (int) (player.getDuration() * (progress / 1000f));
                    timeText.setText(formatTime(target) + " / " + formatTime(player.getDuration()));
                    showFeedback(formatTime(target));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                userSeeking = true;
                revealControls();
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                if (prepared && player != null) {
                    int target = (int) (player.getDuration() * (bar.getProgress() / 1000f));
                    player.seekTo(target);
                    saveVideoPosition(currentUri, target);
                    showFeedback(formatTime(target));
                }
                userSeeking = false;
                scheduleControlsHide();
            }
        });
        progressRow.addView(seekBar, new LinearLayout.LayoutParams(0, dp(34), 1f));

        timeText = makeText(12, Color.rgb(220, 226, 235), false);
        timeText.setText("00:00 / 00:00");
        timeText.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(dp(104), dp(34));
        timeParams.leftMargin = dp(8);
        progressRow.addView(timeText, timeParams);
        bottomBar.addView(progressRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        HorizontalScrollView controlsScroll = new HorizontalScrollView(this);
        controlsScroll.setHorizontalScrollBarEnabled(false);
        controlsScroll.setFillViewport(true);

        LinearLayout primaryRow = new LinearLayout(this);
        primaryRow.setGravity(Gravity.CENTER_VERTICAL);
        primaryRow.setOrientation(LinearLayout.HORIZONTAL);
        primaryRow.setPadding(0, dp(4), 0, 0);

        playButton = makeCompactButton("▶");
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                togglePlay();
            }
        });
        primaryRow.addView(playButton, new LinearLayout.LayoutParams(dp(44), dp(36)));

        previousButton = makeCompactButton("◀◀");
        previousButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switchPlaybackItem(-1);
            }
        });
        LinearLayout.LayoutParams previousParams = new LinearLayout.LayoutParams(dp(48), dp(36));
        previousParams.leftMargin = dp(6);
        primaryRow.addView(previousButton, previousParams);

        nextButton = makeCompactButton("▶▶");
        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switchPlaybackItem(1);
            }
        });
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(dp(48), dp(36));
        nextParams.leftMargin = dp(6);
        primaryRow.addView(nextButton, nextParams);

        Button restartButton = makeCompactButton("重播");
        restartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                restartCurrentVideo();
            }
        });
        LinearLayout.LayoutParams restartParams = new LinearLayout.LayoutParams(dp(52), dp(36));
        restartParams.leftMargin = dp(6);
        primaryRow.addView(restartButton, restartParams);

        speedSpinner = new Spinner(this, Spinner.MODE_DROPDOWN);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                SPEED_LABELS);
        speedSpinner.setAdapter(adapter);
        speedSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                playbackSpeed = SPEED_VALUES[position];
                getPreferences().edit().putFloat(KEY_LAST_SPEED, playbackSpeed).apply();
                applyPlaybackSpeed();
                revealControls();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        LinearLayout.LayoutParams speedParams = new LinearLayout.LayoutParams(dp(78), dp(36));
        speedParams.leftMargin = dp(10);
        primaryRow.addView(speedSpinner, speedParams);

        fitButton = makeCompactButton(scaleModeLabel());
        fitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showScaleModeMenu();
            }
        });
        LinearLayout.LayoutParams fitParams = new LinearLayout.LayoutParams(dp(56), dp(36));
        fitParams.leftMargin = dp(6);
        primaryRow.addView(fitButton, fitParams);

        lockButton = makeCompactButton("锁");
        lockButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                controlsLocked = !controlsLocked;
                updateLockState();
                if (!controlsLocked) {
                    revealControls();
                }
            }
        });
        LinearLayout.LayoutParams lockParams = new LinearLayout.LayoutParams(dp(44), dp(36));
        lockParams.leftMargin = dp(6);
        primaryRow.addView(lockButton, lockParams);

        autoNextButton = makeCompactButton(playbackModeLabel());
        autoNextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showPlaybackModeMenu();
            }
        });
        LinearLayout.LayoutParams autoNextParams = new LinearLayout.LayoutParams(dp(56), dp(36));
        autoNextParams.leftMargin = dp(6);
        primaryRow.addView(autoNextButton, autoNextParams);

        controlsScroll.addView(primaryRow, new HorizontalScrollView.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        bottomBar.addView(controlsScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(42)));

        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        bottomParams.setMargins(dp(8), 0, dp(8), dp(8));
        root.addView(bottomBar, bottomParams);
    }

    private void buildShizukuBrowserOverlay() {
        shizukuBrowserOverlay = new FrameLayout(this);
        shizukuBrowserOverlay.setBackgroundColor(Color.rgb(8, 11, 15));
        shizukuBrowserOverlay.setVisibility(View.GONE);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(12), dp(12), dp(12), dp(12));
        shizukuBrowserOverlay.addView(page, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout topRow = new LinearLayout(this);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        Button closeButton = makeButton("返回");
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hideShizukuBrowser();
            }
        });
        topRow.addView(closeButton, new LinearLayout.LayoutParams(dp(64), dp(40)));

        browserPathText = makeText(14, Color.WHITE, true);
        browserPathText.setSingleLine(true);
        LinearLayout.LayoutParams pathParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        pathParams.leftMargin = dp(10);
        topRow.addView(browserPathText, pathParams);
        page.addView(topRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout toolRow = new LinearLayout(this);
        toolRow.setGravity(Gravity.CENTER_VERTICAL);
        toolRow.setOrientation(LinearLayout.HORIZONTAL);
        toolRow.setPadding(0, dp(10), 0, dp(8));

        browserSortButton = makeButton("名称");
        browserSortButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showSortModeMenu();
            }
        });
        toolRow.addView(browserSortButton, new LinearLayout.LayoutParams(0, dp(40), 1f));

        browserDirectionButton = makeButton("正序");
        browserDirectionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (shizukuSortMode == SORT_RANDOM) {
                    shizukuRandomSeed = System.nanoTime();
                } else {
                    shizukuSortAscending = !shizukuSortAscending;
                }
                refreshCurrentBrowserEntries();
            }
        });
        LinearLayout.LayoutParams directionParams = new LinearLayout.LayoutParams(0, dp(40), 1f);
        directionParams.leftMargin = dp(8);
        toolRow.addView(browserDirectionButton, directionParams);

        browserFilterButton = makeButton(videoOnlyBrowsing ? "仅视频" : "全部");
        browserFilterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                videoOnlyBrowsing = !videoOnlyBrowsing;
                getPreferences().edit().putBoolean(KEY_VIDEO_ONLY, videoOnlyBrowsing).apply();
                refreshCurrentBrowserEntries();
            }
        });
        LinearLayout.LayoutParams filterParams = new LinearLayout.LayoutParams(0, dp(40), 1f);
        filterParams.leftMargin = dp(8);
        toolRow.addView(browserFilterButton, filterParams);
        page.addView(toolRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        browserEmptyText = makeText(14, Color.rgb(174, 183, 194), false);
        browserEmptyText.setGravity(Gravity.CENTER);
        browserEmptyText.setText("没有可显示的文件");
        browserEmptyText.setVisibility(View.GONE);
        page.addView(browserEmptyText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44)));

        browserListView = new ListView(this);
        browserListView.setDividerHeight(0);
        page.addView(browserListView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f));

        root.addView(shizukuBrowserOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            shizukuBrowserOverlay.setElevation(dp(24));
        }
    }

    private void bindGestures() {
        root.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (isControlView(event)) {
                    return false;
                }
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        touchDownX = event.getX();
                        touchDownY = event.getY();
                        touchDownTime = System.currentTimeMillis();
                        touchMoved = false;
                        activeVerticalGesture = GESTURE_NONE;
                        longPressTriggered = false;
                        if (!controlsLocked) {
                            handler.postDelayed(longPressRunnable, LONG_PRESS_DELAY_MS);
                        }
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(event.getX() - touchDownX) > dp(16)
                                || Math.abs(event.getY() - touchDownY) > dp(16)) {
                            touchMoved = true;
                            handler.removeCallbacks(longPressRunnable);
                        }
                        if (!controlsLocked && handleVerticalGesture(event)) {
                            return true;
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        handler.removeCallbacks(longPressRunnable);
                        handler.removeCallbacks(singleTapRunnable);
                        stopLongPressSpeed();
                        return true;
                    case MotionEvent.ACTION_UP:
                        handler.removeCallbacks(longPressRunnable);
                        if (longPressTriggered) {
                            stopLongPressSpeed();
                            return true;
                        }
                        if (controlsLocked) {
                            showFeedback("控制已锁定");
                            return true;
                        }
                        if (handleProgressSwipe(event)) {
                            tapCount = 0;
                            handler.removeCallbacks(singleTapRunnable);
                            return true;
                        }
                        long now = System.currentTimeMillis();
                        boolean sameTapSeries = now - lastTapTime < TAP_DELAY_MS
                                && Math.abs(event.getX() - lastTapX) < dp(80);
                        tapCount = sameTapSeries ? tapCount + 1 : 1;
                        lastTapTime = now;
                        lastTapX = event.getX();
                        if (tapCount == 1) {
                            handler.postDelayed(singleTapRunnable, TAP_DELAY_MS);
                        } else if (tapCount == 2) {
                            handler.removeCallbacks(singleTapRunnable);
                            int delta = event.getX() < root.getWidth() / 2f ? -SEEK_STEP_MS : SEEK_STEP_MS;
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    if (tapCount == 2) {
                                        seekBy(delta);
                                        tapCount = 0;
                                    }
                                }
                            }, TAP_DELAY_MS);
                        } else {
                            handler.removeCallbacks(singleTapRunnable);
                            seekBy(event.getX() < root.getWidth() / 2f ? -SEEK_TRIPLE_STEP_MS : SEEK_TRIPLE_STEP_MS);
                            tapCount = 0;
                        }
                        return true;
                    default:
                        return true;
                }
            }
        });
    }

    private boolean isControlView(MotionEvent event) {
        if (browserVisible) {
            return true;
        }
        return isPointInside(topBar, event.getX(), event.getY())
                || isPointInside(bottomBar, event.getX(), event.getY())
                || isPointInside(unlockButton, event.getX(), event.getY())
                || isPointInside(centerPlayButton, event.getX(), event.getY());
    }

    private boolean handleProgressSwipe(MotionEvent event) {
        if (!touchMoved || root == null || player == null || !prepared) {
            return false;
        }
        float deltaX = event.getX() - touchDownX;
        float deltaY = event.getY() - touchDownY;
        long duration = System.currentTimeMillis() - touchDownTime;
        if (duration > SWIPE_SWITCH_MAX_DURATION_MS
                || Math.abs(deltaX) < dp(SWIPE_SWITCH_MIN_DISTANCE_DP)
                || Math.abs(deltaX) < Math.abs(deltaY) * 1.6f) {
            return false;
        }
        int videoDuration = Math.max(player.getDuration(), 0);
        if (videoDuration <= 0) {
            return false;
        }
        int maxDeltaMs = Math.max(30_000, videoDuration / 3);
        int deltaMs = Math.round((deltaX / Math.max(root.getWidth(), 1)) * maxDeltaMs);
        int target = Math.max(0, Math.min(videoDuration, player.getCurrentPosition() + deltaMs));
        player.seekTo(target);
        saveVideoPosition(currentUri, target);
        updateProgress();
        showFeedback((deltaMs >= 0 ? "+" : "-") + formatTime(Math.abs(deltaMs)) + "  " + formatTime(target));
        return true;
    }

    private boolean handleVerticalGesture(MotionEvent event) {
        if (!prepared || root == null || root.getHeight() <= 0) {
            return false;
        }
        float deltaX = event.getX() - touchDownX;
        float deltaY = event.getY() - touchDownY;
        if (activeVerticalGesture == GESTURE_NONE) {
            if (Math.abs(deltaY) < dp(36) || Math.abs(deltaY) < Math.abs(deltaX) * 1.4f) {
                return false;
            }
            activeVerticalGesture = touchDownX < root.getWidth() / 2f ? GESTURE_BRIGHTNESS : GESTURE_VOLUME;
            gestureStartBrightness = currentBrightness();
            AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
            gestureStartVolume = audioManager == null ? 0 : audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        }
        float percentDelta = -deltaY / Math.max(root.getHeight(), 1);
        if (activeVerticalGesture == GESTURE_VOLUME) {
            adjustVolume(percentDelta);
        } else if (activeVerticalGesture == GESTURE_BRIGHTNESS) {
            adjustBrightness(percentDelta);
        }
        return true;
    }

    private void adjustVolume(float percentDelta) {
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioManager == null) {
            return;
        }
        int maxVolume = Math.max(audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 1);
        int target = Math.max(0, Math.min(maxVolume,
                gestureStartVolume + Math.round(percentDelta * maxVolume)));
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
        showFeedback("音量 " + Math.round(target * 100f / maxVolume) + "%");
    }

    private float currentBrightness() {
        WindowManager.LayoutParams params = getWindow().getAttributes();
        if (params.screenBrightness >= 0f) {
            return params.screenBrightness;
        }
        return 0.5f;
    }

    private void adjustBrightness(float percentDelta) {
        float target = Math.max(0.02f, Math.min(1f, gestureStartBrightness + percentDelta));
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.screenBrightness = target;
        getWindow().setAttributes(params);
        showFeedback("亮度 " + Math.round(target * 100f) + "%");
    }

    private boolean isPointInside(View target, float x, float y) {
        if (target == null || target.getVisibility() != View.VISIBLE) {
            return false;
        }
        int[] rootLocation = new int[2];
        int[] targetLocation = new int[2];
        root.getLocationOnScreen(rootLocation);
        target.getLocationOnScreen(targetLocation);
        float left = targetLocation[0] - rootLocation[0];
        float top = targetLocation[1] - rootLocation[1];
        return x >= left && x <= left + target.getWidth()
                && y >= top && y <= top + target.getHeight();
    }

    private void showPrivacyNoticeIfNeeded() {
        if (getPreferences().getBoolean(KEY_PRIVACY_ACCEPTED, false)) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("权限说明")
                .setMessage("Smooth Player 会读取你选择的媒体或文件用于本地播放；会访问网络用于检查更新和下载更新包；在你输入受限路径并确认打开时，可能通过 root/su 将文件挂载到本应用目录后播放，不额外复制大文件。")
                .setPositiveButton("知道了", (dialog, which) ->
                        getPreferences().edit().putBoolean(KEY_PRIVACY_ACCEPTED, true).apply())
                .show();
    }

    private void requestStoragePermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_MEDIA_VIDEO}, REQUEST_READ_STORAGE);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT <= 28
                && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_READ_STORAGE);
        }
    }

    private void requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            requestStoragePermissionIfNeeded();
            Toast.makeText(this, "已请求存储读取权限", Toast.LENGTH_SHORT).show();
            return;
        }
        if (android.os.Environment.isExternalStorageManager()) {
            Toast.makeText(this, "已拥有所有文件访问权限", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException exception) {
            startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
        }
    }

    private void showAccessOptions() {
        new AlertDialog.Builder(this)
                .setTitle("文件访问")
                .setItems(new CharSequence[]{"系统所有文件权限", "检测 Shizuku", "检测 root/su", "清理 root 挂载/缓存"},
                        (dialog, which) -> {
                            if (which == 0) {
                                requestAllFilesAccess();
                            } else if (which == 1) {
                                checkShizukuAccess();
                            } else if (which == 2) {
                                checkRootAccess();
                            } else {
                                clearRootCache();
                            }
                        })
                .show();
    }

    private void checkRootAccess() {
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                boolean available = canRunSu();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this,
                                available ? "root/su 可用" : "root/su 不可用或未授权",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void clearRootCache() {
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                unmountAndDeleteChildren(rootMountDir());
                deleteChildren(rootCacheDir());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "root 缓存已清理", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_READ_STORAGE && grantResults.length > 0
                && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "可通过系统选择文件继续播放视频", Toast.LENGTH_LONG).show();
        } else if (requestCode == REQUEST_SHIZUKU_PERMISSION) {
            if (isShizukuGranted() && pendingShizukuPath != null) {
                String path = pendingShizukuPath;
                pendingShizukuPath = null;
                if (new File(path).isDirectory()) {
                    showShizukuDirectory(path);
                } else {
                    openRestrictedPathWithShizuku(path);
                }
            } else {
                Toast.makeText(this, "Shizuku 未授权", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openFromPathInput() {
        String raw = pathInput.getText().toString().trim();
        if (raw.isEmpty()) {
            showFeedback("请输入路径或选择文件");
            return;
        }
        clearPlaybackQueue();

        Uri uri;
        if (raw.startsWith("content://") || raw.startsWith("file://")) {
            uri = Uri.parse(raw);
        } else {
            File file = new File(raw);
            if (!file.exists()) {
                if (looksLikeRestrictedPath(raw)) {
                    openRestrictedPathOptions(raw);
                } else {
                    showFeedback("直接路径不可访问，请选择文件");
                }
                return;
            }
            if (looksLikeRestrictedPath(raw)) {
                openRestrictedPathOptions(raw);
                return;
            }
            uri = Uri.fromFile(file);
            setLocalPlaybackQueue(file);
        }
        saveLastPath(raw);
        openVideo(uri, 0, true);
    }

    private void showShizukuPickOptions() {
        String lastDir = getPreferences().getString(KEY_LAST_SHIZUKU_DIR, "");
        new AlertDialog.Builder(this)
                .setTitle("Shizuku 浏览")
                .setItems(shizukuPickLabels(lastDir),
                        (dialog, which) -> {
                            if (!lastDir.isEmpty() && which == 0) {
                                browseRestrictedDirectory(lastDir);
                            } else if (which == (lastDir.isEmpty() ? 0 : 1)) {
                                browseRestrictedDirectory("/sdcard/Android/data");
                            } else if (which == (lastDir.isEmpty() ? 1 : 2)) {
                                browseRestrictedDirectory("/sdcard/Android/obb");
                            } else {
                                browseRestrictedDirectory("/sdcard");
                            }
                        })
                .show();
    }

    private CharSequence[] shizukuPickLabels(String lastDir) {
        if (lastDir == null || lastDir.trim().isEmpty()) {
            return new CharSequence[]{"Android/data", "Android/obb", "存储根目录"};
        }
        return new CharSequence[]{"上次目录", "Android/data", "Android/obb", "存储根目录"};
    }

    private void pickVideo() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, PICKER_MIME_TYPES);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_PICK_VIDEO);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, "没有可用的文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    private void persistReadPermission(Uri uri, int flags) {
        if (!"content".equals(uri.getScheme()) || (flags & Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0) {
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException exception) {
            Log.w(TAG, "Provider did not grant persistable read permission: " + uri, exception);
        }
        if ((flags & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0) {
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (SecurityException exception) {
                Log.w(TAG, "Provider did not grant persistable write permission: " + uri, exception);
            }
        }
    }

    private void setPendingVideo(Uri uri, int seekMs, boolean autoPlay) {
        pendingUri = uri;
        pendingSeekMs = Math.max(0, seekMs);
        pendingAutoPlay = autoPlay;
        if (surfaceReady && surface != null) {
            Uri target = pendingUri;
            pendingUri = null;
            openVideo(target, pendingSeekMs, pendingAutoPlay);
            pendingSeekMs = 0;
        }
    }

    private void openVideo(Uri uri, int seekMs, boolean autoPlay) {
        if (!surfaceReady || surface == null) {
            setPendingVideo(uri, seekMs, autoPlay);
            return;
        }
        if ("shizuku".equals(uri.getScheme())) {
            openShizukuVideoAsync(uri, seekMs, autoPlay);
            return;
        }

        loading.setVisibility(View.VISIBLE);
        releasePlayer();
        currentUri = uri;
        updateFavoriteButton();

        player = new MediaPlayer();
        prepared = false;
        videoWidth = 0;
        videoHeight = 0;
        try {
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            player.setSurface(surface);
            player.setDataSource(this, uri);
            attachPlayerListeners(uri, seekMs, autoPlay);
            player.prepareAsync();
        } catch (IOException | IllegalArgumentException | SecurityException error) {
            Log.w(TAG, "Failed to open video: " + uri, error);
            loading.setVisibility(View.GONE);
            releasePlayer();
            if (isRestrictedFileUri(uri)) {
                openRestrictedPathOptions(uri.getPath());
            } else {
                showFeedback(error instanceof SecurityException ? "没有文件访问权限" : "打开失败");
            }
        }
    }

    private void attachPlayerListeners(Uri uri, int seekMs, boolean autoPlay) {
        player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                prepared = true;
                loading.setVisibility(View.GONE);
                videoWidth = mediaPlayer.getVideoWidth();
                videoHeight = mediaPlayer.getVideoHeight();
                titleText.setText(displayTitle(uri));
                updateFavoriteButton();
                int resumeMs = seekMs > 0 ? seekMs : savedVideoPosition(uri);
                if (resumeMs > 0) {
                    mediaPlayer.seekTo(Math.min(resumeMs, mediaPlayer.getDuration()));
                }
                if (autoPlay) {
                    mediaPlayer.start();
                }
                applyPlaybackSpeed();
                applyVideoTransform();
                updatePlayButton();
                updateProgress();
                revealControls();
            }
        });
        player.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener() {
            @Override
            public void onVideoSizeChanged(MediaPlayer mediaPlayer, int width, int height) {
                videoWidth = width;
                videoHeight = height;
                applyVideoTransform();
            }
        });
        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                saveVideoPosition(uri, 0);
                updatePlayButton();
                if (playbackMode == PLAYBACK_MODE_ONE_LOOP) {
                    mediaPlayer.seekTo(0);
                    mediaPlayer.start();
                    applyPlaybackSpeed();
                    return;
                }
                if (playbackMode != PLAYBACK_MODE_SINGLE && switchPlaybackItem(1)) {
                    return;
                }
                revealControls();
                showFeedback("播放完成");
            }
        });
        player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mediaPlayer, int what, int extra) {
                Log.w(TAG, "MediaPlayer error what=" + what + " extra=" + extra + " uri=" + uri);
                loading.setVisibility(View.GONE);
                prepared = false;
                showFeedback(errorMessage(what, extra));
                return true;
            }
        });
    }

    private void openShizukuVideoAsync(Uri uri, int seekMs, boolean autoPlay) {
        loading.setVisibility(View.VISIBLE);
        releasePlayer();
        currentUri = uri;
        String path = uri.getSchemeSpecificPart();
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    ParcelFileDescriptor descriptor = openFileDescriptorWithShizuku(path);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            prepareShizukuVideo(uri, descriptor, seekMs, autoPlay);
                        }
                    });
                } catch (IOException | RemoteException | InterruptedException exception) {
                    Log.w(TAG, "Failed to open Shizuku fd: " + path, exception);
                    if (exception instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            loading.setVisibility(View.GONE);
                            releasePlayer();
                            showFeedback(shizukuErrorMessage(exception));
                        }
                    });
                }
            }
        });
    }

    private void prepareShizukuVideo(Uri uri, ParcelFileDescriptor descriptor, int seekMs, boolean autoPlay) {
        if (!surfaceReady || surface == null) {
            try {
                descriptor.close();
            } catch (IOException exception) {
                Log.w(TAG, "Failed to close pending Shizuku fd", exception);
            }
            setPendingVideo(uri, seekMs, autoPlay);
            return;
        }
        closeActiveShizukuFd();
        activeShizukuFd = descriptor;
        loading.setVisibility(View.VISIBLE);
        player = new MediaPlayer();
        prepared = false;
        videoWidth = 0;
        videoHeight = 0;
        try {
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            player.setSurface(surface);
            player.setDataSource(descriptor.getFileDescriptor());
            attachPlayerListeners(uri, seekMs, autoPlay);
            player.prepareAsync();
        } catch (IOException | IllegalArgumentException | SecurityException error) {
            Log.w(TAG, "Failed to prepare Shizuku video: " + uri, error);
            loading.setVisibility(View.GONE);
            releasePlayer();
            showFeedback(error instanceof SecurityException ? "没有文件访问权限" : "打开失败");
        }
    }

    private void openPlaybackItem(PlaybackItem item, boolean autoPlay) {
        if (item == null) {
            return;
        }
        saveCurrentVideoPosition();
        pathInput.setText(item.path);
        saveLastPath(item.path);
        if (item.shizuku) {
            openVideo(Uri.fromParts("shizuku", item.path, null), 0, autoPlay);
        } else {
            openVideo(Uri.fromFile(new File(item.path)), 0, autoPlay);
        }
    }

    private void setPlaybackQueue(List<PlaybackItem> items, String selectedPath) {
        playbackQueue.clear();
        playbackQueue.addAll(items);
        currentQueueIndex = -1;
        for (int i = 0; i < playbackQueue.size(); i++) {
            if (playbackQueue.get(i).path.equals(selectedPath)) {
                currentQueueIndex = i;
                break;
            }
        }
    }

    private void setLocalPlaybackQueue(File selectedFile) {
        clearPlaybackQueue();
        if (selectedFile == null || !selectedFile.isFile()) {
            return;
        }
        File parent = selectedFile.getParentFile();
        File[] files = parent == null ? null : parent.listFiles();
        if (files == null) {
            return;
        }
        List<RestrictedEntry> entries = new ArrayList<>();
        for (File file : files) {
            if (file.isFile()) {
                RestrictedEntry entry = new RestrictedEntry(false, file.length(), file.lastModified(),
                        file.getName(), file.getAbsolutePath());
                if (entry.looksLikeVideo()) {
                    entries.add(entry);
                }
            }
        }
        sortRestrictedEntries(entries, false);
        List<PlaybackItem> queue = new ArrayList<>();
        for (RestrictedEntry entry : entries) {
            queue.add(new PlaybackItem(entry.path, false));
        }
        setPlaybackQueue(queue, selectedFile.getAbsolutePath());
    }

    private void clearPlaybackQueue() {
        playbackQueue.clear();
        currentQueueIndex = -1;
    }

    private boolean switchPlaybackItem(int direction) {
        if (playbackQueue.size() <= 1 || currentQueueIndex < 0) {
            showFeedback("没有可切换的视频");
            return false;
        }
        int nextIndex = currentQueueIndex + direction;
        if ((nextIndex < 0 || nextIndex >= playbackQueue.size())
                && playbackMode == PLAYBACK_MODE_LIST_LOOP) {
            nextIndex = nextIndex < 0 ? playbackQueue.size() - 1 : 0;
        }
        if (nextIndex < 0 || nextIndex >= playbackQueue.size()) {
            showFeedback(direction > 0 ? "已经是最后一个" : "已经是第一个");
            return false;
        }
        boolean wasPlaying = player != null && prepared && player.isPlaying();
        currentQueueIndex = nextIndex;
        PlaybackItem item = playbackQueue.get(currentQueueIndex);
        showFeedback((direction > 0 ? "下一个 " : "上一个 ") + new File(item.path).getName());
        openPlaybackItem(item, wasPlaying || playbackMode != PLAYBACK_MODE_SINGLE);
        return true;
    }

    private PlaybackItem removeFromPlaybackQueue(String path) {
        PlaybackItem fallback = null;
        for (int i = playbackQueue.size() - 1; i >= 0; i--) {
            if (playbackQueue.get(i).path.equals(path)) {
                playbackQueue.remove(i);
                if (i < playbackQueue.size()) {
                    fallback = playbackQueue.get(i);
                } else if (!playbackQueue.isEmpty()) {
                    fallback = playbackQueue.get(playbackQueue.size() - 1);
                }
                if (i < currentQueueIndex) {
                    currentQueueIndex--;
                } else if (i == currentQueueIndex) {
                    currentQueueIndex = Math.min(i, playbackQueue.size() - 1);
                }
            }
        }
        if (playbackQueue.isEmpty()) {
            currentQueueIndex = -1;
        }
        return fallback;
    }

    private void openRestrictedPathOptions(String path) {
        new AlertDialog.Builder(this)
                .setTitle("访问受限路径")
                .setMessage("推荐使用 Shizuku：由 Shizuku 服务打开原文件 fd 后直接播放，不复制大文件。root 挂载作为兜底。")
                .setNegativeButton("取消", null)
                .setNeutralButton("root 选项", (dialog, which) -> openRestrictedPathWithRoot(path))
                .setPositiveButton("Shizuku 播放", (dialog, which) -> openRestrictedPathWithShizuku(path))
                .show();
    }

    private void openRestrictedPathWithRoot(String path) {
        new AlertDialog.Builder(this)
                .setTitle("root 访问")
                .setMessage("通过 root/su 将文件 bind mount 到本应用目录后播放，不占用额外视频空间。")
                .setNegativeButton("取消", null)
                .setNeutralButton("复制兜底", (dialog, which) -> copyRestrictedPathAndPlay(path))
                .setPositiveButton("挂载播放", (dialog, which) -> mountRestrictedPathAndPlay(path))
                .show();
    }

    private void openRestrictedPathWithShizuku(String path) {
        if (!isShizukuAvailable()) {
            Toast.makeText(this, "请先安装并启动 Shizuku", Toast.LENGTH_LONG).show();
            return;
        }
        if (!isShizukuGranted()) {
            pendingShizukuPath = path;
            Shizuku.requestPermission(REQUEST_SHIZUKU_PERMISSION);
            return;
        }
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("正在通过 Shizuku 打开文件");
        progressDialog.setIndeterminate(true);
        progressDialog.setCancelable(false);
        progressDialog.show();
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    ensureShizukuServiceBound();
                    openFileDescriptorWithShizuku(path).close();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.dismiss();
                            pathInput.setText(path);
                            saveLastPath(path);
                            openVideo(Uri.fromParts("shizuku", path, null), 0, true);
                        }
                    });
                } catch (IOException | RemoteException | InterruptedException exception) {
                    Log.w(TAG, "Shizuku open failed: " + path, exception);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.dismiss();
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("Shizuku 打开失败")
                                    .setMessage("请确认 Shizuku 正在运行且已授权本应用。也可以尝试 root 挂载方式。")
                                    .setNegativeButton("取消", null)
                                    .setPositiveButton("root 选项", (dialog, which) ->
                                            openRestrictedPathWithRoot(path))
                                    .show();
                        }
                    });
                }
            }
        });
    }

    private void browseRestrictedDirectory(String startPath) {
        if (startPath != null && !startPath.trim().isEmpty()) {
            saveLastShizukuDir(startPath);
        }
        if (!isShizukuAvailable()) {
            Toast.makeText(this, "请先安装并启动 Shizuku", Toast.LENGTH_LONG).show();
            return;
        }
        if (!isShizukuGranted()) {
            pendingShizukuPath = startPath;
            Shizuku.requestPermission(REQUEST_SHIZUKU_PERMISSION);
            return;
        }
        showShizukuDirectory(startPath);
    }

    private void showShizukuDirectory(String path) {
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    ensureShizukuServiceBound();
                    IRestrictedFileService service = restrictedFileService;
                    if (service == null) {
                        throw new IOException("Shizuku service is not connected");
                    }
                    String[] rawEntries = service.listFiles(path);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showShizukuDirectoryDialog(path, rawEntries, false);
                        }
                    });
                } catch (IOException | RemoteException | InterruptedException exception) {
                    Log.w(TAG, "Shizuku list failed: " + path, exception);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Shizuku 无法浏览此目录", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }

    private void showShizukuDirectoryDialog(String path, String[] rawEntries, boolean sortBySize) {
        shizukuSortMode = sortBySize ? 1 : shizukuSortMode;
        saveLastShizukuDir(path);
        currentBrowserPath = path;
        currentBrowserRawEntries = rawEntries;
        showShizukuBrowser();
        refreshCurrentBrowserEntries();
    }

    private void refreshCurrentBrowserEntries() {
        if (currentBrowserPath == null || currentBrowserRawEntries == null || browserListView == null) {
            return;
        }
        previewGeneration++;
        int generation = previewGeneration;
        List<RestrictedEntry> entries = new ArrayList<>();
        if (!"/sdcard".equals(currentBrowserPath)) {
            entries.add(new RestrictedEntry(true, 0L, 0L, "..", parentPath(currentBrowserPath)));
        }
        List<RestrictedEntry> children = new ArrayList<>();
        for (String rawEntry : currentBrowserRawEntries) {
            RestrictedEntry entry = RestrictedEntry.parse(rawEntry);
            if (entry != null) {
                if (!videoOnlyBrowsing || entry.directory || entry.looksLikeVideo()) {
                    children.add(entry);
                }
            }
        }
        sortRestrictedEntries(children, shizukuSortMode, shizukuSortAscending);
        entries.addAll(children);
        RestrictedEntryAdapter adapter = new RestrictedEntryAdapter(entries, generation);
        browserListView.setAdapter(adapter);
        browserListView.setOnItemClickListener((parent, view, which, id) -> {
            RestrictedEntry entry = entries.get(which);
            if (entry.directory) {
                showShizukuDirectory(entry.path);
            } else {
                hideShizukuBrowser();
                openShizukuEntryFromDirectory(entries, entry);
            }
        });
        browserListView.setOnItemLongClickListener((parent, view, which, id) -> {
            RestrictedEntry entry = entries.get(which);
            if (entry.directory) {
                return false;
            }
            showShizukuFileActions(currentBrowserPath, currentBrowserRawEntries, shizukuSortMode == 1,
                    entries, entry);
            return true;
        });
        browserEmptyText.setVisibility(entries.size() <= 1 ? View.VISIBLE : View.GONE);
        updateBrowserToolbar();
    }

    private void showShizukuBrowser() {
        browserVisible = true;
        if (shizukuBrowserOverlay != null) {
            shizukuBrowserOverlay.setVisibility(View.VISIBLE);
            shizukuBrowserOverlay.bringToFront();
        }
        setControlsVisible(false);
        updatePlayButton();
        hideSystemBars();
    }

    private void hideShizukuBrowser() {
        browserVisible = false;
        currentBrowserPath = null;
        currentBrowserRawEntries = null;
        if (shizukuBrowserOverlay != null) {
            shizukuBrowserOverlay.setVisibility(View.GONE);
        }
        previewGeneration++;
        setControlsVisible(true);
    }

    private void updateBrowserToolbar() {
        if (browserPathText != null) {
            browserPathText.setText(shizukuTitle(currentBrowserPath));
        }
        if (browserSortButton != null) {
            browserSortButton.setText(currentSortName());
        }
        if (browserDirectionButton != null) {
            browserDirectionButton.setText(shizukuSortMode == SORT_RANDOM ? "重排"
                    : (shizukuSortAscending ? "正序" : "倒序"));
        }
        if (browserFilterButton != null) {
            browserFilterButton.setText(videoOnlyBrowsing ? "仅视频" : "全部");
        }
    }

    private void showSortModeMenu() {
        new AlertDialog.Builder(this)
                .setTitle("排序")
                .setItems(new CharSequence[]{"名称", "大小", "时间", "随机"}, (dialog, which) -> {
                    shizukuSortMode = which;
                    if (shizukuSortMode == SORT_RANDOM) {
                        shizukuRandomSeed = System.nanoTime();
                    }
                    refreshCurrentBrowserEntries();
                })
                .show();
    }

    private void openShizukuEntryFromDirectory(List<RestrictedEntry> entries, RestrictedEntry selected) {
        List<PlaybackItem> queue = new ArrayList<>();
        for (RestrictedEntry entry : entries) {
            if (!entry.directory && entry.looksLikeVideo()) {
                queue.add(new PlaybackItem(entry.path, true));
            }
        }
        if (selected.looksLikeVideo()) {
            setPlaybackQueue(queue, selected.path);
        } else {
            clearPlaybackQueue();
        }
        pathInput.setText(selected.path);
        openRestrictedPathWithShizuku(selected.path);
    }

    private void saveLastShizukuDir(String path) {
        getPreferences().edit().putString(KEY_LAST_SHIZUKU_DIR, path).apply();
    }

    private void showShizukuFileActions(String directoryPath, String[] rawEntries, boolean sortBySize,
            List<RestrictedEntry> entries, RestrictedEntry entry) {
        new AlertDialog.Builder(this)
                .setTitle(entry.name)
                .setItems(new CharSequence[]{"播放", "删除文件"}, (dialog, which) -> {
                    if (which == 0) {
                        dialog.dismiss();
                        hideShizukuBrowser();
                        openShizukuEntryFromDirectory(entries, entry);
                    } else {
                        confirmDeleteShizukuFile(directoryPath, rawEntries, sortBySize, entry);
                    }
                })
                .show();
    }

    private void confirmDeleteShizukuFile(String directoryPath, String[] rawEntries, boolean sortBySize,
            RestrictedEntry entry) {
        new AlertDialog.Builder(this)
                .setTitle("删除文件")
                .setMessage("确定删除此文件？\n\n" + entry.name + "\n" + entry.readableSize()
                        + "\n" + entry.path)
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) ->
                        deleteShizukuFile(directoryPath, rawEntries, sortBySize, entry))
                .show();
    }

    private void confirmDeleteCurrentFile() {
        String path = currentFilePath();
        if (path == null || path.trim().isEmpty()) {
            Toast.makeText(this, "当前没有可删除的文件", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("删除文件")
                .setMessage("确定删除当前文件？\n\n" + new File(path).getName())
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> deleteCurrentFile(path))
                .show();
    }

    private String currentFilePath() {
        if (currentUri != null) {
            if ("shizuku".equals(currentUri.getScheme())) {
                return currentUri.getSchemeSpecificPart();
            }
            if ("content".equals(currentUri.getScheme())) {
                return currentUri.toString();
            }
            if ("file".equals(currentUri.getScheme())) {
                return currentUri.getPath();
            }
        }
        String raw = pathInput == null ? "" : pathInput.getText().toString().trim();
        if (raw.startsWith("file://")) {
            return Uri.parse(raw).getPath();
        }
        if (!raw.startsWith("content://")) {
            return raw;
        }
        return null;
    }

    private void deleteCurrentFile(String path) {
        if (currentUri != null && "shizuku".equals(currentUri.getScheme())) {
            deleteCurrentShizukuFile(path);
            return;
        }
        if (path.startsWith("content://")) {
            deleteCurrentContentUri(Uri.parse(path));
            return;
        }
        if (looksLikeRestrictedPath(path)) {
            confirmDeleteRestrictedPath(path);
            return;
        }
        File file = new File(path);
        if (!file.exists() || file.isDirectory()) {
            Toast.makeText(this, "文件不可删除", Toast.LENGTH_SHORT).show();
            return;
        }
        releasePlayer();
        if (file.delete()) {
            removeFavoriteForPath(path, false, false);
            PlaybackItem fallback = removeFromPlaybackQueue(path);
            if (fallback != null) {
                openPlaybackItem(fallback, true);
            } else {
                pathInput.setText("");
                titleText.setText("Smooth Player");
                resetPlaybackDisplay();
            }
            Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteCurrentContentUri(Uri uri) {
        releasePlayer();
        try {
            int deleted = getContentResolver().delete(uri, null, null);
            if (deleted > 0) {
                removeFavoriteForKey(uri.toString());
                clearPlaybackQueue();
                currentUri = null;
                pathInput.setText("");
                titleText.setText("Smooth Player");
                resetPlaybackDisplay();
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "系统文件选择器未允许删除", Toast.LENGTH_SHORT).show();
            }
        } catch (SecurityException | IllegalArgumentException exception) {
            Log.w(TAG, "Content uri delete failed: " + uri, exception);
            Toast.makeText(this, "没有删除权限", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmDeleteRestrictedPath(String path) {
        new AlertDialog.Builder(this)
                .setTitle("受限路径删除")
                .setMessage("此路径需要通过 Shizuku 删除。")
                .setNegativeButton("取消", null)
                .setPositiveButton("Shizuku 删除", (dialog, which) -> deleteCurrentShizukuFile(path))
                .show();
    }

    private void deleteCurrentShizukuFile(String path) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("正在通过 Shizuku 删除文件");
        progressDialog.setIndeterminate(true);
        progressDialog.setCancelable(false);
        progressDialog.show();
        releasePlayer();
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                boolean deleted = false;
                try {
                    ensureShizukuServiceBound();
                    IRestrictedFileService service = restrictedFileService;
                    if (service == null) {
                        throw new IOException("Shizuku service is not connected");
                    }
                    deleted = service.deleteFile(path);
                } catch (IOException | RemoteException | InterruptedException exception) {
                    Log.w(TAG, "Current Shizuku delete failed: " + path, exception);
                    if (exception instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                }
                boolean success = deleted;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.dismiss();
                        if (success) {
                            previewCache.remove(path);
                            previewLoading.remove(path);
                            removeFavoriteForPath(path, true, false);
                            PlaybackItem fallback = removeFromPlaybackQueue(path);
                            currentUri = null;
                            if (fallback != null) {
                                openPlaybackItem(fallback, true);
                            } else {
                                pathInput.setText("");
                                titleText.setText("Smooth Player");
                                resetPlaybackDisplay();
                            }
                            Toast.makeText(MainActivity.this, "已删除", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, "删除失败", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });
    }

    private void deleteShizukuFile(String directoryPath, String[] rawEntries, boolean sortBySize,
            RestrictedEntry entry) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("正在删除文件");
        progressDialog.setIndeterminate(true);
        progressDialog.setCancelable(false);
        progressDialog.show();
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                boolean deleted = false;
                try {
                    ensureShizukuServiceBound();
                    IRestrictedFileService service = restrictedFileService;
                    if (service == null) {
                        throw new IOException("Shizuku service is not connected");
                    }
                    deleted = service.deleteFile(entry.path);
                } catch (IOException | RemoteException | InterruptedException exception) {
                    Log.w(TAG, "Shizuku delete failed: " + entry.path, exception);
                    if (exception instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                }
                boolean success = deleted;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.dismiss();
                        if (success) {
                            previewCache.remove(entry.path);
                            previewLoading.remove(entry.path);
                            removeFavoriteForPath(entry.path, true, false);
                            PlaybackItem fallback = removeFromPlaybackQueue(entry.path);
                            if (currentUri != null && "shizuku".equals(currentUri.getScheme())
                                    && entry.path.equals(currentUri.getSchemeSpecificPart())) {
                                releasePlayer();
                                currentUri = null;
                                if (fallback != null) {
                                    openPlaybackItem(fallback, true);
                                } else {
                                    pathInput.setText("");
                                    titleText.setText("Smooth Player");
                                    resetPlaybackDisplay();
                                }
                            }
                            Toast.makeText(MainActivity.this, "已删除", Toast.LENGTH_SHORT).show();
                            showShizukuDirectory(directoryPath);
                        } else {
                            Toast.makeText(MainActivity.this, "删除失败", Toast.LENGTH_SHORT).show();
                            showShizukuDirectoryDialog(directoryPath, rawEntries, sortBySize);
                        }
                    }
                });
            }
        });
    }

    private String shizukuTitle(String path) {
        if (shizukuSortMode == SORT_RANDOM) {
            return currentSortName() + " " + breadcrumbPath(path);
        }
        return currentSortName() + (shizukuSortAscending ? "正序 " : "倒序 ") + breadcrumbPath(path);
    }

    private String currentSortName() {
        String sortName;
        if (shizukuSortMode == SORT_SIZE) {
            sortName = "大小";
        } else if (shizukuSortMode == SORT_TIME) {
            sortName = "时间";
        } else if (shizukuSortMode == SORT_RANDOM) {
            sortName = "随机";
        } else {
            sortName = "名称";
        }
        return sortName;
    }

    private String breadcrumbPath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return "/sdcard";
        }
        return path.replace("/", " > ").replaceFirst("^ > ", "/");
    }

    private void sortRestrictedEntries(List<RestrictedEntry> entries, boolean sortBySize) {
        sortRestrictedEntries(entries, sortBySize ? 1 : 0, true);
    }

    private void sortRestrictedEntries(List<RestrictedEntry> entries, int sortMode) {
        sortRestrictedEntries(entries, sortMode, true);
    }

    private void sortRestrictedEntries(List<RestrictedEntry> entries, int sortMode, boolean ascending) {
        if (sortMode == SORT_RANDOM) {
            Collections.shuffle(entries, new Random(shizukuRandomSeed));
            return;
        }
        Collections.sort(entries, new Comparator<RestrictedEntry>() {
            @Override
            public int compare(RestrictedEntry left, RestrictedEntry right) {
                if (left.directory != right.directory) {
                    return left.directory ? -1 : 1;
                }
                int result;
                if (sortMode == SORT_SIZE && !left.directory) {
                    result = Long.compare(left.size, right.size);
                } else if (sortMode == SORT_TIME) {
                    result = Long.compare(left.modifiedTime, right.modifiedTime);
                } else {
                    result = left.name.toLowerCase(Locale.US).compareTo(right.name.toLowerCase(Locale.US));
                }
                if (result == 0) {
                    result = left.name.toLowerCase(Locale.US).compareTo(right.name.toLowerCase(Locale.US));
                }
                return ascending ? result : -result;
            }
        });
    }

    private void showVideoPreview(RestrictedEntry entry) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("正在生成预览");
        progressDialog.setIndeterminate(true);
        progressDialog.setCancelable(false);
        progressDialog.show();
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap bitmap = null;
                try {
                    bitmap = loadVideoFrame(entry.path, true, false);
                } catch (IOException | RemoteException | InterruptedException exception) {
                    Log.w(TAG, "Failed to load preview: " + entry.path, exception);
                }
                Bitmap preview = bitmap;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.dismiss();
                        showVideoPreviewDialog(entry, preview);
                    }
                });
            }
        });
    }

    private Bitmap loadVideoFrame(String path, boolean shizuku, boolean contentUri)
            throws IOException, RemoteException, InterruptedException {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        ParcelFileDescriptor descriptor = null;
        try {
            if (shizuku) {
                descriptor = openFileDescriptorWithShizuku(path);
                retriever.setDataSource(descriptor.getFileDescriptor());
            } else if (contentUri) {
                retriever.setDataSource(this, Uri.parse(path));
            } else {
                retriever.setDataSource(path);
            }
            return retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
        } finally {
            try {
                retriever.release();
            } catch (RuntimeException exception) {
                Log.w(TAG, "Failed to release retriever", exception);
            }
            if (descriptor != null) {
                descriptor.close();
            }
        }
    }

    private void showVideoPreviewDialog(RestrictedEntry entry, Bitmap bitmap) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), dp(8), dp(18), 0);
        if (bitmap != null) {
            ImageView preview = new ImageView(this);
            preview.setImageBitmap(bitmap);
            preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            layout.addView(preview, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(180)));
        }
        TextView info = makeText(13, Color.rgb(220, 226, 235), false);
        info.setText(entry.name + "\n" + formatFileSize(entry.size));
        info.setPadding(0, dp(10), 0, 0);
        layout.addView(info, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle("视频预览")
                .setView(layout)
                .setNegativeButton("取消", null)
                .setPositiveButton("播放", (dialog, which) -> openRestrictedPathWithShizuku(entry.path))
                .show();
    }

    private String parentPath(String path) {
        if (path == null || path.trim().isEmpty() || "/".equals(path)) {
            return "/sdcard";
        }
        File file = new File(path);
        String parent = file.getParent();
        if (parent == null || parent.trim().isEmpty()) {
            return "/sdcard";
        }
        return parent;
    }

    private ParcelFileDescriptor openFileDescriptorWithShizuku(String path)
            throws IOException, RemoteException, InterruptedException {
        ensureShizukuServiceBound();
        IRestrictedFileService service = restrictedFileService;
        if (service == null) {
            throw new IOException("Shizuku service is not connected");
        }
        ParcelFileDescriptor descriptor = service.openFile(path);
        if (descriptor == null) {
            throw new IOException("Shizuku returned null fd");
        }
        return descriptor;
    }

    private void ensureShizukuServiceBound() throws IOException, InterruptedException {
        if (restrictedFileService != null) {
            return;
        }
        if (!isShizukuAvailable()) {
            throw new IOException("Shizuku is not available");
        }
        Shizuku.bindUserService(shizukuServiceArgs, shizukuConnection);
        long deadline = System.currentTimeMillis() + 5000L;
        while (restrictedFileService == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(50L);
        }
        if (restrictedFileService == null) {
            throw new IOException("Timed out binding Shizuku service");
        }
    }

    private void unbindShizukuService() {
        try {
            Shizuku.unbindUserService(shizukuServiceArgs, shizukuConnection, true);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Failed to unbind Shizuku service", exception);
        }
        restrictedFileService = null;
    }

    private boolean isShizukuAvailable() {
        try {
            return Shizuku.pingBinder();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean isShizukuGranted() {
        try {
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void checkShizukuAccess() {
        if (!isShizukuAvailable()) {
            Toast.makeText(this, "Shizuku 未运行", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isShizukuGranted()) {
            Shizuku.requestPermission(REQUEST_SHIZUKU_PERMISSION);
            return;
        }
        Toast.makeText(this, "Shizuku 已授权", Toast.LENGTH_SHORT).show();
    }

    private String shizukuErrorMessage(Exception exception) {
        String message = exception == null ? "" : exception.getMessage();
        if (!isShizukuAvailable()) {
            return "Shizuku 未运行";
        }
        if (!isShizukuGranted()) {
            return "Shizuku 未授权";
        }
        if (message != null && message.toLowerCase(Locale.US).contains("timed out")) {
            return "Shizuku 连接超时";
        }
        return "Shizuku 打开失败";
    }

    private void mountRestrictedPathAndPlay(String path) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("正在通过 root 挂载文件");
        progressDialog.setIndeterminate(true);
        progressDialog.setCancelable(false);
        progressDialog.show();
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    File mountedFile = bindFileWithSu(path);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.dismiss();
                            pathInput.setText(path);
                            saveLastPath(path);
                            openVideo(Uri.fromFile(mountedFile), 0, true);
                        }
                    });
                } catch (IOException | InterruptedException exception) {
                    Log.w(TAG, "Root bind mount failed: " + path, exception);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.dismiss();
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("挂载失败")
                                    .setMessage("当前 root 环境可能没有把挂载传播到应用进程。可以尝试复制兜底，或使用支持 root 的文件管理器通过“打开方式”分享给本应用。")
                                    .setNegativeButton("取消", null)
                                    .setPositiveButton("复制兜底", (dialog, which) ->
                                            copyRestrictedPathAndPlay(path))
                                    .show();
                        }
                    });
                }
            }
        });
    }

    private File bindFileWithSu(String sourcePath) throws IOException, InterruptedException {
        File mountDir = rootMountDir();
        if (!mountDir.exists() && !mountDir.mkdirs()) {
            throw new IOException("Failed to create root mount dir: " + mountDir);
        }
        File target = new File(mountDir, System.currentTimeMillis() + "-" + safeFileName(sourcePath));
        if (!target.createNewFile()) {
            throw new IOException("Failed to create mount target: " + target);
        }
        String command = "mount --bind " + shellQuote(sourcePath) + " " + shellQuote(target.getAbsolutePath())
                + " && chmod 644 " + shellQuote(target.getAbsolutePath());
        runSuCommand(command);
        if (!target.exists() || target.length() == 0) {
            unmountWithSu(target);
            throw new IOException("Mounted file is empty or invisible to app: " + target);
        }
        return target;
    }

    private void copyRestrictedPathAndPlay(String path) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("正在通过 root 复制文件");
        progressDialog.setIndeterminate(true);
        progressDialog.setCancelable(false);
        progressDialog.show();
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    File cachedFile = copyFileWithSu(path);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.dismiss();
                            pathInput.setText(cachedFile.getAbsolutePath());
                            saveLastPath(path);
                            openVideo(Uri.fromFile(cachedFile), 0, true);
                        }
                    });
                } catch (IOException | InterruptedException exception) {
                    Log.w(TAG, "Root copy failed: " + path, exception);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.dismiss();
                            Toast.makeText(MainActivity.this, "root 复制失败，请确认已授权 su", Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });
    }

    private File copyFileWithSu(String sourcePath) throws IOException, InterruptedException {
        File cacheDir = rootCacheDir();
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            throw new IOException("Failed to create root cache dir: " + cacheDir);
        }
        File output = new File(cacheDir, System.currentTimeMillis() + "-" + safeFileName(sourcePath));
        Process process = new ProcessBuilder("su", "-c", "cat " + shellQuote(sourcePath))
                .redirectErrorStream(false)
                .start();
        try (InputStream inputStream = process.getInputStream();
                OutputStream outputStream = new FileOutputStream(output)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = inputStream.read(buffer)) >= 0) {
                outputStream.write(buffer, 0, count);
            }
        }
        int exitCode = process.waitFor();
        if (exitCode != 0 || output.length() == 0) {
            output.delete();
            throw new IOException("su cat failed, exitCode=" + exitCode);
        }
        return output;
    }

    private void runSuCommand(String command) throws IOException, InterruptedException {
        IOException lastError = null;
        String[] suModes = {"-mm", "-c"};
        for (String mode : suModes) {
            Process process = null;
            try {
                if ("-mm".equals(mode)) {
                    process = new ProcessBuilder("su", "-mm", "-c", command).start();
                } else {
                    process = new ProcessBuilder("su", "-c", command).start();
                }
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    return;
                }
                lastError = new IOException("su " + mode + " failed, exitCode=" + exitCode);
            } catch (IOException exception) {
                lastError = exception;
            } finally {
                if (process != null) {
                    process.destroy();
                }
            }
        }
        throw lastError == null ? new IOException("su failed") : lastError;
    }

    private boolean canRunSu() {
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", "id").start();
            return process.waitFor() == 0;
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private File rootCacheDir() {
        return new File(getExternalFilesDir(null), ROOT_CACHE_DIR);
    }

    private File rootMountDir() {
        return new File(getExternalFilesDir(null), ROOT_MOUNT_DIR);
    }

    private void deleteChildren(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                deleteChildren(file);
            }
            if (!file.delete()) {
                Log.w(TAG, "Failed to delete cache file: " + file);
            }
        }
    }

    private void unmountAndDeleteChildren(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            unmountWithSu(file);
            if (!file.delete()) {
                Log.w(TAG, "Failed to delete mount file: " + file);
            }
        }
    }

    private void unmountWithSu(File file) {
        try {
            runSuCommand("umount " + shellQuote(file.getAbsolutePath()));
        } catch (IOException | InterruptedException exception) {
            Log.w(TAG, "Failed to unmount root file: " + file, exception);
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private String safeFileName(String sourcePath) {
        String name = new File(sourcePath).getName();
        if (name.trim().isEmpty()) {
            return "restricted-video";
        }
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private boolean looksLikeRestrictedPath(String path) {
        String normalized = path == null ? "" : path.toLowerCase(Locale.US);
        return normalized.contains("/android/data/") || normalized.contains("/android/obb/");
    }

    private boolean isRestrictedFileUri(Uri uri) {
        return uri != null && "file".equals(uri.getScheme()) && looksLikeRestrictedPath(uri.getPath());
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private void togglePlay() {
        if (player == null || !prepared) {
            openFromPathInput();
            return;
        }
        if (player.isPlaying()) {
            player.pause();
            resumePlaying = false;
            revealControls();
        } else {
            player.start();
            applyPlaybackSpeed();
            scheduleControlsHide();
        }
        updatePlayButton();
    }

    private void restartCurrentVideo() {
        if (player == null || !prepared) {
            return;
        }
        player.seekTo(0);
        saveVideoPosition(currentUri, 0);
        updateProgress();
        showFeedback("从头播放");
        revealControls();
    }

    private void seekBy(int deltaMs) {
        if (player == null || !prepared) {
            return;
        }
        int duration = player.getDuration();
        int next = Math.max(0, Math.min(duration, player.getCurrentPosition() + deltaMs));
        player.seekTo(next);
        saveVideoPosition(currentUri, next);
        updateProgress();
        int seconds = Math.abs(deltaMs) / 1000;
        showFeedback((deltaMs > 0 ? "+" : "-") + seconds + "s  " + formatTime(next));
    }

    private void startLongPressSpeed() {
        if (player == null || !prepared || !player.isPlaying() || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        longPressTriggered = true;
        if (!longPressActive) {
            speedBeforeLongPress = playbackSpeed;
            longPressActive = true;
        }
        applyTemporaryPlaybackSpeed(3f);
        showFeedback("3×");
    }

    private void stopLongPressSpeed() {
        if (!longPressActive) {
            return;
        }
        longPressActive = false;
        applyTemporaryPlaybackSpeed(speedBeforeLongPress);
    }

    private void applyTemporaryPlaybackSpeed(float speed) {
        if (player == null || !prepared || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        boolean wasPlaying = player.isPlaying();
        try {
            PlaybackParams params = player.getPlaybackParams();
            params.setSpeed(speed);
            player.setPlaybackParams(params);
            if (!wasPlaying) {
                player.pause();
            }
        } catch (IllegalStateException | IllegalArgumentException exception) {
            Log.w(TAG, "Temporary speed rejected: " + speed, exception);
        }
    }

    private void applyPlaybackSpeed() {
        if (player == null || !prepared || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        boolean wasPlaying = player.isPlaying();
        try {
            PlaybackParams params = player.getPlaybackParams();
            params.setSpeed(playbackSpeed);
            player.setPlaybackParams(params);
            if (!wasPlaying) {
                player.pause();
            }
        } catch (IllegalStateException | IllegalArgumentException exception) {
            Log.w(TAG, "Playback speed rejected: " + playbackSpeed, exception);
        }
    }

    private void applyVideoTransform() {
        if (textureView == null || videoWidth <= 0 || videoHeight <= 0) {
            return;
        }
        int viewWidth = textureView.getWidth();
        int viewHeight = textureView.getHeight();
        if (viewWidth <= 0 || viewHeight <= 0) {
            return;
        }

        float sx = viewWidth / (float) videoWidth;
        float sy = viewHeight / (float) videoHeight;
        float scaledWidth;
        float scaledHeight;
        if (videoScaleMode == SCALE_MODE_STRETCH) {
            scaledWidth = viewWidth;
            scaledHeight = viewHeight;
        } else if (videoScaleMode == SCALE_MODE_ORIGINAL) {
            scaledWidth = Math.min(videoWidth, viewWidth);
            scaledHeight = Math.min(videoHeight, viewHeight);
        } else {
            float scale = videoScaleMode == SCALE_MODE_FILL ? Math.max(sx, sy) : Math.min(sx, sy);
            scaledWidth = videoWidth * scale;
            scaledHeight = videoHeight * scale;
        }
        float dx = (viewWidth - scaledWidth) / 2f;
        float dy = (viewHeight - scaledHeight) / 2f;

        Matrix matrix = new Matrix();
        matrix.setScale(scaledWidth / viewWidth, scaledHeight / viewHeight);
        matrix.postTranslate(dx, dy);
        textureView.setTransform(matrix);
    }

    private void updateProgress() {
        if (player == null || !prepared || userSeeking) {
            return;
        }
        int duration = Math.max(player.getDuration(), 0);
        int current = Math.max(player.getCurrentPosition(), 0);
        if (duration > 0) {
            seekBar.setProgress((int) (current * 1000L / duration));
        } else {
            seekBar.setProgress(0);
        }
        timeText.setText(formatTime(current) + " / " + formatTime(duration));
        updatePlayButton();
    }

    private void resetPlaybackDisplay() {
        prepared = false;
        if (seekBar != null) {
            seekBar.setProgress(0);
        }
        if (timeText != null) {
            timeText.setText("00:00 / 00:00");
        }
        updateFavoriteButton();
        updatePlayButton();
    }

    private void updatePlayButton() {
        if (playButton == null || centerPlayButton == null) {
            return;
        }
        boolean playing = player != null && prepared && player.isPlaying();
        playButton.setText(playing ? "⏸" : "▶");
        centerPlayButton.setImageResource(playing ? R.drawable.ic_center_pause : R.drawable.ic_center_play);
        centerPlayButton.setContentDescription(playing ? "暂停" : "播放");
        if (browserVisible || controlsLocked) {
            centerPlayButton.setVisibility(View.GONE);
            return;
        }
        centerPlayButton.setVisibility((controlsVisible || !playing) ? View.VISIBLE : View.GONE);
    }

    private void revealControls() {
        setControlsVisible(true);
        scheduleControlsHide();
    }

    private void scheduleControlsHide() {
        handler.removeCallbacks(hideControlsRunnable);
        if (!controlsLocked && prepared && player != null && player.isPlaying()) {
            handler.postDelayed(hideControlsRunnable, CONTROLS_HIDE_DELAY_MS);
        }
    }

    private void setControlsVisible(boolean visible) {
        controlsVisible = visible && !controlsLocked;
        int visibility = controlsVisible ? View.VISIBLE : View.GONE;
        topBar.setVisibility(visibility);
        bottomBar.setVisibility(visibility);
        dimLayer.setVisibility(controlsVisible ? View.VISIBLE : View.GONE);
        updateLockState();
        updatePlayButton();
        if (controlsVisible) {
            scheduleControlsHide();
            hideSystemBars();
        } else {
            hideSystemBars();
        }
    }

    private void updateLockState() {
        if (lockButton != null) {
            lockButton.setText(controlsLocked ? "解" : "锁");
        }
        if (unlockButton != null) {
            unlockButton.setVisibility(controlsLocked ? View.VISIBLE : View.GONE);
        }
        if (controlsLocked) {
            handler.removeCallbacks(hideControlsRunnable);
            controlsVisible = false;
            if (topBar != null) {
                topBar.setVisibility(View.GONE);
            }
            if (bottomBar != null) {
                bottomBar.setVisibility(View.GONE);
            }
            if (dimLayer != null) {
                dimLayer.setVisibility(View.GONE);
            }
        }
        updatePlayButton();
    }

    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private void showSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    private void showFeedback(String text) {
        feedbackText.setText(text);
        feedbackText.setVisibility(View.VISIBLE);
        feedbackText.animate().cancel();
        feedbackText.setAlpha(1f);
        feedbackText.animate()
                .alpha(0f)
                .setStartDelay(650L)
                .setDuration(240L)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        feedbackText.setVisibility(View.GONE);
                        feedbackText.setAlpha(1f);
                    }
                })
                .start();
    }

    private void checkForUpdates(boolean manual) {
        Toast.makeText(this, "正在检查更新", Toast.LENGTH_SHORT).show();
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject info = new JSONObject(readUrl(UPDATE_INFO_URL));
                    int latestCode = info.optInt("versionCode", 0);
                    String latestName = info.optString("versionName", "");
                    String apkUrl = info.optString("apkUrl", "");
                    String notes = info.optString("notes", "");
                    long apkSize = info.optLong("apkSize", 0L);
                    String sha256 = info.optString("sha256", "");
                    int currentCode = packageInfo().versionCode;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (latestCode > currentCode && !apkUrl.isEmpty()) {
                                showUpdateDialog(latestName, notes, apkUrl, apkSize, sha256);
                            } else if (manual) {
                                Toast.makeText(MainActivity.this, "当前已是最新版本", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } catch (Exception exception) {
                    Log.w(TAG, "Update check failed", exception);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (manual) {
                                Toast.makeText(MainActivity.this, "更新检查失败", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }
            }
        });
    }

    private void showUpdateDialog(String versionName, String notes, String apkUrl, long apkSize, String sha256) {
        String message = "发现新版本 " + versionName;
        if (apkSize > 0L) {
            message += "\n大小：" + formatFileSize(apkSize);
        }
        if (notes != null && !notes.trim().isEmpty()) {
            message += "\n\n" + notes.trim();
        }
        new AlertDialog.Builder(this)
                .setTitle("Smooth Player 更新")
                .setMessage(message)
                .setNegativeButton("稍后", null)
                .setPositiveButton("下载并安装", (dialog, which) -> downloadAndInstallUpdate(apkUrl, sha256))
                .show();
    }

    private void downloadAndInstallUpdate(String apkUrl, String sha256) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("正在下载更新包");
        progressDialog.setIndeterminate(true);
        progressDialog.setCancelable(false);
        progressDialog.show();
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    File apkFile = new File(getExternalFilesDir(null), ApkProvider.APK_NAME);
                    downloadFile(apkUrl, apkFile);
                    if (sha256 != null && !sha256.trim().isEmpty()
                            && !sha256.equalsIgnoreCase(sha256(apkFile))) {
                        throw new IOException("APK 校验失败");
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.dismiss();
                            installDownloadedApk();
                        }
                    });
                } catch (IOException exception) {
                    Log.w(TAG, "Update download failed", exception);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.dismiss();
                            Toast.makeText(MainActivity.this, "更新包下载失败", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }

    private void installDownloadedApk() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getPackageManager().canRequestPackageInstalls()) {
            Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
            Toast.makeText(this, "请允许 Smooth Player 安装更新", Toast.LENGTH_LONG).show();
            startActivity(settingsIntent);
            return;
        }
        Uri apkUri = Uri.parse("content://" + getPackageName() + ".apkprovider/" + ApkProvider.APK_NAME);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private String readUrl(String urlString) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        try (InputStream inputStream = connection.getInputStream()) {
            return readText(inputStream);
        } finally {
            connection.disconnect();
        }
    }

    private String readText(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[4096];
        StringBuilder builder = new StringBuilder();
        int count;
        while ((count = inputStream.read(buffer)) >= 0) {
            builder.append(new String(buffer, 0, count, "UTF-8"));
        }
        return builder.toString();
    }

    private void downloadFile(String urlString, File output) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(20000);
        try (InputStream inputStream = connection.getInputStream();
                FileOutputStream outputStream = new FileOutputStream(output)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = inputStream.read(buffer)) >= 0) {
                outputStream.write(buffer, 0, count);
            }
        } finally {
            connection.disconnect();
        }
    }

    private String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream inputStream = new java.io.FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = inputStream.read(buffer)) >= 0) {
                    digest.update(buffer, 0, count);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format(Locale.US, "%02x", value & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 is unavailable", exception);
        }
    }

    private void releasePlayer() {
        if (player != null) {
            try {
                player.reset();
            } catch (IllegalStateException ignored) {
            }
            player.release();
            player = null;
        }
        closeActiveShizukuFd();
        prepared = false;
    }

    private void closeActiveShizukuFd() {
        if (activeShizukuFd == null) {
            return;
        }
        try {
            activeShizukuFd.close();
        } catch (IOException exception) {
            Log.w(TAG, "Failed to close Shizuku fd", exception);
        }
        activeShizukuFd = null;
    }

    private void savePlaybackState() {
        saveCurrentVideoPosition();
        getPreferences().edit()
                .putString(KEY_LAST_PATH, currentUri == null ? pathInput.getText().toString() : currentUri.toString())
                .putInt(KEY_LAST_POSITION, currentPosition())
                .putFloat(KEY_LAST_SPEED, playbackSpeed)
                .putBoolean(KEY_LAST_FILL, fillMode)
                .apply();
    }

    private void savePlaybackPosition(int positionMs) {
        getPreferences().edit().putInt(KEY_LAST_POSITION, positionMs).apply();
    }

    private void saveCurrentVideoPosition() {
        if (currentUri != null && player != null && prepared) {
            saveVideoPosition(currentUri, currentPosition());
        }
    }

    private void saveVideoPosition(Uri uri, int positionMs) {
        if (uri == null) {
            return;
        }
        int normalized = Math.max(positionMs, 0);
        if (player != null && prepared) {
            int duration = Math.max(player.getDuration(), 0);
            if (normalized < 10_000 || (duration > 0 && duration - normalized < 15_000)) {
                normalized = 0;
            }
        }
        getPreferences().edit()
                .putInt(videoPositionKey(uri), normalized)
                .apply();
    }

    private int savedVideoPosition(Uri uri) {
        if (uri == null) {
            return 0;
        }
        return getPreferences().getInt(videoPositionKey(uri), 0);
    }

    private String videoPositionKey(Uri uri) {
        return "video_position_" + Integer.toHexString(uri.toString().hashCode());
    }

    private int currentPosition() {
        if (player != null && prepared) {
            try {
                return player.getCurrentPosition();
            } catch (IllegalStateException ignored) {
            }
        }
        return getPreferences().getInt(KEY_LAST_POSITION, 0);
    }

    private PackageInfo packageInfo() throws PackageManager.NameNotFoundException {
        return getPackageManager().getPackageInfo(getPackageName(), 0);
    }

    private String errorMessage(int what, int extra) {
        if (what == MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK) {
            return "视频格式不适合渐进播放";
        }
        if (extra == MediaPlayer.MEDIA_ERROR_IO) {
            return "读取视频失败";
        }
        if (extra == MediaPlayer.MEDIA_ERROR_MALFORMED) {
            return "视频文件可能已损坏";
        }
        if (extra == MediaPlayer.MEDIA_ERROR_UNSUPPORTED) {
            return "设备不支持此格式";
        }
        return "无法播放此视频";
    }

    private void setSpeedSpinnerSelection(float speed) {
        if (speedSpinner == null) {
            return;
        }
        int selected = 2;
        for (int i = 0; i < SPEED_VALUES.length; i++) {
            if (Math.abs(SPEED_VALUES[i] - speed) < 0.001f) {
                selected = i;
                break;
            }
        }
        speedSpinner.setSelection(selected);
    }

    private TextView makeText(int sp, int color, boolean bold) {
        TextView textView = new TextView(this);
        textView.setTextSize(sp);
        textView.setTextColor(color);
        textView.setIncludeFontPadding(true);
        if (bold) {
            textView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        }
        return textView;
    }

    private ImageButton makeCenterButton() {
        ImageButton button = new ImageButton(this);
        button.setImageResource(R.drawable.ic_center_play);
        button.setColorFilter(Color.WHITE);
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setPadding(dp(32), dp(32), dp(32), dp(32));
        button.setBackgroundResource(R.drawable.bg_center_button);
        button.setContentDescription("播放");
        return button;
    }

    private Button makeButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(13);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackgroundResource(R.drawable.bg_button);
        return button;
    }

    private Button makeCompactButton(String text) {
        Button button = makeButton(text);
        button.setTextSize(12);
        button.setPadding(dp(4), 0, dp(4), 0);
        return button;
    }

    private SharedPreferences getPreferences() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private void saveLastPath(String path) {
        getPreferences().edit().putString(KEY_LAST_PATH, path).apply();
    }

    private String displayTitle(Uri uri) {
        String value = uri == null ? "" : uri.getPath();
        if ((value == null || value.trim().isEmpty()) && uri != null) {
            value = uri.getSchemeSpecificPart();
        }
        if (value == null || value.trim().isEmpty()) {
            value = uri.toString();
        }
        value = Uri.decode(value);
        int lastSlash = value.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < value.length() - 1) {
            value = value.substring(lastSlash + 1);
        }
        return value == null || value.trim().isEmpty() ? "Smooth Player" : value;
    }

    private String formatTime(int millis) {
        int totalSeconds = Math.max(millis / 1000, 0);
        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60) % 60;
        int hours = totalSeconds / 3600;
        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double value = bytes / 1024.0;
        String[] units = {"KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        while (value >= 1024.0 && unitIndex < units.length - 1) {
            value /= 1024.0;
            unitIndex++;
        }
        return String.format(Locale.US, "%.1f %s", value, units[unitIndex]);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private final class RestrictedEntryAdapter extends BaseAdapter {
        private final List<RestrictedEntry> entries;
        private final int generation;

        RestrictedEntryAdapter(List<RestrictedEntry> entries, int generation) {
            this.entries = entries;
            this.generation = generation;
        }

        @Override
        public int getCount() {
            return entries.size();
        }

        @Override
        public RestrictedEntry getItem(int position) {
            return entries.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, android.view.ViewGroup parent) {
            RowHolder holder;
            if (convertView == null) {
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(12), dp(8), dp(12), dp(8));

                ImageView preview = new ImageView(MainActivity.this);
                preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
                LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(dp(96), dp(54));
                row.addView(preview, previewParams);

                LinearLayout textColumn = new LinearLayout(MainActivity.this);
                textColumn.setOrientation(LinearLayout.VERTICAL);
                textColumn.setPadding(dp(12), 0, 0, 0);
                TextView name = makeText(14, Color.WHITE, true);
                name.setSingleLine(true);
                TextView meta = makeText(12, Color.rgb(174, 183, 194), false);
                textColumn.addView(name, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
                textColumn.addView(meta, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
                row.addView(textColumn, new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                holder = new RowHolder(preview, name, meta);
                row.setTag(holder);
                convertView = row;
            } else {
                holder = (RowHolder) convertView.getTag();
            }

            RestrictedEntry entry = getItem(position);
            holder.name.setText(entry.name);
            holder.meta.setText(entry.directory ? "目录" : entry.readableMeta());
            if (entry.directory) {
                holder.preview.setImageBitmap(null);
                holder.preview.setBackgroundColor(Color.rgb(24, 35, 46));
                holder.preview.setColorFilter(null);
                holder.preview.setImageResource(android.R.drawable.ic_menu_upload);
            } else {
                holder.preview.setBackgroundColor(Color.rgb(18, 24, 31));
                Bitmap cached = previewCache.get(entry.path);
                if (cached != null) {
                    holder.preview.setImageBitmap(cached);
                } else if (entry.looksLikeVideo()) {
                    holder.preview.setImageBitmap(null);
                    holder.preview.setImageResource(android.R.drawable.ic_media_play);
                    loadListPreview(entry, this, generation);
                } else {
                    holder.preview.setImageBitmap(null);
                    holder.preview.setImageResource(android.R.drawable.ic_menu_save);
                }
            }
            return convertView;
        }
    }

    private final class FavoriteItemAdapter extends BaseAdapter {
        private final List<FavoriteItem> entries;
        private final int generation;

        FavoriteItemAdapter(List<FavoriteItem> entries, int generation) {
            this.entries = entries;
            this.generation = generation;
        }

        @Override
        public int getCount() {
            return entries.size();
        }

        @Override
        public FavoriteItem getItem(int position) {
            return entries.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, android.view.ViewGroup parent) {
            RowHolder holder;
            if (convertView == null) {
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(12), dp(8), dp(12), dp(8));

                ImageView preview = new ImageView(MainActivity.this);
                preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
                row.addView(preview, new LinearLayout.LayoutParams(dp(96), dp(54)));

                LinearLayout textColumn = new LinearLayout(MainActivity.this);
                textColumn.setOrientation(LinearLayout.VERTICAL);
                textColumn.setPadding(dp(12), 0, 0, 0);
                TextView name = makeText(14, Color.WHITE, true);
                name.setSingleLine(true);
                TextView meta = makeText(12, Color.rgb(174, 183, 194), false);
                textColumn.addView(name, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
                textColumn.addView(meta, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
                row.addView(textColumn, new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                holder = new RowHolder(preview, name, meta);
                row.setTag(holder);
                convertView = row;
            } else {
                holder = (RowHolder) convertView.getTag();
            }

            FavoriteItem item = getItem(position);
            holder.name.setText(item.title);
            holder.meta.setText(item.sourceLabel());
            holder.preview.setBackgroundColor(Color.rgb(18, 24, 31));
            Bitmap cached = previewCache.get(item.previewKey());
            if (cached != null) {
                holder.preview.setImageBitmap(cached);
            } else {
                holder.preview.setImageBitmap(null);
                holder.preview.setImageResource(android.R.drawable.ic_media_play);
                loadFavoritePreview(item, this, generation);
            }
            return convertView;
        }
    }

    private static final class RowHolder {
        final ImageView preview;
        final TextView name;
        final TextView meta;

        RowHolder(ImageView preview, TextView name, TextView meta) {
            this.preview = preview;
            this.name = name;
            this.meta = meta;
        }
    }

    private static final class PlaybackItem {
        final String path;
        final boolean shizuku;

        PlaybackItem(String path, boolean shizuku) {
            this.path = path;
            this.shizuku = shizuku;
        }
    }

    private static final class FavoriteItem {
        final String key;
        final String title;
        final String path;
        final boolean shizuku;
        final boolean contentUri;

        FavoriteItem(String key, String title, String path, boolean shizuku, boolean contentUri) {
            this.key = key;
            this.title = title;
            this.path = path;
            this.shizuku = shizuku;
            this.contentUri = contentUri;
        }

        String previewKey() {
            return "favorite:" + key;
        }

        String sourceLabel() {
            if (shizuku) {
                return "Shizuku";
            }
            if (contentUri) {
                return "系统文件";
            }
            return "本地文件";
        }
    }

    private void loadListPreview(RestrictedEntry entry, BaseAdapter adapter, int generation) {
        if (previewCache.containsKey(entry.path) || previewLoading.contains(entry.path)) {
            return;
        }
        previewLoading.add(entry.path);
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (generation != previewGeneration) {
                        return;
                    }
                    Bitmap bitmap = loadVideoFrame(entry.path, true, false);
                    if (bitmap != null && generation == previewGeneration) {
                        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, dp(96), dp(54), true);
                        previewCache.put(entry.path, scaled);
                    }
                } catch (IOException | RemoteException | InterruptedException exception) {
                    Log.w(TAG, "Failed to load list preview: " + entry.path, exception);
                    if (exception instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                } finally {
                    previewLoading.remove(entry.path);
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (generation == previewGeneration) {
                            adapter.notifyDataSetChanged();
                        }
                    }
                });
            }
        });
    }

    private void loadFavoritePreview(FavoriteItem item, BaseAdapter adapter, int generation) {
        String key = item.previewKey();
        if (previewCache.containsKey(key) || previewLoading.contains(key)) {
            return;
        }
        previewLoading.add(key);
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (generation != previewGeneration) {
                        return;
                    }
                    Bitmap bitmap = loadVideoFrame(item.path, item.shizuku, item.contentUri);
                    if (bitmap != null && generation == previewGeneration) {
                        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, dp(96), dp(54), true);
                        previewCache.put(key, scaled);
                    }
                } catch (IOException | RemoteException | InterruptedException exception) {
                    Log.w(TAG, "Failed to load favorite preview: " + item.path, exception);
                    if (exception instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                } finally {
                    previewLoading.remove(key);
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (generation == previewGeneration) {
                            adapter.notifyDataSetChanged();
                        }
                    }
                });
            }
        });
    }

    private static final class RestrictedEntry {
        final boolean directory;
        final long size;
        final long modifiedTime;
        final String name;
        final String path;

        RestrictedEntry(boolean directory, long size, long modifiedTime, String name, String path) {
            this.directory = directory;
            this.size = size;
            this.modifiedTime = modifiedTime;
            this.name = name;
            this.path = path;
        }

        String label() {
            if (directory) {
                return "[目录] " + name;
            }
            return "[文件] " + name + "  " + readableSize();
        }

        String readableSize() {
            if (size < 1024L) {
                return size + " B";
            }
            double value = size / 1024.0;
            String[] units = {"KB", "MB", "GB", "TB"};
            int unitIndex = 0;
            while (value >= 1024.0 && unitIndex < units.length - 1) {
                value /= 1024.0;
                unitIndex++;
            }
            return String.format(Locale.US, "%.1f %s", value, units[unitIndex]);
        }

        String readableMeta() {
            String meta = readableSize();
            if (modifiedTime > 0L) {
                meta += "  " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                        .format(new java.util.Date(modifiedTime));
            }
            return meta;
        }

        boolean looksLikeVideo() {
            String lower = name.toLowerCase(Locale.US);
            return lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".webm")
                    || lower.endsWith(".avi") || lower.endsWith(".mov") || lower.endsWith(".m4v")
                    || lower.endsWith(".ts") || lower.endsWith(".3gp") || lower.endsWith(".flv");
        }

        static RestrictedEntry parse(String value) {
            if (value == null) {
                return null;
            }
            String[] parts = value.split("\\|", 5);
            if (parts.length != 5) {
                String[] legacyParts = value.split("\\|", 4);
                if (legacyParts.length != 4) {
                    return null;
                }
                long legacySize = parseLong(legacyParts[1]);
                return new RestrictedEntry("D".equals(legacyParts[0]), legacySize, 0L,
                        legacyParts[2], legacyParts[3]);
            }
            long size = parseLong(parts[1]);
            long modifiedTime = parseLong(parts[2]);
            return new RestrictedEntry("D".equals(parts[0]), size, modifiedTime, parts[3], parts[4]);
        }

        private static long parseLong(String value) {
            if (value == null) {
                return 0L;
            }
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
    }
}
