"""Copy supplied launcher artwork directly, without resampling or image edits."""
from __future__ import annotations
import argparse
import hashlib
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def prepare(source: Path, preview: bool = False, preview_dir: Path | None = None) -> None:
    original = source.resolve()
    data = original.read_bytes()
    targets = [ROOT / "assets/app-icon.png", ROOT / "app/src/main/res/drawable-nodpi/ic_launcher_artwork.png"]
    for target in targets:
        target.parent.mkdir(parents=True, exist_ok=True)
        if original != target.resolve():
            shutil.copyfile(original, target)
        assert target.read_bytes() == data
        print(target.relative_to(ROOT))
    if preview:
        output = preview_dir or ROOT / ".test-data/qa/v1.2.2"
        output.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(original, output / "icon-original.png")
        report = {"sourceSha256": hashlib.sha256(data).hexdigest(), "copiedByteForByte": True,
                  "resampled": False, "addedPaddingPixels": 0, "addedBorderPixels": 0,
                  "customMaskApplied": False, "visibleImageFraction": 1.0}
        (output / "icon-verification.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print("Original SHA-256:", hashlib.sha256(data).hexdigest())


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=ROOT / "assets/app-icon.png")
    parser.add_argument("--preview", action="store_true")
    parser.add_argument("--preview-dir", type=Path)
    options = parser.parse_args()
    prepare(options.source, options.preview, options.preview_dir)
