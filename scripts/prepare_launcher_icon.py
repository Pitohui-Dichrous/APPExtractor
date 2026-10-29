"""Package supplied launcher artwork whole, with space for Android icon masks.

Requires Pillow. The asset is copied byte-for-byte; launcher resources only apply
uniform scaling and centering. No subject pixels are cropped or redrawn.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCALES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
BACKGROUND = (36, 36, 36)
LAYER_DP = 108
SAFE_RADIUS_DP = 33
MARGIN_DP = 2


def subject_radius(image) -> float:
    """Farthest lime/white subject pixel; dark grayscale artwork is background."""
    radius = 0.0
    pixels = image.load()
    for y in range(image.height):
        for x in range(image.width):
            r, g, b, alpha = pixels[x, y]
            if alpha and ((g > r + 8 and g > b + 8) or (min(r, g, b) > 128 and max(r, g, b) - min(r, g, b) < 24)):
                radius = max(radius, math.hypot(x + 0.5 - image.width / 2, y + 0.5 - image.height / 2))
    return radius


def prepare(source: Path, preview: bool = False, preview_dir: Path | None = None) -> None:
    from PIL import Image, ImageDraw

    original = source.resolve()
    asset = ROOT / "assets" / "app-icon.png"
    asset.parent.mkdir(parents=True, exist_ok=True)
    if original != asset.resolve():
        shutil.copyfile(original, asset)
    source_bytes = original.read_bytes()
    assert source_bytes == asset.read_bytes(), "The artwork copy must remain unchanged"
    with Image.open(asset) as image:
        artwork = image.convert("RGBA")
    if artwork.width != artwork.height:
        raise ValueError("Use square launcher artwork; no automatic cropping is performed")

    source_radius = subject_radius(artwork)
    if not source_radius:
        raise ValueError("The supplied image has no visible lime/white subject")
    # The complete lime/white subject fits the guaranteed 66dp circular safe zone.
    # Round down to an even size so every density centers on whole pixels.
    artwork_dp = math.floor(min(LAYER_DP, (SAFE_RADIUS_DP - MARGIN_DP) * artwork.width / source_radius) / 2) * 2
    if artwork_dp < 2:
        raise ValueError("The image cannot be represented safely at launcher size")

    resources = ROOT / "app" / "src" / "main" / "res"
    checks = []
    for density, scale in SCALES.items():
        canvas_size = round(LAYER_DP * scale)
        artwork_size = round(artwork_dp * scale)
        foreground = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
        resized = artwork.resize((artwork_size, artwork_size), Image.Resampling.LANCZOS)
        offset = (canvas_size - artwork_size) // 2
        foreground.alpha_composite(resized, (offset, offset))
        radius = subject_radius(foreground) / scale
        assert radius < SAFE_RADIUS_DP, f"{density}: lime/white subject must stay inside the safe circle"
        target = resources / ("mipmap-" + density) / "ic_launcher_artwork.png"
        target.parent.mkdir(parents=True, exist_ok=True)
        foreground.save(target, optimize=True)
        checks.append({"density": density, "canvasPixels": canvas_size, "artworkPixels": artwork_size,
                       "subjectRadiusDp": round(radius, 4), "safeRadiusDp": SAFE_RADIUS_DP,
                       "allSubjectInsideSafeCircle": True})
        print(f"{target.relative_to(ROOT)}: {canvas_size}x{canvas_size}")

    if preview:
        output_dir = preview_dir or ROOT / ".test-data" / "qa" / "v1.2.1"
        output_dir.mkdir(parents=True, exist_ok=True)
        # Preview the normal 72dp visible mask, without changing source artwork.
        visible_size = 576
        ratio = visible_size / 72
        layer_size = round(LAYER_DP * ratio)
        layer = Image.new("RGBA", (layer_size, layer_size), (*BACKGROUND, 255))
        artwork_size = round(artwork_dp * ratio)
        offset = (layer_size - artwork_size) // 2
        layer.alpha_composite(artwork.resize((artwork_size, artwork_size), Image.Resampling.LANCZOS), (offset, offset))
        inset = (layer_size - visible_size) // 2
        visible = layer.crop((inset, inset, inset + visible_size, inset + visible_size))
        for shape in ("circle", "rounded-square"):
            mask = Image.new("L", visible.size, 0)
            draw = ImageDraw.Draw(mask)
            if shape == "circle":
                draw.ellipse((0, 0, visible_size - 1, visible_size - 1), fill=255)
            else:
                draw.rounded_rectangle((0, 0, visible_size - 1, visible_size - 1), radius=round(visible_size * 0.2), fill=255)
            result = visible.copy()
            result.putalpha(mask)
            result.save(output_dir / f"icon-{shape}.png", optimize=True)
        report = {"sourceSha256": hashlib.sha256(source_bytes).hexdigest(), "sourceSize": list(artwork.size),
                  "sourceSubjectRadiusPixels": source_radius, "artworkDp": artwork_dp,
                  "safeCircleDiameterDp": SAFE_RADIUS_DP * 2, "sourceCopiedExactly": True,
                  "wholeImageUniformlyScaled": True, "backgroundColor": "#242424",
                  "criticalSubject": "lime and white; grayscale decoration remains in the whole image", "densityChecks": checks}
        (output_dir / "icon-verification.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
        print(output_dir)

    print("Artwork size:", artwork_dp, "dp")
    print("Original SHA-256:", hashlib.sha256(source_bytes).hexdigest())


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=ROOT / "assets" / "app-icon.png")
    parser.add_argument("--preview", action="store_true")
    parser.add_argument("--preview-dir", type=Path)
    options = parser.parse_args()
    prepare(options.source, options.preview, options.preview_dir)
