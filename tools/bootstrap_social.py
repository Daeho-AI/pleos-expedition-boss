"""Install the small social-server environment next to the Android toolchain."""
import hashlib
import json
from pathlib import Path
import subprocess
import venv
from boss_storage import configure_storage

root = Path(__file__).resolve().parents[1]
state = json.loads((root/'tools/toolchain-state.json').read_text(encoding='utf-8-sig'))
toolchain = Path(state['toolchainRoot'])
configure_storage(toolchain)
environment = toolchain/'social-env'
python = environment/'Scripts/python.exe'
requirements = root/'server/requirements.txt'
stamp = environment/'requirements.sha256'
expected = hashlib.sha256(requirements.read_bytes()).hexdigest()
if not python.exists():
    venv.EnvBuilder(with_pip=True).create(environment)
if not stamp.exists() or stamp.read_text() != expected or not (environment/'Lib/site-packages/uvicorn').is_dir():
    subprocess.run([str(python),'-m','pip','install','--disable-pip-version-check','--cache-dir',str(toolchain/'pip-cache'),'-r',str(requirements)],check=True)
    stamp.write_text(expected)
print('Social server environment ready: '+str(environment))
