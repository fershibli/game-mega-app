#!/usr/bin/env python3
"""Shrinks the PNGs in docs/screenshots (75 % size, 256-color palette) so the repository stays light, and
converts the Google Play graphics in fastlane/metadata/android to the formats Play asks for, at their
exact size: the icon as a 32-bit PNG, screenshots and the feature graphic as high-quality JPEG.

Usage: python3 scripts/optimize_screenshots.py   (requires Pillow: pip install pillow)
"""
import glob
import os

from PIL import Image

root = os.path.join(os.path.dirname(__file__), "..")
docs = sorted(glob.glob(os.path.join(root, "docs", "screenshots", "*.png")))
play = sorted(glob.glob(os.path.join(root, "fastlane", "metadata", "android", "**", "*.png"), recursive=True))

total_before = total_after = 0
for path in play:
    total_before += os.path.getsize(path)
    image = Image.open(path)
    if os.path.basename(path) == "icon.png":
        image.convert("RGBA").save(path, optimize=True)
        total_after += os.path.getsize(path)
        continue
    jpeg = path[:-4] + ".jpg"
    image.convert("RGB").save(jpeg, quality=85, optimize=True)
    os.remove(path)
    total_after += os.path.getsize(jpeg)
for path in docs:
    total_before += os.path.getsize(path)
    image = Image.open(path).convert("RGB")
    if image.width > 640:  # only shrink full-size renders, never twice
        image = image.resize((image.width * 3 // 4, image.height * 3 // 4), Image.LANCZOS)
    image.quantize(colors=256, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).save(path, optimize=True)
    total_after += os.path.getsize(path)
print(f"{total_before // 1024} KB -> {total_after // 1024} KB")
