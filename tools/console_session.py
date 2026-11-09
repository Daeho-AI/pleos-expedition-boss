"""Keep the official virtual console active on our test emulator only.

This is a developer test helper, never packaged in the app. No real car control.
Endpoints are the same ones served by the official console at localhost:8088.
"""
import argparse
import json
from pathlib import Path
import subprocess
import time
import urllib.request
from boss_storage import configure_storage

root = Path(__file__).resolve().parents[1]
state = json.loads((root / "tools/toolchain-state.json").read_text(encoding="utf-8-sig"))
configure_storage(Path(state["toolchainRoot"]))
adb = str(Path(state["androidSdk"]) / "platform-tools/adb.exe")
parser = argparse.ArgumentParser()
parser.add_argument("--seconds", type=int, default=300)
args = parser.parse_args()
serial = "emulator-5554"
fingerprint = subprocess.check_output([adb, "-s", serial, "shell", "getprop", "ro.build.fingerprint"], text=True).strip()
if "Pleos/crp_avd/" not in fingerprint:
    raise SystemExit("This helper only supports the dedicated Pleos test emulator.")
subprocess.run([adb, "-s", serial, "forward", "tcp:8088", "tcp:8088"], check=True)

def request(path, method="POST"):
    with urllib.request.urlopen(urllib.request.Request("http://127.0.0.1:8088" + path, method=method), timeout=5) as response:
        return json.load(response)

print("Official emulator console session active; synthetic VHAL values only.", flush=True)
try:
    deadline = time.monotonic() + args.seconds
    while time.monotonic() < deadline:
        request("/browser/session?client=boss-validation")
        time.sleep(1)
finally:
    request("/vehicle/controls?speed=0&accelerator=0&brake=0&steering=0", "PUT")
    request("/vehicle/gear?gear=Park", "PUT")
    request("/browser/session?client=boss-validation&closing=1")
    print("Test emulator restored to Park, speed 0.", flush=True)
