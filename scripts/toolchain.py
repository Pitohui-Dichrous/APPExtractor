"""Discover locally configured Android and Java tools without machine-specific paths."""
import os
import shutil
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def sdk_root() -> Path | None:
    configured = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if configured:
        return Path(configured)
    properties = ROOT / "local.properties"
    if properties.is_file():
        for line in properties.read_text(encoding="utf-8-sig").splitlines():
            if line.strip().startswith("sdk.dir="):
                value = line.split("=", 1)[1].strip()
                return Path(value.replace("\\:", ":").replace("\\\\", "\\").replace("\\ ", " "))
    return None


def java_home() -> Path | None:
    if os.environ.get("JAVA_HOME"):
        return Path(os.environ["JAVA_HOME"])
    executable = shutil.which("java")
    return Path(executable).resolve().parent.parent if executable else None


def executable_name(name: str) -> str:
    return name + ".exe" if os.name == "nt" else name


def adb_path() -> Path:
    sdk = sdk_root()
    if sdk:
        candidate = sdk / "platform-tools" / executable_name("adb")
        if candidate.is_file():
            return candidate
    return Path(shutil.which("adb") or executable_name("adb"))
