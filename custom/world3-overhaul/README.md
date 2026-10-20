# World 3 End overhaul

On 2026-10-20, World 3's End was reset and its saved generator was changed from
the vanilla End biome source to Nullscape's multi-noise biome source. The
True Ending and Unlock The End datapacks were adapted for Minecraft 26.1.2
and the `minecraft:world3_the_end` dimension. The key-room scan is limited to
World 3.

The backup from before these changes is at
`backups/world3-end-before-repair-20261019-235849/`. It includes the old End,
World 3, datapacks, player data, and relevant configuration. The old End
terrain was intentionally reset so new chunks can use Nullscape.

`patch_datapacks.py` reproduces the installed datapack ZIPs from the original
ZIPs in that backup. `prepare_worldgen.py` reproduces the saved generator NBT;
it requires the Python `nbtlib` package.

After restart, the server successfully located `nullscape:shadowlands` and
`nullscape:crystal_peaks` in `minecraft:world3_the_end`. The key-room change
will run when a player approaches a World 3 stronghold portal room.

On 2026-10-20, all three World 3 dimensions were regenerated with seed
`8813199044843315766`. The immediately preceding worlds are backed up at
`backups/world3-full-regeneration-20261020-001048/`. Existing player files
and the other worlds were retained. The World 3 spawn was set to `(8, 95, 8)`.

The Unlock The End portal scanner originally exceeded World 3's default
32,768 block command limit. It now scans 15,625 blocks near the player, only
in World 3. The new stronghold portal room at `(1277, 31, -667)` was prepared,
and its key lock entity was verified. Its display effect was updated for the
26.1 particle format. True Ending's dimension predicate must remain
`minecraft:the_end` on this Multiverse server, while its dragon tick is scoped
to `minecraft:world3_the_end`. The live dragon was verified with the
`trueEnding_dragon_particlechecked` tag and 300 health.

The client-side key models are in `Unlock_The_End_26.1.2_ResourcePack.zip`.
Java players need to install this pack to see the custom lock and key art.
