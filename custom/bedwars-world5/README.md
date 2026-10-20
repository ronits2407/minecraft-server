# BedWars maps

The server uses ScreamingBedWars 0.2.44 for match rules. In the main hub, right-click the World Selector villager to choose a server world. Its World 5 item opens `/bwmaps`, where players can choose:

- **Tentacle Tides** (`world5`), an eight-team ocean map by [NoobGamerz](https://www.curseforge.com/minecraft/worlds/tentacle-tides-8-player-bedwars-map). The archive is `maps/Tentacle-Tides-Bedwars-RoadMap.zip` (SHA-256 `3e01b56cbfc95b56fcdf129b3ec81f95f9717252e5f0dafe15181fafb9ba2192`).
- **Airshow (Hypixel)** (`bwairshow`), Hypixel's classic eight-team hot air balloon BedWars map (solos/doubles). The archive is `maps/airshow.zip`; `maps/prepare_airshow.py` writes its arena configuration.
- **Xemtori Small BedWars** (`bwxemtori`), a four-team island map by [XEMTORI](https://www.curseforge.com/minecraft/worlds/xemtori-small-bedwars). The archive is `maps/xemtori.zip`; `maps/prepare_xemtori.py` writes its arena configuration.
- **Gardens (Hypixel)** (`bwgardens`), Hypixel's 4v4 face-off arena across two large bases (2 teams of 4). The archive is `maps/gardens.zip`.
- **Aquarium (Hypixel)** (`bwaquarium`), four-base arena with diagonal diamond islands (4 teams of 4). The archive is `maps/aquarium.zip`.
- **Lighthouse (Hypixel)** (`bwlighthouse`), classic eight-base solo and doubles arena (8 teams of 2). The archive is `maps/lighthouse.zip`. `maps/prepare_extra_maps.py` writes configurations for Gardens, Aquarium, and Lighthouse.

The map selector works from any world with `/bwmaps`. Each arena uses its own world and its own match inventory.

The main hub stays at daytime with clear weather. Players there receive Speed I and Jump Boost II; those effects are removed when they leave. Three floating BedWars leaderboards near the spawn show the top players by wins, beds broken, and kills, refreshing from the BedWars statistics file about once a minute. The old hub world-selector compass is no longer given on join. Operators in the hub receive a Hub Placement Stick: right-click the floor, then choose the World Selector villager or one of the three leaderboards. The villager stands on that floor; leaderboard text appears 2.5 blocks above it. Each placement is saved in `plugins/BedwarsWorldFive/hub-placements.yml` and updates immediately. Mark the same type again to move it.

In the BedWars waiting lobby, players right-click the compass to choose a team. The plugin returns them to the hub after a match or when they leave.

Operators can run `/bwsetup` to choose 2–8 teams and 1–4 players per team on Tentacle Tides. The Xemtori arena is configured for four teams of two, and Airshow is configured for eight teams of two. The arena must be empty, and the chosen capacity cannot exceed the server's `max-players` setting (currently 20). The **Assign players** button lists players waiting in the Tentacle Tides arena and lets an operator place each one on a team.

## Correcting map placements

An operator in creative mode automatically receives a BedWars Location Marker on entering a BedWars world. Right-click the exact block, then choose the object type and team in the menus. The same marker works for every selection. For spawns, generators, and villagers, click the floor block beneath the desired position. You can also right-click an existing villager with the marker to capture its exact position. Beds and chests are already correctly placed and do not need marking. The selected coordinate is shown in chat and appended to `plugins/BedwarsWorldFive/placement-marks.yml`, including the map, type, team, source block, and operator. `/bwmark status` shows the number of marks; `/bwmark undo` removes the most recent one. Mark the item shop, upgrade shop, player spawn, iron generator, and gold generator separately for each team being sampled. Mark neutral diamond and emerald generators where their placement is unclear. These marks are a survey; they do not move objects during an active match. Once the marks are complete, update the arena configurations and upgrade villager placement from the recorded file.

The World 5 survey was applied on 2026-10-20. Red and Blue positions were measured; the other six teams were calculated with quarter-turn rotations around the map's block center `(0.5, 0.5)` and checked against the corresponding floor blocks. The four diamond and four emerald generators use direct marks. The measured file remains available as an audit record. World 5 runs eight teams of two and its format control uses the same calibrated positions when an operator changes its team count.

Airshow was calibrated the same way: Red and Blue team positions were marked, the other six teams were rotated around `(0.5, 0.5)` and checked against their spawn, generator, and shop floor blocks. All four diamond and four emerald generators were marked directly. The original Airshow map's spawn island is at `(0.5, 118, 0.5)`; the temporary waiting platform at Y 140–145 was removed.

Xemtori's Red team positions were marked directly. The other three bases were rotated around `(-109, 66)` and adjusted to their corresponding generator slabs and shop cobblestone. All four diamond and four emerald floor blocks were marked and confirmed. The sign at the map's center labels a redstone block as a generator, but this BedWars setup has no redstone currency or purchases, so it remains a map feature rather than an active resource spawner. The original Xemtori waiting area is at `(-108.5, 53, 66.5)`; the temporary glass platform at Y 75–80 was removed.

The Gardens survey was applied on 2026-10-20. Red (+X) and Blue (-X) positions were measured directly, including jungle-slab combined forges, jungle-plank spawns, and separate item/upgrade shop planks. Both diamond generators at Z = +/-52 and both emerald generators at (-20.5, 77, -20.5) and (21.5, 77, 21.5) use direct marks.

Aquarium was calibrated from the Red base (+X) survey and rotated across all four quadrants around (0, 0). The cobblestone forge slabs, polished andesite spawns, and green/lime stained glass shop floors were confirmed on Red, Blue, Green, and Yellow. All four diagonal diamond generators and both emerald generators at (8.5, 95, 11.5) and (-9.5, 95, -10.5) use direct marks.

Lighthouse was calibrated from Red and Blue base surveys on the negative Z side, rotated in 90-degree steps around (3, 0) across all eight team bases, and verified against each base's stone-brick forge slab, spruce spawn planks, and white stained glass shop floors. All four diamond generators and all four emerald generators on the lighthouse tower structure use direct marks.

For a placement check outside a match, `/bwpreview on` displays the item-shop villagers, upgrade-shop villagers, and generator locations in the current BedWars world. Use `/bwpreview on <world5|bwairshow|bwxemtori|bwgardens|bwaquarium|bwlighthouse>` to choose another arena. Generator previews show resource items and labels at the saved drop points. Preview villagers identify their positions when right-clicked; shop transactions require a match. Preview entities appear near players in the selected arena, hide when a match starts, and are removed with `/bwpreview off`. The setting remains enabled across restarts until turned off.


The source is in `src/`. Run `bash build.sh` from this directory to rebuild `plugins/BedwarsWorldFive.jar`. A JDK 25 is required. Restart the Minecraft server while no players are online to load a new build.

The map is installed as World 5's region files. Its original waiting area is at `(0.5, 98, 0.5)`. The temporary cyan waiting platform at Y 99–104 was removed, and the oak framing it covered was restored from the original map archive. The previous generated map is preserved in the dated World 5 backup.

World 5, Xemtori, and Airshow stay at daytime with clear weather by default. Configure `always-daytime`, `daytime-ticks`, and `always-clear-weather` in `plugins/BedwarsWorldFive/config.yml`; restart the server to apply changes. The Item Shop has separate Blocks, Melee, Armor, Tools, Ranged, Potions, and Utility categories. Utility sells BedWars' throwable fireballs and TNT that ignites as soon as it is placed (5-second fuse).

All BedWars maps give a wooden sword at match start and after respawn. Each team has a Team Upgrades villager beside its item shop. The upgrade menu offers Sharpness I for 4 diamonds; Protection I–IV for 2/4/8/16 diamonds; Iron Forge I–III for 2 diamonds per level; Gold Forge I–II for 4 diamonds per level; and a one-use blindness trap for 1/2/4 diamonds on successive purchases. The trap triggers when an enemy comes within eight blocks of the bed and resets each match. The Potions category sells Jump Boost V (45 seconds, 1 emerald) and Invisibility (30 seconds, 2 emeralds); the armor shop does not sell shields.

BedWars players have effectively instant sword recharge during matches. The temporary attack-speed modifier is removed on leave, logout, and match end. Sword sweep damage is disabled in BedWars to keep combat focused on the targeted player.

Purchased armor (Chainmail, Iron, and Diamond) equips the complete armor set (helmet, chestplate, leggings, and boots) immediately. The full armor set is permanent for the current match, returns after respawn, and does not drop on death. Purchased Protection upgrades apply to all equipped armor pieces. Fire cannot burn blocks or spread in BedWars worlds, protecting original map wool and player-placed defenses. Shop glass is team-colored Blast-Proof Glass (4 for 12 iron) that resists TNT and fireball explosions while remaining breakable by players.

During matches, the server loops Minecraft's built-in Pigstep disc for players with the Records/Jukebox sound category enabled. Configure `bedwars-music` in `plugins/BedwarsWorldFive/config.yml` and restart to change or disable it. This uses a built-in Minecraft track; a custom Hypixel soundtrack would require a client resource pack and rights to that music.
