"""Install an isolated Android build toolchain without changing system PATH."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import urllib.request
import zipfile
import xml.etree.ElementTree as ET
from boss_storage import configure_storage

ROOT = Path(__file__).resolve().parents[1]
TOOLS = Path(os.environ.get("PLEOS_BOSS_TOOLCHAIN", "D:/Codex/PleosBossToolchain"))
SDK = TOOLS / "android-sdk"
CACHE = TOOLS / "downloads"
JDK_URL = "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.21%2B9/OpenJDK17U-jdk_x64_windows_hotspot_17.0.21_9.zip"
JDK_SHA = "98518ecf554465057ee5509462631deeee374bc368c12a411d225cd29381f84b"
CMD_URL = "https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip"
CMD_SHA = "90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a"
PLEOS_XML = "https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml"

def download(url: str, name: str, expected: str | None = None, algorithm="sha256") -> Path:
    CACHE.mkdir(parents=True, exist_ok=True)
    dest = CACHE / name
    if dest.exists() and (not expected or digest(dest, algorithm) == expected):
        print(f"Cached: {name}", flush=True)
        return dest
    temp = dest.with_suffix(dest.suffix + ".part")
    request = urllib.request.Request(url, headers={"User-Agent": "PleosBossDev/1.0"})
    print(f"Downloading: {name}", flush=True)
    with urllib.request.urlopen(request, timeout=120) as response, temp.open("wb") as stream:
        while data := response.read(1024 * 1024):
            stream.write(data)
    if expected and digest(temp, algorithm) != expected:
        raise RuntimeError(f"Checksum mismatch: {name}")
    temp.replace(dest)
    print(f"Verified: {name} ({dest.stat().st_size} bytes)", flush=True)
    return dest

def digest(path: Path, algorithm: str) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, algorithm).hexdigest()

def extract(archive: Path, dest: Path):
    dest.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive) as zipped:
        for entry in zipped.infolist():
            if not (dest / entry.filename).resolve().is_relative_to(dest.resolve()):
                raise RuntimeError("Archive path outside destination")
        zipped.extractall(dest)

def main():
    configure_storage(TOOLS)
    parser = argparse.ArgumentParser()
    parser.add_argument("--pleos-image", action="store_true")
    parser.add_argument("--base-only", action="store_true")
    args = parser.parse_args()
    TOOLS.mkdir(parents=True, exist_ok=True)
    jdk_parent = TOOLS / "jdk"
    if not list(jdk_parent.glob("*/bin/java.exe")):
        extract(download(JDK_URL, "jdk17.zip", JDK_SHA), jdk_parent)
    java_home = next(jdk_parent.glob("*/bin/java.exe")).parents[1]
    cmd_parent = SDK / "cmdline-tools" / "latest"
    sdkmanager = cmd_parent / "bin" / "sdkmanager.bat"
    if not sdkmanager.exists():
        archive = download(CMD_URL, "commandline-tools.zip", CMD_SHA)
        staging = TOOLS / "commandline-unpacked"
        extract(archive, staging)
        cmd_parent.parent.mkdir(parents=True, exist_ok=True)
        (staging / "cmdline-tools").rename(cmd_parent)
    state = {"javaHome": str(java_home), "androidSdk": str(SDK), "toolchainRoot": str(TOOLS)}
    (ROOT / "tools" / "toolchain-state.json").write_text(json.dumps(state, indent=2), encoding="utf-8")
    android = ROOT / "android"
    android.mkdir(exist_ok=True)
    (android / "local.properties").write_text("sdk.dir=" + SDK.as_posix() + "\n", encoding="utf-8")
    print(json.dumps(state), flush=True)
    if args.base_only:
        return
    env = {**os.environ, "JAVA_HOME": str(java_home), "ANDROID_HOME": str(SDK), "ANDROID_SDK_ROOT": str(SDK)}
    required_packages = {
        "platform-tools": SDK / "platform-tools/adb.exe",
        "platforms;android-35": SDK / "platforms/android-35/android.jar",
        "build-tools;35.0.0": SDK / "build-tools/35.0.0/apksigner.bat",
        "emulator": SDK / "emulator/emulator.exe",
    }
    packages = [name for name, executable in required_packages.items() if not executable.is_file()]
    log_path = TOOLS / "sdk-install.log"
    if packages:
        print("Installing missing Android SDK packages; see the Android SDK license terms linked in README.", flush=True)
        with log_path.open("w", encoding="utf-8") as log:
            result = subprocess.run([str(sdkmanager), f"--sdk_root={SDK}", *packages], input="y\n" * 100, text=True, stdout=log, stderr=subprocess.STDOUT, env=env)
        print(f"Android SDK install exit={result.returncode}; log={log_path}", flush=True)
        if result.returncode:
            raise SystemExit(result.returncode)
    else:
        print("Android SDK packages already installed", flush=True)
    if args.pleos_image:
        xml_path = download(PLEOS_XML, "crp-sys-img.xml")
        doc = ET.parse(xml_path).getroot()
        package = doc.find("remotePackage")
        complete = package.find("archives/archive/complete")
        destination = SDK / "system-images" / "android-34" / "pleos_car_emulator"
        marker = destination / "verified-extraction.json"
        image_root = destination / "crp_avd_out_x86_64"
        if not marker.exists() or not (image_root / "system.img").is_file():
            image = download(complete.findtext("url"), "pleos-connect-2.1.0.zip", complete.findtext("checksum"), "sha1")
            extract(image, destination)
            marker.write_text(json.dumps({"sha1": complete.findtext("checksum"), "folder": "crp_avd_out_x86_64"}), encoding="utf-8")
        print(f"Pleos image extracted: {destination}", flush=True)
    print("Toolchain ready", flush=True)

if __name__ == "__main__":
    main()
