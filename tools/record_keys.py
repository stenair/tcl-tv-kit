"""Record which key codes the remote's buttons send, and what (if anything) each one opens.

Run from the CleanYourTV folder (it uses that repo's tools/tv.py and venv), with TV_HOST set, then press the buttons
on the REAL remote, one at a time, a few seconds apart:

    tools\\venv\\Scripts\\python.exe -I ..\\tcl-tv-kit\\tools\\record_keys.py 90

Why a file on the TV: TCL's logs run at ~230 lines a second, so a live read over the network times out or misses
lines, and the buffer forgets everything older than about a minute. The recording runs on the TV itself
(logcat to /data/local/tmp, stopped by `timeout`), is read once at the end and then deleted.

Injected keys (`input keyevent`) are no proof for TV Buttons: they have no gap between press and release, and some
buttons behave differently when held. Always press the real remote.
"""
import re
import subprocess
import sys
import time

FILE = "/data/local/tmp/tvkit-keys.log"
KEY = re.compile(r"^(\d\d-\d\d \S+) .*handleKeyBeforeQueueing event=KeyEvent \{ action=ACTION_DOWN, "
                 r"keyCode=([A-Za-z_0-9]+), scanCode=(\d+).*?repeatCount=0,")
START = re.compile(r"^(\d\d-\d\d \S+) .*START u0 \{(.*?)\} with")


def tv(cmd, timeout=60):
    r = subprocess.run([sys.executable, "-I", "tools/tv.py", cmd], capture_output=True, text=True,
                       encoding="utf-8", errors="replace", timeout=timeout)
    return r.stdout


def main():
    seconds = int(sys.argv[1]) if len(sys.argv) > 1 and sys.argv[1].isdigit() else 60
    # The "sleep 2" matters: if the ADB shell ends the instant the job is backgrounded, the job dies with it before
    # setsid has detached it, and nothing is recorded.
    out = tv(f"rm -f {FILE}; setsid nohup sh -c 'timeout {seconds} logcat -v time -T 1 -s WindowManager:D "
             f"ActivityTaskManager:I > {FILE}' >/dev/null 2>&1 < /dev/null & sleep 2; ls {FILE} >/dev/null && echo started")
    if "started" not in out:
        sys.exit("Could not start the recording on the TV. Check TV_HOST and that `tools\\tv.py --probe` works.")
    print(f"Recording for {seconds} s. Press each button on the remote once, a few seconds apart.")
    for left in range(seconds, 0, -10):
        print(f"  {left} s left")
        time.sleep(min(10, left))
    time.sleep(2)
    lines = tv(f"grep -aE 'handleKeyBeforeQueueing.*ACTION_DOWN.*repeatCount=0,|START u0' {FILE}; rm -f {FILE}",
               timeout=120).splitlines()

    events = []
    for line in lines:
        m = KEY.search(line)
        if m:
            events.append(["key", m.group(1), m.group(2), m.group(3), []])
            continue
        s = START.search(line)
        if s and events:
            what = re.search(r"cmp=(\S+)", s.group(2)) or re.search(r"act=(\S+)", s.group(2))
            events[-1][4].append(what.group(1) if what else s.group(2)[:80])
    if not events:
        print("No key presses recorded. Check TV_HOST, and press the real remote while it records.")
        return
    print(f"\n{'time':<20} {'keyCode (use this in mappings)':<34} {'scan':<6} opened")
    for _, t, code, scan, opened in events:
        print(f"{t:<20} {code:<34} {scan:<6} {', '.join(opened) if opened else '(nothing)'}")


if __name__ == "__main__":
    main()
