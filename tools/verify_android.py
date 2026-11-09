"""Run device tests and retrieve evidence from the dedicated test emulator."""
import argparse
import json
from pathlib import Path
import subprocess
import sys
from boss_storage import configure_storage

root = Path(__file__).resolve().parents[1]
config = json.loads((root / "tools/toolchain-state.json").read_text(encoding="utf-8-sig"))
configure_storage(Path(config["toolchainRoot"]))
adb = str(Path(config["androidSdk"]) / "platform-tools/adb.exe")
parser = argparse.ArgumentParser()
parser.add_argument("--fixture-tests", action="store_true")
parser.add_argument("--reset-demo", action="store_true", help="Clear only the fixture app's test progress before UI tests.")
parser.add_argument("--pull-images", action="store_true")
parser.add_argument("--sdk-report", help="Output evidence filename, e.g. sdk-after-background.json")
args = parser.parse_args()
base = [adb, "-s", "emulator-5554"]
fingerprint = subprocess.check_output(base + ["shell", "getprop", "ro.build.fingerprint"], text=True).strip()
if "Pleos/crp_avd/" not in fingerprint:
    raise SystemExit("Connect the dedicated Pleos test emulator first.")
user = subprocess.check_output(base + ["shell", "am", "get-current-user"], text=True).strip()
reports = root / "docs/validation"
reports.mkdir(parents=True, exist_ok=True)
fixture = "dev.pleos.entry.boss.fixture"

def run(*command):
    return subprocess.run(base + list(command), check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

if args.fixture_tests:
    build = Path(config["toolchainRoot"]) / "build/app-boss/outputs/apk"
    for apk in (build / "fixture/debug/app-boss-fixture-debug.apk", build / "androidTest/fixture/debug/app-boss-fixture-debug-androidTest.apk"):
        print(run("install", "-r", str(apk)).stdout.decode(), flush=True)
    if args.reset_demo:
        print(run("shell", "pm", "clear", "--user", user, fixture).stdout.decode(), flush=True)
    for test, filename in (("GameStorageTest", "instrumentation-storage.txt"), ("FixtureJourneyUiTest", "instrumentation-ui.txt")):
        completed = run("shell", "am", "instrument", "-w", "-r", "-e", "class", "dev.pleos.entry.boss." + test,
                        fixture + ".test/androidx.test.runner.AndroidJUnitRunner")
        result = completed.stdout.decode("utf-8", errors="replace").replace("\r", "")
        (reports / filename).write_text(result, encoding="utf-8")
        if "OK (" not in result or "FAILURES" in result or "Process crashed" in result:
            print(result)
            sys.exit(1)
        print(test + ": " + result.split("OK (")[-1].splitlines()[0], flush=True)

if args.pull_images:
    destination = root / "artifacts/screenshots"
    destination.mkdir(parents=True, exist_ok=True)
    names = run("shell", "run-as", fixture, "--user", user, "ls", "files/screenshots").stdout.decode().split()
    for name in names:
        if not name.endswith(".png") or "/" in name:
            continue
        data = run("exec-out", "run-as", fixture, "--user", user, "cat", "files/screenshots/" + name).stdout
        if not data.startswith(b"\x89PNG\r\n\x1a\n"):
            raise RuntimeError("Screenshot is not a valid PNG: " + name)
        (destination / name).write_bytes(data)
    print("Saved screenshots: " + str(len(names)), flush=True)

if args.sdk_report:
    if Path(args.sdk_report).name != args.sdk_report:
        raise SystemExit("Use a simple report filename.")
    data = run("exec-out", "run-as", "dev.pleos.entry.boss", "--user", user, "cat", "files/sdk-evidence.json").stdout
    evidence = json.loads(data)
    (reports / args.sdk_report).write_text(json.dumps(evidence, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(evidence, ensure_ascii=False, indent=2))
