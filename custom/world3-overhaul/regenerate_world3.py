#!/usr/bin/env python3
"""Rebuild World 3's three dimensions from backed-up settings with a new seed.

Run only while the server is stopped. The backup directory must already exist.
"""

from __future__ import annotations

import argparse
import secrets
import shutil
from pathlib import Path

import nbtlib
from nbtlib import tag


NAMES = ("world3", "world3_nether", "world3_the_end")
ROOT = Path("world/dimensions/minecraft")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("backup", type=Path)
    args = parser.parse_args()
    backup_root = args.backup / ROOT
    assert all((backup_root / name / "data/minecraft/world_gen_settings.dat").is_file() for name in NAMES)

    seed = secrets.randbelow(2**63 - 1) + 1
    for name in NAMES:
        source = backup_root / name
        target = ROOT / name
        shutil.rmtree(target)
        (target / "data/minecraft").mkdir(parents=True)
        (target / "data/paper").mkdir(parents=True)

        worldgen = nbtlib.load(source / "data/minecraft/world_gen_settings.dat")
        worldgen["data"]["seed"] = tag.Long(seed)
        worldgen.save(target / "data/minecraft/world_gen_settings.dat")

        # Keep each dimension's identity, Multiverse settings, and game rules.
        shutil.copy2(source / "paper-world.yml", target / "paper-world.yml")
        shutil.copy2(
            source / "data/minecraft/game_rules.dat",
            target / "data/minecraft/game_rules.dat",
        )
        shutil.copy2(
            source / "data/paper/metadata.dat", target / "data/paper/metadata.dat"
        )
        assert not (target / "region").exists()

    print(f"Regenerated {', '.join(NAMES)} with seed {seed}")


if __name__ == "__main__":
    main()
