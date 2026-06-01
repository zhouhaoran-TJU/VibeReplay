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
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
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
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import rikka.shizuku.Shizuku;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements TextureView.SurfaceTextureListener {
    private static final String TAG = "SmoothPlayer";
    private static final int REQUEST_PICK_VIDEO = 1001;
    private static final int REQUEST_READ_STORAGE = 1002;
    private static final long CONTROLS_HIDE_DELAY_MS = 3200L;
    private static final int SEEK_STEP_MS = 10_000;
    private static final String PREFS = "smooth_player";
    private static final String KEY_LAST_PATH = "last_path";
    private static final String KEY_LAST_POSITION = "last_position";
    private static final String KEY_LAST_SPEED = "last_speed";
    private static final String KEY_LAST_FILL = "last_fill";
    private static final String KEY_PRIVACY_ACCEPTED = "privacy_accepted";
    private static final String ROOT_CACHE_DIR = "root-cache";
    private static final String ROOT_MOUNT_DIR = "root-mount";
    private static final int REQUEST_SHIZUKU_PERMISSION = 1003;
    private static final long TAP_DELAY_MS = 260L;
    private static final long LONG_PRESS_DELAY_MS = 420L;
    private static final String UPDATE_INFO_URL =
            "https://raw.githubusercontent.com/zhouhaoran-TJU/VibeReplay/main/dist/version.json";
    private static final String[] SPEED_LABELS = {"0.5x", "0.75x", "1x", "1.25x", "1.5x", "2x"};
    private static final float[] SPEED_VALUES = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};
    private static final String[] PICKER_MIME_TYPES = {
            "video/*",
            "audio/*",
            "application/octet-stream",
            "application/*",
            "text/*"
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
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
                    .version(1);
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
    private Button centerPlayButton;
    private Button fitButton;
    private Button lockButton;
    private SeekBar seekBar;
    private TextView timeText;
    private TextView titleText;
    private TextView feedbackText;
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
    private boolean resumePlaying;
    private int videoWidth;
    private int videoHeight;
    private float playbackSpeed = 1f;
    private float speedBeforeLongPress = 1f;
    private long lastTapTime;
    private float lastTapX;
    private boolean longPressActive;
    private boolean longPressTriggered;
    private IRestrictedFileService restrictedFileService;
    private ParcelFileDescriptor activeShizukuFd;
    private String pendingShizukuPath;

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
            if (!lastPath.isEmpty()) {
                setPendingVideo(Uri.parse(lastPath), getPreferences().getInt(KEY_LAST_POSITION, 0), false);
            }
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
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener);
        ioExecutor.shutdownNow();
        super.onDestroy();
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

        centerPlayButton = makeCenterButton("▶");
        centerPlayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                togglePlay();
            }
        });
        FrameLayout.LayoutParams centerParams = new FrameLayout.LayoutParams(dp(104), dp(104), Gravity.CENTER);
        root.addView(centerPlayButton, centerParams);

        buildTopBar();
        buildBottomBar();
        bindGestures();
        setSpeedSpinnerSelection(playbackSpeed);
        setControlsVisible(true);
    }

    private void buildTopBar() {
        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.VERTICAL);
        topBar.setPadding(dp(14), dp(10), dp(14), dp(12));
        topBar.setBackgroundResource(R.drawable.bg_panel);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleText = makeText(15, Color.WHITE, true);
        titleText.setText("Smooth Player");
        titleText.setSingleLine(true);
        titleRow.addView(titleText, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button updateButton = makeButton("更新");
        updateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkForUpdates(true);
            }
        });
        LinearLayout.LayoutParams updateParams = new LinearLayout.LayoutParams(dp(64), dp(36));
        updateParams.leftMargin = dp(8);
        titleRow.addView(updateButton, updateParams);
        Button filesButton = makeButton("权限");
        filesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showAccessOptions();
            }
        });
        LinearLayout.LayoutParams filesParams = new LinearLayout.LayoutParams(dp(64), dp(36));
        filesParams.leftMargin = dp(8);
        titleRow.addView(filesButton, filesParams);
        topBar.addView(titleRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout pathRow = new LinearLayout(this);
        pathRow.setGravity(Gravity.CENTER_VERTICAL);
        pathRow.setOrientation(LinearLayout.HORIZONTAL);
        pathRow.setPadding(0, dp(10), 0, 0);

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
        pathRow.addView(pathInput, new LinearLayout.LayoutParams(0, dp(42), 1f));

        Button openButton = makeButton("打开");
        openButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openFromPathInput();
            }
        });
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(dp(72), dp(42));
        openParams.leftMargin = dp(8);
        pathRow.addView(openButton, openParams);

        Button systemPickButton = makeButton("系统");
        systemPickButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pickVideo();
            }
        });
        LinearLayout.LayoutParams pickParams = new LinearLayout.LayoutParams(dp(64), dp(42));
        pickParams.leftMargin = dp(8);
        pathRow.addView(systemPickButton, pickParams);

        Button shizukuPickButton = makeButton("Shizuku");
        shizukuPickButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showShizukuPickOptions();
            }
        });
        LinearLayout.LayoutParams shizukuParams = new LinearLayout.LayoutParams(dp(82), dp(42));
        shizukuParams.leftMargin = dp(8);
        pathRow.addView(shizukuPickButton, shizukuParams);

        topBar.addView(pathRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        topParams.setMargins(dp(12), dp(12), dp(12), 0);
        root.addView(topBar, topParams);
    }

    private void buildBottomBar() {
        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.VERTICAL);
        bottomBar.setPadding(dp(14), dp(10), dp(14), dp(10));
        bottomBar.setBackgroundResource(R.drawable.bg_panel);

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
                }
                userSeeking = false;
                scheduleControlsHide();
            }
        });
        bottomBar.addView(seekBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(46)));

        LinearLayout primaryRow = new LinearLayout(this);
        primaryRow.setGravity(Gravity.CENTER_VERTICAL);
        primaryRow.setOrientation(LinearLayout.HORIZONTAL);

        playButton = makeButton("▶");
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                togglePlay();
            }
        });
        primaryRow.addView(playButton, new LinearLayout.LayoutParams(dp(54), dp(42)));

        timeText = makeText(13, Color.rgb(220, 226, 235), false);
        timeText.setText("00:00 / 00:00");
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        timeParams.leftMargin = dp(12);
        primaryRow.addView(timeText, timeParams);
        bottomBar.addView(primaryRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout optionsRow = new LinearLayout(this);
        optionsRow.setGravity(Gravity.CENTER_VERTICAL);
        optionsRow.setOrientation(LinearLayout.HORIZONTAL);
        optionsRow.setPadding(0, dp(6), 0, 0);

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
        optionsRow.addView(speedSpinner, new LinearLayout.LayoutParams(0, dp(42), 1f));

        fitButton = makeButton(fillMode ? "填充" : "适配");
        fitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                fillMode = !fillMode;
                fitButton.setText(fillMode ? "填充" : "适配");
                getPreferences().edit().putBoolean(KEY_LAST_FILL, fillMode).apply();
                applyVideoTransform();
                revealControls();
            }
        });
        LinearLayout.LayoutParams fitParams = new LinearLayout.LayoutParams(dp(72), dp(42));
        fitParams.leftMargin = dp(8);
        optionsRow.addView(fitButton, fitParams);

        lockButton = makeButton("锁定");
        lockButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                controlsLocked = !controlsLocked;
                lockButton.setText(controlsLocked ? "解锁" : "锁定");
                revealControls();
            }
        });
        LinearLayout.LayoutParams lockParams = new LinearLayout.LayoutParams(dp(72), dp(42));
        lockParams.leftMargin = dp(8);
        optionsRow.addView(lockButton, lockParams);
        bottomBar.addView(optionsRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        bottomParams.setMargins(dp(12), 0, dp(12), dp(12));
        root.addView(bottomBar, bottomParams);
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
                        longPressTriggered = false;
                        handler.postDelayed(longPressRunnable, LONG_PRESS_DELAY_MS);
                        return true;
                    case MotionEvent.ACTION_MOVE:
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
                        long now = System.currentTimeMillis();
                        boolean doubleTap = now - lastTapTime < TAP_DELAY_MS
                                && Math.abs(event.getX() - lastTapX) < dp(80);
                        lastTapTime = now;
                        lastTapX = event.getX();
                        if (doubleTap) {
                            handler.removeCallbacks(singleTapRunnable);
                            if (event.getX() < root.getWidth() / 2f) {
                                seekBy(-SEEK_STEP_MS);
                            } else {
                                seekBy(SEEK_STEP_MS);
                            }
                        } else {
                            handler.postDelayed(singleTapRunnable, TAP_DELAY_MS);
                        }
                        return true;
                    default:
                        return true;
                }
            }
        });
    }

    private boolean isControlView(MotionEvent event) {
        return isPointInside(topBar, event.getX(), event.getY())
                || isPointInside(bottomBar, event.getX(), event.getY())
                || isPointInside(centerPlayButton, event.getX(), event.getY());
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
        }
        saveLastPath(raw);
        openVideo(uri, 0, true);
    }

    private void showShizukuPickOptions() {
        new AlertDialog.Builder(this)
                .setTitle("Shizuku 浏览")
                .setItems(new CharSequence[]{"Android/data", "Android/obb", "存储根目录"},
                        (dialog, which) -> {
                            if (which == 0) {
                                browseRestrictedDirectory("/sdcard/Android/data");
                            } else if (which == 1) {
                                browseRestrictedDirectory("/sdcard/Android/obb");
                            } else {
                                browseRestrictedDirectory("/sdcard");
                            }
                        })
                .show();
    }

    private void pickVideo() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, PICKER_MIME_TYPES);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
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

        loading.setVisibility(View.VISIBLE);
        releasePlayer();
        currentUri = uri;

        player = new MediaPlayer();
        prepared = false;
        videoWidth = 0;
        videoHeight = 0;
        try {
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            player.setSurface(surface);
            if ("shizuku".equals(uri.getScheme())) {
                String path = uri.getSchemeSpecificPart();
                ParcelFileDescriptor descriptor = openFileDescriptorWithShizuku(path);
                closeActiveShizukuFd();
                activeShizukuFd = descriptor;
                player.setDataSource(descriptor.getFileDescriptor());
            } else {
                player.setDataSource(this, uri);
            }
            player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mediaPlayer) {
                    prepared = true;
                    loading.setVisibility(View.GONE);
                    videoWidth = mediaPlayer.getVideoWidth();
                    videoHeight = mediaPlayer.getVideoHeight();
                    titleText.setText(displayTitle(uri));
                    if (seekMs > 0) {
                        mediaPlayer.seekTo(Math.min(seekMs, mediaPlayer.getDuration()));
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
                    savePlaybackPosition(0);
                    updatePlayButton();
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
            player.prepareAsync();
        } catch (IOException | IllegalArgumentException | SecurityException | RemoteException
                | InterruptedException error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
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
                            showShizukuDirectoryDialog(path, rawEntries);
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

    private void showShizukuDirectoryDialog(String path, String[] rawEntries) {
        List<RestrictedEntry> entries = new ArrayList<>();
        if (!"/sdcard".equals(path)) {
            entries.add(new RestrictedEntry(true, "..", parentPath(path)));
        }
        for (String rawEntry : rawEntries) {
            RestrictedEntry entry = RestrictedEntry.parse(rawEntry);
            if (entry != null) {
                entries.add(entry);
            }
        }
        CharSequence[] labels = new CharSequence[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            RestrictedEntry entry = entries.get(i);
            labels[i] = (entry.directory ? "[目录] " : "[文件] ") + entry.name;
        }
        new AlertDialog.Builder(this)
                .setTitle(path)
                .setItems(labels, (dialog, which) -> {
                    RestrictedEntry entry = entries.get(which);
                    if (entry.directory) {
                        showShizukuDirectory(entry.path);
                    } else {
                        pathInput.setText(entry.path);
                        openRestrictedPathWithShizuku(entry.path);
                    }
                })
                .setNegativeButton("取消", null)
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

    private void seekBy(int deltaMs) {
        if (player == null || !prepared) {
            return;
        }
        int duration = player.getDuration();
        int next = Math.max(0, Math.min(duration, player.getCurrentPosition() + deltaMs));
        player.seekTo(next);
        updateProgress();
        showFeedback(deltaMs > 0 ? "+10s" : "-10s");
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
        float scale = fillMode ? Math.max(sx, sy) : Math.min(sx, sy);
        float scaledWidth = videoWidth * scale;
        float scaledHeight = videoHeight * scale;
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

    private void updatePlayButton() {
        if (playButton == null || centerPlayButton == null) {
            return;
        }
        boolean playing = player != null && prepared && player.isPlaying();
        playButton.setText(playing ? "⏸" : "▶");
        centerPlayButton.setText(playing ? "⏸" : "▶");
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
        controlsVisible = visible;
        int visibility = visible ? View.VISIBLE : View.GONE;
        topBar.setVisibility(visibility);
        bottomBar.setVisibility(visibility);
        dimLayer.setVisibility(visible ? View.VISIBLE : View.GONE);
        updatePlayButton();
        if (visible) {
            scheduleControlsHide();
            hideSystemBars();
        } else {
            hideSystemBars();
        }
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
                    int currentCode = packageInfo().versionCode;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (latestCode > currentCode && !apkUrl.isEmpty()) {
                                showUpdateDialog(latestName, notes, apkUrl);
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

    private void showUpdateDialog(String versionName, String notes, String apkUrl) {
        String message = "发现新版本 " + versionName;
        if (notes != null && !notes.trim().isEmpty()) {
            message += "\n\n" + notes.trim();
        }
        new AlertDialog.Builder(this)
                .setTitle("Smooth Player 更新")
                .setMessage(message)
                .setNegativeButton("稍后", null)
                .setPositiveButton("下载并安装", (dialog, which) -> downloadAndInstallUpdate(apkUrl))
                .show();
    }

    private void downloadAndInstallUpdate(String apkUrl) {
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

    private Button makeCenterButton(String text) {
        Button button = makeButton(text);
        button.setTextSize(18);
        button.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        button.setBackgroundResource(R.drawable.bg_center_button);
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

    private SharedPreferences getPreferences() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private void saveLastPath(String path) {
        getPreferences().edit().putString(KEY_LAST_PATH, path).apply();
    }

    private String displayTitle(Uri uri) {
        String value = uri.getLastPathSegment();
        if (value == null || value.trim().isEmpty()) {
            value = uri.toString();
        }
        return value;
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

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class RestrictedEntry {
        final boolean directory;
        final String name;
        final String path;

        RestrictedEntry(boolean directory, String name, String path) {
            this.directory = directory;
            this.name = name;
            this.path = path;
        }

        static RestrictedEntry parse(String value) {
            if (value == null) {
                return null;
            }
            String[] parts = value.split("\\|", 3);
            if (parts.length != 3) {
                return null;
            }
            return new RestrictedEntry("D".equals(parts[0]), parts[1], parts[2]);
        }
    }
}
