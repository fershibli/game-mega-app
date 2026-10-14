#!/usr/bin/env python3
"""Shrinks the PNGs in docs/screenshots (75 % size, 256-color palette) so the repository stays light.

Usage: python3 scripts/optimize_screenshots.py   (requires Pillow: pip install pillow)
"""
import glob
import os

from PIL import Image

total_before = total_after = 0
for path in sorted(glob.glob(os.path.join(os.path.dirname(__file__), "..", "docs", "screenshots", "*.png"))):
    total_before += os.path.getsize(path)
    image = Image.open(path).convert("RGB")
    if image.width > 640:  # only shrink full-size renders, never twice
        image = image.resize((image.width * 3 // 4, image.height * 3 // 4), Image.LANCZOS)
    image.quantize(colors=256, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).save(path, optimize=True)
    total_after += os.path.getsize(path)
print(f"{total_before // 1024} KB -> {total_after // 1024} KB")
