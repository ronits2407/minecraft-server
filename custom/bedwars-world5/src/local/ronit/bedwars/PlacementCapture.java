package local.ronit.bedwars;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Records operator-selected arena coordinates without changing a live game. */
final class PlacementCapture implements Listener {
    private static final String TYPE_MENU = "BedWars Mark: What is here?";
    private static final String TEAM_MENU = "BedWars Mark: Which team?";
    private static final String[] TEAMS = {"Red", "Blue", "Green", "Yellow", "Pink", "Aqua", "White", "Gray"};
    private static final Material[] TEAM_ITEMS = {Material.RED_WOOL, Material.BLUE_WOOL, Material.GREEN_WOOL,
            Material.YELLOW_WOOL, Material.PINK_WOOL, Material.LIGHT_BLUE_WOOL, Material.WHITE_WOOL, Material.GRAY_WOOL};
    private static final Kind[] KINDS = {
            new Kind(11, "spawn", "Player spawn", Material.ARMOR_STAND, true, true),
            new Kind(12, "iron", "Iron generator", Material.IRON_INGOT, true, true),
            new Kind(13, "gold", "Gold generator", Material.GOLD_INGOT, true, true),
            new Kind(14, "item_shop", "Item Shop villager", Material.EMERALD, true, true),
            new Kind(15, "upgrade_shop", "Team Upgrades villager", Material.DIAMOND, true, true),
            new Kind(21, "diamond", "Diamond generator", Material.DIAMOND_BLOCK, false, true),
            new Kind(22, "emerald", "Emerald generator", Material.EMERALD_BLOCK, false, true)
    };

    private record Kind(int slot, String key, String label, Material icon, boolean team, boolean aboveBlock) {}
    private record Selection(Location location, String source, Material blockType) {}
    private record Pending(Selection selection, Kind kind) {}

    private final JavaPlugin plugin;
    private final NamespacedKey markerKey;
    private final File output;
    private final Map<UUID, Selection> selections = new HashMap<>();
    private final Map<UUID, Pending> pending = new HashMap<>();

    PlacementCapture(JavaPlugin plugin) {
        this.plugin = plugin;
        markerKey = new NamespacedKey(plugin, "location_marker");
        output = new File(plugin.getDataFolder(), "placement-marks.yml");
    }

    private boolean isBedwarsWorld(Player player) {
        String name = player.getWorld().getName();
        return name.equals("world5") || name.equals("bwxemtori") || name.equals("bwairshow")
                || name.equals("bwgardens") || name.equals("bwaquarium") || name.equals("bwlighthouse");
    }

