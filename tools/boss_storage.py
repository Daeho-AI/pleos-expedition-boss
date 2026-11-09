"""Keep this project's temporary and Android user files on the D drive."""
import os
from pathlib import Path
import tempfile


def configure_storage(toolchain_root: Path) -> None:
    if toolchain_root.resolve().drive.upper() != "D:":
        raise RuntimeError("Development files must be stored on D:. Update the toolchain path.")
    paths = {
        "TEMP": Path("D:/Codex/.tmp"),
        "TMP": Path("D:/Codex/.tmp"),
        "TMPDIR": Path("D:/Codex/.tmp"),
        "ANDROID_USER_HOME": toolchain_root / "android-user",
        "ANDROID_EMULATOR_HOME": Path("D:/Codex/PleosBossEmulator"),
        "ANDROID_AVD_HOME": Path("D:/Codex/PleosBossEmulator/avd"),
    }
    for name, path in paths.items():
        path.mkdir(parents=True, exist_ok=True)
        os.environ[name] = str(path)
    tempfile.tempdir = str(paths["TEMP"])
