package local.tvbuttons;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A small, quiet confirmation pill, top-right: a coloured dot and a few words, fading in and out (about 2 s in
 * all). An accessibility overlay like the camera window: never focusable or touchable, so it can't disturb what is
 * playing. A second notice while one shows replaces its text and restarts its timer.
 */
final class Notice {
    private static final long IN_MS = 160, HOLD_MS = 1500, OUT_MS = 240;

    private final Context context;
    private final WindowManager windows;
    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout pill;
    private GradientDrawable dot;
    private TextView text;
    private final Runnable fadeOut = this::fadeOut;

    Notice(Context serviceContext) {
        context = serviceContext;
        windows = serviceContext.getSystemService(WindowManager.class);
    }

    void show(String message, int dotColor) {
        if (pill == null) build();
        dot.setColor(dotColor);
        text.setText(message);
        main.removeCallbacks(fadeOut);
        pill.animate().cancel();
        pill.animate().alpha(1f).translationY(0f).setDuration(IN_MS).withEndAction(null);
        main.postDelayed(fadeOut, IN_MS + HOLD_MS);
    }

    void dismiss() {
        main.removeCallbacks(fadeOut);
        remove();
    }

    private void fadeOut() {
        if (pill == null) return;
        pill.animate().alpha(0f).setDuration(OUT_MS).withEndAction(this::remove);
    }

    private void remove() {
        if (pill == null) return;
        try {
            windows.removeView(pill);
        } catch (RuntimeException ignored) {
        }
        pill = null;
    }

    private void build() {
        float d = context.getResources().getDisplayMetrics().density;
        pill = new LinearLayout(context);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        int padH = Math.round(16 * d), padV = Math.round(9 * d);
        pill.setPadding(padH, padV, padH + Math.round(2 * d), padV);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xD914171C); // dark glass, like the launcher's pills
        bg.setCornerRadius(22 * d);
        bg.setStroke(Math.max(1, Math.round(d)), 0x26FFFFFF);
        pill.setBackground(bg);

        View dotView = new View(context);
        dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dotView.setBackground(dot);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(Math.round(9 * d), Math.round(9 * d));
        dotLp.setMarginEnd(Math.round(10 * d));
        pill.addView(dotView, dotLp);

        text = new TextView(context);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        text.setTextColor(0xF2FFFFFF);
        text.setLetterSpacing(0.02f);
        pill.addView(text);

        pill.setAlpha(0f);
        pill.setTranslationY(-6 * d);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = Math.round(32 * d);
        lp.y = Math.round(28 * d);
        lp.setTitle("TV Buttons notice");
        windows.addView(pill, lp);
    }
}
