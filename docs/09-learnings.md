# Everything we learned, decided and ruled out

From one 75C755 (Google TV 14, `tcl9618-user 14 UTT2.250416.001 AU02`), November 2026. Read this before starting:
most of it cost time to find out.

## Decisions

| Decision | Why |
|---|---|
| Disable, never uninstall; staged batches; backup first | Every step stays reversible with one command; a bad batch is caught before the next |
| Keep Chromecast, Assistant/Gemini, game bar, TCL Channel, Play Store | Used in the household; the mic needs `katniss` **and** `tv.remote.service` |
| Disable `ttvs` (Samba TV ACR tracking, TCL analytics) even though Alexa dies with it | Responsiveness and privacy first; Alexa was expendable |
| Re-enable `com.tcl.interactive` | Disabled, it caused a 10×/second retry loop in the TV app (see below) |
| Firmware updates by hand | An update can re-enable packages or change key logging; install, then re-check (step 8) |
| CleanYourTV launcher, no network permission | Instant, no ads possible; Google TV's home is an ad surface by design |
| 4 favourites + one half-size row + dashed free slot | Whole TV on one screen, clear at a glance |
| Names under every tile; cinematic glow; no greeting | Anyone can read the screen; calm without video/blur |
| PS5/Cameras/TCL Channel as tiles via TCL's spare entries (`com.tcl.suspension`, `com.tcl.dashboard`) | One press to switch input, no menus; those entries otherwise open TCL's source bar |
| TCL Channel tile → the TCL Channel *input* | `com.tcl.tv` alone resumes the last input (it opened the cameras once) |
| Buttons via a log reader (TV Buttons), not a key-filtering helper | Key filtering kills the held-volume ramp on this firmware |
| Gear → TCL's own quick-settings bar | Google's side panel can't exist without Google's home; TCL's is ad-free and editable |
| Camera overlay in TV Buttons, over RTSP, with Media3 | No picture-in-picture for HDMI; Media3 plays the H.265 sub-stream and doesn't log the password |
| The camera login typed on the TV; a view-only recorder account | The TV never holds the admin password; the assistant never handles passwords |
| Accept: the Input button's TCL source bar (it shows TCL Channel promos) | It's a system key; can't be remapped without key filtering |
| Accept: the volume ramp speed (fast, ~20 steps/s) | Set by system properties that need root |

## How this TV behaves (facts we verified)

