#!/usr/bin/env python3
"""Put Nullscape's End generator into World 3's saved worldgen settings."""

from __future__ import annotations

import json
from pathlib import Path
from zipfile import ZipFile

import nbtlib
from nbtlib import tag


def nbt_value(value):
    if isinstance(value, bool):
        return tag.Byte(value)
    if isinstance(value, str):
        return tag.String(value)
    if isinstance(value, int):
        return tag.Int(value)
    if isinstance(value, float):
        return tag.Double(value)
    if isinstance(value, dict):
        return tag.Compound({key: nbt_value(item) for key, item in value.items()})
    if isinstance(value, list):
        if not value:
            return tag.List[tag.End]()
        if all(isinstance(item, (int, float)) and not isinstance(item, bool) for item in value):
            kind = tag.Double if any(isinstance(item, float) for item in value) else tag.Int
            return tag.List[kind]([kind(item) for item in value])
        converted = [nbt_value(item) for item in value]
        kind = type(converted[0])
        assert all(type(item) is kind for item in converted), value
        return tag.List[kind](converted)
    raise TypeError(type(value))


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser()
    parser.add_argument("nullscape_zip", type=Path)
    parser.add_argument("worldgen_dat", type=Path)
    parser.add_argument("output_dat", type=Path)
    args = parser.parse_args()
    with ZipFile(args.nullscape_zip) as z:
        spec = json.loads(z.read("data/minecraft/dimension/the_end.json"))
    saved = nbtlib.load(args.worldgen_dat)
    saved["data"]["dimensions"]["minecraft:the_end"] = nbt_value(spec)
    args.output_dat.parent.mkdir(parents=True, exist_ok=True)
    saved.save(args.output_dat)
    check = nbtlib.load(args.output_dat)
    assert check["data"]["dimensions"]["minecraft:the_end"]["generator"]["biome_source"]["biomes"]
    print(args.output_dat)
