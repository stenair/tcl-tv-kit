# TV Buttons

An Android TV app (package `local.tvbuttons`, 2.1, ~467 KB, plain Java plus Google's Media3 player) that gives remote
buttons a job on TCL Google TVs: open apps, switch inputs, open TCL's quick-settings bar, show a live camera picture.
It **never intercepts a key**, so held volume keeps ramping and held arrows keep scrolling. How to install and use
it: [docs/06](../docs/06-remote-buttons.md) and [docs/07](../docs/07-camera-overlay.md). This page is how it's built.

> [!NOTE]
> Written for, and tested on, a TCL 75C755 with firmware `tcl9618-user 14 UTT2.250416.001 AU02`. It depends on that
> firmware's behaviour: every key press logged by `WindowManager`, ADB on `127.0.0.1:5555` with USB debugging, TCL's
> `ShowWindowService`, and the log lines in the table below. On another model or after a firmware update, check
> those with `tools/record_keys.py` and the commands in [docs/01](../docs/01-before-you-start.md#check-your-tv-against-ours).

## How it works

| Part | File | What it does |
|---|---|---|
| Service | `ButtonService.java` | An accessibility service with **no key filtering** (capabilities 0, `res/xml/button_service.xml`). Android binds it at boot; it starts the log reader, acts on mapped buttons, owns the camera overlay and the Back guard |
| ADB client | `AdbClient.java` | A minimal ADB protocol client to the TV's own port `127.0.0.1:5555` (CNXN, RSA AUTH with its own key stored in app files, `shell:` streams). Approved once on the TV ("Always allow") |
| Mappings | `Mappings.java` | The button → target map: built in (`res/values/strings.xml`) or a pushed `mappings.txt` that replaces it; camera target parsing; button names for hints |
| Camera overlay | `CameraOverlay.java` | A `TYPE_ACCESSIBILITY_OVERLAY` window (not focusable/touchable, 400×225 dp, bottom-right) with a `TextureView` and a Media3 ExoPlayer on the recorder's RTSP sub-stream, over TCP, video only |
| Setup screen | `CameraSetupActivity.java`, `layout/camera_setup.xml`, `layout/camera_row.xml` | App Library → TV Buttons: recorder address, login, one name + Try per mapped camera. Stored in private `SharedPreferences` (`camera`: `host`, `user`, `pass`, `name<n>`) |

**The log it reads** (one ADB shell stream):
`logcat -v brief -T 1 -s WindowManager:D QSOptionMenusDialog:D InputDispatcher:D ActivityTaskManager:I`

| Source | Line it uses | For |
|---|---|---|
| `WindowManager` | `handleKeyBeforeQueueing event=KeyEvent { action=ACTION_DOWN\|UP, keyCode=…, … repeatCount=N,` | every button press and release (TCL logs them all) |
| `QSOptionMenusDialog` | `onWindowFocusChanged: hasFocus = true` / `dismiss` | whether TCL's quick-settings bar is open |
| `InputDispatcher` | `publishKeyEvent(inputChannel=… com.android.tv.settings/… action=0, keyCode=4)` | a Back press that went to TV Settings |
| `ActivityTaskManager` | `moveTaskToBack: Task{… #<id> … A=<uid>:…}` | an app sending itself away (YouTube on the stray Back release) |

All four are silent at idle. The reader costs ~0.3% of one core.

**Actions.** A mapped button acts on its first press (`repeatCount=0`), except `quick_settings`, which acts on the
**release** (TCL's bar closes on the gear's release) and not at all if the bar was open at the press (the bar's own
"close"). Targets:

| Target | Implementation |
|---|---|
| `input:<id>` | `ACTION_VIEW` on `TvContract.buildChannelUriForPassthroughInput(id)`, type `vnd.android.cursor.item/channel`, package `com.tcl.tv` (`live_tv_package`) |
| `<package>` | its leanback (else regular) launch intent. Needs `<queries>` in the manifest (Android 11 package visibility) |
| `settings` | `Settings.ACTION_SETTINGS` |
| `quick_settings` | `startService(com.tcl.settings.SHOW_WINDOW → com.android.tv.settings/com.tcl.settings.ShowWindowService, Type=quicksettings)`, else settings |
| `camera:<n>` / `camera:<a>,<b>,…` | the overlay: toggle one channel / step through a list, close after the last |

**Back guard.** A Back press delivered to TV Settings arms it (until 1.5 s after the release); an app task moved to
the back while armed is put back with `ActivityManager.moveTaskToFront` (`REORDER_TASKS`), falling back to its
launch intent. Any other key press disarms it.

**Camera overlay.** URL `rtsp://<user>:<pass>@<host>:554/cam/realmonitor?channel=<n>&subtype=1` (Dahua; login
URL-encoded, so any characters work; **never logged**: only error codes are). Media3 `RtspMediaSource` with
`setForceUseRtpTcp(true)`, an 8 s timeout, audio disabled in the track selection, and a short live buffer
(`DefaultLoadControl` 500/2000/250/250 ms). Retries every 5 s; after 3 failures the message mentions the login.
Closes on the same button, on `ACTION_SCREEN_OFF`, and when the setup screen pauses. Another brand's recorder:
change the URL in `play()`.

## Mappings

Built in: `res/values/strings.xml` → `mappings` (the 75C755 this was made on). On a TV with other inputs, codes or
cameras, push a `mappings.txt` instead (see [`mappings.example.txt`](mappings.example.txt)). It **replaces** the
built-in list and is re-read when it changes (checked on each button press). Path:
`/sdcard/Android/data/local.tvbuttons/files/mappings.txt`.

## Build

JDK 17, Android SDK platform 34 + build-tools 34.0.0. From this folder (PowerShell):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot'    # your JDK 17
# only if Gradle fails with "Unable to establish loopback connection":
# $env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir=C:\java-sockets'
.\gradlew.bat lintRelease assembleRelease --no-daemon
```

Output: `app\build\outputs\apk\release\app-release.apk` (R8 on; signed with this PC's debug key). Lint: 0 errors.
Raise `versionCode` in `app/build.gradle` for every build you install over an older one (2.1 = 11). An APK signed
with a different key can't update an installed one: see `../apks/README.md`.

## Install, enable, undo

See [docs/06](../docs/06-remote-buttons.md#install-and-enable) (install + appops + accessibility + approving its
key) and [docs/08](../docs/08-maintenance-and-undo.md) (undo).

## Version history

| Version (code) | Change |
|---|---|
| 1.0 (1) | Disney+ → PS5 input, Stan → cameras input; log reader + non-filtering service |
| 1.1 (2) | a one-off on-screen message (removed again in 1.2) |
| 1.2 (3) | = 1.0 |
| 1.3 (4) | YouTube, Prime, gear, TCL buttons (Google TV's home was gone) |
| 1.4 (5) | `<queries>`: app targets were invisible to it (Android 11+) |
| 1.5 (6) | Netflix (`4062`); TCL → the TCL Channel *input* |
| 1.6 (7) | gear → TCL's quick-settings bar |
| 1.7 (8) | the bar opens on the gear's release, and the gear toggles it |
| 1.8 (9) | Back guard (YouTube exiting on the stray Back release) |
| 2.0 (10) | camera overlay (yellow/blue), setup screen, Media3, R8 |
| 2.1 (11) | `mappings.txt` without rebuilding; any number of cameras (`camera:a,b,c` steps through); setup lists every mapped camera; lint clean |
