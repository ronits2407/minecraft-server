package local.ronit.bedwars;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;
import org.screamingsandals.bedwars.Main;
import org.screamingsandals.bedwars.api.RunningTeam;
import org.screamingsandals.bedwars.api.events.BedwarsGameEndEvent;
import org.screamingsandals.bedwars.api.events.BedwarsGameStartedEvent;
import org.screamingsandals.bedwars.api.events.BedwarsPlayerLeaveEvent;
import org.screamingsandals.bedwars.api.events.BedwarsPlayerRespawnedIngameEvent;
import org.screamingsandals.bedwars.api.game.GameStatus;
import org.screamingsandals.bedwars.api.game.GameStore;
import org.screamingsandals.bedwars.game.Game;
import org.screamingsandals.bedwars.lib.sgui.events.ShopTransactionEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class BedwarsGameplay implements Listener {
    private static final String MENU = "Team Upgrades (Diamonds)";
    private static final String NPC_TAG = "bedwars_team_upgrades";
    private static final String[] ARENAS = {"world5", "bwxemtori", "bwairshow",
            "bwgardens", "bwaquarium", "bwlighthouse"};
    private static final int[] PROTECTION_COST = {2, 4, 8, 16};
    private static final int[] TRAP_COST = {1, 2, 4};
    private final JavaPlugin plugin;
    private final Map<String, Map<String, Upgrades>> upgrades = new HashMap<>();
    private final Map<String, Integer> permanentArmor = new HashMap<>();
    private final Map<ShopTransactionEvent, ArmorPurchase> armorPurchases = new IdentityHashMap<>();
    private final Map<UUID, String> openMenus = new HashMap<>();
    private final Map<UUID, Long> nextMusic = new HashMap<>();
    private final Set<UUID> musicPlaying = new HashSet<>();
    private final Set<UUID> fastAttackPlayers = new HashSet<>();
    private final NamespacedKey attackSpeedKey;
    private long ticks;

    private static final class Upgrades {
        boolean sharpness;
        int protection;
        int ironForge;
        int goldForge;
        int trapsBought;
        boolean trapArmed;
    }

    private record ArmorPurchase(int currencyBefore, int armorBefore, Material armor, Material currency) {}

    BedwarsGameplay(JavaPlugin plugin) {
        this.plugin = plugin;
        this.attackSpeedKey = new NamespacedKey(plugin, "bedwars_no_cooldown");
    }

    void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        applyBlastResistance();
        for (String name : ARENAS) {
            World world = Bukkit.getWorld(name);
            if (world != null) for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                if (villager.getScoreboardTags().contains(NPC_TAG)) villager.remove();
            }
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
    }

    private Game game(Player player) {
        if (!Main.isPlayerInGame(player)) return null;
        var profile = Main.getPlayerGameProfile(player);
        if (profile == null || !(profile.getGame() instanceof Game game)) return null;
        return supported(game) && game.getStatus() == GameStatus.RUNNING ? game : null;
    }

    private boolean supported(org.screamingsandals.bedwars.api.game.Game game) {
        if (game == null) return false;
        for (String arena : ARENAS) if (arena.equals(game.getName())) return true;
        return false;
    }

    private boolean bedwarsWorld(World world) {
        if (world == null) return false;
        for (String arena : ARENAS) if (arena.equals(world.getName())) return true;
        return false;
    }

    private String key(Game game, RunningTeam team) { return game.getName() + ":" + team.getName(); }

    private String armorKey(org.screamingsandals.bedwars.api.game.Game game, Player player) {
        return game.getName() + ":" + player.getUniqueId();
    }

    private Upgrades state(Game game, RunningTeam team) {
        return upgrades.computeIfAbsent(game.getName(), ignored -> new HashMap<>())
                .computeIfAbsent(team.getName(), ignored -> new Upgrades());
    }

    private void tick() {
        ticks += 20;
        Set<UUID> active = new HashSet<>();
        for (String name : ARENAS) {
            Game game = Main.getGame(name);
            if (game == null || game.getStatus() != GameStatus.RUNNING) continue;
            ensureUpgradeVillagers(game);
            for (Player player : game.getConnectedPlayers()) {
                RunningTeam team = game.getTeamOfPlayer(player);
                if (team == null || game.isSpectator(player) || !player.isOnline() || player.isDead()) continue;
                active.add(player.getUniqueId());
                enableFastAttacks(player);
                applyEnchantments(player, state(game, team));
                checkTrap(game, team, player);
                playMusic(player);
            }
        }
        for (UUID id : new HashSet<>(musicPlaying)) if (!active.contains(id)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) player.stopSound(musicSound(), SoundCategory.RECORDS);
            musicPlaying.remove(id);
            nextMusic.remove(id);
        }
        for (UUID id : new HashSet<>(fastAttackPlayers)) if (!active.contains(id)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) disableFastAttacks(player);
            else fastAttackPlayers.remove(id);
        }
    }

    private void enableFastAttacks(Player player) {
        AttributeInstance speed = player.getAttribute(Attribute.ATTACK_SPEED);
        if (speed == null) return;
        if (speed.getModifier(attackSpeedKey) == null) {
            speed.addTransientModifier(new AttributeModifier(attackSpeedKey, 100.0,
                    AttributeModifier.Operation.ADD_NUMBER));
        }
        fastAttackPlayers.add(player.getUniqueId());
    }

    private void disableFastAttacks(Player player) {
        AttributeInstance speed = player.getAttribute(Attribute.ATTACK_SPEED);
        if (speed != null && speed.getModifier(attackSpeedKey) != null) speed.removeModifier(attackSpeedKey);
        fastAttackPlayers.remove(player.getUniqueId());
    }

    private void ensureUpgradeVillagers(Game game) {
        for (GameStore store : game.getGameStores()) {
            Location shop = store.getStoreLocation();
            if (shop != null) {
                RunningTeam nearest = null;
                double bestDistance = 400;
                for (RunningTeam team : game.getRunningTeams()) {
                    Location spawn = team.getTeamSpawn();
                    if (spawn == null || spawn.getWorld() != shop.getWorld()) continue;
                    double distance = spawn.distanceSquared(shop);
                    if (distance < bestDistance) { nearest = team; bestDistance = distance; }
                }
                if (nearest == null) continue;
                String teamName = nearest.getName();
                String tag = NPC_TAG + ":" + game.getName() + ":" + teamName;
                Location spot = configuredUpgradeSpot(shop.getWorld(), game.getName(), teamName);
                if (spot == null) spot = findSpot(shop);
                if (spot != null) {
                    Villager existing = null;
                    for (Villager villager : spot.getWorld().getEntitiesByClass(Villager.class)) {
                        if (!villager.getScoreboardTags().contains(tag)) continue;
                        if (existing == null) existing = villager;
                        else villager.remove();
                    }
                    if (existing != null) {
                        if (existing.getLocation().distanceSquared(spot) > 0.25) existing.teleport(spot);
                        continue;
                    }
                    Villager villager = spot.getWorld().spawn(spot, Villager.class);
                    villager.setAI(false);
                    villager.setInvulnerable(true);
                    villager.setSilent(true);
                    villager.setRemoveWhenFarAway(false);
                    villager.setCustomName(ChatColor.AQUA + "Team Upgrades " + ChatColor.GRAY + "(Diamonds)");
                    villager.setCustomNameVisible(true);
                    villager.addScoreboardTag(NPC_TAG);
                    villager.addScoreboardTag(tag);
                }
            }
        }
    }

    private Location configuredUpgradeSpot(World world, String arenaName, String teamName) {
        if (world == null) return null;
        String encoded = plugin.getConfig().getString("upgrade-shop-locations." + arenaName + "." + teamName);
        if (encoded == null) return null;
        String[] parts = encoded.split(";");
        if (parts.length < 3) return null;
        try {
            return new Location(world, Double.parseDouble(parts[0]), Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]), parts.length > 3 ? Float.parseFloat(parts[3]) : 0f, 0f);
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Invalid upgrade shop location for " + arenaName + "/" + teamName);
            return null;
        }
    }

    private Location findSpot(Location shop) {
        for (int[] offset : new int[][]{{2,0},{-2,0},{0,2},{0,-2},{1,0},{-1,0},{0,1},{0,-1}}) {
            Location spot = shop.clone().add(offset[0], 0, offset[1]);
            if (spot.getBlock().isPassable() && spot.clone().add(0,1,0).getBlock().isPassable()
                    && spot.clone().add(0,-1,0).getBlock().getType().isSolid()) return spot;
        }
        return null;
    }

    private Sound musicSound() {
        try { return Sound.valueOf(plugin.getConfig().getString("bedwars-music.sound", "MUSIC_DISC_PIGSTEP")); }
        catch (IllegalArgumentException ex) { return Sound.MUSIC_DISC_PIGSTEP; }
    }

    private void playMusic(Player player) {
        if (!plugin.getConfig().getBoolean("bedwars-music.enabled", true)) return;
        UUID id = player.getUniqueId();
        if (ticks < nextMusic.getOrDefault(id, 0L)) return;
        player.playSound(player.getLocation(), musicSound(), SoundCategory.RECORDS,
                (float) plugin.getConfig().getDouble("bedwars-music.volume", 0.7), 1f);
        nextMusic.put(id, ticks + plugin.getConfig().getLong("bedwars-music.repeat-ticks", 2960L));
        musicPlaying.add(id);
    }

    private void applyEnchantments(Player player, Upgrades state) {
        if (state.sharpness) for (int slot = 0; slot < player.getInventory().getStorageContents().length; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack != null && stack.getType().name().endsWith("_SWORD")
                    && stack.getEnchantmentLevel(Enchantment.SHARPNESS) < 1) {
                stack.addEnchantment(Enchantment.SHARPNESS, 1);
                player.getInventory().setItem(slot, stack);
            }
        }
        ItemStack[] armor = player.getInventory().getArmorContents();
        boolean changed = false;
        if (state.protection > 0) for (ItemStack stack : armor) {
            if (stack != null && stack.getType() != Material.AIR
                    && stack.getEnchantmentLevel(Enchantment.PROTECTION) < state.protection) {
                stack.addEnchantment(Enchantment.PROTECTION, state.protection);
                changed = true;
            }
        }
        if (changed) player.getInventory().setArmorContents(armor);
    }

    private void checkTrap(Game game, RunningTeam attackerTeam, Player attacker) {
        for (RunningTeam defending : game.getRunningTeams()) {
            if (defending == attackerTeam || !defending.isTargetBlockExists()) continue;
            Upgrades state = state(game, defending);
            if (!state.trapArmed) continue;
            Location bed = defending.getTargetBlock();
            if (bed == null || bed.getWorld() != attacker.getWorld()
                    || bed.distanceSquared(attacker.getLocation()) > 64) continue;
            state.trapArmed = false;
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 160, 0));
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 160, 0));
            attacker.removePotionEffect(PotionEffectType.INVISIBILITY);
            attacker.sendMessage(ChatColor.RED + "You triggered a blindness trap!");
            for (Player teammate : defending.getConnectedPlayers()) {
                teammate.sendMessage(ChatColor.RED + "TRAP TRIGGERED! An enemy is near your bed.");
                teammate.playSound(teammate.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.6f);
            }
        }
    }

    @EventHandler public void onGameStart(BedwarsGameStartedEvent event) {
        if (!supported(event.getGame())) return;
        upgrades.remove(event.getGame().getName());
        if (event.getGame() instanceof Game game) for (var spawner : game.getSpawners()) {
            if (spawner.getTeam() != null) spawner.setCurrentLevel(spawner.getStartLevel());
        }
        permanentArmor.keySet().removeIf(key -> key.startsWith(event.getGame().getName() + ":"));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player player : event.getGame().getConnectedPlayers()) {
                giveSword(player);
                if (game(player) != null) enableFastAttacks(player);
            }
        }, 2L);
    }

    @EventHandler public void onRespawn(BedwarsPlayerRespawnedIngameEvent event) {
        if (supported(event.getGame())) Bukkit.getScheduler().runTaskLater(plugin,
                () -> {
                    giveSword(event.getPlayer());
                    restoreArmor(event.getPlayer());
                }, 2L);
    }

    private int armorTier(Material material) {
        return switch (material) {
            case CHAINMAIL_HELMET, CHAINMAIL_CHESTPLATE, CHAINMAIL_LEGGINGS, CHAINMAIL_BOOTS -> 1;
            case IRON_HELMET, IRON_CHESTPLATE, IRON_LEGGINGS, IRON_BOOTS -> 2;
            case DIAMOND_HELMET, DIAMOND_CHESTPLATE, DIAMOND_LEGGINGS, DIAMOND_BOOTS -> 3;
            default -> 0;
        };
    }

    private ItemStack[] fullArmorSet(int tier) {
        return switch (tier) {
            case 1 -> new ItemStack[]{
                    new ItemStack(Material.CHAINMAIL_BOOTS),
                    new ItemStack(Material.CHAINMAIL_LEGGINGS),
                    new ItemStack(Material.CHAINMAIL_CHESTPLATE),
                    new ItemStack(Material.CHAINMAIL_HELMET)
            };
            case 2 -> new ItemStack[]{
                    new ItemStack(Material.IRON_BOOTS),
                    new ItemStack(Material.IRON_LEGGINGS),
                    new ItemStack(Material.IRON_CHESTPLATE),
                    new ItemStack(Material.IRON_HELMET)
            };
            case 3 -> new ItemStack[]{
                    new ItemStack(Material.DIAMOND_BOOTS),
                    new ItemStack(Material.DIAMOND_LEGGINGS),
                    new ItemStack(Material.DIAMOND_CHESTPLATE),
                    new ItemStack(Material.DIAMOND_HELMET)
            };
            default -> null;
        };
    }

    private String tierName(int tier) {
        return switch (tier) {
            case 1 -> "chainmail";
            case 2 -> "iron";
            case 3 -> "diamond";
            default -> "armor";
        };
    }

    private int countItem(Player player, Material material) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) count += stack.getAmount();
        }
        return count;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void beforeArmorPurchase(ShopTransactionEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        Game game = game(player);
        if (game == null) return;
        ItemStack stack = event.getStack();
        if (stack == null || armorTier(stack.getType()) == 0) return;
        int tier = armorTier(stack.getType());
        int currentTier = permanentArmor.getOrDefault(armorKey(game, player), 0);
        if (tier <= currentTier) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You already have this armor tier or better!");
            return;
        }
        Material currency = switch (event.getType().toLowerCase()) {
            case "iron" -> Material.IRON_INGOT;
            case "gold" -> Material.GOLD_INGOT;
            case "emerald" -> Material.EMERALD;
            default -> Material.AIR;
        };
        if (currency == Material.AIR) return;
        armorPurchases.put(event, new ArmorPurchase(countItem(player, currency),
                countItem(player, stack.getType()), stack.getType(), currency));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void afterArmorPurchase(ShopTransactionEvent event) {
        ArmorPurchase purchase = armorPurchases.remove(event);
        if (purchase == null || event.isCancelled()) return;
        Player player = event.getPlayer();
        Game game = game(player);
        if (game == null) return;
        if (countItem(player, purchase.currency()) < purchase.currencyBefore()) {
            int tier = armorTier(purchase.armor());
            permanentArmor.merge(armorKey(game, player), tier, Math::max);
            equipArmor(player, permanentArmor.get(armorKey(game, player)));
            Bukkit.getScheduler().runTask(plugin, () -> equipArmor(player, permanentArmor.get(armorKey(game, player))));
            player.sendMessage(ChatColor.AQUA + "Your " + tierName(tier) + " armor set is now permanent for this match.");
        }
    }

    private void equipArmor(Player player, int tier) {
        if (tier <= 0) return;
        ItemStack[] set = fullArmorSet(tier);
        if (set == null) return;
        for (int i = 0; i < player.getInventory().getStorageContents().length; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && armorTier(item.getType()) > 0) {
                player.getInventory().setItem(i, null);
            }
        }
        player.getInventory().setArmorContents(set);
        Game game = game(player);
        if (game != null) {
            RunningTeam team = game.getTeamOfPlayer(player);
            if (team != null) applyEnchantments(player, state(game, team));
        }
        player.updateInventory();
    }

    private void restoreArmor(Player player) {
        Game game = game(player);
        if (game == null || game.isSpectator(player)) return;
        int tier = permanentArmor.getOrDefault(armorKey(game, player), 0);
        if (tier <= 0) return;
        equipArmor(player, tier);
    }

    private void giveSword(Player player) {
        Game game = game(player);
        if (game == null || game.isSpectator(player) || game.getTeamOfPlayer(player) == null) return;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType().name().endsWith("_SWORD")) return;
        }
        player.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));
    }

    @EventHandler public void onDeath(PlayerDeathEvent event) {
        if (game(event.getEntity()) != null)
            event.getDrops().removeIf(stack -> stack.getType() == Material.WOODEN_SWORD
                    || armorTier(stack.getType()) > 0);
    }

    @EventHandler public void onGameEnd(BedwarsGameEndEvent event) {
        if (!supported(event.getGame())) return;
        for (Player player : event.getGame().getConnectedPlayers()) disableFastAttacks(player);
        permanentArmor.keySet().removeIf(key -> key.startsWith(event.getGame().getName() + ":"));
        upgrades.remove(event.getGame().getName());
        openMenus.clear();
        for (Villager villager : event.getGame().getGameWorld().getEntitiesByClass(Villager.class)) {
            if (villager.getScoreboardTags().contains(NPC_TAG)) villager.remove();
        }
    }

    @EventHandler public void onLeave(BedwarsPlayerLeaveEvent event) {
        if (supported(event.getGame())) {
            disableFastAttacks(event.getPlayer());
            permanentArmor.remove(armorKey(event.getGame(), event.getPlayer()));
        }
    }

    @EventHandler(ignoreCancelled = true) public void onBlockBurn(BlockBurnEvent event) {
        if (bedwarsWorld(event.getBlock().getWorld())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true) public void onFireSpread(BlockIgniteEvent event) {
        if (bedwarsWorld(event.getBlock().getWorld())
                && event.getCause() == BlockIgniteEvent.IgniteCause.SPREAD) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (bedwarsWorld(event.getLocation().getWorld())) {
            event.blockList().removeIf(this::isBlastProofGlass);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (bedwarsWorld(event.getBlock().getWorld())) {
            event.blockList().removeIf(this::isBlastProofGlass);
        }
    }

    private boolean isBlastProofGlass(Block block) {
        return block != null && isBlastProofGlass(block.getType());
    }

    private boolean isBlastProofGlass(Material material) {
        if (material == null) return false;
        String name = material.name();
        return material == Material.GLASS
                || material == Material.TINTED_GLASS
                || material == Material.GLASS_PANE
                || name.endsWith("_STAINED_GLASS")
                || name.endsWith("_STAINED_GLASS_PANE");
    }

    private void applyBlastResistance() {
        try {
            Class<?> craftMagicNumbers = Class.forName("org.bukkit.craftbukkit.util.CraftMagicNumbers");
            java.lang.reflect.Method getBlock = craftMagicNumbers.getMethod("getBlock", Material.class);
            java.lang.reflect.Field field = null;
            for (Material material : Material.values()) {
                if (isBlastProofGlass(material) && material.isBlock()) {
                    Object nmsBlock = getBlock.invoke(null, material);
                    if (nmsBlock != null) {
                        if (field == null) {
                            Class<?> cls = nmsBlock.getClass();
                            while (cls != null && field == null) {
                                try {
                                    field = cls.getField("explosionResistance");
                                } catch (NoSuchFieldException ignored) {
                                    cls = cls.getSuperclass();
                                }
                            }
                        }
                        if (field != null) {
                            field.setAccessible(true);
                            field.setFloat(nmsBlock, 1200.0f);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not apply explosionResistance via reflection: " + t.getMessage());
        }
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        if (fastAttackPlayers.contains(event.getPlayer().getUniqueId())) disableFastAttacks(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true) public void onSweep(EntityDamageByEntityEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK
                && event.getDamager() instanceof Player player && game(player) != null) event.setCancelled(true);
    }

    @EventHandler public void onVillager(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!entity.getScoreboardTags().contains(NPC_TAG)) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        Game game = game(player);
        if (game == null || game.getTeamOfPlayer(player) == null) return;
        String tag = NPC_TAG + ":" + game.getName() + ":" + game.getTeamOfPlayer(player).getName();
        if (!entity.getScoreboardTags().contains(tag)) {
            player.sendMessage(ChatColor.RED + "Use your own team's upgrade shop.");
            return;
        }
        open(player, game);
    }

    private ItemStack icon(Material material, String name, String... details) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        meta.setLore(java.util.Arrays.stream(details)
                .map(line -> ChatColor.translateAlternateColorCodes('&', line)).toList());
        item.setItemMeta(meta);
        return item;
    }

    private String roman(int level) {
        return level < 1 ? "None" : new String[]{"I", "II", "III", "IV"}[Math.min(level, 4) - 1];
    }

    private void upgradeItem(Inventory menu, int slot, Material material, String name,
                             String effect, String current, String next, int cost, boolean maxed) {
        menu.setItem(slot, icon(material, maxed ? "&a" + name : "&e" + name,
                "&7" + effect, "", "&7Current: &f" + current,
                maxed ? "&aMaximum level reached" : "&7Next: &f" + next,
                maxed ? "&7Your team already has this." : "&6Cost: &f" + cost + " Diamonds",
                "", maxed ? "&aPurchased" : "&eClick to purchase"));
    }

    private void open(Player player, Game game) {
        RunningTeam team = game.getTeamOfPlayer(player);
        if (team == null) return;
        Upgrades state = state(game, team);
        Inventory menu = Bukkit.createInventory(null, 54, MENU);
        ItemStack divider = icon(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 9; slot < 18; slot++) menu.setItem(slot, divider);
        for (int slot = 45; slot < 54; slot++) menu.setItem(slot, divider);
        menu.setItem(4, icon(Material.DIAMOND, "&bTeam Upgrades &7| &f" + team.getName(),
                "&7Purchases apply to your whole team.", "&7Diamonds in inventory: &b" + countDiamonds(player)));
        upgradeItem(menu, 19, Material.IRON_SWORD, "Sharpened Swords",
                "Sharpness I on every team sword", state.sharpness ? "I" : "None", "I", 4, state.sharpness);
        upgradeItem(menu, 21, Material.IRON_CHESTPLATE, "Reinforced Armor",
                "Protection on all team armor", roman(state.protection),
                roman(state.protection + 1), state.protection >= 4 ? 0 : PROTECTION_COST[state.protection],
                state.protection >= 4);
        upgradeItem(menu, 23, Material.IRON_BLOCK, "Iron Forge",
                "Your iron generator runs faster", roman(state.ironForge + 1),
                roman(state.ironForge + 2), 2, state.ironForge >= 3);
        upgradeItem(menu, 25, Material.GOLD_BLOCK, "Gold Forge",
                "Your gold generator runs faster", roman(state.goldForge + 1),
                roman(state.goldForge + 2), 4, state.goldForge >= 2);
        menu.setItem(31, icon(Material.TRIPWIRE_HOOK,
                state.trapArmed ? "&aBlindness Trap &7(Armed)" : "&dBlindness Trap",
                "&7One use: blind and slow an enemy", "&7within 8 blocks of your bed.", "",
                "&7Status: " + (state.trapArmed ? "&aArmed" : "&cNot armed"),
                state.trapArmed ? "&aWaiting for an enemy" : "&6Cost: &f" + TRAP_COST[Math.min(state.trapsBought, 2)] + " Diamonds",
                "", state.trapArmed ? "&aPurchased" : "&eClick to purchase"));
        menu.setItem(47, icon(Material.BOOK, "&bHow upgrades work",
                "&7Sword, armor and forge upgrades last", "&7for this match and help your whole team.",
                "&7The trap must be bought again after it fires."));
        menu.setItem(49, icon(Material.DIAMOND, "&bYour Diamonds: &f" + countDiamonds(player),
                "&7Collect these from diamond generators."));
        menu.setItem(51, icon(Material.BARRIER, "&cClose"));
        openMenus.put(player.getUniqueId(), key(game, team));
        player.openInventory(menu);
    }

    private boolean upgradeForge(Game game, RunningTeam team, String resource, int level) {
        boolean found = false;
        for (var spawner : game.getSpawners()) {
            if (spawner.getTeam() == null || !spawner.getTeam().getName().equals(team.getName())
                    || !spawner.getItemSpawnerType().getConfigKey().equalsIgnoreCase(resource)) continue;
            spawner.setCurrentLevel(level);
            spawner.rerenderHologram = true;
            found = true;
        }
        return found;
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(MENU)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()) return;
        Game game = game(player);
        RunningTeam team = game == null ? null : game.getTeamOfPlayer(player);
        if (team == null || !key(game, team).equals(openMenus.get(player.getUniqueId()))) return;
        Upgrades state = state(game, team);
        int slot = event.getSlot();
        int cost;
        String purchase;
        if (slot == 19 && !state.sharpness) { cost = 4; purchase = "Sharpened Swords"; }
        else if (slot == 21 && state.protection < 4) { cost = PROTECTION_COST[state.protection]; purchase = "Reinforced Armor"; }
        else if (slot == 23 && state.ironForge < 3) { cost = 2; purchase = "Iron Forge"; }
        else if (slot == 25 && state.goldForge < 2) { cost = 4; purchase = "Gold Forge"; }
        else if (slot == 31 && !state.trapArmed) { cost = TRAP_COST[Math.min(state.trapsBought, 2)]; purchase = "Blindness Trap"; }
        else if (slot == 51) { player.closeInventory(); return; }
        else return;
        if (countDiamonds(player) < cost) {
            player.sendMessage(ChatColor.RED + "You need " + cost + " diamonds.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        if (slot == 23 && !upgradeForge(game, team, "iron", state.ironForge + 2)
                || slot == 25 && !upgradeForge(game, team, "gold", state.goldForge + 2)) {
            player.sendMessage(ChatColor.RED + "This team's generator is unavailable.");
            return;
        }
        takeDiamonds(player, cost);
        if (slot == 19) state.sharpness = true;
        if (slot == 21) state.protection++;
        if (slot == 23) state.ironForge++;
        if (slot == 25) state.goldForge++;
        if (slot == 31) { state.trapArmed = true; state.trapsBought++; }
        for (Player teammate : team.getConnectedPlayers()) {
            teammate.sendMessage(ChatColor.AQUA + player.getName() + " purchased " + purchase + " for the team!");
            teammate.playSound(teammate.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
            applyEnchantments(teammate, state);
        }
        open(player, game);
    }

    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTitle().equals(MENU)) event.setCancelled(true);
    }

    private int countDiamonds(Player player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getStorageContents())
            if (stack != null && stack.getType() == Material.DIAMOND) count += stack.getAmount();
        return count;
    }

    private void takeDiamonds(Player player, int amount) {
        player.getInventory().removeItem(new ItemStack(Material.DIAMOND, amount));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPotion(PlayerItemConsumeEvent event) {
        if (game(event.getPlayer()) == null || event.getItem().getType() != Material.POTION
                || !event.getItem().hasItemMeta()) return;
        if (!event.getItem().getItemMeta().hasDisplayName()) return;
        String name = ChatColor.stripColor(event.getItem().getItemMeta().getDisplayName());
        Player player = event.getPlayer();
        if (name.equals("Jump Boost V (45s)")) Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (game(player) != null) player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 900, 4, false, true, true));
        }, 1L);
        if (name.equals("Invisibility (30s)")) Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (game(player) != null) player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 600, 0, false, false, true));
        }, 1L);
    }
}
