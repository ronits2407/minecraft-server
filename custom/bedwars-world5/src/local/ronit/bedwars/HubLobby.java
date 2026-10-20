package local.ronit.bedwars;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.function.ToIntFunction;

/** Keeps the main hub pleasant and shows BedWars rankings beside its world selector. */
final class HubLobby implements Listener {
    private static final String SELECTOR_TAG = "bedwars_hub_world_selector";
    private static final String BOARD_TAG = "bedwars_hub_leaderboard";
    private static final String MARK_MENU = "Hub Mark: Place What?";
    private static final String[] MARK_KEYS = {"selector", "wins", "beds", "kills"};
    private static final String[] MARK_NAMES = {"World Selector villager", "Wins leaderboard", "Beds Broken leaderboard", "Kills leaderboard"};
    private static final Material[] MARK_ICONS = {Material.VILLAGER_SPAWN_EGG, Material.GOLDEN_APPLE,
            Material.RED_BED, Material.IRON_SWORD};
    private static final double[] BOARD_X = {-69.5, -61.5, -53.5};
    private static final String[] HEADINGS = {"WINS", "BEDS BROKEN", "KILLS"};
    private final JavaPlugin plugin;
    private final NamespacedKey markerKey;
    private final File marksFile;
    private final Set<UUID> boosted = new HashSet<>();
    private final Set<Chunk> hubChunks = new HashSet<>();
    private final List<TextDisplay> boards = new ArrayList<>();
    private final Map<UUID, Location> pendingMarks = new HashMap<>();
    private int refreshes;

    private record Stats(String name, int wins, int beds, int kills) {}

    HubLobby(JavaPlugin plugin) {
        this.plugin = plugin;
        markerKey = new NamespacedKey(plugin, "hub_villager_marker");
        marksFile = new File(plugin.getDataFolder(), "hub-placements.yml");
    }

