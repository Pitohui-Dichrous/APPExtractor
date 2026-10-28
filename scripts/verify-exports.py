"""Verify exported fixture bytes and optionally reinstall them on the owned emulator."""

from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
import zipfile
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath
from toolchain import adb_path


ROOT = Path(__file__).resolve().parents[1]
QA = ROOT / ".test-data" / "qa"
FIXTURES = ROOT / ".test-data" / "fixtures"


def sha256(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def archive_entries(path: Path) -> list[dict[str, object]]:
    with zipfile.ZipFile(path) as archive:
        assert archive.testzip() is None, f"Invalid CRC: {path}"
        entries = []
        for info in archive.infolist():
            name = PurePosixPath(info.filename)
            assert not name.is_absolute() and ".." not in name.parts, info.filename
            assert "\\" not in info.filename and not info.is_dir(), info.filename
            with archive.open(info) as stream:
                digest = hashlib.file_digest(stream, "sha256").hexdigest()
            entries.append({"name": info.filename, "bytes": info.file_size, "sha256": digest})
        assert len({item["name"] for item in entries}) == len(entries), path
        return entries


def run(adb: Path, serial: str, *args: object) -> str:
    command = [str(adb), "-s", serial, *map(str, args)]
    result = subprocess.run(command, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=60)
    assert result.returncode == 0, f"Command failed: {command}: {result.stdout} {result.stderr}"
    return (result.stdout + result.stderr).strip()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--install", action="store_true", help="Reinstall only the verified test fixtures")
    parser.add_argument("--serial", default="emulator-5580")
    parser.add_argument("--adb", type=Path, default=adb_path(), help="ADB executable; defaults to the configured SDK or PATH")
    args = parser.parse_args()
    if args.install and args.serial != "emulator-5580":
        parser.error("Reinstallation is restricted to the project's owned emulator-5580")

    expected = {name: sha256(FIXTURES / f"{name}.apk") for name in ("single", "base", "config.en")}
    split_hashes = {expected["base"], expected["config.en"]}
    results: dict[str, object] = {
        "verified_at_utc": datetime.now(timezone.utc).isoformat(),
        "original_fixture_sha256": expected,
        "exports": [],
    }
    exports = sorted((QA / "exports").rglob("*"))
    single_export = None
    split_export = None
    batches = 0
    for path in exports:
        if not path.is_file() or path.suffix not in (".apk", ".apks", ".zip"):
            continue
        item: dict[str, object] = {"path": str(path), "bytes": path.stat().st_size, "sha256": sha256(path)}
        if path.suffix == ".apk":
            assert item["sha256"] == expected["single"], path
            item.update(format="APK", original_bytes_identical=True)
            single_export = path
        else:
            entries = archive_entries(path)
            item["entries"] = entries
            item["zip_crc_valid"] = True
            if path.suffix == ".apks":
                assert len(entries) == 2 and {entry["sha256"] for entry in entries} == split_hashes, path
                assert next(entry for entry in entries if entry["name"] == "base.apk")["sha256"] == expected["base"], path
                assert all(len(PurePosixPath(str(entry["name"])).parts) == 1 for entry in entries), path
                item.update(format="APKS", apk_count=2, all_original_bytes_identical=True)
                split_export = path
            else:
                assert len(entries) == 3, path
                assert {entry["sha256"] for entry in entries} == {expected["single"], *split_hashes}, path
                normal = [entry for entry in entries if len(PurePosixPath(str(entry["name"])).parts) == 1]
                splits = [entry for entry in entries if len(PurePosixPath(str(entry["name"])).parts) == 2]
                assert len(normal) == 1 and normal[0]["sha256"] == expected["single"], path
                assert len(splits) == 2 and {entry["sha256"] for entry in splits} == split_hashes, path
                assert len({PurePosixPath(str(entry["name"])).parent for entry in splits}) == 1, path
                item.update(format="ZIP", app_count=2, apk_count=3, all_original_bytes_identical=True)
                batches += 1
        results["exports"].append(item)
    assert single_export is not None and split_export is not None and batches > 0, "Missing export format"

    if args.install:
        staging = QA / "verified-split"
        staging.mkdir(parents=True, exist_ok=True)
        split_files = []
        with zipfile.ZipFile(split_export) as archive:
            for name in archive.namelist():
                # Paths were checked above; APKS members are constrained to one component.
                target = staging / name
                target.write_bytes(archive.read(name))
                split_files.append(target)
        single_result = run(args.adb, args.serial, "install", "-r", single_export)
        assert "Success" in single_result, single_result
        split_result = run(args.adb, args.serial, "install-multiple", "-r", *split_files)
        assert "Success" in split_result, split_result
        paths = run(args.adb, args.serial, "shell", "pm", "path", "app.appextractor.fixture")
        assert len([line for line in paths.splitlines() if line.startswith("package:")]) == 2, paths
        results["reinstallation"] = {
            "serial": args.serial, "single_apk": single_result, "split_apks": split_result,
            "installed_split_paths": paths.splitlines(), "passed": True,
        }
    results["passed"] = True
    report = QA / "export-verification.json"
    report.write_text(json.dumps(results, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"PASS: {len(results['exports'])} exports; APK, complete APKS, and two-app ZIP verified")
    if args.install:
        print("PASS: exported single APK and exported split set reinstalled successfully on emulator-5580")
    print(report)


if __name__ == "__main__":
    main()
