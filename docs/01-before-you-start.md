# 1. Before you start

## What you need

- **A Windows PC** on the same home network as the TV. (CleanYourTV's tools also run on macOS/Linux; this kit's
  commands are written for Windows PowerShell.)
- **Git** and **Python 3** (any 3.x, from python.org; tick "Add to PATH").
- **The TV's remote**, and a stretch of time when nobody needs the TV.
- **Not needed** unless you want to rebuild the apps: Java and the Android SDK (see the end of this page).

Both the PC and the TV must be on your **main** network, not a guest Wi-Fi. Guest networks wall devices off from
each other (and from wired devices such as a camera recorder), even when the addresses look alike.

## Folder layout

Put the kit and upstream CleanYourTV side by side in any folder (here `C:\TV`). Every command in this guide runs
from the `CleanYourTV` folder and reaches the kit as `..\tcl-tv-kit`.

```text
C:\TV\
  CleanYourTV\     upstream repo + our launcher patches; its tools do the safe work; your backups and notes live here
  tcl-tv-kit\      this kit
```

## Get CleanYourTV, pinned to the version this kit was built on

```powershell
cd C:\TV
git clone https://github.com/Gathin23/cleanyourtv CleanYourTV
cd CleanYourTV
git checkout -b local/tcl-c755 04aec4b7
git am (Get-ChildItem ..\tcl-tv-kit\cleanyourtv\patches\*.patch)
python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt
```

`04aec4b7` is the upstream commit the kit was tested against; the two patches add our launcher changes
([what they do](../cleanyourtv/README.md)). `git am` needs `git config user.name` / `user.email` set. A newer
upstream probably works too, but the patches may then need adjusting: stay on the pin unless you have a reason.

CleanYourTV's own guide (`README.md`, `PROMPT.md`, `docs/`) is worth a skim; its safety rules apply here too.

## Turn on debugging on the TV

1. **Settings → System → About**, scroll to **Android TV OS build**, press OK on it 7 times ("You are now a
   developer").
2. **Settings → System → Developer options → USB debugging: On.** On this TCL that alone opens the network
   debugging port (5555). **Wireless debugging** ("Network debugging") is *not* needed; leave it off.
3. Find the TV's address: **Settings → Network & Internet** → your network (or your router's app).

USB debugging stays on afterwards: TV Buttons needs it. That's a trade-off: port 5555 is open on your home
network, but the TV only accepts computers you approved on screen ([details](06-remote-buttons.md#security)).
**Never approve a debugging prompt you didn't start.**

## First connection

```powershell
$env:TV_HOST = '<tv ip>'            # e.g. 192.168.1.50; set it in every new PowerShell window
tools\venv\Scripts\python.exe -I tools\tv.py --probe
```

The TV shows **"Allow USB debugging?"**: tick **Always allow from this computer**, then OK. `--probe` prints the
model, Android version and firmware.

**No prompt, and the connection is refused?** adbd sometimes holds a stuck prompt ("prompt currently pending"):
switch **Wireless debugging off**, **USB debugging off and on again**, and the queued prompt(s) appear. Approve.

## Check your TV against ours

> [!IMPORTANT]
> Everything in this kit was verified on **one** TV: a TCL 75C755 on firmware `tcl9618-user 14 UTT2.250416.001 AU02`
> (Android 14, security patch 2025-12-05). Our lists, codes, IDs and APKs are *for those versions*. Compare yours now
> (all read-only) and keep the results: they decide how much of the kit applies as written.

| If yours… | Then |
|---|---|
| matches every row | follow the guide as written |
| is another C755 size, or the same firmware family with another build/region | follow the guide, but check each fact it marks (packages in step 3, codes in step 6, input IDs in step 4) before using it |
| is a different model or Android version | use the guide as a method, start from upstream's `examples/` list for your model, and verify every step's facts |
| got a firmware update later | re-run this comparison and step 8's after-update checks |

The rows to compare:

| Fact | Ours | Check yours |
|---|---|---|
| Model, firmware | TCL 75C755 (`G08`), Google TV 14, `tcl9618-user 14 UTT2.250416.001 AU02` | `tv.py --probe` |
| Home today | `com.google.android.apps.tv.launcherx` (Google TV) | `tv.py "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"` |
| HDMI input IDs | `HW15`…`HW18` = HDMI 1…4 | `tv.py "dumpsys tv_input \| grep -oE 'inputId=[^ ,]*HW[0-9]+' \| sort -u"` and `docs/04` |
| TCL Channel input | `com.tcl.channelplus/com.tcl.iptv.service.InputService` | same command, look for `channelplus` |
| TCL quick-settings bar | `com.tcl.settings.ShowWindowService` in TV Settings | `tv.py "dumpsys package com.android.tv.settings \| grep -c ShowWindowService"` (≥ 1) |
| The firmware logs every key press | yes (TV Buttons depends on it) | `tools/record_keys.py` in [docs/06](06-remote-buttons.md) |
| Display | 3840×2160 panel, 1920×1080 UI, density 320 (factory, don't change) | `tv.py "wm size; wm density"` |

Upstream CleanYourTV's `examples/` folder has tested lists for many TCL models; `examples/tcl-c755-google-tv` is the
one this kit's list grew from. If your firmware differs, the package list in step 3 is still checked package by
package before anything is disabled.

## Windows quirks we hit

- **PowerShell 5.1 drops inner double quotes** from arguments to `python`, so a TV command such as
  `tv.py "settings put secure x \"a b\""` arrives broken. Use single quotes inside, `tv.py --batch` with commands on
  stdin, or Git Bash.
- **Git Bash rewrites `/data/...` and `/sdcard/...`** into Windows paths. Prefix commands with
  `MSYS_NO_PATHCONV=1`.
- **The TV's address changes after standby** (DHCP). Reserve it in the router, or find it again by MAC:
  `Get-NetNeighbor | Where-Object LinkLayerAddress -like '<first 3 bytes of the TV's MAC>*'`.

## Only if you want to rebuild the apps

The APKs in `apks/` are ready to install. To rebuild them: **Microsoft OpenJDK 17**, and the **Android SDK
command-line tools** with `platforms;android-34` and `build-tools;34.0.0` (in PowerShell, quote them:
`sdkmanager '"platforms;android-34"' '"build-tools;34.0.0"'`).

If Gradle or sdkmanager fails with **"Unable to establish loopback connection"**, Java can't create its Unix socket
files under your user profile. Point them elsewhere for the session:
`$env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir=C:\java-sockets'` (create the folder first).

Next: [2. Back up the original state](02-backup.md)
