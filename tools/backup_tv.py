"""Read-only backup of a TV's original state, before the first change.

Run from the CleanYourTV folder (it uses that repo's tools/tv.py and venv), with TV_HOST set:

    tools\\venv\\Scripts\\python.exe -I ..\\tcl-tv-kit\\tools\\backup_tv.py backup\\2026-11-05-original

Every capture is a read-only shell command (package lists, settings, properties, Home owner, inputs, Wi-Fi owners).
The folder ends with _log.txt (exit code and line count per capture) and SHA256SUMS.txt. It holds identifiers
(serial, Android ID, Wi-Fi details): CleanYourTV's .gitignore already keeps backup/ out of git; never share it.
"""
import hashlib
import os
import subprocess
import sys

CAPTURES = [
    ("packages-all-with-paths.txt", "pm list packages -f"),
    ("packages-enabled.txt", "pm list packages -e"),
    ("packages-disabled.txt", "pm list packages -d"),
    ("packages-system.txt", "pm list packages -s"),
    ("packages-thirdparty.txt", "pm list packages -3"),
    ("packages-incl-uninstalled.txt", "pm list packages -u"),
    ("packages-versions.txt", "pm list packages --show-versioncode"),
    ("users.txt", "pm list users"),
    ("getprop.txt", "getprop"),
    ("settings-global.txt", "settings list global"),
    ("settings-secure.txt", "settings list secure"),
    ("settings-system.txt", "settings list system"),
    ("home.txt", "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME;"
                 " echo; echo role:; cmd role get-role-holders --user 0 android.app.role.HOME;"
                 " echo; echo claimants:; cmd package query-activities --brief -a android.intent.action.MAIN"
                 " -c android.intent.category.HOME"),
    ("activity-now.txt", "dumpsys activity activities | grep -E 'mFocusedApp|topResumedActivity'"),
    ("wifi-owner.txt", "cmd wifi list-networks; echo; dumpsys wifi | grep -o 'cname=[^ ]*' | sort | uniq -c"),
    ("display.txt", "wm size; wm density"),
    ("tv-inputs.txt", "dumpsys tv_input"),
    ("hdmi-cec.txt", "dumpsys hdmi_control"),
    ("accessibility-and-anim.txt",
     "for k in enabled_accessibility_services accessibility_enabled screensaver_components screensaver_enabled;"
     " do echo \"secure $k=$(settings get secure $k)\"; done;"
     " for k in window_animation_scale transition_animation_scale animator_duration_scale private_dns_mode"
     " ota_disable_automatic_update adb_wifi_enabled; do echo \"global $k=$(settings get global $k)\"; done"),
]


def main():
    if len(sys.argv) != 2 or sys.argv[1] in ("-h", "--help"):
        sys.exit(__doc__)
    out = sys.argv[1]
    tv = os.path.join("tools", "tv.py")
    if not os.path.isfile(tv):
        sys.exit("run this from the CleanYourTV folder (tools/tv.py not found here)")
    if not os.environ.get("TV_HOST"):
        sys.exit("set TV_HOST to the TV's IP address first")
    if os.path.isdir(out) and os.listdir(out):
        sys.exit(f"{out} already exists and is not empty: a backup is never overwritten")
    os.makedirs(out, exist_ok=True)

    log = []
    for name, cmd in CAPTURES:
        try:
            r = subprocess.run([sys.executable, "-I", tv, cmd], capture_output=True, text=True,
                               encoding="utf-8", errors="replace", timeout=180)
            text, code = r.stdout, r.returncode
        except subprocess.TimeoutExpired:
            text, code = "", "timeout"
        with open(os.path.join(out, name), "w", encoding="utf-8", newline="\n") as f:
            f.write(text)
        lines = text.count("\n")
        log.append(f"{name:<34} exit={code}  lines={lines}")
        print(log[-1])

    # Plain sorted package names, for quick comparisons later.
    with open(os.path.join(out, "packages-all-with-paths.txt"), encoding="utf-8") as f:
        names = sorted({l.strip().rsplit("=", 1)[-1] for l in f if l.startswith("package:")})
    with open(os.path.join(out, "_installed-names.txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(names) + "\n")
    with open(os.path.join(out, "_log.txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(log) + "\ndone\n")

    sums = []
    for name in sorted(os.listdir(out)):
        if name == "SHA256SUMS.txt":
            continue
        with open(os.path.join(out, name), "rb") as f:
            sums.append(f"{hashlib.sha256(f.read()).hexdigest()}  {name}")
    with open(os.path.join(out, "SHA256SUMS.txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(sums) + "\n")

    failed = [l for l in log if "exit=0 " not in l]
    print(f"\n{len(CAPTURES)} captures in {out}" + (f"; {len(failed)} FAILED - check _log.txt" if failed else ", all OK"))
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    main()
