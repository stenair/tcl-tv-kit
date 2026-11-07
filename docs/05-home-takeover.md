# 5. Make it Home

Until now Google TV is still Home. This step makes the new launcher Home and switches off Google TV's home, its
promo screensaver and the setup wizard. **Do it when the TV is free**: it restarts once.

Before you start: steps 3 and 4 done, the theme pushed, the tiles arranged, and the launcher opens fine as an app.

## The order (it matters)

### 1. Screensaver: the launcher's clock instead of Google's

Google's screensaver (`dreamx`) shows promos; it is disabled below, so point the screensaver elsewhere first:

```powershell
tools\venv\Scripts\python.exe -I tools\tv.py "settings get secure screensaver_components"   # note it (ours: com.google.android.apps.tv.dreamx/.service.Backdrop)
tools\venv\Scripts\python.exe -I tools\tv.py "settings put secure screensaver_components com.gathin.tvhome/.ClockDream"
```

### 2. Check the setup wizard doesn't own your Wi-Fi

Google TV's setup wizard (`setupwraith`) claims Home once Google TV's home is off, so it gets disabled too. But **if
the wizard created the Wi-Fi network the TV is using, disabling it deletes that network and the TV drops off
Wi-Fi** (and off your PC). Check which app owns each saved network:

```powershell
tools\venv\Scripts\python.exe -I tools\tv.py "cmd wifi list-networks; dumpsys wifi | grep -o 'cname=[^ ]*' | sort | uniq -c"
```

- Every network owned by `android.uid.system` (joined through TV Settings): fine.
- A network owned by `com.google.android.tungsten.setupwraith`:
  - **if it's the network in use**, re-join your Wi-Fi from TV Settings (that makes it system-owned) and check again;
  - **if it's an old unused one** (ours: a hidden network saved during first setup, ID 0), forget it:
    `tv.py "cmd wifi forget-network <id>"`.

`disable.ps1` checks this too and refuses while the wizard owns the connected network.

### 3. Disable the screensaver app and the setup wizard

In `work\my-tv\debloat.txt`, uncomment the two Batch 6 lines (`dreamx`, `setupwraith`), then:

```powershell
powershell -ExecutionPolicy Bypass -File tools\disable.ps1 -TvHost $env:TV_HOST work\my-tv\debloat.txt
```

### 4. Hand Home to the new launcher

```powershell
$env:SKIP_BUILD = '1'     # uses launcher\app\build\outputs\apk\release\app-release.apk from step 4
powershell -ExecutionPolicy Bypass -File launcher\build-and-install.ps1 -TvHost $env:TV_HOST
```

The script starts the new launcher, disables Google TV's home (`launcherx`), sets the new Home, then checks where
Home landed, and **puts Google TV back on its own** if it isn't the new launcher. It must end with
`Home now resolves to com.gathin.tvhome/.MainActivity` and `Installed.` Press Home on the remote.

### 5. Restart and check everything

```powershell
tools\venv\Scripts\python.exe -I tools\tv.py reboot
```

After about a minute:

| Check | Expected |
|---|---|
| What the TV shows after starting | the new Home |
| `tv.py "cmd role get-role-holders --user 0 android.app.role.HOME"` | `com.gathin.tvhome` |
| `tools\check.py work\my-tv\debloat.txt` | nothing drifted |
| Mic button: ask "what's the weather" | the Assistant answers |
| Cast a YouTube video from a phone **on the same network** | it plays |
| Input button, HDMI tiles, TCL Channel tile | they switch |
| Settings pill → Accounts and Profiles | opens, lists the accounts |

## What changes when Google TV's home goes

| Thing | After the takeover | Fixed in |
|---|---|---|
| YouTube, Prime Video, gear buttons | **do nothing** (Google TV's home handled them) | step 6 |
| Netflix button | **does nothing** (TCL routes it through `ttvs`, disabled in step 3) | step 6 |
| TCL button | tries TCL's disabled recommendation app, so nothing | step 6 |
| Gear | Google TV's side panel is gone (it was part of Google's home) | step 6: TCL's own quick-settings bar |
| Input button | still TCL's source bar. It shows TCL Channel promos in its "Apps" row; it's a system key, so it can't be remapped. We left it. | — |
| Profiles, Google TV's watchlist/recommendations | gone with Google TV's home | — |

On ours, after the restart: boots into the new Home, Wi-Fi and Chromecast fine, Assistant answers, casting works.

## Undo

`restore.ps1 … -Stock com.google.android.apps.tv.launcherx` (step 8) re-enables everything and gives Home back to
Google TV; then put the screensaver back with the value you noted in step 1.

Next: [6. Remote buttons](06-remote-buttons.md)
