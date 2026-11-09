# Ready-built APKs

> [!WARNING]
> Built and tested for a TCL 75C755 on firmware `tcl9618-user 14 UTT2.250416.001 AU02` (Android 14). They install on
> any Android TV 8+ (minSdk 26), but what they *do* depends on that TV: TV Buttons' built-in mappings use its
> button codes and HDMI IDs, and its tricks rely on TCL firmware behaviour (key logging, the quick-settings
> service, log tags). On another model or firmware, verify first ([docs/01](../docs/01-before-you-start.md#check-your-tv-against-ours))
> and adapt the mappings ([docs/06](../docs/06-remote-buttons.md#your-own-buttons-no-rebuild-needed)).

| File | Package | Version | Built from |
|---|---|---|---|
| `cleanyourtv-launcher-3.3-tcl-c755.apk` | `com.gathin.tvhome` | 3.3 (versionCode 12) | upstream CleanYourTV `04aec4b7` + `../cleanyourtv/patches/` (the exact APK running on the 75C755 this kit was made on) |
| `tv-buttons-2.2.apk` | `local.tvbuttons` | 2.2 (versionCode 12) | `../tv-buttons/` on branch `main` (Media3 1.4.1 inside, Apache 2.0) |
| `tv-buttons-2.2-cameras.apk` | `local.tvbuttons` | 2.2-cameras (versionCode 12) | `../tv-buttons/` on branch `cameras-only`: the four colour buttons → cameras 1–4 only, Back guard off ([cameras-only install](../docs/cameras-only.md)) |

Check them with `SHA256SUMS.txt` (`Get-FileHash -Algorithm SHA256 <file>` in PowerShell).

**Signing:** both are signed with an Android **debug** certificate (`CN=Android Debug`, SHA-256
`92dbaeef9030f0834ce8d86b325d45317e7de1af175ea98a7d272c5a4b92f2f5`) from the PC that built them. That's normal for
sideloaded personal apps. One consequence: an APK you build yourself is signed with *your* debug key and **can't
update** these installs. To switch keys, uninstall first, and for the launcher only after Google TV is Home again
([docs/08](../docs/08-maintenance-and-undo.md#updating-the-apps-later)). The signing key itself is not in this repo,
and never should be.

How to install each: the launcher in [docs/04](../docs/04-launcher-and-theme.md#install-it-as-an-app-first-google-tv-stays-home)
and [docs/05](../docs/05-home-takeover.md), TV Buttons in [docs/06](../docs/06-remote-buttons.md#install-and-enable).
