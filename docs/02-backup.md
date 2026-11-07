# 2. Back up the original state

Before the first change, record exactly how the TV is now. Nothing here changes the TV.

```powershell
# from C:\TV\CleanYourTV, with $env:TV_HOST set
tools\venv\Scripts\python.exe -I ..\tcl-tv-kit\tools\backup_tv.py backup\2026-11-07-original
tools\venv\Scripts\python.exe -I tools\tv.py --screenshot shots\00-before-home.png
```

(Use today's date.) `backup_tv.py` saves 19 read-only captures plus `_log.txt` (exit code and line count of each,
all should be `exit=0`) and `SHA256SUMS.txt`:

| File | What it is |
|---|---|
| `packages-*.txt` | Every package: with APK paths, enabled, disabled, system, installed by you, including uninstalled, version codes |
| `_installed-names.txt` | Plain sorted package names, for comparisons |
| `getprop.txt`, `settings-global/secure/system.txt` | All system properties and Android settings |
| `home.txt` | Who owns Home now, the Home role, and every other Home claimant (the setup wizard is one) |
| `wifi-owner.txt` | Saved Wi-Fi networks and **which app created each**, which matters in step 5 |
| `tv-inputs.txt`, `hdmi-cec.txt` | HDMI input IDs and CEC devices, for tiles and buttons |
| `display.txt`, `accessibility-and-anim.txt`, `activity-now.txt`, `users.txt` | Display, accessibility services, animation scales, screensaver, Private DNS, update setting, what's on screen, users |

**This folder holds identifiers** (serial number, Android ID, Wi-Fi details). CleanYourTV's `.gitignore` keeps
`backup/` and `shots/` out of git. Don't share it.

On ours, the original state was: **0 packages disabled**, Home = Google TV (`launcherx`), no accessibility
services, screensaver = Google's `dreamx` Backdrop, animation scales 1.0, and two saved Wi-Fi networks (one created
by the setup wizard at first setup, see step 5).

## The undo record

The second safety net is written by CleanYourTV's `disable.ps1` itself: before its first change it saves the
pre-change state to `%USERPROFILE%\.cleanyourtv\recovery-<tv ip>-<hash>.json`, and `restore.ps1` replays it. The
hash is of the **list's path**, so pick the list's location in step 3 once and never move it. If the TV's address
changes later, a restore falls back to "enable every package on the list", which is the original state anyway.

Start your change log now: `work\<your tv>\CHANGES.md` in the CleanYourTV folder (gitignored), one row per change
with its undo. [Step 8](08-maintenance-and-undo.md) has the full table from our TV to copy.

Next: [3. Remove the ads and bloat](03-debloat.md)
