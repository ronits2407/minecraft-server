#!/usr/bin/env python3
"""Adapt the installed End datapacks for the World 3 dimension on 26.1.2."""

from __future__ import annotations

import json
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile

def patch_true_ending(source: Path, target: Path) -> None:
    counts = {"flash": 0, "dimension_commands": 0, "dimension_json": 0, "clock": 0}
    with ZipFile(source) as src, ZipFile(target, "w", ZIP_DEFLATED) as dst:
        for item in src.infolist():
            data = src.read(item)
            if item.filename.endswith(".mcfunction"):
                text = data.decode("utf-8")
                counts["flash"] += text.count("particle flash ")
                text = text.replace("particle flash ", "particle flash{color:16777215} ")
                counts["dimension_commands"] += text.count(" in the_end")
                text = text.replace(" in the_end", " in minecraft:world3_the_end")
                if item.filename == "data/true_ending/function/tick.mcfunction":
                    old_dragon = "execute as @e[type=ender_dragon,tag=!trueEnding_mirrordragon"
                    assert text.count(old_dragon) == 1
                    text = text.replace(
                        old_dragon,
                        "execute in minecraft:world3_the_end as @e[type=ender_dragon,tag=!trueEnding_mirrordragon",
                    )
                data = text.encode("utf-8")
            elif item.filename == "pack.mcmeta":
                meta = json.loads(data)
                meta["pack"]["min_format"] = [101, 1]
                meta["pack"]["max_format"] = [101, 1]
                data = json.dumps(meta, indent=2).encode("utf-8")
            elif item.filename == "data/true_ending/advancement/enter_the_end.json":
                text = data.decode("utf-8")
                counts["dimension_json"] += text.count('"minecraft:the_end"')
                data = text.replace('"minecraft:the_end"', '"minecraft:world3_the_end"').encode("utf-8")
            elif item.filename in (
                "data/true_ending/predicate/time/200.json",
                "data/true_ending/predicate/time/daytime.json",
            ):
                text = data.decode("utf-8")
                counts["clock"] += text.count('"minecraft:day_time"')
                data = text.replace('"minecraft:day_time"', '"minecraft:overworld"').encode("utf-8")
            dst.writestr(item, data)
    assert counts == {"flash": 5, "dimension_commands": 15, "dimension_json": 2, "clock": 2}, counts


def patch_unlock_end(source: Path, target: Path) -> None:
    with ZipFile(source) as src, ZipFile(target, "w", ZIP_DEFLATED) as dst:
        for item in src.infolist():
            data = src.read(item)
            if item.filename == "pack.mcmeta":
                meta = json.loads(data)
                meta["pack"] = {
                    "min_format": [101, 1],
                    "max_format": [101, 1],
                    "description": meta["pack"]["description"],
                }
                data = json.dumps(meta, indent=2).encode("utf-8")
            elif item.filename == "data/endkey/function/tick.mcfunction":
                text = data.decode("utf-8")
                old = "execute at @a[predicate=endkey:other_tick] run fill"
                new = "execute as @a[predicate=endkey:other_tick] at @s if dimension minecraft:world3 run fill"
                assert text.count(old) == 1
                text = text.replace(old, new)
                # The original 65 x 33 x 65 fill exceeds the per-world default
                # command limit of 32,768 blocks, so it never reaches the portal.
                old_area = "~32 ~16 ~32 ~-32 ~-16 ~-32"
                new_area = "~12 ~12 ~12 ~-12 ~-12 ~-12"
                assert text.count(old_area) == 1
                data = text.replace(old_area, new_area).encode("utf-8")
            elif item.filename == "data/endkey/function/lock/unlock.mcfunction":
                text = data.decode("utf-8")
                old_flash = "particle minecraft:flash ~ ~ ~"
                assert text.count(old_flash) == 1
                text = text.replace(
                    old_flash, "particle minecraft:flash{color:16777215} ~ ~ ~"
                )
                old_display = 'item.components."minecraft:custom_model_data".strings insert 0 value "endkey.frame"'
                assert text.count(old_display) == 1
                text = text.replace(
                    old_display,
                    'item.components."minecraft:custom_model_data" set value {strings:["endkey.frame"]}',
                )
                data = text.encode("utf-8")
            dst.writestr(item, data)


if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser()
    parser.add_argument("source_dir", type=Path)
    parser.add_argument("output_dir", type=Path)
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    patch_true_ending(
        args.source_dir / "true-ending-1.1.4d.zip",
        args.output_dir / "true-ending-1.1.4d.zip",
    )
    patch_unlock_end(
        args.source_dir / "Unlock_The_End_1.4.zip",
        args.output_dir / "Unlock_The_End_1.4.zip",
    )
