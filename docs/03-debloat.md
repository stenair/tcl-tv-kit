# 3. Remove the ads and bloat

The list is [`cleanyourtv/debloat-tcl-75c755.txt`](../cleanyourtv/debloat-tcl-75c755.txt): 29 packages in five
batches, each line with its reason, plus two more that wait for step 5. It disables; it never uninstalls.

## 1. Decide with the household

Ask these before editing the list. Our answers are in brackets.

| Question | If yes, keep ON |
|---|---|
| Do you cast from phones? (yes) | `com.google.android.apps.mediashell` (it isn't on the list; just never add it) |
| AirPlay from iPhones, or Miracast screen mirroring? (no) | remove the Batch 4 lines you need |
| Alexa / Echo controls the TV? (wasn't important) | then keep **both** `com.tcl.ttvs` and `com.tcl.smartalexa`: Alexa fails with ttvs off, and ttvs is also Samba TV's viewing tracker. Your call. |
| Google Assistant on the mic button? (yes) | `katniss` and `tv.remote.service` are never on the list |
| TCL Channel free live channels? (yes) | `com.tcl.channelplus` is never on the list |
| A TCL soundbar? (no) | remove `com.tcl.UpdatePeripheral` |
| Photos/videos from USB sticks? (no) | remove `com.tcl.ui_mediaCenter` and `com.tcl.gallery` |
| Stan, YouTube Music, Play Games? (no) | remove their lines |
| An aerial antenna for live TV? (no) | nothing to change: `com.tcl.tv` stays (it is also the HDMI player) |

## 2. Put the list in place and check it fits your TV

```powershell
mkdir work\my-tv
copy ..\tcl-tv-kit\cleanyourtv\debloat-tcl-75c755.txt work\my-tv\debloat.txt
notepad work\my-tv\debloat.txt            # apply the answers above

# Every listed package that is NOT installed on your TV (should print nothing):
$installed = Get-Content backup\<date>-original\_installed-names.txt
Get-Content work\my-tv\debloat.txt | ForEach-Object { ($_ -split '#')[0].Trim() } | Where-Object { $_ } |
    Where-Object { $installed -notcontains $_ }
```

A missing package is harmless (the tool skips it), but it means your firmware differs from ours: look at what else
differs before going on. **Don't add packages to the list** that aren't explained in it or in upstream's
`examples/` for your model. CleanYourTV's tools refuse anything on their protected list anyway.

## 3. Apply in batches

Prefix the batches you haven't reached with `#pending ` (so the file holds only Batch 1 active), then:

```powershell
powershell -ExecutionPolicy Bypass -File tools\disable.ps1 -TvHost $env:TV_HOST work\my-tv\debloat.txt
tools\venv\Scripts\python.exe -I tools\tv.py --screenshot shots\01-after-batch1.png HOME
```

The first run writes the undo record (step 2). Check that Home still works, then remove `#pending ` from the next
batch and run the same command again. Packages that are already disabled are skipped, and the record grows with
each run.

| After | Check |
|---|---|
| Batch 1 (promos) | Home opens; the "TCL" tab content and sponsored pushes are gone or going |
| Batch 2 (tracking) | nothing visible |
| Batch 3 (preloads) | the removed apps are gone from Apps |
| Batch 4 (mirroring) | casting from a phone still works (Chromecast) |
| Batch 5 (`ttvs`) | **TCL Channel plays**; HDMI inputs switch; Alexa (if kept) responds |

## 4. Restart and check nothing came back

```powershell
tools\venv\Scripts\python.exe -I tools\tv.py reboot
# wait ~60 s, then
tools\venv\Scripts\python.exe -I tools\check.py work\my-tv\debloat.txt
```

`check.py` must report nothing drifted. On ours, every package stayed disabled after the restart, Chromecast still
advertised, all four HDMI inputs, the TCL Channel input and the PS5 (CEC) were still registered,
and nothing crashed.

## 5. Firmware updates by hand (our choice)

A firmware update can re-enable packages and change how buttons and logs behave. We turned automatic updates off
and install them deliberately, then re-check (step 8):

```powershell
tools\venv\Scripts\python.exe -I tools\tv.py "settings put global ota_disable_automatic_update 1"
# undo: tv.py "settings delete global ota_disable_automatic_update"   (or Developer options -> Automatic system updates)
```

The security patch on ours was 2025-12-05. Check for updates now and then (Settings → System → About → System
update), install, then run step 8's after-update routine.

## Why these decisions

- **`com.tcl.interactive` stays on.** We disabled it at first (it's TCL's HbbTV "TIAS" engine, with internet access,
  log access and overlay rights). The TV app (`com.tcl.tv`) then retried it 10 times a second, forever: about 1.2%
  of a core and ~150 log lines a second, which also drowned the log TV Buttons reads. Enabled, the TV app starts it
  only when TCL Channel or an input opens: about 0.2% of a core and 20 MB. It makes one call to TCL's cloud
  (`api.leiniao.com/init`), which TCL refused with a 403. HbbTV needs an antenna, so nothing is gained or lost
  either way.
- **`ttvs` goes.** It is Samba TV's automatic content recognition (it identifies what's on screen for advertising),
  TCL's analytics and TCL's own updater. Google's updater stays, so firmware updates still work. Its cost was Alexa.
- **The Netflix button stops working after Batch 5**, because TCL routes it through `ttvs`. Step 6 brings it back.
- **The TCL button** opened TCL's recommendation browser (`waterfall`, Batch 1). Step 6 points it at TCL Channel.

Next: [4. The new Home screen](04-launcher-and-theme.md)
