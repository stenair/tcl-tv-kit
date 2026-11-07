# CleanYourTV: pin, patches, list

[CleanYourTV](https://github.com/Gathin23/cleanyourtv) (MIT, [licence](LICENSE-CleanYourTV)) does the safe work: its
tools disable packages with a backup record and protected-package checks, push themes, and install its launcher
with a Home check that rolls back on failure. This kit uses it unchanged except for the launcher patches below.

## Pin

Tested against upstream commit **`04aec4b7`** ("Round AS: fixes folded in on merge, tables and cards regenerated"),
launcher 3.3. Set up as in [docs/01](../docs/01-before-you-start.md#get-cleanyourtv-pinned-to-the-version-this-kit-was-built-on):

```powershell
git clone https://github.com/Gathin23/cleanyourtv CleanYourTV
cd CleanYourTV
git checkout -b local/tcl-c755 04aec4b7
git am (Get-ChildItem ..\tcl-tv-kit\cleanyourtv\patches\*.patch)
```

## Patches (launcher only; checked to apply cleanly on `04aec4b7`)

| Patch | Files | What |
|---|---|---|
| `0001-…banners-single-til….patch` | `res/drawable/banner_console.xml`, `banner_cameras.xml`, `MainActivity.java`, `strings.xml` | Controller and camera banners for input tiles (`app_banners`); a Move chosen from a tile's menu ends once the tile is placed (`move_one_hint`) |
| `0002-…half_grid….patch` | `Config.java`, `config.xml`, `AppAdapter.java`, `MainActivity.java`, new `HalfGridSlots.java` | Theme key `half_grid` (off by default): rows under the dock hold 2 × columns half-size tiles, dock tiles span two columns, dock-aware D-pad and reorder maths, dashed outlines in free slots |

Banners are single solid-fill vectors (gradients cost ~1 KB each, and the APK was 31 bytes under the gate before
`half_grid`). With `half_grid` the release APK is 590,965 bytes, 1,141 over upstream's 589,824-byte CI gate:
fine on a TV, but a PR would need trimming. All 314 launcher unit tests pass.

The prebuilt result is `../apks/cleanyourtv-launcher-3.3-tcl-c755.apk`. To build it yourself:
`cd launcher; .\gradlew.bat --no-daemon testReleaseUnitTest assembleRelease`.

## Debloat list

[`debloat-tcl-75c755.txt`](debloat-tcl-75c755.txt): 29 packages in five batches plus two for the Home takeover,
each with its reason, from upstream's `examples/tcl-c755-google-tv/debloat.txt` adjusted for one household
([decisions](../docs/03-debloat.md)). Copy it to `work\<your tv>\debloat.txt` and keep it there.
