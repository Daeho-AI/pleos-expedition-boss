"""Create a local publication key, keeping secrets outside the checkout."""
import json
import os
from pathlib import Path
import secrets
import subprocess
from boss_storage import configure_storage

root = Path(__file__).resolve().parents[1]
config = json.loads((root / "tools/toolchain-state.json").read_text(encoding="utf-8-sig"))
toolchain = Path(config["toolchainRoot"])
configure_storage(toolchain)
folder = toolchain / "signing"
folder.mkdir(parents=True, exist_ok=True)
properties = folder / "release-signing.properties"
keystore = folder / "expedition-boss.jks"
if properties.exists() and keystore.exists():
    print(f"Existing release key retained: {keystore}")
elif properties.exists() or keystore.exists():
    raise SystemExit("Incomplete signing setup. Restore the matching key/properties pair; neither file was overwritten.")
else:
    password = secrets.token_urlsafe(36)
    keytool = Path(config["javaHome"]) / "bin/keytool.exe"
    env = {**os.environ, "BOSS_KEY_PASSWORD": password}
    subprocess.run([
        str(keytool), "-genkeypair", "-noprompt", "-keystore", str(keystore),
        "-storetype", "JKS", "-alias", "expedition-boss", "-keyalg", "RSA",
        "-keysize", "2048", "-validity", "10000",
        "-dname", "CN=Daeho-AI, OU=Expedition Boss, O=Daeho-AI",
        "-storepass:env", "BOSS_KEY_PASSWORD", "-keypass:env", "BOSS_KEY_PASSWORD",
    ], check=True, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    properties.write_text(
        f"storeFile={keystore.as_posix()}\nstorePassword={password}\n"
        f"keyAlias=expedition-boss\nkeyPassword={password}\n", encoding="ascii"
    )
    print(f"Created local release key: {keystore}")
print("Back up this signing directory privately. Never commit or upload its files.")
