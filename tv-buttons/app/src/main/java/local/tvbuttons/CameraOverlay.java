package local.tvbuttons;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DecoderReuseEvaluation;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.rtsp.RtspMediaSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A small live picture of one camera from the home recorder, bottom-right, over whatever is on screen (apps, HDMI).
 * It is an accessibility overlay of the button service: no extra permission, never focusable (the remote keeps
 * driving the app underneath), silent (the audio track is never selected).
 *
 * <p>Each camera is a <em>feed</em>: a Media3 player on the recorder's RTSP sub-stream that runs independently of
 * the window. Only the feed on screen is attached to the window's picture; any other feed keeps decoding into
 * Media3's off-screen placeholder surface, unseen and never composited. Attaching a running feed shows its next
 * frame at once (no new connection, no keyframe wait), which is how switching, and opening with "Keep cameras
 * ready" on, are instant.
 *
 * <ul>
 *   <li><b>Cold open</b> (feed not running): the camera's still JPEG from the recorder (grey dot) while the stream
 *   connects and waits for a keyframe; the live picture replaces it (red dot).</li>
 *   <li><b>While open</b>: the likely next camera (next in a stepping list, else the one shown before, else another
 *   mapped one) is kept running, so switching is instant both ways.</li>
 *   <li><b>Keep cameras ready</b> (setup screen toggle, off by default): the first camera of every camera button
 *   runs all the time the screen is on, so even the first open is instant. Stops on standby; starts again a few
 *   seconds after the TV wakes.</li>
 *   <li><b>Closed, toggle off</b>: nothing runs.</li>
 * </ul>
 *
 * <p>The login is the one typed on the TV in CameraSetupActivity, kept in this app's private storage; only timings
 * and error codes are logged, never an address with a login in it.
 */
final class CameraOverlay {
    static final String PREFS = "camera";
    static final String PREF_READY = "ready";
    private static final String TAG = "TvButtons";
    private static final long RETRY_MS = 5000, HINT_MS = 4000, MESSAGE_MS = 6000, WARM_AFTER_LIVE_MS = 400;
    private static final long READY_AFTER_WAKE_MS = 15_000, READY_RETRY_MIN_MS = 30_000, READY_RETRY_MAX_MS = 300_000;
    private static final int FAILURES_BEFORE_LOGIN_HINT = 3, MAX_READY = 4, STILL_WIDTH = 800;
    private static final int LIVE_DOT = 0xFFFF3B30, STILL_DOT = 0xFF8E8E93;

    /** One camera's stream. {@code ready}: the player is playing (data flowing), on screen or not. */
    private static final class Feed {
        final int channel;
        ExoPlayer player;
        boolean ready;
        int failures, token;
        long retryMs = READY_RETRY_MIN_MS;

        Feed(int channel) {
            this.channel = channel;
        }
    }

