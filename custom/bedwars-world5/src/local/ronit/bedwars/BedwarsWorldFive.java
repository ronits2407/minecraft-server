package local.ronit.bedwars;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Bed;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.screamingsandals.bedwars.Main;
import org.screamingsandals.bedwars.game.Game;
import org.screamingsandals.bedwars.api.game.GameStatus;
import org.screamingsandals.bedwars.game.Team;
import org.screamingsandals.bedwars.game.TeamColor;
import org.screamingsandals.bedwars.game.ItemSpawner;
import org.screamingsandals.bedwars.game.GameStore;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BedwarsWorldFive extends JavaPlugin implements Listener {
    private static final String ARENA = "world5";
    private static final String MENU = "BedWars Setup";
    private static final String MAP_MENU = "Choose a BedWars Map";
    private static final String PLAYERS = "Assign a Player";
    private static final String TEAMS = "Choose a Team";
    private static final String[] NAMES = {"Red", "Blue", "Green", "Yellow", "Pink", "Aqua", "White", "Gray"};
    // Tentacle Tides by NoobGamerz: https://www.curseforge.com/minecraft/worlds/tentacle-tides-8-player-bedwars-map
    private static final int[][] BEDS_AT = {{-32,-71},{32,-71},{71,-32},{71,32},{-71,32},{32,71},{-32,71},{-71,-32}};
    // Measured on Red and Blue. Other islands are quarter-turn copies around block center (0.5, 0.5).
    private static final double[][] SPAWN_BASE = {{-31.5,-80.5},{32.5,-80.5}};
    private static final double[][] FORGE_BASE = {{-31.5,-86.5},{32.5,-86.5}};
    private static final double[][] SHOP_BASE = {{-37.5,-81.5},{26.5,-81.5}};
    private static final int[] TEAM_BASE = {0,1,0,1,0,0,1,1};
    private static final int[] TEAM_TURNS = {0,0,1,1,3,2,2,3};
    private static final double[][] DIAMOND_SPAWNS = {{32.5,65,-31.5},{32.5,65,32.5},
            {-31.5,65,32.5},{-31.5,65,-31.5}};
    private static final double[][] EMERALD_SPAWNS = {{12.5,66,-14.5},{-11.5,66,15.5},
            {3.5,56,-7.5},{-2.5,56,8.5}};
    private static final BlockFace[] BED_FACES = {BlockFace.NORTH,BlockFace.NORTH,BlockFace.EAST,BlockFace.EAST,
            BlockFace.WEST,BlockFace.SOUTH,BlockFace.SOUTH,BlockFace.WEST};
    private static final Material[] BEDS = {Material.RED_BED,Material.BLUE_BED,Material.GREEN_BED,Material.YELLOW_BED,
            Material.PINK_BED,Material.LIGHT_BLUE_BED,Material.WHITE_BED,Material.GRAY_BED};
    private static final Material[] CONCRETE = {Material.RED_CONCRETE,Material.BLUE_CONCRETE,Material.GREEN_CONCRETE,
            Material.YELLOW_CONCRETE,Material.PINK_CONCRETE,Material.LIGHT_BLUE_CONCRETE,Material.WHITE_CONCRETE,Material.GRAY_CONCRETE};
    private final Map<UUID, UUID> targetPlayers = new HashMap<>();
    private final Map<UUID, List<UUID>> playerMenus = new HashMap<>();
    private PlacementCapture placementCapture;
    private PlacementPreview placementPreview;
    private HubLobby hubLobby;

    @Override public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
        placementCapture = new PlacementCapture(this);
        Bukkit.getPluginManager().registerEvents(placementCapture, this);
        placementPreview = new PlacementPreview(this);
        Bukkit.getPluginManager().registerEvents(placementPreview, this);
        placementPreview.start();
        new BedwarsGameplay(this).start();
        hubLobby = new HubLobby(this);
        hubLobby.start();
        Bukkit.getScheduler().runTaskLater(this, this::initializeArena, 20L);
    }

    private void initializeArena() {
        World world = Bukkit.getWorld("world5");
        if (world == null) {
            getLogger().severe("World 5 is not loaded.");
            return;
        }
        world.setSpawnLocation(0, 98, 0);
        boolean alwaysDaytime = getConfig().getBoolean("always-daytime", true);
        world.setGameRule(GameRules.ADVANCE_TIME, !alwaysDaytime);
        if (alwaysDaytime) world.setTime(getConfig().getLong("daytime-ticks", 6000L));
        boolean alwaysClearWeather = getConfig().getBoolean("always-clear-weather", true);
        world.setGameRule(GameRules.ADVANCE_WEATHER, !alwaysClearWeather);
        if (alwaysClearWeather) {
            world.setStorm(false);
            world.setThundering(false);
        }
        world.setSpawnFlags(false, false);
        removeTemporaryWorld5Lobby(world);
        File arenaFile = new File(Bukkit.getPluginManager().getPlugin("BedWars").getDataFolder(), "arenas/world5.yml");
        if (!arenaFile.exists()) {
            try {
                writeArena(arenaFile, world, 4, 2);
                Game.loadGame(arenaFile);
            } catch (IOException e) {
                getLogger().severe("Could not create BedWars arena: " + e.getMessage());
            }
        }
        Game game=arena();
        if (game!=null && game.getStatus()==GameStatus.WAITING && game.countPlayers()==0) {
            game.stop();
            syncBeds(world, game.getTeams().size());
            game.start();
        }
        initializeXemtori();
        initializeAirshow();
        initializeExtraArena("bwgardens", -2, 149, 0);
        initializeExtraArena("bwaquarium", 0, 118, 0);
        initializeExtraArena("bwlighthouse", 3, 118, 0);
    }

    private void initializeExtraArena(String name, int spawnX, int spawnY, int spawnZ) {
        World world = Bukkit.getWorld(name);
        if (world == null) {
            getLogger().warning(name + " BedWars world is not loaded.");
            return;
        }
        world.setSpawnLocation(spawnX, spawnY, spawnZ);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setTime(6000L);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setStorm(false);
        world.setThundering(false);
        world.setSpawnFlags(false, false);
        File arenaFile = new File(Bukkit.getPluginManager().getPlugin("BedWars").getDataFolder(), "arenas/" + name + ".yml");
        if (arenaFile.exists() && Main.getGame(name) == null) {
            try { Game.loadGame(arenaFile); }
            catch (Exception e) { getLogger().severe("Could not load " + name + " BedWars arena: " + e.getMessage()); }
        }
    }

    private void removeTemporaryWorld5Lobby(World world) {
        if (!getConfig().getBoolean("waiting-lobby-built", false)) return;
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
            boolean edge = Math.abs(x) == 5 || Math.abs(z) == 5;
            Material floor = edge ? Material.CYAN_CONCRETE
                    : (x == 0 && z == 0 ? Material.SEA_LANTERN : Material.LIGHT_BLUE_STAINED_GLASS);
            Material roof = x == 0 && z == 0 ? Material.SEA_LANTERN : Material.GLASS;
            if (world.getBlockAt(x, 99, z).getType() != floor
                    || world.getBlockAt(x, 104, z).getType() != roof) {
                getLogger().warning("World 5 temporary lobby differs from the original build; cleanup skipped.");
                return;
            }
            if (edge) for (int y = 100; y <= 103; y++)
                if (world.getBlockAt(x, y, z).getType() != Material.GLASS) {
                    getLogger().warning("World 5 temporary lobby differs from the original build; cleanup skipped.");
                    return;
                }
        }
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
            world.getBlockAt(x, 99, z).setType(Material.AIR, false);
            if (Math.abs(x) == 5 || Math.abs(z) == 5)
                for (int y = 100; y <= 103; y++) world.getBlockAt(x, y, z).setType(Material.AIR, false);
            world.getBlockAt(x, 104, z).setType(Material.AIR, false);
        }
        // The original archive has oak framing in this footprint. Restore
        // these 24 logs with their original axes after removing our platform.
        var vertical = Bukkit.createBlockData("minecraft:oak_log[axis=y]");
        var acrossX = Bukkit.createBlockData("minecraft:oak_log[axis=x]");
        var acrossZ = Bukkit.createBlockData("minecraft:oak_log[axis=z]");
        for (int x : new int[]{-5, 5}) for (int z : new int[]{-5, 5}) {
            for (int y = 101; y <= 104; y++) world.getBlockAt(x, y, z).setBlockData(vertical, false);
        }
        for (int x : new int[]{-4, 4}) for (int z : new int[]{-5, 5})
            world.getBlockAt(x, 101, z).setBlockData(acrossX, false);
        for (int x : new int[]{-5, 5}) for (int z : new int[]{-4, 4})
            world.getBlockAt(x, 101, z).setBlockData(acrossZ, false);
        getConfig().set("waiting-lobby-built", null);
        saveConfig();
        getLogger().info("Removed the temporary World 5 waiting platform and restored the original oak framing.");
    }

    @Override public void onDisable() {
        if (placementPreview != null) placementPreview.clear();
        if (hubLobby != null) hubLobby.stop();
    }

    private void initializeXemtori() {
        World world = Bukkit.getWorld("bwxemtori");
        if (world == null) {
            getLogger().warning("Xemtori BedWars world is not loaded.");
            return;
        }
        world.setSpawnLocation(-109, 53, 66);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setTime(6000L);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setStorm(false);
        world.setThundering(false);
        world.setSpawnFlags(false, false);
        removeTemporaryXemtoriLobby(world);
        File arenaFile = new File(Bukkit.getPluginManager().getPlugin("BedWars").getDataFolder(), "arenas/bwxemtori.yml");
        if (arenaFile.exists() && Main.getGame("bwxemtori") == null) {
            try {
                Game.loadGame(arenaFile);
            } catch (Exception e) {
                getLogger().severe("Could not load bwxemtori BedWars arena: " + e.getMessage());
            }
        }
    }

    private void removeTemporaryXemtoriLobby(World world) {
        if (!getConfig().getBoolean("xemtori-waiting-lobby-built", false)) return;
        for (int x = -114; x <= -104; x++) for (int z = 61; z <= 71; z++) {
            boolean edge = x == -114 || x == -104 || z == 61 || z == 71;
            if (world.getBlockAt(x, 75, z).getType() != (edge ? Material.LIME_CONCRETE : Material.GLASS)
                    || world.getBlockAt(x, 80, z).getType() != Material.GLASS) {
                getLogger().warning("Xemtori temporary lobby differs from the original build; cleanup skipped.");
                return;
            }
            if (edge) for (int y = 76; y <= 79; y++)
                if (world.getBlockAt(x, y, z).getType() != Material.GLASS) {
                    getLogger().warning("Xemtori temporary lobby differs from the original build; cleanup skipped.");
                    return;
                }
        }
        for (int x = -114; x <= -104; x++) for (int z = 61; z <= 71; z++) {
            world.getBlockAt(x, 75, z).setType(Material.AIR, false);
            if (x == -114 || x == -104 || z == 61 || z == 71)
                for (int y = 76; y <= 79; y++) world.getBlockAt(x, y, z).setType(Material.AIR, false);
            world.getBlockAt(x, 80, z).setType(Material.AIR, false);
        }
        getConfig().set("xemtori-waiting-lobby-built", null);
        saveConfig();
        getLogger().info("Removed the temporary Xemtori waiting platform and restored the original waiting area.");
    }

    private void initializeAirshow() {
        World world = Bukkit.getWorld("bwairshow");
        if (world == null) {
            getLogger().warning("Airshow BedWars world is not loaded.");
            return;
        }
        world.setSpawnLocation(0, 118, 0);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setTime(6000L);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setStorm(false);
        world.setThundering(false);
        world.setSpawnFlags(false, false);
        removeTemporaryAirshowLobby(world);
        File arenaFile = new File(Bukkit.getPluginManager().getPlugin("BedWars").getDataFolder(), "arenas/bwairshow.yml");
        if (arenaFile.exists() && Main.getGame("bwairshow") == null) {
            try {
                Game.loadGame(arenaFile);
            } catch (Exception e) {
                getLogger().severe("Could not load bwairshow BedWars arena: " + e.getMessage());
            }
        }
    }

    private void removeTemporaryAirshowLobby(World world) {
        if (!getConfig().getBoolean("airshow-waiting-lobby-built", false)) return;
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
            boolean edge = Math.abs(x) == 5 || Math.abs(z) == 5;
            Material floor = edge ? Material.RED_CONCRETE
                    : (x == 0 && z == 0 ? Material.SEA_LANTERN : Material.WHITE_STAINED_GLASS);
            Material roof = x == 0 && z == 0 ? Material.SEA_LANTERN : Material.GLASS;
            if (world.getBlockAt(x, 140, z).getType() != floor
                    || world.getBlockAt(x, 145, z).getType() != roof) {
                getLogger().warning("Airshow temporary lobby differs from the original build; cleanup skipped.");
                return;
            }
            if (edge) for (int y = 141; y <= 144; y++)
                if (world.getBlockAt(x, y, z).getType() != Material.GLASS) {
                    getLogger().warning("Airshow temporary lobby differs from the original build; cleanup skipped.");
                    return;
                }
        }
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
            world.getBlockAt(x, 140, z).setType(Material.AIR, false);
            if (Math.abs(x) == 5 || Math.abs(z) == 5)
                for (int y = 141; y <= 144; y++) world.getBlockAt(x, y, z).setType(Material.AIR, false);
            world.getBlockAt(x, 145, z).setType(Material.AIR, false);
        }
        getConfig().set("airshow-waiting-lobby-built", null);
        saveConfig();
        getLogger().info("Removed the temporary Airshow waiting platform and restored the original spawn island.");
    }

    private Location bedHead(World world, int i) {
        return new Location(world, BEDS_AT[i][0], 66, BEDS_AT[i][1]);
    }

    private Location teamSpawn(World world, int i) {
        Location spawn = teamFeature(world, i, SPAWN_BASE);
        spawn.setYaw(TEAM_TURNS[i] * 90f);
        return spawn;
    }

    private Location teamFeature(World world, int i, double[][] base) {
        double x = base[TEAM_BASE[i]][0], z = base[TEAM_BASE[i]][1];
        for (int turn = 0; turn < TEAM_TURNS[i]; turn++) {
            double nextX = 1 - z;
            z = x;
            x = nextX;
        }
        return new Location(world, x, 66, z);
    }

    private void placeBed(World world, int i) {
        Location head = bedHead(world, i);
        BlockFace face = BED_FACES[i];
        int footX = head.getBlockX() + (face == BlockFace.EAST ? -1 : face == BlockFace.WEST ? 1 : 0);
        int footZ = head.getBlockZ() + (face == BlockFace.SOUTH ? -1 : face == BlockFace.NORTH ? 1 : 0);
        Bed headData = (Bed) BEDS[i].createBlockData();
        headData.setPart(Bed.Part.HEAD);
        headData.setFacing(face);
        Bed footData = (Bed) BEDS[i].createBlockData();
        footData.setPart(Bed.Part.FOOT);
        footData.setFacing(face);
        world.getBlockAt(footX, 66, footZ).setBlockData(footData, false);
        head.getBlock().setBlockData(headData, false);
    }

    private void syncBeds(World world, int count) {
        for (int i=0;i<8;i++) {
            if (i<count) placeBed(world,i);
            else {
                Location head=bedHead(world,i);
                head.getBlock().setType(Material.AIR,false);
                BlockFace face=BED_FACES[i];
                int fx=head.getBlockX()+(face==BlockFace.EAST?-1:face==BlockFace.WEST?1:0);
                int fz=head.getBlockZ()+(face==BlockFace.SOUTH?-1:face==BlockFace.NORTH?1:0);
                world.getBlockAt(fx,66,fz).setType(Material.AIR,false);
            }
        }
    }

    private static String loc(Location l) {
        return l.getX() + ";" + l.getY() + ";" + l.getZ() + ";" + l.getYaw() + ";" + l.getPitch();
    }

    private void writeArena(File file, World world, int teamCount, int teamSize) throws IOException {
        file.getParentFile().mkdirs();
        YamlConfiguration y = new YamlConfiguration();
        y.set("name", ARENA);
        y.set("pauseCountdown", 20);
        y.set("gameTime", 3600);
        y.set("world", world.getName());
        y.set("pos1", loc(new Location(world, -100, 40, -100)));
        y.set("pos2", loc(new Location(world, 100, 120, 100)));
        y.set("specSpawn", loc(new Location(world, .5, 99, .5)));
        y.set("lobbySpawn", loc(new Location(world, .5, 98, .5)));
        y.set("lobbySpawnWorld", world.getName());
        y.set("minPlayers", 2);
        String[] colors = {"RED","BLUE","GREEN","YELLOW","PINK","CYAN","WHITE","GRAY"};
        for (int i = 0; i < teamCount; i++) {
            String path = "teams." + NAMES[i].toLowerCase() + ".";
            y.set(path + "isNewColor", true);
            y.set(path + "color", colors[i]);
            y.set(path + "maxPlayers", teamSize);
            y.set(path + "bed", loc(bedHead(world, i)));
            y.set(path + "spawn", loc(teamSpawn(world, i)));
            y.set(path + "actualName", NAMES[i]);
        }
        List<Map<String,Object>> spawners = new ArrayList<>();
        List<Map<String,Object>> shops = new ArrayList<>();
        for (int i = 0; i < teamCount; i++) {
            Location forge = teamFeature(world, i, FORGE_BASE);
            spawners.add(spawner(forge,"iron",NAMES[i]));
            spawners.add(spawner(forge,"gold",NAMES[i]));
            Map<String,Object> shop = new HashMap<>();
            shop.put("loc", loc(teamFeature(world, i, SHOP_BASE)));
            shop.put("shop", "shop.yml"); shop.put("parent", "true"); shop.put("type", "VILLAGER");
            shop.put("name", "Item Shop");
            shops.add(shop);
        }
        for (double[] at : DIAMOND_SPAWNS)
            spawners.add(spawner(new Location(world,at[0],at[1],at[2]),"diamond",null));
        for (double[] at : EMERALD_SPAWNS)
            spawners.add(spawner(new Location(world,at[0],at[1],at[2]),"emerald",null));
        y.set("spawners", spawners);
        y.set("stores", shops);
        y.set("constant.join-random-team-on-join", "false");
        y.save(file);
    }

    private Map<String,Object> spawner(Location l, String type, String team) {
        Map<String,Object> s = new HashMap<>();
        s.put("location", loc(l)); s.put("type", type); s.put("customName", null);
        s.put("hologramEnabled", true); s.put("startLevel", 1.0);
        s.put("team", team); s.put("maxSpawnedResources", 32);
        return s;
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        List<String> lines = new ArrayList<>();
        for (String line : lore) lines.add(ChatColor.translateAlternateColorCodes('&', line));
        meta.setLore(lines);
        stack.setItemMeta(meta);
        return stack;
    }

    private Game arena() { return Main.getGame(ARENA); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("bwmark")) return placementCapture.command(sender, args);
        if (command.getName().equalsIgnoreCase("bwpreview")) return placementPreview.command(sender, args);
        if (!(sender instanceof Player player)) { sender.sendMessage("Run this in game."); return true; }
        if (command.getName().equalsIgnoreCase("bwmaps")) {
            openMapMenu(player);
            return true;
        }
        if (!player.hasPermission("bedwarsworld5.setup")) return true;
        openMain(player);
        return true;
    }

    private void openMapMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, MAP_MENU);
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);
        inv.setItem(10, item(Material.NAUTILUS_SHELL, "&b⚓ Tentacle Tides",
                "&7Ocean arena by NoobGamerz", "&72-8 teams; 1-4 players per team", "&eClick to join"));
        inv.setItem(11, item(Material.FIREWORK_ROCKET, "&c☁ Airshow (Hypixel)",
                "&7Classic hot air balloon arena", "&78 teams; 1-2 players per team", "&eClick to join"));
        inv.setItem(12, item(Material.CAKE, "&a✦ Xemtori Small BedWars",
                "&7Four-island party arena by XEMTORI", "&74 teams; 2 players per team", "&eClick to join"));
        inv.setItem(14, item(Material.OAK_LEAVES, "&a✿ Gardens (Hypixel)",
                "&7Two-base 4v4 arena", "&72 teams; 4 players per team", "&eClick to join"));
        inv.setItem(15, item(Material.TROPICAL_FISH, "&b✦ Aquarium (Hypixel)",
                "&7Four-base arena", "&74 teams; 4 players per team", "&eClick to join"));
        inv.setItem(16, item(Material.BEACON, "&e⚡ Lighthouse (Hypixel)",
                "&7Classic solo and doubles arena", "&78 teams; 2 players per team", "&eClick to join"));
        player.openInventory(inv);
    }

    private void openMain(Player player) {
        Game game = arena();
        if (game == null) { player.sendMessage("World 5 arena is not loaded."); return; }
        Inventory inv = Bukkit.createInventory(null, 36, MENU);
        int teams = game.getTeams().size(), size = game.getTeams().get(0).maxPlayers;
        for (int i = 0; i < 7; i++) {
            int count = i + 2;
            inv.setItem(10+i, item(count == teams ? Material.LIME_WOOL : Material.WHITE_WOOL,
                    "&e"+count+" teams", "&7Click to set team count"));
        }
        for (int sizeOption = 1; sizeOption <= 4; sizeOption++)
            inv.setItem(18+sizeOption, item(sizeOption == size ? Material.LIME_WOOL : Material.WHITE_WOOL,
                    "&b"+sizeOption+" players per team", "&7Click to set team size"));
        inv.setItem(31, item(Material.PLAYER_HEAD, "&aAssign players", "&7Choose a player, then their team"));
        player.openInventory(inv);
    }

    private void openPlayers(Player admin) {
        Inventory inv = Bukkit.createInventory(null, 54, PLAYERS);
        List<UUID> ids = new ArrayList<>();
        int slot = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!Main.isPlayerInGame(player) || Main.getPlayerGameProfile(player).getGame() != arena()) continue;
            ids.add(player.getUniqueId());
            inv.setItem(slot++, item(Material.PLAYER_HEAD, "&e"+player.getName(), "&7Click to choose their team"));
            if (slot == 54) break;
        }
        playerMenus.put(admin.getUniqueId(), ids);
        admin.openInventory(inv);
    }

    private void openTeams(Player admin, UUID target) {
        targetPlayers.put(admin.getUniqueId(), target);
        Inventory inv = Bukkit.createInventory(null, 27, TEAMS);
        Game game = arena();
        for (int i=0;i<game.getTeams().size();i++)
            inv.setItem(9+i, item(CONCRETE[i], "&e"+game.getTeams().get(i).name,
                    "&7Assign " + Bukkit.getOfflinePlayer(target).getName() + " to this team"));
        admin.openInventory(inv);
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!title.equals(MENU) && !title.equals(PLAYERS) && !title.equals(TEAMS) && !title.equals(MAP_MENU)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player admin)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        int slot = event.getSlot();
        if (title.equals(MAP_MENU)) {
            String chosen = switch (slot) {
                case 10 -> "world5";
                case 11 -> "bwairshow";
                case 12 -> "bwxemtori";
                case 14 -> "bwgardens";
                case 15 -> "bwaquarium";
                case 16 -> "bwlighthouse";
                default -> null;
            };
            if (chosen != null) {
                admin.closeInventory();
                Bukkit.dispatchCommand(admin, "bw join " + chosen);
            }
            return;
        }
        if (!admin.hasPermission("bedwarsworld5.setup")) return;
        if (title.equals(MENU)) {
            if (slot>=10 && slot<=16) { setFormat(admin, slot-8, -1); return; }
            if (slot>=19 && slot<=22) { setFormat(admin, -1, slot-18); return; }
            if (slot==31) openPlayers(admin);
        } else if (title.equals(PLAYERS)) {
            List<UUID> ids = playerMenus.get(admin.getUniqueId());
            if (ids!=null && slot>=0 && slot<ids.size()) openTeams(admin, ids.get(slot));
        } else if (title.equals(TEAMS)) {
            UUID targetId = targetPlayers.get(admin.getUniqueId());
            Player target = targetId==null ? null : Bukkit.getPlayer(targetId);
            Game game = arena();
            int index=slot-9;
            if (target!=null && game!=null && index>=0 && index<game.getTeams().size()
                    && Main.isPlayerInGame(target) && Main.getPlayerGameProfile(target).getGame()==game) {
                game.selectPlayerTeam(target, game.getTeams().get(index));
                admin.sendMessage("Assigned " + target.getName() + " to " + NAMES[index] + ".");
            }
            admin.closeInventory();
        }
    }

    @EventHandler public void onDrag(InventoryDragEvent event) {
        String title = event.getView().getTitle();
        if (title.equals(MENU) || title.equals(PLAYERS) || title.equals(TEAMS) || title.equals(MAP_MENU)) event.setCancelled(true);
    }

    private void setFormat(Player admin, int countOption, int sizeOption) {
        Game game = arena();
        if (game==null || game.getStatus()!=GameStatus.WAITING || game.countPlayers()!=0) {
            admin.sendMessage("&cChange the format only while the arena is empty and waiting.".replace('&','§'));
            return;
        }
        int count=countOption>0 ? countOption : game.getTeams().size();
        int size=sizeOption>0 ? sizeOption : game.getTeams().get(0).maxPlayers;
        if (count * size > Bukkit.getMaxPlayers()) {
            admin.sendMessage(ChatColor.RED + "That format needs " + (count * size)
                    + " slots, but the server limit is " + Bukkit.getMaxPlayers() + ".");
            return;
        }
        game.stop();
        game.getTeams().clear();
        String[] colors={"RED","BLUE","GREEN","YELLOW","PINK","CYAN","WHITE","GRAY"};
        World world=Bukkit.getWorld("world5");
        for (int i=0;i<count;i++) {
            Team team=new Team();
            team.name=NAMES[i]; team.saveName=NAMES[i].toLowerCase();
            team.newColor=true; team.color=TeamColor.valueOf(colors[i]);
            team.maxPlayers=size; team.bed=bedHead(world,i); team.spawn=teamSpawn(world,i); team.game=game;
            game.getTeams().add(team);
        }
        game.getSpawners().clear();
        game.getGameStoreList().clear();
        for (int i=0;i<count;i++) {
            Team team=game.getTeams().get(i);
            Location forge = teamFeature(world, i, FORGE_BASE);
            game.getSpawners().add(new ItemSpawner(forge,
                    Main.getSpawnerType("iron"),null,true,1.0,team,32));
            game.getSpawners().add(new ItemSpawner(forge.clone(),
                    Main.getSpawnerType("gold"),null,true,1.0,team,32));
            game.getGameStoreList().add(new GameStore(teamFeature(world, i, SHOP_BASE),
                    "shop.yml",true,EntityType.VILLAGER,"Item Shop",true,false,null,null));
        }
        for (double[] at : DIAMOND_SPAWNS)
            game.getSpawners().add(new ItemSpawner(new Location(world,at[0],at[1],at[2]),
                    Main.getSpawnerType("diamond"),null,true,1.0,null,32));
        for (double[] at : EMERALD_SPAWNS)
            game.getSpawners().add(new ItemSpawner(new Location(world,at[0],at[1],at[2]),
                    Main.getSpawnerType("emerald"),null,true,1.0,null,32));
        syncBeds(world,count);
        game.saveToConfig();
        game.start();
        admin.sendMessage(ChatColor.GREEN+"World 5 set to "+count+" teams of "+size+".");
        openMain(admin);
    }
}
