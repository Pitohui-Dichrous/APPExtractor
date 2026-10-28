"""Package the supplied artwork as Android launcher resources without redrawing it.

Regeneration requires Pillow. The source copy under assets remains byte-for-byte
identical; only the Android resource variants are scaled for screen density.
"""
from __future__ import annotations

import argparse
import hashlib
import shutil
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCALES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
BACKGROUND = (29, 50, 79)


def prepare(source: Path, preview: bool = False) -> None:
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
        raise ValueError("Use the supplied square launcher artwork")

    resources = ROOT / "app" / "src" / "main" / "res"
    for density, scale in SCALES.items():
        # Adaptive icons use a 108dp layer; the visible mask is about 72dp.
        # The artwork occupies 82dp so its surrounding navy margins are clipped
        # while the robot, antennae, box flaps and arrow stay inside the mask.
        canvas_size = round(108 * scale)
        artwork_size = round(82 * scale)
        foreground = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
        resized = artwork.resize((artwork_size, artwork_size), Image.Resampling.LANCZOS)
        offset = (canvas_size - artwork_size) // 2
        foreground.alpha_composite(resized, (offset, offset))
        target = resources / ("mipmap-" + density) / "ic_launcher_artwork.png"
        target.parent.mkdir(parents=True, exist_ok=True)
        foreground.save(target, optimize=True)
        print(f"{target.relative_to(ROOT)}: {canvas_size}x{canvas_size}")

    if preview:
        # Simulate a circular 72dp launcher mask for visual inspection only.
        layer_size = 768
        layer = Image.new("RGBA", (layer_size, layer_size), (*BACKGROUND, 255))
        artwork_size = round(layer_size * 82 / 108)
        offset = (layer_size - artwork_size) // 2
        layer.alpha_composite(artwork.resize((artwork_size, artwork_size), Image.Resampling.LANCZOS), (offset, offset))
        visible = layer.crop((128, 128, 640, 640))
        mask = Image.new("L", (512, 512), 0)
        ImageDraw.Draw(mask).ellipse((0, 0, 511, 511), fill=255)
        visible.putalpha(mask)
        output = ROOT / ".test-data" / "qa" / "v1.1.0" / "icon-circle.png"
        output.parent.mkdir(parents=True, exist_ok=True)
        visible.save(output, optimize=True)
        print(output.relative_to(ROOT))

    print("Original SHA-256:", hashlib.sha256(source_bytes).hexdigest())


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=ROOT / "assets" / "app-icon.png")
    parser.add_argument("--preview", action="store_true")
    options = parser.parse_args()
    prepare(options.source, options.preview)