    private final Context context;
    private final WindowManager windows;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "tvbuttons-still");
        t.setDaemon(true);
        return t;
    });
    private final Map<Integer, Feed> feeds = new HashMap<>();
    private FrameLayout root;
    private ImageView still;
    private TextureView video;
    private TextView label, status, hint;
    private GradientDrawable dot;
    private Bitmap stillBitmap;
    private int stillToken;
    private Feed shown;
    private boolean screenOn = true;
    private int channel;      // the camera on screen; 0 = closed
    private int previous;     // the camera shown before it: the likely way back
    private int[] cycle = {};
    private List<Integer> cameras = Collections.emptyList();
    private List<Integer> readySet = Collections.emptyList();
    private long pressedAt;
    private final Runnable hideHint = () -> {
        if (hint != null) hint.setVisibility(View.GONE);
    };
    private final Runnable warmUp = this::warmNext;
    private final Runnable closeLater = this::close;
    private final Runnable startReady = this::startReadyFeeds;

    CameraOverlay(Context serviceContext) {
        context = serviceContext;
        windows = serviceContext.getSystemService(WindowManager.class);
    }

    boolean isShowing() {
        return channel != 0;
    }

    /** The mapped cameras, and the ones "Keep cameras ready" keeps running (first camera of each button). */
    void setCameras(List<Integer> all, List<Integer> firstOfEachButton) {
        cameras = all;
        readySet = firstOfEachButton.size() > MAX_READY ? firstOfEachButton.subList(0, MAX_READY) : firstOfEachButton;
        trimFeeds();
        scheduleReady(READY_AFTER_WAKE_MS); // not at once: start-up (boot) and a new mappings.txt settle first
    }

    /** "Keep cameras ready" was switched, or the service just started: apply it after {@code delayMs}. */
    void scheduleReady(long delayMs) {
        main.removeCallbacks(startReady);
        if (readyEnabled() && screenOn) main.postDelayed(startReady, delayMs);
        else trimFeeds();
    }

    void screenOff() {
        screenOn = false;
        main.removeCallbacks(startReady);
        close();
        for (Feed f : new ArrayList<>(feeds.values())) release(f); // nothing streams to a dark screen
    }

    void screenOn() {
        screenOn = true;
        scheduleReady(READY_AFTER_WAKE_MS);
    }

    /** The {@code cameras_ready} button: flips "Keep cameras ready" and returns the new state. */
    boolean toggleReady() {
        boolean on = !prefs().getBoolean(PREF_READY, false);
        prefs().edit().putBoolean(PREF_READY, on).apply();
        scheduleReady(0); // on: start the ready streams now; off: stop them (unless one is on screen)
        Log.i(TAG, "keep cameras ready: " + (on ? "on" : "off"));
        return on;
    }

    /** For the setup screen: whether the toggle is on and its streams are running. */
    String readyState() {
        int running = 0;
        for (int ch : readySet) {
            Feed f = feeds.get(ch);
            if (f != null && f.ready) running++;
        }
        return running + " of " + readySet.size();
    }

    /**
     * A camera button was pressed. With one channel ({@code camera:2}) it toggles: shows it, or closes it when it is
     * already showing. With several ({@code camera:3,4,5}) each press steps to the next channel, and the press after
     * the last closes. A button for other cameras switches straight to its (first) camera.
     */
    void press(int[] channels, String buttonName, List<Integer> allCameras) {
        cameras = allCameras;
        int at = -1;
        for (int i = 0; i < channels.length; i++) if (channels[i] == channel) at = i;
        if (at == channels.length - 1) { // the only camera, or the last of a cycle, is showing: close
            close();
            return;
        }
        cycle = channels;
        boolean more = at + 2 < channels.length; // another camera follows the one about to show
        show(channels[at + 1], more ? "Press " + buttonName + " for the next camera"
                : "Press " + buttonName + " again to close");
    }

    private void show(int ch, String hintText) {
        pressedAt = SystemClock.uptimeMillis();
        if (root == null) build();
        main.removeCallbacks(closeLater);
        main.removeCallbacks(warmUp);
        SharedPreferences p = prefs();
        String name = p.getString("name" + ch, "").trim();
        label.setText((name.isEmpty() ? "Camera " + ch : name).toUpperCase(Locale.ROOT));
        hint.setText(hintText);
        hint.setVisibility(View.VISIBLE);
        main.removeCallbacks(hideHint);
        main.postDelayed(hideHint, HINT_MS);

        int was = channel;
        channel = ch;
        if (was != 0 && was != ch) previous = was;
        detachShown();
        clearStill();
        video.setAlpha(0f); // until this camera's first frame: never show the previous camera under this name
        dot.setColor(STILL_DOT);
        if (p.getString("pass", "").isEmpty()) {
            showStatus("Camera login not set yet.\nOpen TV Buttons from your apps to add it.");
            main.postDelayed(closeLater, MESSAGE_MS);
            return;
        }

        Feed f = feeds.get(ch);
        boolean running = f != null && f.player != null && f.ready;
        if (f == null) {
            f = new Feed(ch);
            feeds.put(ch, f);
        }
        if (f.player == null) startPlayer(f);
        shown = f;
        f.player.setVideoTextureView(video); // a running feed renders its next frame here within ~0.1 s
        if (running) {
            status.setVisibility(View.GONE);
        } else {
            showStatus("Connecting…");
            fetchStill(f);
        }
        trimFeeds();
    }

    void close() {
        main.removeCallbacks(hideHint);
        main.removeCallbacks(warmUp);
        main.removeCallbacks(closeLater);
        detachShown();
        clearStill();
        channel = 0;
        if (root != null) {
            try {
                windows.removeView(root);
            } catch (RuntimeException ignored) {
            }
            root = null;
            video = null;
            still = null;
        }
        trimFeeds(); // keeps only the "ready" cameras, if that's on
    }

    /** The shown feed leaves the window but keeps running (decoding off screen) until trimFeeds decides. */
    private void detachShown() {
        if (shown != null && shown.player != null && video != null) shown.player.clearVideoTextureView(video);
        shown = null;
    }

    /** Keeps exactly the feeds that are wanted: the shown one, the warm next one, and the ready set; drops the rest. */
    private void trimFeeds() {
        List<Integer> keep = new ArrayList<>();
        if (channel != 0) {
            keep.add(channel);
            int next = nextWarm(channel);
            if (next != 0) keep.add(next);
        }
        if (readyEnabled() && screenOn) keep.addAll(readySet);
        for (Feed f : new ArrayList<>(feeds.values())) if (!keep.contains(f.channel)) release(f);
    }

    private boolean readyEnabled() {
        return prefs().getBoolean(PREF_READY, false) && !prefs().getString("pass", "").isEmpty();
    }

    private void startReadyFeeds() {
        if (!readyEnabled() || !screenOn) return;
        for (int ch : readySet) {
            Feed f = feeds.get(ch);
            if (f == null) {
                f = new Feed(ch);
                feeds.put(ch, f);
            }
            if (f.player == null) startPlayer(f);
        }
        Log.i(TAG, "keeping cameras ready: " + readySet);
    }

    /** The camera most likely shown next: next in a stepping list, else the one before, else another mapped one. */
    private int nextWarm(int ch) {
        for (int i = 0; i + 1 < cycle.length; i++) if (cycle[i] == ch) return cycle[i + 1];
        if (previous != 0 && previous != ch && cameras.contains(previous)) return previous;
        for (int c : cameras) if (c != ch) return c;
        return 0;
    }

    private void warmNext() {
        if (channel == 0) return;
        int want = nextWarm(channel);
        if (want == 0) return;
        Feed f = feeds.get(want);
        if (f == null) {
            f = new Feed(want);
            feeds.put(want, f);
        }
        if (f.player == null) startPlayer(f);
    }

    @OptIn(markerClass = UnstableApi.class) // DefaultLoadControl.Builder, RtspMediaSource, AnalyticsListener
    private void startPlayer(Feed f) {
        SharedPreferences p = prefs();
        // Media3 reads the login from the address (decoded, so any character works) and sends it only as RTSP
        // authentication. Never log this address.
        String url = "rtsp://" + Uri.encode(p.getString("user", "")) + ":" + Uri.encode(p.getString("pass", ""))
                + "@" + p.getString("host", "").trim() + ":554/cam/realmonitor?channel=" + f.channel + "&subtype=1";
        ExoPlayer ep = new ExoPlayer.Builder(context)
                // A live preview: start after a quarter of a second of video and keep the buffer short.
                .setLoadControl(new DefaultLoadControl.Builder().setBufferDurationsMs(500, 2000, 250, 250).build())
                // (experimentalSetDynamicSchedulingEnabled was tried for the ready streams: no measurable saving.)
                .build();
        f.player = ep;
        f.ready = false;
        ep.setTrackSelectionParameters(ep.getTrackSelectionParameters().buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build());
        ep.setVolume(0f);
        final long t0 = SystemClock.uptimeMillis();
        ep.addAnalyticsListener(new AnalyticsListener() {
            @Override
            public void onVideoInputFormatChanged(AnalyticsListener.EventTime e, Format format,
                                                  DecoderReuseEvaluation reuse) {
                Log.i(TAG, "camera " + f.channel + " stream connected in " + (SystemClock.uptimeMillis() - t0) + " ms ("
                        + format.sampleMimeType + " " + format.width + "x" + format.height + ")");
            }
        });
        ep.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (f.player != ep) return;
                if (state == Player.STATE_READY && !f.ready) {
                    f.ready = true;
                    f.failures = 0;
                    f.retryMs = READY_RETRY_MIN_MS;
                    if (f != shown) Log.i(TAG, "camera " + f.channel + " running off screen ("
                            + (SystemClock.uptimeMillis() - t0) + " ms)");
                } else if (state == Player.STATE_ENDED) {
                    failed(f);
                }
            }

            @Override
            public void onRenderedFirstFrame() {
                if (f.player != ep || f != shown) return;
                onLive(f);
            }

            @Override
            public void onPlayerError(PlaybackException e) {
                if (f.player != ep) return;
                Log.w(TAG, "camera " + f.channel + " error " + e.getErrorCodeName()); // messages may hold the address
                failed(f);
            }
        });
        ep.setMediaSource(new RtspMediaSource.Factory().setForceUseRtpTcp(true).setTimeoutMs(8000)
                .createMediaSource(MediaItem.fromUri(url)));
        ep.setPlayWhenReady(true);
        ep.prepare();
    }

    private void onLive(Feed f) {
        if (video == null) return;
        video.setAlpha(1f);
        status.setVisibility(View.GONE);
        dot.setColor(LIVE_DOT);
        Log.i(TAG, "camera " + f.channel + " live in " + (SystemClock.uptimeMillis() - pressedAt) + " ms");
        clearStill();
        main.removeCallbacks(warmUp);
        main.postDelayed(warmUp, WARM_AFTER_LIVE_MS);
    }

    /** A stream failed. On screen: retry with a message (the still stays). Off screen: retry later, or drop it. */
    private void failed(Feed f) {
        stopPlayer(f);
        f.ready = false;
        final int token = f.token;
        if (f == shown) {
            f.failures++;
            dot.setColor(STILL_DOT);
            showStatus(f.failures >= FAILURES_BEFORE_LOGIN_HINT
                    ? "Can't reach the camera.\nCheck the login in TV Buttons (your apps). Retrying…"
                    : "Can't reach the camera.\nTrying again…");
            main.postDelayed(() -> {
                if (f == shown && f.token == token && f.player == null) {
                    startPlayer(f);
                    f.player.setVideoTextureView(video);
                }
            }, RETRY_MS);
        } else if (readyEnabled() && screenOn && readySet.contains(f.channel)) {
            long wait = f.retryMs; // a ready camera keeps trying (recorder restart, Wi-Fi drop), backing off
            f.retryMs = Math.min(f.retryMs * 2, READY_RETRY_MAX_MS);
            main.postDelayed(() -> {
                if (f.token == token && f.player == null && feeds.get(f.channel) == f && readyEnabled() && screenOn) {
                    startPlayer(f);
                }
            }, wait);
        } else {
            release(f); // a warm "next" camera is simply dropped
        }
    }

    /** Fetches the camera's still in the background and shows it unless the live picture got there first. */
    private void fetchStill(Feed f) {
        SharedPreferences p = prefs();
        final String host = p.getString("host", "").trim(), user = p.getString("user", ""), pass = p.getString("pass", "");
        final long t0 = pressedAt;
        final int token = ++stillToken;
        io.execute(() -> {
            Bitmap b = null;
            String why = null;
            try {
                b = Snapshot.fetch(host, f.channel, user, pass, STILL_WIDTH);
            } catch (Snapshot.Failure e) {
                why = "HTTP " + e.status;
            } catch (Exception e) {
                why = e.getClass().getSimpleName();
            }
            final Bitmap bmp = b;
            final String reason = why;
            main.post(() -> {
                boolean wanted = token == stillToken && f == shown && still != null && video != null
                        && video.getAlpha() == 0f;
                if (!wanted) {
                    if (bmp != null) bmp.recycle();
                    return;
                }
                if (bmp == null) {
                    Log.w(TAG, "camera " + f.channel + " still: " + reason);
                    return;
                }
                stillBitmap = bmp;
                still.setImageBitmap(bmp);
                still.setVisibility(View.VISIBLE);
                status.setVisibility(View.GONE);
                Log.i(TAG, "camera " + f.channel + " still in " + (SystemClock.uptimeMillis() - t0) + " ms ("
                        + Snapshot.lastTiming + ")");
            });
        });
    }

    private void clearStill() {
        stillToken++;
        if (still != null) {
            still.setVisibility(View.GONE);
            still.setImageDrawable(null);
        }
        if (stillBitmap != null) {
            stillBitmap.recycle();
            stillBitmap = null;
        }
    }

    private void stopPlayer(Feed f) {
        ExoPlayer ep = f.player;
        f.player = null;
        if (ep != null) ep.release();
    }

    private void release(Feed f) {
        if (f == null) return;
        f.token++; // its pending retries are now ignored
        if (f == shown) detachShown();
        stopPlayer(f);
        f.ready = false;
        feeds.remove(f.channel);
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private void build() {
        float d = context.getResources().getDisplayMetrics().density;
        int w = Math.round(400 * d), h = Math.round(225 * d), margin = Math.round(32 * d);

        root = new FrameLayout(context);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.BLACK);
        bg.setCornerRadius(14 * d);
        root.setBackground(bg);
        root.setClipToOutline(true);
        GradientDrawable edge = new GradientDrawable();
        edge.setColor(Color.TRANSPARENT);
        edge.setCornerRadius(14 * d);
        edge.setStroke(Math.max(1, Math.round(d)), 0x40FFFFFF);
        root.setForeground(edge);

        still = new ImageView(context); // the recorder's JPEG while a cold stream connects
        still.setScaleType(ImageView.ScaleType.FIT_XY);
        still.setVisibility(View.GONE);
        root.addView(still, new FrameLayout.LayoutParams(-1, -1));
        video = new TextureView(context); // the shown feed renders here; the D1 sub-stream is squeezed to 704x576
        video.setAlpha(0f);
        root.addView(video, new FrameLayout.LayoutParams(-1, -1));

        // Top-left: a dot (grey = still or connecting, red = live) and the camera's name, on a dark chip.
        LinearLayout chip = new LinearLayout(context);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        int padH = Math.round(10 * d), padV = Math.round(5 * d);
        chip.setPadding(padH, padV, padH, padV);
        chip.setBackground(rounded(0xA6000000, 10 * d));
        View dotView = new View(context);
        dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(STILL_DOT);
        dotView.setBackground(dot);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(Math.round(8 * d), Math.round(8 * d));
        dotLp.setMarginEnd(Math.round(7 * d));
        chip.addView(dotView, dotLp);
        label = text(13, Typeface.create("sans-serif-medium", Typeface.NORMAL), 0xFFFFFFFF);
        label.setLetterSpacing(0.06f);
        chip.addView(label);
        FrameLayout.LayoutParams chipLp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        chipLp.setMargins(Math.round(10 * d), Math.round(10 * d), 0, 0);
        root.addView(chip, chipLp);

        status = text(14, Typeface.DEFAULT, 0xE6FFFFFF);
        status.setGravity(Gravity.CENTER);
        int padS = Math.round(16 * d);
        status.setPadding(padS, padS, padS, padS);
        root.addView(status, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));

        hint = text(12, Typeface.DEFAULT, 0xE6FFFFFF);
        hint.setPadding(padH, padV, padH, padV);
        hint.setBackground(rounded(0xA6000000, 10 * d));
        FrameLayout.LayoutParams hintLp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hintLp.bottomMargin = Math.round(10 * d);
        root.addView(hint, hintLp);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, h,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.END;
        lp.x = margin;
        lp.y = margin;
        lp.windowAnimations = android.R.style.Animation_Toast;
        lp.setTitle("TV Buttons camera");
        windows.addView(root, lp);
    }

    private void showStatus(String s) {
        if (status == null) return;
        status.setText(s);
        status.setVisibility(View.VISIBLE);
    }

    private TextView text(float sp, Typeface face, int color) {
        TextView t = new TextView(context);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(face);
        t.setTextColor(color);
        t.setShadowLayer(4, 0, 1, 0x80000000);
        return t;
    }

    private static GradientDrawable rounded(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }
}
