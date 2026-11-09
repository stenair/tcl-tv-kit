# AGENTS.md: instructions for an AI agent setting up a TV with this kit

You are helping the owner turn their TCL Google TV into the set-up this kit describes. The guide in `docs/` is the
procedure; this file is how to run it. Read `README.md`, then `docs/09-learnings.md` (it saves hours), then follow
`docs/01` … `docs/08` in order. `docs/run-sheet.md` is the checklist to keep current as you go.

The kit was built on one TCL 75C755. The owner's TV may be a different size or a close model: **verify every fact
on their TV before relying on it** (package names, button codes, input IDs, firmware). The guide marks each fact
with how to check it.

## Rules that are not negotiable

0. **Verify the versions first.** This kit was built for one TV: TCL 75C755, firmware
   `tcl9618-user 14 UTT2.250416.001 AU02` (Android 14, security patch 2025-12-05), CleanYourTV `04aec4b7` + launcher
   3.3, TV Buttons 2.2 (full table in `README.md`). Before any change, run `docs/01`'s comparison and show the owner
   what matches and what differs. **Where anything differs, don't apply that step from the kit as written:**
   re-verify its facts on this TV (package present and doing the same thing, the button's real code, the input's real
   ID, the TCL service present), adapt, and tell the owner. After a firmware update, re-check the same way.
1. **Never brick the TV; everything must be reversible.** Disable, never uninstall system packages
   (`pm disable-user --user 0`). No root, no flashing, no factory or test packages (never start `com.tcl.factory*`,
   `*factorymenu*`, `com.cvte.tv.hkcfac`, not even to look). Use upstream CleanYourTV's tools (`disable.ps1`,
   `restore.ps1`, `check.py`, `build-and-install.ps1`): they carry the safety checks. Don't hand-roll replacements.
2. **Back up before the first change** (`docs/02`), and keep the debloat list at one fixed path afterwards: the
   restore record is keyed to it.
3. **Ask before every change to the TV**, say what it does and how it is undone, and wait for a clear yes.
   Read-only checks don't need asking. Group changes into small batches with a check between each.
4. **Don't do disruptive steps while someone is using the TV** (reboots, Home changes, app installs, switching
   inputs). Ask first; when the owner says the TV is busy, stop and wait.
5. **Keep Google TV's home until the new launcher is proven.** The order in `docs/05` is deliberate: the screensaver
   first, then the setup wizard (check its Wi-Fi ownership first), then `build-and-install.ps1`, which verifies Home and
   rolls back on its own.
6. **Passwords are the owner's.** Never type, store, log or repeat a password: not the camera recorder's, not
   Wi-Fi's, not an account's. The camera login is typed by the owner on the TV's own setup screen. If the owner
   pastes one into chat, don't use it, and suggest changing it.
7. **Privacy.** Never commit or paste serial numbers, Android IDs, MAC addresses, Wi-Fi names, IP addresses of the
   owner's network, account names or camera images. Backups, screenshots and notes stay in CleanYourTV's
   gitignored `backup/`, `shots/` and `work/` folders.
8. **Real presses prove buttons.** `input keyevent` sends press and release at the same instant, and
   `input keyevent --duration` doesn't hold keys on these TVs. Anything about held keys, the gear or Back must be
   confirmed by the owner pressing the real remote, ideally while `tools/record_keys.py` records.
9. **Report honestly.** If a check fails or a step was skipped, say so, with the evidence.

## Working agreement

- **Log every change** in `CleanYourTV/work/<tv>/CHANGES.md`: what changed, the original value, and the undo
  command. Use `docs/08`'s undo table as the template.
- **Ask the owner up front**, in one checklist: which streaming apps they use and want as the 4 favourites; whether
  they cast (Chromecast), use AirPlay/Miracast, Alexa, the Assistant mic, TCL Channel, live TV with an antenna, a
  games console (which HDMI), a camera system (brand/app; on which HDMI), the colour buttons, Stan/Disney+ buttons.
  Their answers decide the debloat list, the theme and the button mappings.
- **One stage per session is fine.** Each stage ends in a working TV; stop at any stage boundary.
- **After each stage:** screenshot (`tools/tv.py --screenshot`), quick checks, and tick the run sheet.

## Facts that cost us hours (details in docs/09)

- **Volume:** any accessibility service that *filters key events* turns held volume into single steps on this
  firmware. That's why TV Buttons reads the button log instead of intercepting keys. Never enable a key-filtering
  helper (`InputKeyService`, `HomeKeyService`, `app_keys`) to map buttons.
- **TV Buttons needs USB debugging to stay ON** (port 5555); it connects to the TV's own ADB. Its first connection
  shows an "Allow USB debugging?" prompt on the TV, and the owner must tick "Always allow". If no prompt appears,
  adbd may have a stuck pending prompt: Network/Wireless debugging off, USB debugging off and on, and the queued
  prompts appear.
- **Long log reads over the network time out.** Record on the TV to a file (`setsid nohup sh -c 'timeout N logcat
  … > /data/local/tmp/x.log' >/dev/null 2>&1 </dev/null & sleep 2`; without the `sleep 2` the job dies before it
  detaches), read it once, delete it (`tools/record_keys.py` does this). `pkill -f '<pattern>'`
  inside a `tv.py` command matches its own command line and kills itself: use `pkill -f 'logca[t]'`.
- **Guest Wi-Fi isolates the TV** from wired devices (camera recorders) even with the same address range: the TV
  must be on the main network for the camera overlay.
- **The TV's IP changes after standby** (DHCP). Find it by MAC (`Get-NetNeighbor -LinkLayerAddress …`) or in the
  router app; suggest a DHCP reservation.

## Windows tooling (the kit was run from Windows 11)

- Run upstream's tools from the `CleanYourTV` folder: `tools\venv\Scripts\python.exe -I tools\tv.py "<cmd>"`.
- Windows PowerShell 5.1 drops inner double quotes from native-command arguments. For TV commands with quotes, use
  Git Bash (with `MSYS_NO_PATHCONV=1`, or it rewrites `/data/...` and `/sdcard/...` paths) or `tv.py --batch` with
  stdin.
- Building is optional (the APKs are in `apks/`). If you build: JDK 17 and Android SDK platform 34 + build-tools
  34.0.0. If Gradle or sdkmanager fails with "Unable to establish loopback connection", set
  `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\<short path outside the user profile>`. Quote sdkmanager
  packages as `'"platforms;android-34"'` in PowerShell.
- A rebuilt APK is signed with *your* PC's debug key and can't update an install signed by another key. Read
  `apks/README.md` before replacing an installed app; never uninstall the launcher while it is Home.
