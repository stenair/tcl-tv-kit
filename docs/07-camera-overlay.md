# 7. Camera overlay (Dahua / DMSS recorders)

Press a colour button and a live picture of a camera appears bottom-right, over whatever is on: Netflix, YouTube,
the PS5, Home. Press it again to close it; another camera's button switches. It never takes the remote's focus, so
the app underneath keeps working, and it's silent.

![The overlay window (here showing its "no login yet" message; normally the live picture)](../images/camera-overlay-no-login.png)

Tested on ours: two cameras (yellow, blue) at Home, over Netflix (which kept playing smoothly) and over the PS5 on
HDMI. With **"Keep cameras ready" on** (red button), a camera appears **~0.1 s** after the press; with it off, ~3 s.
Switching between cameras while one shows is ~0.05 s either way.

## Why not just show HDMI 2 in a corner?

The recorder is already plugged into HDMI 2, but this TV has **one** HDMI picture path and **no picture-in-picture**
(no `android.software.picture_in_picture`; TCL's Multi View is absent). Showing HDMI 2 means switching the whole
screen, which is what the Stan button does. The overlay instead pulls the camera's network stream (RTSP) and
plays it like any app video, so it works over anything.

## What you need

1. **A recorder that serves RTSP.** Dahua recorders (the ones the DMSS app manages) do. The overlay asks for
   `rtsp://<recorder>:554/cam/realmonitor?channel=<n>&subtype=1`, the small "sub-stream" of channel n. Another brand?
   It's one line in `tv-buttons/.../CameraOverlay.java` (`play()`, the `url`), e.g. Hikvision
   `/Streaming/Channels/<n>02`. A eufy doorbell generally only streams through eufy's cloud, so it's out.
2. **The TV and the recorder on the same network, and not a guest Wi-Fi.** Ours was the trap: the TV (and the PC)
   were on the router's 2.4 GHz **guest** Wi-Fi. It hands out addresses in the same range, but walls guests off
   from everything else, the wired recorder included (no reply to anything, not even a ping). We moved both to the
   main network (the TV to 5 GHz, also better for 4K). Note: phones must be on the TV's network to cast to it.
3. **The recorder's address.** The DMSS app doesn't show it for recorders added by QR/serial (cloud), and the router
   app may not name it. The sure way: on the recorder's own screen (HDMI 2, with a USB mouse plugged into the
   recorder), right-click → **Main Menu → Network → TCP/IP**. Then **reserve that address** in the router so it
   never changes.
4. **A view-only account on the recorder**, so the TV never holds the admin password. On the recorder's screen:
   **Main Menu → Account → User → Add**: username e.g. `tv`, a password, group **User**, permission **Live** only
   (all cameras). Any characters work in the password.
5. **Sub-stream settings** (DMSS → device → Remote Config → Camera Settings → Video Stream → *Sub Stream*). Ours:
   H.265, D1 704×576, 12 fps, CBR 512 kbit/s, which plays fine (the TV decodes H.265 in hardware). Audio can stay on:
   the overlay never plays it. Leave "TLS Encryption" for RTSP off.
6. **Optional, on the PC:** VLC → Media → Open Network Stream →
   `rtsp://<recorder ip>:554/cam/realmonitor?channel=1&subtype=1`. VLC asks for the login (type it there, not in
   the address). If it plays, the recorder side is proven.

## Set it up on the TV