- **Device:** G08, MediaTek `tcl9618`, Android 14 (SDK 34), user build (`ro.debuggable=0`), `ro.adb.secure=1`, A/B
  slots. 3840×2160 panel, 1920×1080 UI at density 320 (the factory setting; don't change it). 2.4 GB RAM, 4 cores.
- **USB debugging alone opens port 5555** (no cable needed). Wireless debugging is separate and not needed.
- **adbd can hold a stuck approval prompt** ("prompt currently pending", nothing on screen). Wireless debugging off +
  USB debugging off/on makes the queued prompts appear.
- **The address changes after standby** (DHCP).
- **Every key press is logged** by TCL's window manager: `handleKeyBeforeQueueing event=KeyEvent { action=ACTION_DOWN,
  keyCode=…, scanCode=…, … repeatCount=0, …`, real or injected. The main log runs at ~230 lines a second, so it
  only reaches back about a minute.
- **Google TV's home owned these buttons** through "global keys" (`dumpsys window policy`): GUIDE, BUTTON_3
  (YouTube), PROFILE_SWITCH, FEATURED_APP_1, MACRO_1. They can't be re-pointed without root. Prime (`4099`) and the
  gear (`KEYCODE_NOTIFICATION`, via SystemUI → `TOGGLE_NOTIFICATION_HANDLER_PANEL`, system apps only) also died with
  it. **Netflix (`4062`)** goes to `com.tcl.ttvs` via TCL's `TclPhoneWindowManagerEx`. **TCL (`4020`)** opens
  `com.tcl.waterfall.overseas`.
- **Remote codes** (Bluetooth remote, all scan code 240): Disney+ `4141`, Stan `4136`, YouTube `KEYCODE_BUTTON_3`,
  Prime `4099`, Netflix `4062`, TCL `4020`, gear `KEYCODE_NOTIFICATION`, colours `KEYCODE_PROG_RED/GREEN/YELLOW/BLUE`.
- **Volume:** TCL's AudioService sets `sys.dynamic_key_repeat_delay` from `sys.tcl.volume_key_repeat_delay` (150 ms)
  while held, and steps by `sys.repeated_key_vol_steps` (30 = 3 of 100 steps) per repeat; `system_prop`, not
  settable from the shell. Volume never reaches the window manager, so accessibility services never see it.
- **Any accessibility service that filters keys** (capabilities include 8) turns held volume into one step. Held
  arrows keep repeating.
- **Android 14 log access:** `LogcatManagerService` refuses `READ_LOGS` for background apps without a dialog, and
  the dialog activity doesn't exist on this TV. Instrumentation (auto-approved) can only start from the shell on a
  user build. **An ADB connection to `127.0.0.1:5555` from an app on the TV has shell rights**, so it gets the full
  log.
- **HDMI:** `com.tcl.tv` (`com.tcl.player.TVActivity`) is the only player for HDMI and TV inputs; `com.tcl.tvinput`
  hosts the inputs: `HW15`…`HW18` = HDMI 1…4, tuner `HW2`, AV `HW3`, TCL Channel
  `com.tcl.channelplus/com.tcl.iptv.service.InputService`. Switching = `ACTION_VIEW` on
  `TvContract.buildChannelUriForPassthroughInput(id)`, type `vnd.android.cursor.item/channel`, package `com.tcl.tv`.
- **No picture-in-picture** (`android.software.picture_in_picture` missing; TCL Multi View absent).
- **TCL's quick-settings bar** lives in TV Settings: `startService` of `com.tcl.settings.ShowWindowService` with action
  `com.tcl.settings.SHOW_WINDOW` and extra `Type=quicksettings`. It closes on the gear's *release*. Its log tag is
  `QSOptionMenusDialog`.
- **TCL's Picture / Sound side panels** need their actions (`android.settings.DISPLAY_SETTINGS`,
  `com.android.tv.action.VIEW_SOUND_SETTINGS`); started by bare component they close within 0.6 s ("action=null").
  `com.android.tv.action.VIEW_INPUTS` opens TCL's source bar with promos.
- **TV Settings closes on the Back press, not the release.** The release reaches the app underneath: YouTube exits on
  it (`moveTaskToBack`), Netflix ignores it.
- **`com.tcl.interactive` (TCL "TIAS", HbbTV):** disabled, `com.tcl.tv`'s `tbrw3_Tbrowser` thread retries
  `vendor.tcl.tvinput.extend.ITvExtendService` every 100 ms forever (~1.2% of a core, ~150 log lines a second).
  Enabled, the TV app starts it on demand.
- **Alexa needs `ttvs`.** "Works with Alexa" fails ("TV isn't responding") with it off.
- **Google TV's setup wizard (`setupwraith`) claims Home** when `launcherx` is off, and owns any Wi-Fi network it
  created at first setup (disabling it deletes those).
- **The built-in `MediaPlayer` can't play the Dahua H.265 sub-stream over RTSP** (wrong picture size "287 × 0", no
  decoder), and TCL's verbose media logs print the full `rtsp://user:pass@…` address. Media3 works.
- **Decoders:** `c2.mtk.avc/hevc.decoder` (several instances) next to a single secure decoder (Netflix): a camera
  stream plays alongside Netflix without stutter.
- **Deco routers:** the guest Wi-Fi uses the same address range as the main network but blocks traffic between
  them (no ARP reply), and casting needs phone and TV on one network.
- **Camera open time is the keyframe wait.** Cold: ~1 s to connect, then ~2 s until the camera's next keyframe.
  The Dahua recorder's still (`snapshot.cgi`) is the full 6 MP JPEG (1.5 MB) and takes it 1.5–2 s, so stills barely
  help on this recorder. Its TV menu has no I-frame interval setting (only in the web interface / the camera).
- **A running Media3 player can switch screens without a keyframe.** Detached from the window, it decodes into
  Media3's placeholder surface; attached again (`setVideoTextureView`, i.e. `MediaCodec.setOutputSurface`), the next
  frame shows in 20–200 ms on this MediaTek decoder. That's the "Keep cameras ready" trick, and why switching is
  instant.
- **Cost of a camera stream:** one shown ≈ +10% of the TV; each one kept ready off screen ≈ +3–4% of the TV (TV
  Buttons' player loop plus MediaTek's `c2` decoder service). The launcher benchmark showed no noticeable effect.
- **The idle TV isn't idle:** Google Assistant's always-listening interactor (`katniss`) uses ~27% of one core all
  the time, TCL's picture engine (`pqu`) ~16%, the audio HAL ~15%. More than the cameras ever do.
- **Colour buttons reach the app in front.** YouTube shows its controls for any standard key, colour buttons
  included; it ignores TCL's vendor codes (≥ 4000). Only key filtering could hide a key from the app (and it breaks
  held volume), and TCL's key layout files are read-only.

## Dead ends (so you don't repeat them)

