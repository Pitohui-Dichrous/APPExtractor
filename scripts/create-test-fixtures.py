"""Build tiny signed, code-free APK fixtures; never installs or touches a device."""

from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
import zipfile
from pathlib import Path
from toolchain import executable_name, java_home, sdk_root


ROOT = Path(__file__).resolve().parents[1]


def run(*args: object) -> str:
    result = subprocess.run(
        [str(arg) for arg in args], capture_output=True, text=True, encoding="utf-8",
        errors="replace", check=False,
    )
    if result.returncode:
        raise RuntimeError(f"{Path(str(args[0])).name} failed ({result.returncode}):\n{result.stdout}\n{result.stderr}")
    return result.stdout.strip()


def build(
    output_dir: Path, name: str, manifest: str, payload: str,
    sdk: Path, java_home: Path,
) -> dict[str, object]:
    source_dir = output_dir / "sources" / name
    source_dir.mkdir(parents=True, exist_ok=True)
    manifest_path = source_dir / "AndroidManifest.xml"
    manifest_path.write_text(manifest, encoding="utf-8")
    tools = sdk / "build-tools" / "37.0.0"
    unsigned = source_dir / "unsigned.apk"
    aligned = source_dir / "aligned.apk"
    signed = output_dir / f"{name}.apk"
    platform = sdk / "platforms" / "android-37.0" / "android.jar"
    if not platform.is_file():
        platform = sdk / "platforms" / "android-37" / "android.jar"
    run(tools / executable_name("aapt2"), "link", "-I", platform,
        "--manifest", manifest_path, "-o", unsigned)
    with zipfile.ZipFile(unsigned, "a", compression=zipfile.ZIP_STORED) as archive:
        archive.writestr("assets/fixture.txt", payload.encode("utf-8"))
    run(tools / executable_name("zipalign"), "-f", "4", unsigned, aligned)
    signer = tools / "lib" / "apksigner.jar"
    java = java_home / "bin" / executable_name("java")
    run(java, "-jar", signer, "sign", "--ks", ROOT / ".signing" / "appextractor.p12",
        "--ks-key-alias", "appextractor", "--ks-pass", f"file:{ROOT / '.signing' / 'password.txt'}",
        "--out", signed, aligned)
    verification = run(java, "-jar", signer, "verify", "--verbose", "--print-certs", signed)
    return {
        "path": str(signed), "bytes": signed.stat().st_size,
        "sha256": hashlib.sha256(signed.read_bytes()).hexdigest(),
        "verification": verification,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk", type=Path, default=sdk_root(), help="Android SDK directory; defaults to ANDROID_HOME, ANDROID_SDK_ROOT or local.properties")
    parser.add_argument("--java-home", type=Path, default=java_home(), help="JDK directory; defaults to JAVA_HOME or Java on PATH")
    args = parser.parse_args()
    if args.sdk is None:
        parser.error("Set ANDROID_HOME/ANDROID_SDK_ROOT or pass --sdk")
    if args.java_home is None:
        parser.error("Set JAVA_HOME or pass --java-home")
    output = ROOT / ".test-data" / "fixtures"
    output.mkdir(parents=True, exist_ok=True)
    android = 'xmlns:android="http://schemas.android.com/apk/res/android"'
    version = 'android:versionCode="1" android:versionName="1.0"'
    sdk = '<uses-sdk android:minSdkVersion="26" android:targetSdkVersion="34" />'
    manifests = {
        "single": f'''<?xml version="1.0" encoding="utf-8"?>
<manifest {android} package="app.appextractor.fixture.single" {version}>
    {sdk}
    <application android:hasCode="false" android:label="Extractor APK Test" android:allowBackup="false" />
</manifest>
''',
        "base": f'''<?xml version="1.0" encoding="utf-8"?>
<manifest {android} package="app.appextractor.fixture" {version}>
    {sdk}
    <application android:hasCode="false" android:label="Extractor Split Test" android:allowBackup="false" />
</manifest>
''',
        "config.en": f'''<?xml version="1.0" encoding="utf-8"?>
<manifest {android} package="app.appextractor.fixture" split="config.en" {version} android:isFeatureSplit="false">
    {sdk}
    <application android:hasCode="false" />
</manifest>
''',
    }
    results = {
        name: build(output, name, manifest, f"APPExtractor fixture: {name}\n", args.sdk, args.java_home)
        for name, manifest in manifests.items()
    }
    (output / "verification.json").write_text(json.dumps(results, indent=2), encoding="utf-8")
    for name, result in results.items():
        print(f"{name}: {result['path']} ({result['bytes']} bytes) SHA-256 {result['sha256']}")


if __name__ == "__main__":
    main()