**App Library → TV Buttons** (it's hidden from Home by the theme) opens the setup screen:

- **Recorder address**, **Username**, **Password**: typed with the remote, by the owner. They're stored in TV
  Buttons' private storage, which other apps and the debugging connection can't read, and never logged.
- **One row per camera that a button shows**, with a name (shown on the overlay, e.g. BACKYARD) and a **Try** button
  that previews it.
- **Save.**

## Buttons for your cameras (how to expand)

The overlay shows **recorder channel numbers**: the numbers on the recorder's own camera list (channel 1 = its first
camera). Which buttons show which channels is in the mappings ([step 6](06-remote-buttons.md#your-own-buttons-no-rebuild-needed)):

| Mapping | Behaviour |
|---|---|
| `KEYCODE_PROG_YELLOW=camera:1` | yellow toggles channel 1 |
| `KEYCODE_PROG_BLUE=camera:2` | blue toggles channel 2 |
| `KEYCODE_PROG_RED=camera:3` | red toggles channel 3 (red is the "ready" toggle in the built-in list: move that to another button) |
| `KEYCODE_PROG_GREEN=camera:4` | green toggles channel 4 |
| `KEYCODE_PROG_BLUE=camera:1,2,3,4,5,6` | blue steps 1 → 2 → … → 6 → closed |
| `KEYCODE_PROG_YELLOW=camera:1,2,3,4` + `KEYCODE_PROG_BLUE=camera:5,6,7,8` | eight cameras on two buttons |

Rules: the same button again closes (or steps to the next camera of its list); a different camera button switches
straight to its first camera; the hint at the bottom says what the button does next ("Press blue for the next
camera"). The four colour buttons are free on a TV without an antenna. Any spare button works (Stan and Disney+
are mapped to inputs here, but could be cameras instead). After editing, push `mappings.txt`; the setup screen
then shows a name field and Try button for every channel you mapped.

A colour button also reaches the app in front, so YouTube briefly shows its controls when a camera opens. TCL's
vendor buttons (Stan, Disney+, TCL) don't disturb apps: see [step 6](06-remote-buttons.md#what-it-maps-built-in).

## Instant opening: "Keep cameras ready"

**Where the time goes on a cold open** (measured on ours): the stream connects in ~1 s (login, setup), then waits
**~2 s for the camera's next keyframe** (a full picture; everything in between only describes changes), so ~3 s in
all. The recorder's still JPEG doesn't beat that by much: it is the full 6 MP image (1.5 MB) and takes the recorder
1.5–2 s to produce. A shorter keyframe interval (Dahua: "I Frame Interval", in the recorder's *web* interface or
the camera's, not its TV menu) would cut ~1 s; ours didn't offer it in the TV menu.

**What "Keep cameras ready" does:** TV Buttons keeps the first camera of each camera button **connected and decoding
in the background, never drawn**. Each one decodes into Media3's off-screen placeholder surface, so nothing is
composited on screen. A button press just attaches the running stream to the window, and its next frame shows within
~0.1 s. The same trick makes switching instant: while the overlay is open, the likely next camera (next in a
stepping list, else the one before, else another mapped one) is kept running even with the toggle off.

| | Toggle off | Toggle on |
|---|---|---|
| Open a camera | ~3 s (still at ~2 s, then live) | **~0.1 s** (measured 106–217 ms) |
| Switch camera while open | ~0.05 s (warm next camera) | ~0.05 s |
| While the overlay is closed | nothing runs | 2 streams decoding off screen |
| Stops | — | on standby; restarts ~15 s after the TV wakes |

**What it costs** (75C755, two D1 H.265 sub-streams at 12 fps), measured with the toggle off and on:

| | Off | On |
|---|---|---|
| TV Buttons | 0% | ~17% of one core |
| MediaTek video decoder service | baseline | +~8% of one core |
| Whole TV (4 cores) | baseline | **~+6–8%** |
| Memory | — | TV Buttons ~16 MB |
| Network | — | ~1 Mbit/s on the home network (none to the internet) |

CleanYourTV's launcher benchmark (`tools/bench.py --keys 36 --series 3`), off vs on: key response 103 vs 105 ms
(p50), 124 vs 144 ms (p95); focus movement 815 vs 818 ms; jank 5.1 vs 5.3%; app cold start 528 vs 574 ms (p50),
751 vs 716 ms (p95); idle CPU 11 vs 19%. In use, nobody noticed a difference. For comparison, Google
Assistant's always-listening process (`katniss`) alone uses ~27% of a core on this TV all the time.

**Switch it** with the red button (a small "Instant cameras on/off" notice appears top-right) or on the setup screen
(App Library → TV Buttons). Off by default in a fresh install. Media3's `experimentalSetDynamicSchedulingEnabled`
was tried to cut the cost further: no measurable saving.

## How it works

| Part | Detail |
|---|---|
| Window | An accessibility overlay of TV Buttons' service (`TYPE_ACCESSIBILITY_OVERLAY`): no extra permission, never focusable or touchable, 400×225 dp (16:9), bottom-right, rounded |
| Player | Google's **Media3** (ExoPlayer + RTSP), over **TCP** (steadier on Wi-Fi), **video only** (the audio track is never selected, so the TV's sound is untouched), short buffer for near-live |
| Feeds | One Media3 player per camera, independent of the window; only the camera on screen is attached to the window's `TextureView`, the others decode off screen (see above) |
| Still | On a cold open, the recorder's JPEG (`/cgi-bin/snapshot.cgi?channel=<n>`, HTTP Digest login) shows with a grey dot until the live picture (red dot) replaces it |
| Picture | Shown in a 16:9 window. Our cameras are actually 3:2 (main stream 3072×2048), so the picture is ~18% wider than real; a 3:2 window (400×267 dp, `build()` in `CameraOverlay.java`) would be exact |
| Cost while open | one camera ~+10% of the TV, two (shown + warm next) ~+16%; decoding runs on the video chip (`c2.mtk.hevc.decoder`) alongside Netflix's protected decoder |
| Closes | same button, standby (screen off), leaving the setup screen (previews) |
| Errors | "Can't reach the camera. Trying again…" every 5 s; after 3 failures it adds "Check the login in TV Buttons" |

**Why Media3 and not Android's built-in player:** we tried the built-in `MediaPlayer` first. It logged in and
started the stream, then got the H.265 picture size wrong ("287 × 0") and never started a decoder, so it was stuck
on "Connecting". Worse, TCL's verbose logging wrote the full stream address, **password included**, into the
system log. Media3 plays the stream and logs neither; TV Buttons logs only error codes.

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| Stuck on "Connecting…" | wrong channel number; TV not on the recorder's network (guest Wi-Fi!); recorder address changed |
| "Can't reach the camera … Check the login" | wrong username/password, or the account lacks Live permission |
| Nothing appears on the button | the button's code differs on your remote (`record_keys.py`), or TV Buttons isn't running (step 6 check) |
| "Camera login not set yet" | open App Library → TV Buttons and fill it in |
| Takes ~3 s to open | "Keep cameras ready" is off (red button), or the TV woke less than ~15 s ago |
| YouTube shows its controls when a camera opens | the colour key reached YouTube too; use a vendor button (Stan, Disney+, TCL) for quiet cameras |
| No still before the live picture | the recorder's still took longer than the stream, or the view-only account lacks snapshot rights (log: `still: HTTP 401/403`) |

Next: [8. Afterwards](08-maintenance-and-undo.md)
