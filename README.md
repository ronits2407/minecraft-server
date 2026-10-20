# Purpur Minecraft Server Network & BedWars Arena Suite

A production-configured, high-performance **Purpur (1.20+)** Minecraft server featuring a multi-world network, cross-play compatibility (Java & Bedrock), custom datapack integrations, and an automated multi-arena **BedWars** suite with custom lobby orchestration.

---

## 📑 Table of Contents
- [Connecting to the Server (IITG Network)](#-connecting-to-the-server-iitg-network-only)
- [Architecture & Worlds](#-architecture--worlds)
- [BedWars Arenas](#-bedwars-arenas)
- [Repository Structure](#-repository-structure)
- [Custom Plugins & Automation](#-custom-plugins--automation)
- [Installed Plugins](#-installed-plugins)
- [Setup & Installation](#-setup--installation)
- [Server Operation & Management](#-server-operation--management)
- [World Generation & Overhaul Tools](#-world-generation--overhaul-tools)

---

## 🎮 Connecting to the Server (IITG Network Only)

> [!IMPORTANT]
> This server is hosted within the **IIT Guwahati (IITG) campus network**. You must be connected to the IITG LAN or campus Wi-Fi to reach and join the server.

| Client Edition | Server Address (IP) | Port | Instructions |
| :--- | :--- | :--- | :--- |
| **Java Edition** (PC / Mac / Linux) | `172.16.115.66` | `8443` | Go to **Multiplayer** → **Add Server** → Server Address: `172.16.115.66:8443` |
| **Bedrock Edition** (Mobile / Windows / Console) | `172.16.115.66` | `19132` | Go to **Play** → **Servers** → **Add Server** → Server IP: `172.16.115.66`, Port: `19132` |

---

## 🌐 Architecture & Worlds

The server operates a multi-world environment orchestrated via **Multiverse** and a custom hub interface:

| Dimension / World | Role / Description | Features |
| :--- | :--- | :--- |
| **`hub`** | Server Lobby | Custom *Xemtori Small Party Lobby* map. Dedicated spawn at `(-61.5, -31, -49.5)`, inventory isolation, protected spawn area, and BedWars arena selector. |
| **`world`** | Primary Survival | Vanilla Overworld survival with VeinMiner, Chunky pre-generation, and Nether portal links. |
| **`world2`** | Secondary Survival | Additional survival world for sandbox play and survival builds. |
| **`world3`** | Custom End Overhaul | Overhauled survival world featuring Nullscape multi-noise End biome generation and the *True Ending* + *Unlock The End* questline. |
| **`world4`** | Hardcore / Alternate Survival | Dedicated Overworld, Nether (`world4_nether`), and End (`world4_the_end`) dimensions. |
| **BedWars Arenas** | Minigame Arenas | 6 isolated arena dimensions automatically regenerated upon game start/finish. |

---

## 🛏️ BedWars Arenas

The server runs **ScreamingBedWars** enhanced by the custom **`BedwarsWorldFive`** plugin. Six custom-calibrated arenas are configured with verified bed locations, team spawns, forge resource drops, and shopkeepers:

| Arena Name | Map ID | Mode / Teams | Key Locations & Details |
| :--- | :--- | :--- | :--- |
| **Tentacle Tides** | `world5` | 4v4 (Red, Blue, Green, Yellow) | Center emerald generators, diagonal diamond generators, custom upgrade villager spawns. |
| **Airshow** | `bwairshow` | Solo / Doubles | Aerial battlefield calibrated for fast-paced vertical bridging and rush gameplay. |
| **Xemtori** | `bwxemtori` | 4-Team Party | Themed around custom Xemtori islands with symmetric bed defenses. |
| **Gardens** | `bwgardens` | 4v4 Face-off | Red ($X = -94.5$) vs Blue ($X = +94.5$). Beds at $X = \pm 80$, forges on jungle slabs, dual diamond generators ($Z = \pm 51.5$), emerald generators at $(\pm 21, 77, \pm 21)$. |
| **Aquarium** | `bwaquarium` | 4-Team Rotational | 4-fold rotational symmetry around underwater biome aesthetics. Beds at $(\pm 48, 87, 0)$ and $(0, 87, \pm 48)$. Diagonal diamond generators and central emerald towers. |
| **Lighthouse** | `bwlighthouse` | 8-Base Solo / Doubles | 8 symmetric bases rotated around $(3, 0)$. Verified spruce plank spawns, stone brick forge slabs, 4 diamond generators, and 4 lighthouse emerald generators. |

---

## 📁 Repository Structure

```text
minecraft_server/
├── start.sh                      # Server startup script (spawns detached tmux session 'mc')
├── stop.sh                       # Graceful shutdown script
├── console.sh                    # Attaches directly to the server tmux console
├── server.properties             # Core server properties (Port: 8443, online-mode: false)
├── purpur.yml                    # Purpur engine performance & gameplay configuration
├── pufferfish.yml                # Pufferfish optimization settings
├── spigot.yml & bukkit.yml       # Spigot and Bukkit engine configuration
├── log4j2.xml                    # Logging layout configuration
├── config/                       # Paper configuration (paper-global.yml, paper-world-defaults.yml)
├── custom/
│   ├── bedwars-world5/           # Source code & map assets for custom BedWars plugin
│   │   ├── src/                  # Java source files (HubLobby, BedwarsWorldFive, PlacementCapture)
│   │   ├── maps/                 # Arena templates (.zip) & Python build/generator scripts
│   │   ├── build.sh              # Standalone compilation script building BedwarsWorldFive.jar
│   │   ├── plugin.yml            # Bukkit plugin descriptor
│   │   └── config.yml            # Arena shop coordinates & hub spawn configuration
│   ├── lobby-map/                # Documentation & asset verification for the Hub map
│   └── world3-overhaul/          # Datapack patchers and regeneration scripts for World 3 End
├── plugins/                      # Plugin configuration directories
│   ├── BedWars/                  # ScreamingBedWars arena configurations, shops, signs
│   ├── BedwarsWorldFive/         # Custom BedWars orchestrator configuration
│   ├── Multiverse-Core/          # Multi-world registry & spawn definitions
│   ├── Multiverse-Inventories/   # Per-world / per-group inventory isolation rules
│   ├── Multiverse-NetherPortals/ # Cross-dimension portal link rules
│   ├── Geyser-Spigot/            # Bedrock client translation configuration
│   ├── floodgate/                # Bedrock authentication encryption
│   ├── Chunky/                   # World chunk pre-generation tasks
│   ├── ServerSelector/           # In-game GUI menu navigation
│   ├── VeinMiner/                # Ores and vein mining configurations
│   ├── UpgradeItems/             # Custom item upgrade recipes
│   ├── ManhuntNG/                # Speedrunner vs Hunter minigame configuration
│   └── ViaVersion/               # Multi-version client protocol compatibility
└── world/
    └── datapacks/                # World datapacks (Nullscape, True Ending, Unlock The End)
```

---

## 🛠️ Custom Plugins & Automation

### 1. `BedwarsWorldFive` (`custom/bedwars-world5/`)
A custom Bukkit plugin written in Java that extends ScreamingBedWars and Multiverse:
- **Lobby Management (`HubLobby.java`)**: Manages player spawn mechanics in the `hub` dimension, hunger protection, fall-damage negation, and automatic inventory resets.
- **Arena Calibration & Markers (`PlacementCapture.java`, `PlacementPreview.java`)**: Calibration tools used during map preparation to record bed locations, spawner coords, and shop positions.
- **Automated Compilation**: Run `./custom/bedwars-world5/build.sh` to compile source files directly against the server libraries into `plugins/BedwarsWorldFive.jar`.

### 2. World 3 End Overhaul (`custom/world3-overhaul/`)
Scripts to overhaul the End dimension in `world3`:
- **`prepare_worldgen.py`**: Modifies the saved End dimension NBT to inject Nullscape's multi-noise biome generator.
- **`patch_datapacks.py`**: Adapts *True Ending* and *Unlock The End* datapacks to target the `minecraft:world3_the_end` dimension safely.
- **`regenerate_world3.py`**: Automated chunk reset and seed re-application script.
- **`Unlock_The_End_26.1.2_ResourcePack.zip`**: Client-side resource pack for custom End lock and key models.

---

## 🔌 Installed Plugins

| Plugin | Version | Purpose |
| :--- | :--- | :--- |
| **Purpur** | 1.20+ (Build 2592) | High-performance Paper fork with granular gameplay configurations. |
| **ScreamingBedWars** | 0.2.44 | Core BedWars minigame engine. |
| **BedwarsWorldFive** | Custom | Hub lobby orchestrator, custom shopkeeper coordinates, and arena hooks. |
| **Multiverse-Core** | 5.8.1 | Multi-world management system. |
| **Multiverse-Inventories** | 5.3.6 | Keeps survival inventories separate between Hub, Survival worlds, and BedWars. |
| **Multiverse-NetherPortals** | 5.1.0 | Links Nether and End portals to their respective custom dimension counterparts. |
| **Geyser-Spigot** | Latest | Translates Bedrock packets so mobile/console players can join. |
| **Floodgate** | Latest | Bedrock player authentication bypass without requiring Java accounts. |
| **ViaVersion** | Latest | Allows newer and older Minecraft Java clients to connect seamlessly. |
| **Chunky** | 1.4.40 | Asynchronous chunk pre-generator to prevent terrain generation lag. |
| **VeinMiner** | 2.4.1 | Sneak-to-mine ore vein extraction utility. |
| **UpgradeItems** | 1.0.0 | Progression system for upgrading tools and equipment. |
| **ServerSelector** | 1.8 | Custom GUI navigation compass. |
| **ManhuntNG** | 1.6.0 | Speedrunner vs Hunter minigame plugin. |
| **Spark** | Latest | Real-time CPU, RAM, and tick-duration performance profiler. |

---

## 🚀 Setup & Installation

### Prerequisites
- **OS**: Linux (Ubuntu/Debian recommended)
- **Java**: OpenJDK 21 or higher
- **Tmux**: Required for `start.sh` background session management
- **Python**: Python 3.10+ (with `nbtlib` installed via `pip install nbtlib` for worldgen scripts)

### Installation Steps
1. **Clone the repository**:
   ```bash
   git clone https://github.com/ronits2407/minecraft-server.git
   cd minecraft-server
   ```

2. **Download Server & Plugin Jars**:
   Download the matching Purpur server JAR into the root directory as `server.jar`:
   ```bash
   # Download Purpur 1.20+
   curl -o server.jar "https://api.purpurmc.org/v2/purpur/1.20.4/latest/download"
   ```
   Place the required plugin JARs (listed above) into the `plugins/` directory.

3. **Build the Custom BedWars Plugin**:
   ```bash
   chmod +x custom/bedwars-world5/build.sh
   ./custom/bedwars-world5/build.sh
   ```

4. **Prepare BedWars Maps**:
   Unpack and configure arena templates using the provided Python tools:
   ```bash
   cd custom/bedwars-world5/maps
   python3 prepare_arena.py
   python3 prepare_airshow.py
   python3 prepare_xemtori.py
   python3 prepare_extra_maps.py
   cd ../../..
   ```

---

## 🎮 Server Operation & Management

### Starting the Server
```bash
./start.sh
```
This launches Purpur inside a persistent tmux session named `mc` with optimized JVM GC flags:
```bash
java -Xms4G -Xmx4G -XX:+UseZGC -XX:+ZGenerational -Dlog4j.configurationFile=log4j2.xml -jar server.jar nogui
```

### Accessing the Console
```bash
./console.sh
```
*(To detach from the console without stopping the server, press `Ctrl + B`, then `D`)*

### Stopping the Server
```bash
./stop.sh
```
Sends a graceful `stop` command to the server session, saving all chunk data and closing database connections cleanly.

---

## 🔒 Security & Version Control Notice

To maintain high performance and repository cleanliness:
- Large chunk databases (`world/dimensions/`, `*.mca`, `level.dat`), backups, and runtime binaries (`server.jar`, plugin jars) are excluded via `.gitignore`.
- Authentication keys (`*.pem`, `*.key`) and player inventory dumps (`Multiverse-Inventories/players/`, `usercache.json`) are strictly untracked.
- Datapacks (`world/datapacks/`) and map generation templates (`custom/bedwars-world5/maps/*.zip`) are preserved within the repository to ensure reproducible deployments.
