# Theme: TCL 75C755 living room

A theme for the CleanYourTV launcher (`com.gathin.tvhome`): `config.json` plus the optional photos in
`wallpapers/`, pushed to the TV with CleanYourTV's `tools\theme.ps1`. Why it looks the way it does, and how to adapt
it: [docs/04](../docs/04-launcher-and-theme.md). It needs the launcher **with our patches** (`half_grid`, the two
banners): `../apks/cleanyourtv-launcher-3.3-tcl-c755.apk`.

## Push, reset

```powershell
# from the CleanYourTV folder
powershell -ExecutionPolicy Bypass -File tools\theme.ps1 -TvHost $env:TV_HOST ..\tcl-tv-kit\theme
powershell -ExecutionPolicy Bypass -File tools\theme.ps1 -TvHost $env:TV_HOST -Reset   # back to the built-in look
```

## Every key, and why

| Key | Value | Why |
|---|---|---|
| `dock_apps` | Netflix, YouTube, Stremio, TiviMate | the four favourites, as big tiles (edit for your household) |
| `fill_dock` | `false` | the dock holds exactly those four; moving one down leaves an empty dock slot until another moves up |
| `columns`, `rows_below_dock`, `half_grid` | 4, 1, `true` | one row of 8 half-size tiles under the dock, so everything fits on one screen (`half_grid` is our patch) |
| `background_color` | `#0B0D10` | near-black |
| `cinematic_home`, `cinematic_dots`, `cinematic_white_focus` | `true`, `false`, `true` | the focused favourite's colour glows at the top with its name in large type; white glass focus |
| `dock_title`, `show_greeting` | `true`, `false` | the big name of the focused app; no "Good evening" |
| `always_show_names` | `true` | names under every tile, dimmed until focused |
| `glass_style`, `focus_overshoot`, `advanced_effects`, `fade_rows` | `glass`, `false`, `true`, `true` | glass tiles, focus glides without bouncing, slight lift and zoom |
| `wallpaper`, `wallpaper_dim`, `wallpaper_true_color` | `false`, 25, `true` | glow by default; the photos are an on-TV choice (Launcher settings → Wallpaper) |
| `app_library`, `library_ok_opens`, `library_button`, `show_library_pill` | `true`, `true`, `true`, `false` | every other app behind the grid button in the top bar; OK opens any of them |
| `hidden_apps` | regional apps not used, Settings, Play Store, `local.tvbuttons` | kept off Home, still in the App Library (TV Buttons' setup screen lives there) |
| `app_labels` | `com.tcl.tv=TCL Channel`, `com.tcl.suspension=PlayStation 5`, `com.tcl.dashboard=Cameras` | names for the three input tiles |
| `app_banners` | `com.tcl.suspension=banner_console`, `com.tcl.dashboard=banner_cameras` | our patch's artwork for them |
| `launch_actions` | PS5 → `…/HW18` (HDMI 4), Cameras → `…/HW16` (HDMI 2), TCL Channel → its input | one press switches input. **Check the HW IDs on your TV** |
| `live_tv_package` | `com.tcl.tv` | the only app that plays HDMI/TV inputs here |
| `inputs_connected_only` | `true` | the Inputs pill lists only connected devices |
| `wifi_status_color`, `wifi_status_warnings` | `true`, `true` | the Wi-Fi pill turns orange/red when the internet or Wi-Fi is down |
| `clock_format` | `system` | follows the TV's 12/24-hour setting |

## On the TV

| Want | How |
|---|---|
| A photo instead of the glow | Settings pill, hold OK → Launcher settings → Wallpaper (NASA, Earth from orbit; credits in `CREDITS.md`). "Theme default" returns to the glow |
| Reorder tiles | Hold OK on a tile → Move, arrows, OK. Many: Settings → Reorder apps |
| Less motion | Launcher settings → advanced effects off |
| Undo every on-TV choice | Launcher settings → Follow theme |
