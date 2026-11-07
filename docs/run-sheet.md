# Run sheet

Tick as you go. Each section ends with a working TV, so you can stop between any two.

## 1. Before you start ([guide](01-before-you-start.md))
- [ ] Kit and CleanYourTV side by side; CleanYourTV at `04aec4b7` with the two patches applied; venv installed
- [ ] TV: Developer options unlocked, **USB debugging on**, Wireless debugging off
- [ ] PC and TV on the **main** network (not guest Wi-Fi); TV address noted (and reserved in the router)
- [ ] `tv.py --probe` works; the PC's key approved on the TV ("Always allow")
- [ ] **Your TV compared with ours** (model, firmware build, Android version, Home, HDMI IDs, TCL Channel input,
      quick-settings service); every difference noted, and the steps it affects marked "verify first"

## 2. Backup ([guide](02-backup.md))
- [ ] `backup_tv.py backup\<date>-original`: 19 captures, all `exit=0`
- [ ] `shots\00-before-home.png`
- [ ] `work\my-tv\CHANGES.md` started

## 3. Debloat ([guide](03-debloat.md))
- [ ] Household questions answered; `work\my-tv\debloat.txt` edited
- [ ] Missing-package check prints nothing (or the differences are understood)
- [ ] Batch 1 → check · Batch 2 → check · Batch 3 → check · Batch 4 → cast still works · Batch 5 → TCL Channel plays
- [ ] Restart → `check.py`: nothing drifted
- [ ] Automatic updates off (if wanted)

## 4. Launcher and theme ([guide](04-launcher-and-theme.md))
- [ ] Launcher installed as an app (`-StockLauncher none`), Home role given back to Google TV
- [ ] Theme edited (favourites, hidden apps, input tiles and their HDMI IDs) and pushed
- [ ] Tiles arranged on the TV; PS5/Cameras/TCL Channel tiles switch correctly

## 5. Home takeover ([guide](05-home-takeover.md)), TV free, one restart
- [ ] Screensaver → `com.gathin.tvhome/.ClockDream` (original value noted)
- [ ] Setup wizard owns no network in use (forget an unused one)
- [ ] Batch 6 (`dreamx`, `setupwraith`) applied
- [ ] `build-and-install.ps1`: "Home now resolves to com.gathin.tvhome…"
- [ ] Restart: new Home at boot · `check.py` clean · Assistant answers · casting works · inputs switch

## 6. Remote buttons ([guide](06-remote-buttons.md))
- [ ] TV Buttons installed; appops; accessibility on; **its prompt approved** ("Always allow")
- [ ] `capabilities=0` and "watching the button log"
- [ ] `record_keys.py` run on the real remote; codes compared with ours
- [ ] `mappings.txt` pushed (if anything differs)
- [ ] Real presses: YouTube · Prime · Netflix · gear (bar opens, gear/Back closes) · TCL · console button · camera-HDMI button
- [ ] Held volume ramps; held arrows scroll
- [ ] YouTube → gear → Settings → Back returns to YouTube

## 7. Camera overlay ([guide](07-camera-overlay.md))
- [ ] Recorder address found (recorder menu → Network → TCP/IP) and reserved
- [ ] View-only account on the recorder
- [ ] (Optional) VLC plays `rtsp://<ip>:554/cam/realmonitor?channel=1&subtype=1`
- [ ] App Library → TV Buttons: address, login (typed by the owner), camera names; Try works for each
- [ ] Each camera button at Home, over a streaming app, over HDMI; same button closes

## 8. Wrap-up ([guide](08-maintenance-and-undo.md))
- [ ] `CHANGES.md` complete with undo commands
- [ ] Final restart: everything above still works
- [ ] Owner knows: the restore command, the after-update routine, never approve unknown debugging prompts