    private ItemStack newMarker() {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "BedWars Location Marker");
        meta.setLore(List.of(ChatColor.GRAY + "Right-click the exact block or floor.",
                ChatColor.GRAY + "Choose its type and team in the menus.",
                ChatColor.YELLOW + "The floor click records the space above it."));
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
        wand.setItemMeta(meta);
        return wand;
    }

    boolean command(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Run this in game."); return true; }
        if (!player.hasPermission("bedwarsworld5.setup")) { player.sendMessage(ChatColor.RED + "Operator permission required."); return true; }
        String action = args.length == 0 ? "give" : args[0].toLowerCase();
        if (action.equals("give")) {
            player.getInventory().addItem(newMarker()).values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            player.sendMessage(ChatColor.GREEN + "Marker given. Right-click a block, then choose its type and team.");
        } else if (action.equals("status")) {
            YamlConfiguration data = YamlConfiguration.loadConfiguration(output);
            int count = data.getConfigurationSection("marks") == null ? 0 : data.getConfigurationSection("marks").getKeys(false).size();
            player.sendMessage(ChatColor.AQUA + "Saved BedWars marks: " + count
                    + " in " + output.getName() + ".");
        } else if (action.equals("undo")) {
            YamlConfiguration data = YamlConfiguration.loadConfiguration(output);
            int last = data.getInt("next-id", 1) - 1;
            if (last < 1 || !data.contains("marks." + last)) player.sendMessage(ChatColor.YELLOW + "No mark to undo.");
            else {
                data.set("marks." + last, null);
                data.set("next-id", last);
                if (save(data, player)) player.sendMessage(ChatColor.YELLOW + "Removed mark #" + last + ".");
            }
        } else player.sendMessage(ChatColor.YELLOW + "Use /bwmark [give|status|undo].");
        return true;
    }

    private boolean isMarker(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(markerKey, PersistentDataType.BYTE);
    }

    private boolean supported(Player player) {
        if (isBedwarsWorld(player)) return true;
        player.sendMessage(ChatColor.RED + "Use the marker inside a BedWars map.");
        return false;
    }

    @EventHandler public void onBlock(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isMarker(event.getItem()) || !event.getPlayer().hasPermission("bedwarsworld5.setup")) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!supported(player)) return;
        Block block = event.getClickedBlock();
        if (block == null) block = player.getTargetBlockExact(30);
        if (block == null || block.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "Aim at and right-click a solid block. For a villager, click the floor where it should stand.");
            return;
        }
        selections.put(player.getUniqueId(), new Selection(block.getLocation(), "block", block.getType()));
        openTypes(player);
    }

    @EventHandler public void onEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isMarker(event.getPlayer().getInventory().getItemInMainHand())
                || !event.getPlayer().hasPermission("bedwarsworld5.setup")) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!supported(player)) return;
        Entity entity = event.getRightClicked();
        selections.put(player.getUniqueId(), new Selection(entity.getLocation(), "entity:" + entity.getType(), Material.AIR));
        openTypes(player);
    }

    private ItemStack item(Material material, String title, String detail) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + title);
        meta.setLore(List.of(ChatColor.GRAY + detail));
        stack.setItemMeta(meta);
        return stack;
    }

    private void openTypes(Player player) {
        Inventory menu = Bukkit.createInventory(null, 36, TYPE_MENU);
        for (Kind kind : KINDS) menu.setItem(kind.slot(), item(kind.icon(), kind.label(),
                kind.aboveBlock() ? "Click floor: records the block above." : "Click the object block itself."));
        player.openInventory(menu);
    }

    private void openTeams(Player player) {
        Inventory menu = Bukkit.createInventory(null, 27, TEAM_MENU);
        int count = teamCount(player);
        for (int i = 0; i < count; i++) menu.setItem(9 + i, item(TEAM_ITEMS[i], TEAMS[i], "Save for " + TEAMS[i]));
        player.openInventory(menu);
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!title.equals(TYPE_MENU) && !title.equals(TEAM_MENU)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.hasPermission("bedwarsworld5.setup")
                || event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (title.equals(TYPE_MENU)) {
            Selection selected = selections.get(player.getUniqueId());
            if (selected == null) return;
            for (Kind kind : KINDS) if (kind.slot() == event.getSlot()) {
                if (selected.source().startsWith("entity:") && !kind.key().equals("item_shop") && !kind.key().equals("upgrade_shop")) {
                    player.sendMessage(ChatColor.RED + "Entity clicks can only mark villagers. Click a block for this type.");
                    return;
                }
                Pending mark = new Pending(selected, kind);
                if (kind.team()) { pending.put(player.getUniqueId(), mark); openTeams(player); }
                else { saveMark(player, mark, null); player.closeInventory(); }
                return;
            }
        } else {
            Pending mark = pending.remove(player.getUniqueId());
            if (mark == null) return;
            int index = event.getSlot() - 9;
            int count = teamCount(player);
            if (index < 0 || index >= count) { pending.put(player.getUniqueId(), mark); return; }
            saveMark(player, mark, TEAMS[index]);
            player.closeInventory();
        }
    }

    private int teamCount(Player player) {
        String world = player.getWorld().getName();
        return world.equals("bwgardens") ? 2
                : world.equals("bwxemtori") || world.equals("bwaquarium") ? 4 : 8;
    }

    @EventHandler public void onDrag(InventoryDragEvent event) {
        String title = event.getView().getTitle();
        if (title.equals(TYPE_MENU) || title.equals(TEAM_MENU)) event.setCancelled(true);
    }

    private void saveMark(Player player, Pending mark, String team) {
        Selection selection = mark.selection();
        Kind kind = mark.kind();
        Location location = selection.location().clone();
        if (!selection.source().startsWith("entity:")) {
            location.setX(location.getBlockX() + 0.5);
            location.setZ(location.getBlockZ() + 0.5);
            if (kind.aboveBlock()) location.add(0, 1, 0);
        }
        location.setYaw(player.getLocation().getYaw());
        location.setPitch(0);
        YamlConfiguration data = YamlConfiguration.loadConfiguration(output);
        int id = data.getInt("next-id", 1);
        String path = "marks." + id + ".";
        data.set(path + "time", Instant.now().toString());
        data.set(path + "operator", player.getName());
        data.set(path + "world", location.getWorld().getName());
        data.set(path + "type", kind.key());
        data.set(path + "team", team);
        data.set(path + "x", location.getX());
        data.set(path + "y", location.getY());
        data.set(path + "z", location.getZ());
        data.set(path + "yaw", location.getYaw());
        data.set(path + "source", selection.source());
        data.set(path + "clicked-block", selection.blockType().name());
        data.set("next-id", id + 1);
        if (save(data, player)) player.sendMessage(ChatColor.GREEN + "Saved #" + id + " " + kind.label()
                + (team == null ? "" : " for " + team) + " at " + location.getWorld().getName() + " "
                + location.getX() + ", " + location.getY() + ", " + location.getZ());
        selections.remove(player.getUniqueId());
    }

    private boolean save(YamlConfiguration data, Player player) {
        try { data.save(output); return true; }
        catch (IOException e) {
            player.sendMessage(ChatColor.RED + "Could not save mark; ask an admin to check the server log.");
            plugin.getLogger().severe("Could not save placement marks: " + e.getMessage());
            return false;
        }
    }
}