| Tried | Result |
|---|---|
| Launcher's `InputKeyService` + `app_keys` for Stan/Disney+ | Worked, but held volume became single steps |
| `consume_volume_keys` | No effect: volume never reaches the helper |
| Dropping the accessibility service ~10 s after a volume press (tvQuickActions' trick) | The ramp doesn't resume mid-hold here |
| tvQuickActions Pro | Same key-filtering mechanism, same volume problem |
| Community "TCL volume script" (Termux + ADB to localhost + SSH server) | Same idea as TV Buttons but needs Termux and runs a password SSH server; not recommended |
| Reading logs with `READ_LOGS` granted | Android 14 refuses it for background apps (above) |
| Changing the volume ramp speed | System properties, root only |
| HDMI 2 in a small window | No picture-in-picture hardware path |
| Rebuilding Google's side panel from Google TV's home | Proprietary and privileged; TCL's own bar (above) does the job |
| TV Settings' `SliceActivity` with TCL's slices (`content://com.tcl.settings.slice/…`) | Pins the slice, shows only a spinner |
| Broadcast `com.tcl.showsetting` to `ShortCutMenuReceiver` | Nothing happens |
| `input keyevent --duration` / `--longpress` to test held keys | `--duration` isn't honoured (press and release at the same instant); `--longpress` sends a long press. `sendevent` is denied by SELinux |
| Live `logcat` over `tv.py` for minutes | Times out (output is buffered); record to a file on the TV instead |
| A still JPEG first, to hide the camera's ~3 s start (your brother's Linux viewer does this) | Works, but this recorder takes 1.5–2 s to make a 6 MP still, so it only gains ~1 s; kept for cold opens, made redundant by "Keep cameras ready" |
| Keeping the warm camera drawn under the shown one | Works, but costs screen compositing; decoding off screen (placeholder surface) does the same for less |
| Media3 `experimentalSetDynamicSchedulingEnabled` | No measurable CPU saving for the ready streams |
| Hiding colour-button presses from YouTube | Impossible without key filtering (volume) or root (key layouts) |

## Tooling gotchas (Windows)

- PowerShell 5.1 strips inner double quotes from native arguments → use single quotes inside, `tv.py --batch`, or
  Git Bash.
- Git Bash rewrites `/data/…` paths → `MSYS_NO_PATHCONV=1`.
- `pkill -f '<pattern>'` inside a `tv.py` command kills its own shell (its command line contains the pattern) → use
  a bracket trick, `pkill -f 'logca[t]'`.
- `setsid nohup sh -c '…' >/dev/null 2>&1 </dev/null & sleep 2` keeps a TV-side job running after `tv.py`
  disconnects. **Both halves matter:** without `setsid` the job dies with the connection (`(sleep 1; reboot) &`
  never rebooted), and without the `sleep 2` the shell ends before `setsid` has detached it, so nothing runs
  (`record_keys.py`'s first version recorded nothing for exactly this reason).
- Java "Unable to establish loopback connection" → `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=<short path>`.
- `sdkmanager` from PowerShell → quote `'"platforms;android-34"'`.
- `build-and-install.ps1 -StockLauncher none` still moves Android's Home *role* to the new launcher → give it back
  with `cmd role add-role-holder --user 0 android.app.role.HOME com.google.android.apps.tv.launcherx`.
- Android 11+ package visibility: an app opening other apps needs `<queries>`, or `getLaunchIntentForPackage`
  quietly returns null (the YouTube/Prime buttons did nothing until we added it).
- Media3's live-tuning APIs need `@OptIn(UnstableApi)` (lint error otherwise); `androidx.annotation` isn't exposed
  transitively, so add `annotation-experimental`.

## Measurements

| What | Value |
|---|---|
| TV Buttons log reader | ~0.3% of one core, ~3 MB |
| Camera overlay, one camera showing | whole TV 27% → 37%; TV Buttons ~15% of one core |
| Camera overlay, showing + warm next camera | whole TV → 42%; TV Buttons ~25% of one core |
| "Keep cameras ready" (2 cameras, overlay closed) | whole TV ~+6–8%; TV Buttons ~17% of one core, ~16 MB; ~1 Mbit/s LAN |
| Camera open time | cold ~3 s (connect ~1 s + keyframe ~2 s); ready 0.1–0.2 s; switch 0.02–0.05 s |
| Launcher benchmark, ready off vs on | key p50 103 vs 105 ms; jank 5.1 vs 5.3%; app cold start p50 528 vs 574 ms |
| `com.tcl.interactive` on vs off | ~0.2% vs ~1.2% of a core; 0 vs ~150 log lines a second |
| Launcher APK | 590,965 bytes (upstream gate 589,824) |
| TV Buttons APK | ~467 KB (Media3, trimmed by R8) |
