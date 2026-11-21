package ui.mobilegl.plugin.trace;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import java.io.File;

public final class TraceReplayActivity extends Activity {
    public static final String ACTION_TRACE_REPLAY = "ui.mobilegl.plugin.TRACE_REPLAY";

    private static final String TAG = "MobileGLTraceRunner";
    /** Set when the runner is not in this APK: a prebuilt-library build skips its native sources. */
    private static final UnsatisfiedLinkError LIBRARY_FAILURE;
    static {
        UnsatisfiedLinkError failure = null;
        try {
            System.loadLibrary("trace_replay_runner");
        } catch (UnsatisfiedLinkError e) {
            failure = e;
        }
        LIBRARY_FAILURE = failure;
    }

    private TextView statusView;
    private TraceReplayRequest request;
    private boolean started;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        );
        SurfaceView surfaceView = new SurfaceView(this);
        setContentView(surfaceView);
        if (LIBRARY_FAILURE != null) {
            TextView message = new TextView(this);
            message.setPadding(24, 24, 24, 24);
            message.setText("Trace replay is not available in this build: "
                    + LIBRARY_FAILURE.getMessage());
            setContentView(message);
            Log.e(TAG, "trace_replay_runner is missing from this APK", LIBRARY_FAILURE);
            return;
        }

        Intent intent = getIntent();
        request = TraceReplayRequest.from(
                intent,
                getFilesDir(),
                getApplicationInfo().nativeLibraryDir,
                getString(ui.mobilegl.plugin.R.string.mobilegl_default_backend)
        );
        statusView = new TextView(this);
        statusView.setText("Waiting for render surface\n" + request.outputDir);
        statusView.setPadding(24, 24, 24, 24);
        addContentView(statusView, new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        SurfaceHolder holder = surfaceView.getHolder();
        if (request.width > 0 && request.height > 0) {
            holder.setFixedSize(request.width, request.height);
        }
        holder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                scheduleReplay(holder);
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                scheduleReplay(holder);
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
            }
        });
    }

    private void scheduleReplay(SurfaceHolder holder) {
        mainHandler.postDelayed(() -> startReplay(holder), 250);
    }

    private void startReplay(SurfaceHolder holder) {
        if (started) {
            return;
        }
        Surface surface = holder.getSurface();
        if (surface == null || !surface.isValid()) {
            return;
        }
        started = true;
        statusView.setText("Running trace replay\n" + request.outputDir);
        new Thread(() -> runRequest(request, surface), "MobileGLTraceReplay").start();
    }

    private void runRequest(TraceReplayRequest request, Surface surface) {
        TraceReplayResult result = nativeRunTraceReplay(
                surface,
                request.tracePath,
                request.goldenPath,
                request.alternateGoldenPath,
                request.outputDir,
                request.diffPath,
                request.backend,
                request.targetFrame,
                request.targetCall,
                request.width,
                request.height,
                request.ssimThreshold,
                request.cropX,
                request.cropY,
                request.cropWidth,
                request.cropHeight,
                request.angleLibraryDir,
                request.useAngle,
                request.usePbuffer,
                request.avoidAngleLlvmpipeSamplerMipmapMinFilter
        );
        Log.i(TAG, result.toString());
        TraceReplayResult finalResult = result;
        runOnUiThread(() -> {
            statusView.setText(finalResult.toString());
            finish();
        });
    }

    private static native TraceReplayResult nativeRunTraceReplay(
            Surface surface,
            String tracePath,
            String goldenPath,
            String alternateGoldenPath,
            String outputDir,
            String diffPath,
            String backend,
            int targetFrame,
            long targetCall,
            int width,
            int height,
            double ssimThreshold,
            int cropX,
            int cropY,
            int cropWidth,
            int cropHeight,
            String angleLibraryDir,
            boolean useAngle,
            boolean usePbuffer,
            boolean avoidAngleLlvmpipeSamplerMipmapMinFilter
    );

    private static final class TraceReplayRequest {
        final String tracePath;
        final String goldenPath;
        final String alternateGoldenPath;
        final String outputDir;
        final String diffPath;
        final String backend;
        final int targetFrame;
        final long targetCall;
        final int width;
        final int height;
        final double ssimThreshold;
        final int cropX;
        final int cropY;
        final int cropWidth;
        final int cropHeight;
        final String angleLibraryDir;
        final boolean useAngle;
        final boolean usePbuffer;
        final boolean avoidAngleLlvmpipeSamplerMipmapMinFilter;

        private TraceReplayRequest(
                String tracePath,
                String goldenPath,
                String alternateGoldenPath,
                String outputDir,
                String diffPath,
                String backend,
                int targetFrame,
                long targetCall,
                int width,
                int height,
                double ssimThreshold,
                int cropX,
                int cropY,
                int cropWidth,
                int cropHeight,
                String angleLibraryDir,
                boolean useAngle,
                boolean usePbuffer,
                boolean avoidAngleLlvmpipeSamplerMipmapMinFilter
        ) {
            this.tracePath = tracePath;
            this.goldenPath = goldenPath;
            this.alternateGoldenPath = alternateGoldenPath;
            this.outputDir = outputDir;
            this.diffPath = diffPath;
            this.backend = backend;
            this.targetFrame = targetFrame;
            this.targetCall = targetCall;
            this.width = width;
            this.height = height;
            this.ssimThreshold = ssimThreshold;
            this.cropX = cropX;
            this.cropY = cropY;
            this.cropWidth = cropWidth;
            this.cropHeight = cropHeight;
            this.angleLibraryDir = angleLibraryDir;
            this.useAngle = useAngle;
            this.usePbuffer = usePbuffer;
            this.avoidAngleLlvmpipeSamplerMipmapMinFilter = avoidAngleLlvmpipeSamplerMipmapMinFilter;
        }

        static TraceReplayRequest from(Intent intent, File filesDir, String nativeLibraryDir, String defaultBackend) {
            String defaultOutput = new File(filesDir, "trace-replay").getAbsolutePath();
            String outputDir = pathInside(filesDir, readString(intent, "output_dir", defaultOutput), defaultOutput, false);
            String diffPath = pathInside(filesDir, readString(intent, "diff_path", ""), "", false);
            File nativeDir = new File(nativeLibraryDir);
            return new TraceReplayRequest(
                    pathInside(filesDir, readString(intent, "trace_path", ""), "", false),
                    pathInside(filesDir, readString(intent, "golden_path", ""), "", false),
                    pathInside(filesDir, readString(intent, "alternate_golden_path", ""), "", false),
                    outputDir,
                    diffPath,
                    readString(intent, "backend", defaultBackend),
                    intent.getIntExtra("target_frame", -1),
                    intent.getLongExtra("target_call", -1L),
                    intent.getIntExtra("width", 0),
                    intent.getIntExtra("height", 0),
                    readDouble(intent, "ssim_threshold", 0.99),
                    intent.getIntExtra("crop_x", 0),
                    intent.getIntExtra("crop_y", 0),
                    intent.getIntExtra("crop_width", 0),
                    intent.getIntExtra("crop_height", 0),
                    pathInside(nativeDir, readString(intent, "angle_library_dir", nativeLibraryDir),
                            nativeLibraryDir, true),
                    intent.getBooleanExtra("use_angle", false),
                    intent.getBooleanExtra("use_pbuffer", false),
                    intent.getBooleanExtra("avoid_angle_llvmpipe_sampler_mipmap_min_filter", false)
            );
        }

        /**
         * This activity answers an exported intent, so a path from it may only name something
         * inside the directory we own. Anything else would let any installed app make the trace
         * sink create or truncate files with this app's all-files privilege.
         */
        private static String pathInside(File root, String path, String fallback, boolean mustExist) {
            if (path == null || path.isEmpty()) {
                return fallback;
            }
            try {
                String canonicalRoot = root.getCanonicalPath() + File.separator;
                File candidate = new File(path);
                String canonical = candidate.getCanonicalPath();
                if (!canonical.startsWith(canonicalRoot) || (mustExist && !candidate.isDirectory())) {
                    Log.w(TAG, "ignoring intent path outside " + root + ": " + path);
                    return fallback;
                }
                return canonical;
            } catch (java.io.IOException e) {
                Log.w(TAG, "cannot resolve intent path " + path, e);
                return fallback;
            }
        }

        private static String readString(Intent intent, String key, String fallback) {
            String value = intent.getStringExtra(key);
            return value == null ? fallback : value;
        }

        private static double readDouble(Intent intent, String key, double fallback) {
            String stringValue = intent.getStringExtra(key);
            if (stringValue != null && !stringValue.isEmpty()) {
                try {
                    return Double.parseDouble(stringValue);
                } catch (NumberFormatException ignored) {
                    return fallback;
                }
            }
            return intent.getDoubleExtra(key, fallback);
        }
    }

    public static final class TraceReplayResult {
        public final boolean passed;
        public final int statusCode;
        public final String message;
        public final String resultPath;
        public final String actualPath;
        public final String diffPath;

        public TraceReplayResult(
                boolean passed,
                int statusCode,
                String message,
                String resultPath,
                String actualPath,
                String diffPath
        ) {
            this.passed = passed;
            this.statusCode = statusCode;
            this.message = message;
            this.resultPath = resultPath;
            this.actualPath = actualPath;
            this.diffPath = diffPath;
        }

        @Override
        public String toString() {
            return "TraceReplayResult{" +
                    "passed=" + passed +
                    ", statusCode=" + statusCode +
                    ", message='" + message + '\'' +
                    ", resultPath='" + resultPath + '\'' +
                    ", actualPath='" + actualPath + '\'' +
                    ", diffPath='" + diffPath + '\'' +
                    '}';
        }
    }
}
