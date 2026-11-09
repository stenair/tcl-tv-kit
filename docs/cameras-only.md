# Cameras-only install (branch `cameras-only`)

This branch is for a TV where you want **only the camera overlay**: the four colour buttons each show one camera,
and **nothing else on the TV changes**. Google TV's home, its apps, the gear, the app buttons and Back all stay
exactly as they were. Nothing is disabled, there's no new launcher and no theme. Steps 3–5 of the main guide
(debloat, launcher, Home takeover) are skipped entirely.

| Button | Shows |
|---|---|
| red | recorder channel 1 |
| green | recorder channel 2 |
| yellow | recorder channel 3 |
| blue | recorder channel 4 |

The same button again closes the camera; another colour switches straight to its camera. "Keep cameras ready"
(instant opening) is a switch on the app's setup screen only; no button toggles it.

> [!WARNING]
> Same caveat as the main kit: built and tested on a TCL 75C755, firmware `tcl9618-user 14 UTT2.250416.001 AU02`.
> Run the check in step 1 on the TV you're setting up and compare before installing anything.

**Tested on:** a **TCL 55C745** (2023, device G08) with the same firmware build as the 75C755
(`tcl9618-user 14 UTT2.250416.001 AU02`, patch 2025-12-05), Google TV's home untouched, a Dahua-type 4-channel
recorder (sub-stream H.265 704×576). Its remote sends different scan codes (398–401) for the colour buttons, but
the same key names, so the built-in list worked as is. Stream connected in ~1.8 s on a cold open.

## What's different from the main build

| | Main build (`main`, 2.2) | This build (`cameras-only`, 2.2-cameras) |
|---|---|---|
| APK | `apks/tv-buttons-2.2.apk` | `apks/tv-buttons-2.2-cameras.apk` |
| Built-in buttons | YouTube, Prime, Netflix, gear, TCL, Disney+, Stan, yellow/blue cameras, red = ready toggle | **red, green, yellow, blue → cameras 1–4, nothing else** |
| "Keep cameras ready" | red button or setup screen | setup screen only |
| Back guard (YouTube after Settings) | on | **off** (Back behaves as stock) |
| Log tags read | `WindowManager`, `QSOptionMenusDialog`, `InputDispatcher`, `ActivityTaskManager` | `WindowManager`, `QSOptionMenusDialog` |
| Debloat, launcher, theme, Home | steps 3–5 | **none** |

Same package (`local.tvbuttons`), same code otherwise. The full source diff: `git diff main cameras-only -- tv-buttons`.

## Everything this changes on the TV, and the undo

Start `work\<tv>\CHANGES.md` (in the CleanYourTV folder) from this table.

| # | Change | Undo |
|---|---|---|
| 1 | **USB debugging on** (Developer options). TV Buttons needs it permanently: it reads the button log through the TV's own debugging port | Developer options → USB debugging off (the camera buttons then stop) |
| 2 | **The PC's debugging key approved** (for the install) | Developer options → *Revoke USB debugging authorisations* |
| 3 | **TV Buttons 2.2-cameras installed** | TV Settings → Apps → TV Buttons → Uninstall |
| 4 | **TCL auto-start allowed** for it (`appops`) | removed with the uninstall |
| 5 | **Its accessibility service on** (`enabled_accessibility_services`, `accessibility_enabled`; note the original values first) | `tv.py "settings put secure enabled_accessibility_services '<original>'; settings put secure accessibility_enabled <original>"`, or uninstall |
| 6 | **TV Buttons' own debugging key approved** | *Revoke USB debugging authorisations* (also removes the PC's) |
| 7 | **The camera login** stored in TV Buttons (typed by the owner) | clear it on the setup screen, or uninstall |
| 8 | (Optional) **"Keep cameras ready"** on | the switch on the setup screen |
| — | On the recorder: a **view-only account** | delete it on the recorder |

Uninstalling TV Buttons undoes 3–5, 7 and 8 at once. Nothing else is touched.

## 1. Check the TV (read-only)

From the CleanYourTV folder, with the PC on the **same main network** as the TV (not guest Wi-Fi):

1. TV: Settings → System → About → tap *Android TV OS build* 7 times; then Developer options → **USB debugging on**
   (Wireless debugging stays off). Approve the PC's prompt on the TV (**Always allow**).
