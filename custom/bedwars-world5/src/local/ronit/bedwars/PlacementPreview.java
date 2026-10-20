package local.ronit.bedwars;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.screamingsandals.bedwars.Main;
import org.screamingsandals.bedwars.api.game.GameStatus;
import org.screamingsandals.bedwars.game.Game;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Temporary, nonpersistent position preview that hides during a running match. */
final class PlacementPreview implements Listener {
    private static final String DEFAULT_ARENA = "world5";
    private static final String TAG = "bw5_placement_preview";
    private record Point(String id, Location location, String label, boolean villager, List<Material> items) {}

    private final JavaPlugin plugin;
    private final Map<String, List<UUID>> spawned = new HashMap<>();
    private List<Point> points = List.of();

    PlacementPreview(JavaPlugin plugin) { this.plugin = plugin; }

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 40L, 40L);
    }

    boolean command(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bedwarsworld5.setup")) {
            sender.sendMessage(ChatColor.RED + "Operator permission required.");
            return true;
        }
        String action = args.length == 0 ? "status" : args[0].toLowerCase();
        if (action.equals("on")) {
            String arena = args.length > 1 ? args[1].toLowerCase()
                    : sender instanceof Player player && supported(player.getWorld().getName())
                    ? player.getWorld().getName() : DEFAULT_ARENA;
            if (!supported(arena)) {
                sender.sendMessage(ChatColor.RED + "Choose world5, bwairshow, bwxemtori, bwgardens, bwaquarium, or bwlighthouse.");
                return true;
            }
            clear();
            plugin.getConfig().set("placement-preview-arena", arena);
            plugin.getConfig().set("placement-preview-enabled", true);
            plugin.saveConfig();
            points = readPoints();
            refresh();
            sender.sendMessage(ChatColor.GREEN + arena + " placement preview enabled. Use /bwpreview off when finished.");
        } else if (action.equals("off")) {
            plugin.getConfig().set("placement-preview-enabled", false);
            plugin.saveConfig();
            clear();
            sender.sendMessage(ChatColor.YELLOW + "Placement preview removed.");
        } else if (action.equals("status")) {
            sender.sendMessage(previewArena() + " placement preview: "
                    + (plugin.getConfig().getBoolean("placement-preview-enabled", false) ? "on" : "off")
                    + "; locations: " + points.size());
        } else sender.sendMessage(ChatColor.YELLOW + "Use /bwpreview [on [world5|bwairshow|bwxemtori|bwgardens|bwaquarium|bwlighthouse]|off|status].");
        return true;
    }

    private boolean supported(String arena) {
        return arena.equals("world5") || arena.equals("bwairshow") || arena.equals("bwxemtori")
                || arena.equals("bwgardens") || arena.equals("bwaquarium") || arena.equals("bwlighthouse");
    }

    private String previewArena() {
        String arena = plugin.getConfig().getString("placement-preview-arena", DEFAULT_ARENA);
        return supported(arena) ? arena : DEFAULT_ARENA;
    }

    private void refresh() {
        Game game = Main.getGame(previewArena());
        boolean safe = game != null && game.getStatus() == GameStatus.WAITING;
        if (!plugin.getConfig().getBoolean("placement-preview-enabled", false) || !safe) {
            clear();
            return;
        }
        if (points.isEmpty()) points = readPoints();
        for (Point point : points) {
            List<UUID> ids = spawned.get(point.id());
            boolean nearby = Bukkit.getOnlinePlayers().stream().anyMatch(player ->
                    player.getWorld() == point.location().getWorld()
                            && player.getLocation().distanceSquared(point.location()) < 128 * 128);
            if (!nearby) {
                remove(ids);
                spawned.remove(point.id());
                continue;
            }
            if (ids != null && ids.stream().allMatch(id -> Bukkit.getEntity(id) != null)) continue;
            remove(ids);
            spawned.put(point.id(), spawn(point));
        }
    }

    private Location parse(World world, String encoded) {
        if (encoded == null) return null;
        String[] values = encoded.split(";");
        if (values.length < 3) return null;
        try {
            return new Location(world, Double.parseDouble(values[0]), Double.parseDouble(values[1]),
                    Double.parseDouble(values[2]));
        } catch (NumberFormatException e) { return null; }
    }

    private List<Point> readPoints() {
        String arenaName = previewArena();
        World world = Bukkit.getWorld(arenaName);
        if (world == null) return List.of();
        File file = new File(Bukkit.getPluginManager().getPlugin("BedWars").getDataFolder(),
                "arenas/" + arenaName + ".yml");
        YamlConfiguration arena = YamlConfiguration.loadConfiguration(file);
        List<Point> result = new ArrayList<>();
        List<String> teams = new ArrayList<>();
        var teamSection = arena.getConfigurationSection("teams");
        if (teamSection != null) for (String key : teamSection.getKeys(false))
            teams.add(teamSection.getString(key + ".actualName", key));
        List<Map<?, ?>> stores = new ArrayList<>(arena.getMapList("stores"));
        for (int i = 0; i < Math.min(teams.size(), stores.size()); i++) {
            String team = teams.get(i);
            Location shop = parse(world, String.valueOf(stores.get(i).get("loc")));
            if (shop != null) result.add(new Point("item-" + team, shop, team + " Item Shop", true, List.of()));
            Location upgrade = parse(world, plugin.getConfig().getString("upgrade-shop-locations."
                    + arenaName + "." + team));
            if (upgrade != null) result.add(new Point("upgrade-" + team, upgrade,
                    team + " Team Upgrades", true, List.of()));
        }
        Map<String, Point> forges = new LinkedHashMap<>();
        int diamond = 0, emerald = 0;
        for (Map<?, ?> spawner : arena.getMapList("spawners")) {
            String type = String.valueOf(spawner.get("type"));
            Location at = parse(world, String.valueOf(spawner.get("location")));
            if (at == null) continue;
            if (type.equals("iron") || type.equals("gold")) {
                String team = String.valueOf(spawner.get("team"));
                String id = "forge-" + team;
                Point previous = forges.get(id);
                List<Material> materials = new ArrayList<>(previous == null ? List.of() : previous.items());
                materials.add(type.equals("iron") ? Material.IRON_INGOT : Material.GOLD_INGOT);
                forges.put(id, new Point(id, at, team + " Iron + Gold Generator", false, materials));
            } else if (type.equals("diamond")) {
                result.add(new Point("diamond-" + ++diamond, at, "Diamond Generator " + diamond,
                        false, List.of(Material.DIAMOND)));
            } else if (type.equals("emerald")) {
                result.add(new Point("emerald-" + ++emerald, at, "Emerald Generator " + emerald,
                        false, List.of(Material.EMERALD)));
            }
        }
        result.addAll(forges.values());
        plugin.getLogger().info("Loaded " + result.size() + " " + arenaName + " placement preview points.");
        return result;
    }

    private List<UUID> spawn(Point point) {
        List<UUID> ids = new ArrayList<>();
        World world = point.location().getWorld();
        if (point.villager()) {
            Villager villager = world.spawn(point.location(), Villager.class);
            villager.setAI(false);
            villager.setInvulnerable(true);
            villager.setSilent(true);
            villager.setPersistent(false);
            villager.setCustomName(ChatColor.AQUA + point.label() + ChatColor.GRAY + " [Preview]");
            villager.setCustomNameVisible(true);
            villager.addScoreboardTag(TAG);
            ids.add(villager.getUniqueId());
        } else {
            int count = point.items().size();
            for (int i = 0; i < count; i++) {
                Location itemLocation = point.location().clone().add(count == 1 ? 0 : (i == 0 ? -0.2 : 0.2), 0.15, 0);
                Item item = world.dropItem(itemLocation, new ItemStack(point.items().get(i)));
                item.setVelocity(new Vector(0, 0, 0));
                item.setPickupDelay(Integer.MAX_VALUE);
                item.setPersistent(false);
                item.addScoreboardTag(TAG);
                ids.add(item.getUniqueId());
            }
            TextDisplay label = world.spawn(point.location().clone().add(0, 1.5, 0), TextDisplay.class);
            label.setText(ChatColor.YELLOW + point.label() + ChatColor.GRAY + " [Preview]");
            label.setBillboard(Display.Billboard.CENTER);
            label.setSeeThrough(true);
            label.setPersistent(false);
            label.addScoreboardTag(TAG);
            ids.add(label.getUniqueId());
        }
        return ids;
    }

    private void remove(List<UUID> ids) {
        if (ids == null) return;
        for (UUID id : ids) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) entity.remove();
        }
    }

    void clear() {
        for (List<UUID> ids : spawned.values()) remove(ids);
        spawned.clear();
    }

    @EventHandler public void onVillager(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getRightClicked().getScoreboardTags().contains(TAG)) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        Location at = event.getRightClicked().getLocation();
        player.sendMessage(ChatColor.AQUA + "Preview villager at " + at.getX() + ", " + at.getY() + ", " + at.getZ());
    }
}
