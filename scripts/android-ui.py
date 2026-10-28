"""Local Android UI test helper. Does not ship in the APK."""
import argparse
import re
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path
from toolchain import adb_path

ROOT = Path(__file__).resolve().parents[1]
ADB = adb_path()
SERIAL = "emulator-5580"
OUT = ROOT / ".test-data" / "qa"
OUT.mkdir(parents=True, exist_ok=True)

def adb(*args, binary=False):
    result = subprocess.run([str(ADB), "-s", SERIAL, *args], capture_output=True, timeout=25, check=True)
    return result.stdout if binary else result.stdout.decode("utf-8", errors="replace")

def snapshot(name="window"):
    adb("shell", "uiautomator", "dump", "/sdcard/appextractor-window.xml")
    xml = adb("shell", "cat", "/sdcard/appextractor-window.xml")
    (OUT / (name + ".xml")).write_text(xml, encoding="utf-8")
    return ET.fromstring(xml)

def tap_text(text, index=0, attribute="text"):
    nodes = [node for node in snapshot().iter("node") if node.get(attribute) == text]
    if len(nodes) <= index:
        raise RuntimeError(f"UI target absent: {attribute}={text!r}")
    values = list(map(int, re.findall(r"\d+", nodes[index].get("bounds"))))
    adb("shell", "input", "tap", str((values[0]+values[2])//2), str((values[1]+values[3])//2))

def capture(name):
    tree = snapshot(name)
    (OUT / (name + ".png")).write_bytes(adb("exec-out", "screencap", "-p", binary=True))
    for node in tree.iter("node"):
        text, desc = node.get("text"), node.get("content-desc")
        if text or desc:
            print(node.get("class").split(".")[-1], repr(text or desc), node.get("resource-id"), node.get("bounds"))

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=["capture", "tap", "desc", "id", "input", "key"])
    parser.add_argument("value")
    parser.add_argument("--index", type=int, default=0)
    parser.add_argument("--adb", type=Path, default=ADB, help="ADB executable; defaults to the configured SDK or PATH")
    parser.add_argument("--serial", default=SERIAL, help="Target Android emulator or device serial")
    options = parser.parse_args()
    ADB, SERIAL = options.adb, options.serial
    if options.action == "capture": capture(options.value)
    elif options.action in ("tap", "desc", "id"):
        attribute = {"tap": "text", "desc": "content-desc", "id": "resource-id"}[options.action]
        tap_text(options.value, options.index, attribute)
    elif options.action == "input": adb("shell", "input", "text", options.value)
    elif options.action == "key": adb("shell", "input", "keyevent", options.value)
