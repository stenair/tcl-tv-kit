package local.tvbuttons;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.tv.TvContract;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Remaps unused remote buttons without intercepting any key. Android binds this service at boot; it asks for no key
 * filtering, so held keys keep auto-repeating. It reads the window manager's key log over the TV's own debugging port
 * (Android 14 refuses log access to an ordinary app in the background, but not to a debugging connection) and acts on
 * the first press of a mapped button. The button still does its own job too; for the unused buttons that is nothing.
 */
public final class ButtonService extends AccessibilityService {
    private static final String TAG = "TvButtons";
    /** The line TCL's window manager logs for every key-down and key-up, real or injected. */
    private static final Pattern KEY = Pattern.compile(
            "handleKeyBeforeQueueing event=KeyEvent \\{ action=ACTION_(DOWN|UP), keyCode=([A-Za-z_0-9]+),.*?repeatCount=(\\d+),");
    /** TCL's quick settings bar logs when it gains focus and when it closes; that tells whether it is open. */
    private static final String QUICK_TAG = "QSOptionMenusDialog";
    /** Which window a key went to (InputDispatcher, action=0 is a press, keyCode=4 is Back). */
    private static final String BACK_TO_SETTINGS = "com.android.tv.settings/";
    /** An app sending its own task to the back (ActivityTaskManager): task id and the app's uid. */
    private static final Pattern MOVED_BACK = Pattern.compile("moveTaskToBack: Task\\{\\S+ #(\\d+) \\S+ A=(\\d+):");
    private static final String LOG_COMMAND = "logcat -v brief -T 1 -s WindowManager:D " + QUICK_TAG
            + ":D InputDispatcher:D ActivityTaskManager:I";
    /** How long after the Back release an app's "exit" still counts as the stray release (YouTube: ~300 ms). */
    private static final long BACK_GUARD_MS = 1500;