2. Compare with ours ([docs/01](01-before-you-start.md#check-your-tv-against-ours)): model, firmware build, Android
   version. Then:

   ```powershell
   tools\venv\Scripts\python.exe -I tools\tv.py "getprop ro.product.model; getprop ro.build.fingerprint; getprop ro.build.version.sdk; settings get secure enabled_accessibility_services; settings get secure accessibility_enabled; pm list packages local.tvbuttons"
   ```

   Note the two accessibility values (the originals for row 5). `local.tvbuttons` should not be listed yet.
3. **Do the colour buttons log the same codes?** Press red, green, yellow, blue once each while this records
   (TV Buttons isn't needed for it):

   ```powershell
   tools\venv\Scripts\python.exe -I ..\tcl-tv-kit\tools\record_keys.py 60
   ```

   Expect `KEYCODE_PROG_RED`, `…_GREEN`, `…_YELLOW`, `…_BLUE`. Anything else: use a `mappings.txt` (below) with the
   codes it printed.
4. **Can the TV reach the recorder?** (toybox `nc` on these TVs has no `-z`, so send a real request):

   ```bash
   MSYS_NO_PATHCONV=1 tools/venv/Scripts/python.exe -I tools/tv.py 'printf "OPTIONS rtsp://<recorder ip>:554/ RTSP/1.0\r\nCSeq: 1\r\n\r\n" | toybox nc -w 3 <recorder ip> 554 | head -1'
   ```

   `RTSP/1.0 401 Unauthorized` (or `200 OK`) means reachable: the recorder answered and wants the login. Nothing:
   see [the network section](#a-tv-on-a-range-extender-or-another-part-of-the-network).
5. **Does the household use the colour buttons for anything?** Live TV with an antenna (TCL's TV app uses them for
   teletext and the guide), or an app that does. TV Buttons never swallows a key, so the app would get the press too.

## 2. Backup (read-only, ~1 minute)

Nothing system-level changes here, but a capture of the original state costs nothing:

```powershell
tools\venv\Scripts\python.exe -I ..\tcl-tv-kit\tools\backup_tv.py backup\<date>-<tv>-original
```

## 3. The recorder

As in [step 7](07-camera-overlay.md#what-you-need): the recorder's address (recorder menu → Network → TCP/IP),
**reserved** in the router; a **view-only account** (group User, Live only, all cameras); the sub-stream on (H.265
or H.264, D1). Optional: VLC plays `rtsp://<recorder ip>:554/cam/realmonitor?channel=1&subtype=1`.

The channel numbers are the recorder's own camera list: channel 1 = its first camera. If you want a different
camera on a different colour, use a `mappings.txt` (below); no rebuild needed.

## 4. Install and enable (the TV must be free: it shows a prompt)

```powershell
tools\venv\Scripts\python.exe -I tools\b64push.py ..\tcl-tv-kit\apks\tv-buttons-2.2-cameras.apk /data/local/tmp/tvbuttons.apk
tools\venv\Scripts\python.exe -I tools\tv.py "pm install -r /data/local/tmp/tvbuttons.apk; rm -f /data/local/tmp/tvbuttons.apk"
tools\venv\Scripts\python.exe -I tools\tv.py "cmd appops set local.tvbuttons AUTO_START allow; cmd appops set local.tvbuttons APP_AUTO_START allow; appops write-settings"
# a few seconds later (if the original value wasn't empty, append ":local.tvbuttons/.ButtonService" to it instead):
tools\venv\Scripts\python.exe -I tools\tv.py "settings put secure enabled_accessibility_services local.tvbuttons/.ButtonService; settings put secure accessibility_enabled 1"
```

On this firmware the `APP_AUTO_START` line answers "Unknown operation string": harmless, `AUTO_START: allow` is the
one that counts (`cmd appops get local.tvbuttons`).

**The TV asks "Allow USB debugging?"** for TV Buttons' own key: tick **Always allow**, OK. No prompt? Wireless
debugging off, USB debugging off and on, and the queued prompt appears.

Check:

```powershell
tools\venv\Scripts\python.exe -I tools\tv.py "dumpsys accessibility | grep -oE 'capabilities=[0-9]+' | head -1; dumpsys package local.tvbuttons | grep versionName; logcat -d -s TvButtons:I | tail -3"
```

Expect `capabilities=0`, `versionName=2.2-cameras` and
`watching the button log for [KEYCODE_PROG_RED, …] (Back guard off)`.

## 5. Set up the cameras

On the TV: **Apps → TV Buttons** (it shows in Google TV's app list; nothing hides it here). The owner types the
recorder address, username and password with the remote (stored in TV Buttons' private storage, never logged),
names the four cameras, presses **Try** on each, then **Save**.

## 6. Test with the real remote

- [ ] Each colour at Home, over a streaming app and over an HDMI input: the right camera, bottom-right
- [ ] The same button closes; another colour switches
- [ ] Held volume still ramps; the gear, Back and the app buttons do exactly what they did before
- [ ] YouTube shows its controls briefly when a colour opens a camera (expected: the key reaches it too)

## 7. "Keep cameras ready" (optional)

Off: a camera opens in ~3 s. On: ~0.1 s. On the setup screen (Apps → TV Buttons), the switch under the cameras.

It keeps **each colour button's camera streaming in the background** while the TV is on. With four buttons that's
**four streams**, twice what we measured on ours: two D1 sub-streams cost about **+6–8% of the whole TV** (TV
Buttons ~17% of one core, the video decoder +8%), so expect roughly **+12–16%** here, plus ~2 Mbit/s on the home
network. Nobody noticed two streams on ours (benchmarks in [step 7](07-camera-overlay.md#instant-opening-keep-cameras-ready)).
Measure four on this TV before leaving it on, ideally with a 4K stream playing, and watch for camera errors
("Can't reach the camera") that would mean the video chip ran out of decoders. If in doubt, leave it off:
switching between cameras is still ~0.05 s while one is open.

## A TV on a range extender or another part of the network

TV Buttons talks to the TV itself (`127.0.0.1`) and, for the cameras, only opens connections **out** to the
recorder: RTSP over TCP on port 554 (the picture travels inside that same connection) and HTTP on port 80 for the
still. Nothing has to connect **in** to the TV. So what matters is whether the TV can reach the recorder's address,
not whether it's on the same Wi-Fi name:

| How the extender is set up | Camera overlay | Installing from the PC |
|---|---|---|
| **Bridges** (repeater / access-point mode): the TV gets an address in the main network's range | works exactly as on the main network | from the PC on the main network |
| **Routes** (its own DHCP and a different address range behind it) | works if the TV reaches the recorder's main-network address through it (step 1.4); the setup screen uses that same address | join the PC to the extender's Wi-Fi for the install and checks (the PC can't reach in through it) |
| **Isolates clients** (a guest network) | the recorder is unreachable | move the TV to a network that isn't a guest network |

Bandwidth is small: ~0.5 Mbit/s per camera sub-stream, ~2 Mbit/s with "Keep cameras ready" on four. A weak
extender link shows as "Can't reach the camera. Trying again…" rather than a stalled TV. The TV's own address can
change freely (TV Buttons doesn't use it); the **recorder's** must not, so reserve it in the main router.

## Changing which colour shows which camera

Push a `mappings.txt` (it replaces the built-in list; see [`mappings.example.txt`](../tv-buttons/mappings.example.txt)):

```text
KEYCODE_PROG_RED=camera:2
KEYCODE_PROG_GREEN=camera:1
KEYCODE_PROG_YELLOW=camera:3
KEYCODE_PROG_BLUE=camera:4
```

```powershell
tools\venv\Scripts\python.exe -I tools\tv.py "mkdir -p /sdcard/Android/data/local.tvbuttons/files"
tools\venv\Scripts\python.exe -I tools\tv.py --push mappings.txt /sdcard/Android/data/local.tvbuttons/files/mappings.txt
```

Delete the file to go back to red/green/yellow/blue = 1/2/3/4.

## Building it

From this branch, exactly as in [`tv-buttons/README.md`](../tv-buttons/README.md#build). The only source changes
from `main` are the built-in list and the `back_guard` switch in `res/values/strings.xml`, the log command in
`ButtonService.java`, and `versionName` in `app/build.gradle`.