    void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 200L);
    }

    void stop() {
        for (TextDisplay board : boards) if (board.isValid()) board.remove();
        boards.clear();
        for (Chunk chunk : hubChunks) chunk.removePluginChunkTicket(plugin);
        hubChunks.clear();
        for (UUID id : new HashSet<>(boosted)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) removeBoosts(player);
        }
    }

    private World hub() { return Bukkit.getWorld("hub"); }

    private boolean inHub(Player player) { return player.getWorld().getName().equals("hub"); }

    private void tick() {
        World world = hub();
        if (world == null) return;
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setTime(6000L);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setStorm(false);
        world.setThundering(false);
        keepHubDisplaysLoaded(world);
        ensureSelector(world);
        if (boards.size() != 3 || boards.stream().anyMatch(board -> !board.isValid())) ensureBoards(world);
        if (refreshes++ % 6 == 0) refreshBoards();
        for (Player player : world.getPlayers()) preparePlayer(player);
    }

    private void keepHubDisplaysLoaded(World world) {
        keepLoaded(world, -60, -53);
        Location selector = markedSpot(world, "selector", new Location(world, -60.5, -31, -53.5));
        keepLoaded(world, selector.getBlockX(), selector.getBlockZ());
        for (int i = 0; i < BOARD_X.length; i++) {
            Location board = boardSpot(world, i);
            keepLoaded(world, board.getBlockX(), board.getBlockZ());
        }
    }

    private void keepLoaded(World world, int x, int z) {
        Chunk chunk = world.getChunkAt(x >> 4, z >> 4);
        if (hubChunks.add(chunk)) chunk.addPluginChunkTicket(plugin);
    }

    private void ensureSelector(World world) {
        Location spot = markedSpot(world, "selector", new Location(world, -60.5, -31, -53.5));
        List<Villager> existing = new ArrayList<>();
        for (Villager villager : world.getEntitiesByClass(Villager.class))
            if (villager.getScoreboardTags().contains(SELECTOR_TAG)) existing.add(villager);
        Villager selector = existing.isEmpty() ? world.spawn(spot, Villager.class) : existing.remove(0);
        if (selector.getLocation().distanceSquared(spot) > 0.1) selector.teleport(spot);
        selector.setAI(false);
        selector.setInvulnerable(true);
        selector.setSilent(true);
        selector.setCollidable(false);
        selector.setRemoveWhenFarAway(false);
        selector.setProfession(Villager.Profession.LIBRARIAN);
        selector.setCustomName(ChatColor.AQUA + "World Selector " + ChatColor.GRAY + "(Right-click)");
        selector.setCustomNameVisible(true);
        selector.addScoreboardTag(SELECTOR_TAG);
        for (Villager extra : existing) extra.remove();
    }

    private Location markedSpot(World world, String key, Location fallback) {
        YamlConfiguration data = YamlConfiguration.loadConfiguration(marksFile);
        String path = "spots." + key + ".";
        if (!data.contains(path + "x") || !data.contains(path + "y") || !data.contains(path + "z")) return fallback;
        return new Location(world, data.getDouble(path + "x"), data.getDouble(path + "y"),
                data.getDouble(path + "z"), (float) data.getDouble(path + "yaw"), 0f);
    }

    private Location boardSpot(World world, int index) {
        return markedSpot(world, MARK_KEYS[index + 1], new Location(world, BOARD_X[index], -27.5, -58.5));
    }

    private void ensureBoards(World world) {
        for (TextDisplay board : boards) if (board.isValid()) board.remove();
        boards.clear();
        for (Entity entity : world.getNearbyEntities(new Location(world, -61.5, -27.5, -57.5), 20, 10, 12))
            if (entity instanceof TextDisplay && entity.getScoreboardTags().contains(BOARD_TAG)) entity.remove();
        for (int i = 0; i < BOARD_X.length; i++) {
            TextDisplay board = world.spawn(boardSpot(world, i), TextDisplay.class);
            board.setBillboard(Display.Billboard.CENTER);
            board.setSeeThrough(true);
            board.setShadowed(true);
            board.setLineWidth(230);
            board.setPersistent(false);
            board.addScoreboardTag(BOARD_TAG);
            boards.add(board);
        }
        refreshBoards();
    }

    private void refreshBoards() {
        if (boards.size() != 3) return;
        var bedwars = Bukkit.getPluginManager().getPlugin("BedWars");
        if (bedwars == null) return;
        File statsFile = new File(bedwars.getDataFolder(), "database/bw_stats_players.yml");
        if (!statsFile.isFile()) return;
        ConfigurationSection data = YamlConfiguration.loadConfiguration(statsFile).getConfigurationSection("data");
        if (data == null) return;
        List<Stats> stats = new ArrayList<>();
        for (String id : data.getKeys(false)) {
            String name = data.getString(id + ".name", "Unknown");
            stats.add(new Stats(name, data.getInt(id + ".wins"),
                    data.getInt(id + ".destroyedBeds"), data.getInt(id + ".kills")));
        }
        setBoard(0, stats, Stats::wins);
        setBoard(1, stats, Stats::beds);
        setBoard(2, stats, Stats::kills);
    }

    private void setBoard(int index, List<Stats> stats, ToIntFunction<Stats> value) {
        List<Stats> ranking = new ArrayList<>(stats);
        ranking.sort(Comparator.comparingInt(value).reversed().thenComparing(Stats::name, String.CASE_INSENSITIVE_ORDER));
        StringBuilder text = new StringBuilder(ChatColor.GOLD + "" + ChatColor.BOLD + "BEDWARS " + HEADINGS[index]);
        text.append('\n').append(ChatColor.GRAY).append("Top players");
        for (int i = 0; i < Math.min(5, ranking.size()); i++) {
            Stats player = ranking.get(i);
            text.append('\n').append(ChatColor.YELLOW).append(i + 1).append(". ")
                    .append(ChatColor.WHITE).append(player.name()).append(ChatColor.GRAY).append("  •  ")
                    .append(ChatColor.AQUA).append(value.applyAsInt(player));
        }
        if (ranking.isEmpty()) text.append('\n').append(ChatColor.GRAY).append("No matches yet");
        boards.get(index).setText(text.toString());
    }

    private void preparePlayer(Player player) {
        removeOldCompass(player);
        removeMarkerStick(player);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 500, 0, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 500, 1, false, false, true));
        boosted.add(player.getUniqueId());
    }

    private void removeMarkerStick(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isMarker(contents[slot])) player.getInventory().setItem(slot, null);
        }
    }

    private void removeOldCompass(Player player) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType() != Material.COMPASS || !item.hasItemMeta()
                    || !item.getItemMeta().hasDisplayName()) continue;
            String title = ChatColor.stripColor(item.getItemMeta().getDisplayName());
            if (title.equals("World Selector (Right-Click)")) player.getInventory().setItem(slot, null);
        }
    }

    private ItemStack newMarker() {
        ItemStack stick = new ItemStack(Material.STICK);
        ItemMeta meta = stick.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Hub Placement Stick");
        meta.setLore(List.of(ChatColor.GRAY + "Right-click the floor where an object belongs.",
                ChatColor.GRAY + "Choose the villager or leaderboard in the menu.",
                ChatColor.YELLOW + "You can mark the same object again to move it."));
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
        stick.setItemMeta(meta);
        return stick;
    }

    private boolean isMarker(ItemStack item) {
        return item != null && item.getType() == Material.STICK && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(markerKey, PersistentDataType.BYTE);
    }

    private void giveMarker(Player player) {
        if (!player.isOnline() || !inHub(player) || !player.hasPermission("bedwarsworld5.setup")) return;
        for (ItemStack item : player.getInventory().getContents()) if (isMarker(item)) return;
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(ChatColor.YELLOW + "Free one inventory slot to receive the Hub Placement Stick.");
            return;
        }
        player.getInventory().addItem(newMarker());
        player.sendMessage(ChatColor.GREEN + "Hub Placement Stick added. Right-click the floor and choose what goes there.");
    }

    @EventHandler public void onMarkBlock(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isMarker(event.getItem())) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!inHub(player) || !player.hasPermission("bedwarsworld5.setup")) {
            player.sendMessage(ChatColor.RED + "Use this stick in the main hub as an operator.");
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) block = player.getTargetBlockExact(30);
        if (block == null || block.getType().isAir()) {
            player.sendMessage(ChatColor.RED + "Right-click the floor below the desired position.");
            return;
        }
        Location floor = new Location(player.getWorld(), block.getX() + 0.5,
                block.getBoundingBox().getMaxY(), block.getZ() + 0.5, player.getLocation().getYaw(), 0f);
        pendingMarks.put(player.getUniqueId(), floor);
        var menu = Bukkit.createInventory(null, 27, MARK_MENU);
        for (int i = 0; i < MARK_KEYS.length; i++) {
            ItemStack icon = new ItemStack(MARK_ICONS[i]);
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName(ChatColor.YELLOW + MARK_NAMES[i]);
            meta.setLore(List.of(ChatColor.GRAY + (i == 0 ? "Stands on the clicked floor."
                    : "Displays 2.5 blocks above the clicked floor.")));
            icon.setItemMeta(meta);
            menu.setItem(10 + i, icon);
        }
        player.openInventory(menu);
    }

    @EventHandler public void onMarkChoice(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(MARK_MENU)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.hasPermission("bedwarsworld5.setup")
                || event.getClickedInventory() != event.getView().getTopInventory()) return;
        int index = event.getSlot() - 10;
        if (index < 0 || index >= MARK_KEYS.length) return;
        Location floor = pendingMarks.remove(player.getUniqueId());
        if (floor == null || !inHub(player)) return;
        Location position = floor.clone().add(0, index == 0 ? 0 : 2.5, 0);
        YamlConfiguration data = YamlConfiguration.loadConfiguration(marksFile);
        String path = "spots." + MARK_KEYS[index] + ".";
        data.set(path + "x", position.getX());
        data.set(path + "y", position.getY());
        data.set(path + "z", position.getZ());
        data.set(path + "yaw", position.getYaw());
        try {
            data.save(marksFile);
        } catch (IOException exception) {
            player.sendMessage(ChatColor.RED + "Could not save the hub mark. Check the server log.");
            plugin.getLogger().severe("Could not save hub placement: " + exception.getMessage());
            return;
        }
        keepLoaded(player.getWorld(), position.getBlockX(), position.getBlockZ());
        if (index == 0) ensureSelector(player.getWorld());
        else ensureBoards(player.getWorld());
        int count = 0;
        for (String key : MARK_KEYS) if (data.contains("spots." + key + ".x")) count++;
        player.closeInventory();
        player.sendMessage(ChatColor.GREEN + "Placed " + MARK_NAMES[index] + " at "
                + position.getX() + ", " + position.getY() + ", " + position.getZ()
                + ChatColor.GRAY + " (" + count + "/4 marked)." );
    }

    @EventHandler public void onMarkDrag(InventoryDragEvent event) {
        if (event.getView().getTitle().equals(MARK_MENU)) event.setCancelled(true);
    }

    private void removeBoosts(Player player) {
        if (!boosted.remove(player.getUniqueId())) return;
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline() && inHub(event.getPlayer())) {
                preparePlayer(event.getPlayer());
            }
        }, 5L);
    }

    @EventHandler public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (inHub(player)) {
            preparePlayer(player);
        }
        else if (event.getFrom().getName().equals("hub")) removeBoosts(player);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { removeBoosts(event.getPlayer()); }

    @EventHandler public void onVillager(PlayerInteractEntityEvent event) {
        if (isMarker(event.getPlayer().getInventory().getItemInMainHand())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.YELLOW + "Right-click the floor to place the villager.");
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND || !inHub(event.getPlayer())
                || !event.getRightClicked().getScoreboardTags().contains(SELECTOR_TAG)) return;
        event.setCancelled(true);
        Bukkit.dispatchCommand(event.getPlayer(), "servergui");
    }
}
