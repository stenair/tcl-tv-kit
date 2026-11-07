package local.tvbuttons;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.rtsp.RtspMediaSource;

/**
 * A small live picture of one camera from the home recorder, bottom-right, over whatever is on screen (apps, HDMI).
 * It is an accessibility overlay of the button service: it needs no extra permission and never takes focus (the
 * remote keeps driving the app underneath). The recorder's small sub-stream comes over RTSP (TCP) through Media3,
 * video only: the audio track is never selected, so the TV's sound is untouched. The login is the one typed on the
 * TV in CameraSetupActivity, kept in this app's private storage; only error codes are logged, never the address.
 */
final class CameraOverlay {
    static final String PREFS = "camera";
    private static final String TAG = "TvButtons";
    private static final long RETRY_MS = 5000, HINT_MS = 4000, MESSAGE_MS = 6000;
    private static final int FAILURES_BEFORE_LOGIN_HINT = 3;

    private final Context context;
    private final WindowManager windows;
    private final Handler main = new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private TextView label, status, hint;
    private TextureView video;
    private ExoPlayer player;
    private int channel; // 0 = closed
    private int failures;

    CameraOverlay(Context serviceContext) {
        context = serviceContext;
        windows = serviceContext.getSystemService(WindowManager.class);
    }

    boolean isShowing() {
        return channel != 0;
    }

    /**
     * A camera button was pressed. With one channel ({@code camera:2}) it toggles: shows it, or closes it when it is
     * already showing. With several ({@code camera:3,4,5}) each press steps to the next channel, and the press after
     * the last closes. A button for other cameras switches straight to its (first) camera.
     */
    void press(int[] channels, String buttonName) {
        int at = -1;
        for (int i = 0; i < channels.length; i++) if (channels[i] == channel) at = i;
        if (at == channels.length - 1) { // the only camera, or the last of a cycle, is showing: close
            close();
            return;
        }
        boolean more = at + 2 < channels.length; // another camera follows the one about to show
        show(channels[at + 1], more ? "Press " + buttonName + " for the next camera"
                : "Press " + buttonName + " again to close");
    }

    private void show(int ch, String hintText) {
        if (root == null) build();
        channel = ch;
        failures = 0;
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String name = p.getString("name" + ch, "").trim();
        label.setText((name.isEmpty() ? "Camera " + ch : name).toUpperCase(java.util.Locale.ROOT));
        hint.setText(hintText);
        hint.setVisibility(View.VISIBLE);
        main.removeCallbacksAndMessages(null);
        main.postDelayed(() -> hint.setVisibility(View.GONE), HINT_MS);
        if (p.getString("pass", "").isEmpty()) {
            stopPlayer();
            showStatus("Camera login not set yet.\nOpen TV Buttons from your apps to add it.");
            main.postDelayed(this::close, MESSAGE_MS);
            return;
        }
        play();
    }

    void close() {
        main.removeCallbacksAndMessages(null);
        stopPlayer();
        channel = 0;
        if (root != null) {
            try {
                windows.removeView(root);
            } catch (RuntimeException ignored) {
            }
            root = null;
            video = null;
        }
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

        video = new TextureView(context); // the player attaches to it and fills it (the D1 sub-stream is 16:9 squeezed)
        root.addView(video, new FrameLayout.LayoutParams(-1, -1));

        // Top-left: a live dot and the camera's name, on a dark chip so it reads over any picture.
        LinearLayout chip = new LinearLayout(context);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        int padH = Math.round(10 * d), padV = Math.round(5 * d);
        chip.setPadding(padH, padV, padH, padV);
        chip.setBackground(rounded(0xA6000000, 10 * d));
        View dot = new View(context);
        GradientDrawable red = new GradientDrawable();
        red.setShape(GradientDrawable.OVAL);
        red.setColor(0xFFFF3B30);
        dot.setBackground(red);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(Math.round(8 * d), Math.round(8 * d));
        dotLp.setMarginEnd(Math.round(7 * d));
        chip.addView(dot, dotLp);
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

    @OptIn(markerClass = UnstableApi.class) // DefaultLoadControl.Builder, RtspMediaSource
    private void play() {
        stopPlayer();
        showStatus("Connecting…");
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String host = p.getString("host", "").trim();
        // Media3 reads the login from the address (decoded, so any character works) and sends it only as RTSP
        // authentication. Never log this address.
        String url = "rtsp://" + Uri.encode(p.getString("user", "")) + ":" + Uri.encode(p.getString("pass", ""))
                + "@" + host + ":554/cam/realmonitor?channel=" + channel + "&subtype=1";
        final int ch = channel;
        ExoPlayer ep = new ExoPlayer.Builder(context)
                // A live preview: start after a quarter of a second of video and keep the buffer short.
                .setLoadControl(new DefaultLoadControl.Builder().setBufferDurationsMs(500, 2000, 250, 250).build())
                .build();
        player = ep;
        ep.setTrackSelectionParameters(ep.getTrackSelectionParameters().buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build());
        ep.setVolume(0f);
        ep.setVideoTextureView(video);
        ep.addListener(new Player.Listener() {
            @Override
            public void onRenderedFirstFrame() {
                if (ep != player) return;
                failures = 0;
                status.setVisibility(View.GONE);
                Log.i(TAG, "camera " + ch + " playing");
            }

            @Override
            public void onPlayerError(PlaybackException e) {
                if (ep != player) return;
                Log.w(TAG, "camera " + ch + " error " + e.getErrorCodeName()); // the code only: messages may hold the address
                retry();
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (ep == player && state == Player.STATE_ENDED) retry();
            }
        });
        ep.setMediaSource(new RtspMediaSource.Factory().setForceUseRtpTcp(true).setTimeoutMs(8000)
                .createMediaSource(MediaItem.fromUri(url)));
        ep.setPlayWhenReady(true);
        ep.prepare();
    }

    private void retry() {
        if (channel == 0) return;
        stopPlayer();
        failures++;
        showStatus(failures >= FAILURES_BEFORE_LOGIN_HINT
                ? "Can't reach the camera.\nCheck the login in TV Buttons (your apps). Retrying…"
                : "Can't reach the camera.\nTrying again…");
        main.postDelayed(() -> {
            if (channel != 0) play();
        }, RETRY_MS);
    }

    private void stopPlayer() {
        ExoPlayer ep = player;
        player = null;
        if (ep != null) ep.release();
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