    private final Handler main = new Handler(Looper.getMainLooper());
    /** Built-in (strings.xml) or the pushed mappings.txt; swapped whole when the file changes. */
    private volatile Map<String, String> mappings = Collections.emptyMap();
    private volatile long mappingsStamp;
    private volatile boolean running;
    // Read on the log thread only.
    private boolean quickOpen;
    private boolean quickOpenAtDown;
    /** Uptime until which an app sending itself to the back is undone; 0 when off. */
    private long backGuardUntil;
    private volatile AdbClient client;
    private Thread reader;
    private static volatile ButtonService instance;
    private CameraOverlay camera;
    /** The camera overlay closes when the TV goes to standby, so it never streams to a dark screen. */
    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (camera != null) camera.close();
        }
    };
    private boolean screenOffRegistered;

    /** The running service, for the camera setup screen's preview; null when the service isn't bound. */
    static ButtonService running() {
        return instance;
    }

    @Override
    protected void onServiceConnected() {
        reloadMappings();
        if (camera == null) camera = new CameraOverlay(this);
        if (!screenOffRegistered) {
            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenOff, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(screenOff, filter);
            screenOffRegistered = true;
        }
        instance = this;
        if (running) return;
        running = true;
        reader = new Thread(this::readLoop, "tvbuttons-log");
        reader.start();
    }

    private void reloadMappings() {
        mappingsStamp = Mappings.stamp(this);
        mappings = Mappings.load(this);
    }

    /** The mappings in force, for the camera setup screen. */
    Map<String, String> mappings() {
        return mappings;
    }

    /** Shows camera n (the setup screen's preview), restarting it so a changed login takes effect. */
    void showCamera(int n) {
        camera.close();
        String button = Mappings.buttonForCamera(mappings, n);
        camera.press(new int[]{n}, button != null ? button : "the same button");
    }

    void closeCamera() {
        if (camera != null) camera.close();
    }

    private void readLoop() {
        long backoff = 2000;
        while (running) {
            try (AdbClient c = AdbClient.connect("127.0.0.1", 5555, AdbClient.keys(this), "tvbuttons@tv", 120_000)) {
                client = c;
                backoff = 2000;
                Log.i(TAG, "watching the button log for " + mappings.keySet());
                c.shellLines(LOG_COMMAND, this::onLine);
            } catch (Exception e) {
                if (running) Log.w(TAG, "debugging connection: " + e);
            } finally {
                client = null;
            }
            if (!running) break;
            try {
                Thread.sleep(backoff);
            } catch (InterruptedException e) {
                break;
            }
            backoff = Math.min(backoff * 2, 60_000);
        }
    }

    private void onLine(String line) {
        if (line.contains(QUICK_TAG)) {
            if (line.contains("onWindowFocusChanged: hasFocus = true")) quickOpen = true;
            else if (line.contains("dismiss")) quickOpen = false; // not on focus loss: its own pop-ups take focus
            return;
        }
        // Back guard. TV settings closes on the Back press, so the release lands on the app underneath. Netflix
        // ignores it; YouTube takes it as "exit" and sends itself to the back. When an app does that straight after
        // a Back that closed TV settings, bring it back. Any other press first (a deliberate Back) cancels it.
        if (line.contains("publishKeyEvent(")) {
            if (line.contains(BACK_TO_SETTINGS) && line.contains("action=0, keyCode=4)")) {
                backGuardUntil = SystemClock.uptimeMillis() + 2 * BACK_GUARD_MS; // until the release, then shorter
            }
            return;
        }
        if (line.contains("moveTaskToBack: ")) {
            Matcher mb = MOVED_BACK.matcher(line);
            if (backGuardUntil != 0 && SystemClock.uptimeMillis() < backGuardUntil && mb.find()) {
                int task = Integer.parseInt(mb.group(1)), uid = Integer.parseInt(mb.group(2));
                if (uid >= android.os.Process.FIRST_APPLICATION_UID) main.post(() -> restoreTask(task, uid));
            }
            backGuardUntil = 0;
            return;
        }
        Matcher m = KEY.matcher(line);
        if (!m.find()) return;
        boolean down = m.group(1).equals("DOWN"), first = m.group(3).equals("0");
        if (down && first && Mappings.stamp(this) != mappingsStamp) reloadMappings(); // mappings.txt pushed or removed
        String key = m.group(2), target = mappings.get(key);
        if (down && first) {
            backGuardUntil = 0; // a new press; the InputDispatcher line just after it re-arms for Back to settings
        } else if (!down && key.equals("KEYCODE_BACK") && backGuardUntil != 0) {
            backGuardUntil = SystemClock.uptimeMillis() + BACK_GUARD_MS;
        }
        if (target == null) return;
        if (target.equals("quick_settings")) {
            // TCL's bar takes focus at once and closes itself on the gear's release, so open it on the release,
            // not the press. A press while it is open is its own "close": leave that to the bar.
            if (down) {
                if (first) quickOpenAtDown = quickOpen;
            } else if (!quickOpenAtDown) {
                main.post(() -> act(key, target));
            }
            return;
        }
        if (down && first) main.post(() -> act(key, target));
    }

    private void act(String key, String target) {
        Log.i(TAG, key + " -> " + target);
        try {
            if (target.startsWith("input:")) {
                // The passthrough URI the launcher's Inputs menu and key helper open, aimed at the Live TV app.
                Intent view = new Intent(Intent.ACTION_VIEW)
                        .setDataAndType(TvContract.buildChannelUriForPassthroughInput(target.substring(6)),
                                "vnd.android.cursor.item/channel")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(new Intent(view).setPackage(getString(R.string.live_tv_package)));
                } catch (ActivityNotFoundException e) {
                    startActivity(view);
                }
            } else if (target.equals("settings")) {
                startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } else if (target.startsWith("camera:")) {
                // camera:2 toggles channel 2; camera:3,4,5 steps through those channels, then closes.
                int[] channels = Mappings.cameraChannels(target);
                if (channels != null) camera.press(channels, Mappings.buttonName(key));
                else Log.w(TAG, "bad camera target " + target);
            } else if (target.equals("quick_settings")) {
                // TCL's own quick settings bar (picture, sound, network...; editable with its "+"), the panel TCL's
                // other TVs open from the gear. It lives in the TV settings app's ShowWindowService.
                Intent quick = new Intent("com.tcl.settings.SHOW_WINDOW")
                        .setClassName("com.android.tv.settings", "com.tcl.settings.ShowWindowService")
                        .putExtra("Type", "quicksettings");
                boolean shown;
                try {
                    shown = startService(quick) != null;
                } catch (RuntimeException e) { // refused (background or security limits): settings instead
                    Log.w(TAG, "TCL quick settings refused: " + e);
                    shown = false;
                }
                if (!shown) {
                    Log.w(TAG, "no TCL quick settings here; opening TV settings");
                    startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                }
            } else {
                Intent launch = getPackageManager().getLeanbackLaunchIntentForPackage(target);
                if (launch == null) launch = getPackageManager().getLaunchIntentForPackage(target);
                if (launch != null) startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                else Log.w(TAG, "no launch intent for " + target + " (not installed, disabled or not visible)");
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "could not open " + target + ": " + e);
        }
    }

    /** Brings back the task an app sent away on the stray Back release: as it was, else through its launcher entry. */
    private void restoreTask(int taskId, int uid) {
        String[] packages = getPackageManager().getPackagesForUid(uid);
        String pkg = packages != null && packages.length > 0 ? packages[0] : null;
        if (getPackageName().equals(pkg) || "com.gathin.tvhome".equals(pkg)) return;
        Log.i(TAG, "Back from TV settings also sent " + pkg + " (task " + taskId + ") away; bringing it back");
        try {
            getSystemService(ActivityManager.class).moveTaskToFront(taskId, 0);
            return;
        } catch (RuntimeException e) {
            Log.w(TAG, "moveTaskToFront refused: " + e);
        }
        if (pkg != null) act("Back guard", pkg);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(Intent intent) {
        stop();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        stop();
        super.onDestroy();
    }

    private void stop() {
        if (instance == this) instance = null;
        if (camera != null) camera.close();
        if (screenOffRegistered) {
            try {
                unregisterReceiver(screenOff);
            } catch (RuntimeException ignored) {
            }
            screenOffRegistered = false;
        }
        running = false;
        AdbClient c = client;
        if (c != null) {
            try {
                c.close();
            } catch (Exception ignored) {
            }
        }
        if (reader != null) reader.interrupt();
    }
}
