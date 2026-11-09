# 8. Afterwards: firmware updates, undo, maintenance

## After every firmware update

Automatic updates are off (step 3), so you choose when. After installing one:

```powershell
tools\venv\Scripts\python.exe -I tools\check.py work\my-tv\debloat.txt          # what the update re-enabled
tools\venv\Scripts\python.exe -I tools\check.py --fix work\my-tv\debloat.txt com.gathin.tvhome   # re-apply, Home back to the launcher
```

Then **press every mapped button once** (YouTube, Prime, Netflix, gear, TCL, Disney+, Stan, the camera buttons) and
open a camera. TV Buttons depends on TCL logging key presses, TCL's quick-settings service and the log tags listed
in `tv-buttons/README.md`. If a button stopped working, run `tools/record_keys.py` and compare.

## If the TV's address changes

It's DHCP, and changed on ours after a standby. Find the TV by MAC (`Get-NetNeighbor`) or in the router app, and
reserve the address in the router (the camera recorder's too). TV Buttons isn't affected: it talks to the TV itself
(`127.0.0.1`).

## Everything that was changed, and its undo

Copy this table into `work\my-tv\CHANGES.md` and keep it current. Commands run from the CleanYourTV folder;
`tv.py` means `tools\venv\Scripts\python.exe -I tools\tv.py`.

| # | Change | Undo |
|---|---|---|
| 1 | **31 packages disabled** from `work\my-tv\debloat.txt` (batches 1–6) | restore command below, or TV Settings → Apps → See all → Show system apps → enable |
| 2 | **CleanYourTV launcher** installed and **made Home**; **Google TV's home (`launcherx`) disabled** by `build-and-install.ps1` | the restore command below gives Home back to Google TV. Uninstall the launcher **only after** that |
| 3 | **Theme** pushed (`theme.ps1`), tile order set on the TV | `tools\theme.ps1 -TvHost $env:TV_HOST -Reset` |
| 4 | **Screensaver** = the launcher's clock (`screensaver_components=com.gathin.tvhome/.ClockDream`; ours was `com.google.android.apps.tv.dreamx/.service.Backdrop`) | after the restore: `tv.py "settings put secure screensaver_components <original value>"` |
| 5 | **Automatic system updates off** (`ota_disable_automatic_update=1`, originally unset) | `tv.py "settings delete global ota_disable_automatic_update"` |
| 6 | **TV Buttons** installed; its accessibility service on (`enabled_accessibility_services=local.tvbuttons/.ButtonService`, `accessibility_enabled=1`, originally empty and 0); TCL auto-start allowed; its debugging key approved | `tv.py "settings put secure enabled_accessibility_services ''; settings put secure accessibility_enabled 0"`, then TV Settings → Apps → TV Buttons → Uninstall; Developer options → Revoke USB debugging authorisations |
| 7 | **`mappings.txt`** pushed (if you made one) | `tv.py "rm /sdcard/Android/data/local.tvbuttons/files/mappings.txt"` |
| 8 | **Camera login** stored in TV Buttons (if used); a view-only `tv` account on the recorder | clear the password on the setup screen or uninstall TV Buttons; delete the account on the recorder |
| 8b | **"Keep cameras ready"** on (if wanted): two camera streams decode in the background while the TV is on | red button, or App Library → TV Buttons → switch off. Uninstalling TV Buttons removes it |
| 9 | **USB debugging on** (needed by TV Buttons) | Developer options. Turning it off stops every TV Buttons button and the camera overlay |
| 10 | **Wi-Fi:** an unused setup-wizard network forgotten (if you had one) | rejoin it in TV Settings |
| 11 | **Network:** TV (and PC) moved off guest Wi-Fi (if they were on it) | TV Settings → Network & Internet |

### Back to the original in one go

```powershell
powershell -ExecutionPolicy Bypass -File tools\restore.ps1 -TvHost $env:TV_HOST work\my-tv\debloat.txt -Stock com.google.android.apps.tv.launcherx
```

It re-enables every package from the list and Google TV's home, and gives Home back to Google TV. Then rows 4–9 as
wanted. Without a computer: TV Settings → Apps → See all apps → Show system apps → enable each. Last resort: a factory
reset re-enables everything (nothing was uninstalled) but erases accounts and apps.

## Updating the apps later

- **Same PC that built/installed them:** `pm install -r` over the old one (a higher `versionCode` for TV Buttons:
  2.2 is `12`). Settings, the camera login and the debugging approval survive.
- **Signing.** The APKs in `apks/` are signed with the debug key of the PC that built them. **An APK signed with a
  different key can't update an installed app.** Install fails with a signature error; nothing breaks. To switch:
  - *TV Buttons:* uninstall it, install yours, redo the appops/accessibility commands, approve its prompt, re-enter
    the camera login.
  - *Launcher:* **never uninstall it while it is Home** (no Home = a stuck TV). First run the restore command (Google
    TV becomes Home), then uninstall, install yours, then step 5 again.
- **Back up your PC's key** (`%USERPROFILE%\.android\debug.keystore`) if you build, and never commit it.

## Debugging and security, long term

USB debugging stays on for TV Buttons (port 5555 accepts only approved keys). Keep **Wireless debugging off**. If the
TV ever shows a debugging prompt you didn't start, decline it. CleanYourTV's `tools/adb-closed.py <tv ip>` checks
whether 5555 is closed, if you ever decide to drop TV Buttons and close it.
