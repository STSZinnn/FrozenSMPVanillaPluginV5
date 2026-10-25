package com.example.punchsnowball;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import net.kyori.adventure.text.Component;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.HashSet;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.entity.Firework;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.event.entity.FireworkExplodeEvent;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.CommandExecutor;
import org.bukkit.util.StringUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Trident;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Iterator;

public final class PunchSnowballPlugin extends JavaPlugin implements Listener, TabCompleter {
    // Trust is directional: a player protects the people THEY trust from THEIR weapons.
    // Stored by UUID to survive player name changes and plugin/server restarts.
    private final Map<UUID, Map<UUID, String>> trustedPlayers = new HashMap<>();
    private File trustFile;
    private FileConfiguration trustFileData;

    private record GuiSetting(String path, String title, Material icon,
                              double step, double minimum, double maximum,
                              boolean integer, boolean toggle) {}

    private static GuiSetting num(String path, String title, Material icon,
                                  double step, double min, double max, boolean integer) {
        return new GuiSetting(path, title, icon, step, min, max, integer, false);
    }
    private static GuiSetting flag(String path, String title, Material icon) {
        return new GuiSetting(path, title, icon, 0, 0, 1, false, true);
    }

    private static final Map<String, List<GuiSetting>> GUI_SETTINGS = new LinkedHashMap<>();
    static {
        GUI_SETTINGS.put("snowball", List.of(
            num("snowball.cooldown-seconds", "Cooldown (seconds)", Material.CLOCK, 1, 1, 300, true),
            num("snowball.slowness-seconds", "Slowness (seconds)", Material.COBWEB, 1, 1, 60, true),
            num("snowball.horizontal-velocity", "Horizontal knockback", Material.FEATHER, 0.1, 0, 5, false),
            num("snowball.vertical-velocity", "Vertical knockback", Material.SLIME_BALL, 0.05, 0, 2, false)
        ));
        GUI_SETTINGS.put("tnt", List.of(
            num("tnt.cooldown-seconds", "Cooldown (seconds)", Material.CLOCK, 1, 1, 300, true),
            num("tnt.fuse-seconds", "Fuse (seconds)", Material.REDSTONE_TORCH, 1, 1, 30, true),
            num("tnt.radius-blocks", "Effect radius (blocks)", Material.TNT, 0.5, 0.5, 16, false),
            num("tnt.explosion-power", "Explosion strength", Material.FIRE_CHARGE, 0.25, 0, 4, false),
            flag("tnt.break-blocks", "Break blocks", Material.IRON_PICKAXE),
            num("tnt.damage-health-points", "Damage (health points)", Material.REDSTONE, 1, 0, 40, false)
        ));
        GUI_SETTINGS.put("windcharge", List.of(
            num("windcharge.cooldown-seconds", "Cooldown (seconds)", Material.CLOCK, 1, 1, 300, true),
            num("windcharge.freeze-seconds", "Freeze duration (seconds)", Material.POWDER_SNOW_BUCKET, 1, 1, 30, true),
            flag("windcharge.powder-snow-overlay", "Powder snow screen", Material.SNOW_BLOCK)
        ));
        GUI_SETTINGS.put("grapple", List.of(
            num("grapple.cooldown-seconds", "Grapple cooldown (seconds)", Material.CLOCK, 1, 1, 300, true),
            num("grapple.horizontal-velocity", "Pull speed", Material.LEAD, 0.1, 0.1, 3, false),
            num("grapple.vertical-velocity", "Vertical pull", Material.FEATHER, 0.05, 0, 1.2, false),
            num("trident.fire-seconds", "Fire duration (seconds)", Material.FLINT_AND_STEEL, 1, 0, 60, true)
        ));
        GUI_SETTINGS.put("riptide", List.of(
            num("riptide.cooldown-seconds", "Cooldown (seconds)", Material.CLOCK, 1, 1, 300, true),
            num("riptide.damage-per-strike-health-points", "Damage per lightning strike", Material.LIGHTNING_ROD, 1, 0, 40, false),
            num("riptide.ticks-between-strikes", "Ticks between strikes", Material.REPEATER, 1, 1, 100, true)
        ));
        GUI_SETTINGS.put("firework", List.of(
            num("firework.cooldown-seconds", "Cooldown (seconds)", Material.CLOCK, 1, 1, 300, true),
            num("firework.missiles", "Number of rockets", Material.FIREWORK_ROCKET, 1, 1, 20, true),
            num("firework.damage-health-points", "Damage per rocket", Material.REDSTONE, 0.5, 0, 40, false),
            num("firework.target-range-blocks", "Target range (blocks)", Material.SPYGLASS, 1, 1, 128, false),
            num("firework.sky-delay-ticks", "Sky launch delay (ticks)", Material.CLOCK, 5, 5, 100, true),
            num("firework.homing-speed", "Rocket speed", Material.FEATHER, 0.05, 0.2, 2, false)
        ));
    }
    private static final Map<Integer, String> MENU_GROUPS = Map.of(
        10, "snowball", 12, "tnt", 14, "windcharge",
        28, "grapple", 30, "riptide", 32, "firework");
    private static final int[] SETTING_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34
    };

    private static final class FrostMenu implements InventoryHolder {
        final String group;
        final Inventory inventory;
        FrostMenu(String group) {
            this.group = group;
            this.inventory = Bukkit.createInventory(this, 54,
                    Component.text(group.isEmpty() ? "Frost - Weapon Config" : "Frost - " + group));
        }
        @Override public Inventory getInventory() { return inventory; }
    }

    private NamespacedKey snowballKey;
    private NamespacedKey tntKey;
    private NamespacedKey tntOwnerKey;
    private NamespacedKey freezeChargeKey;
    private NamespacedKey tridentKey;
    private final Map<UUID, Long> nextWindUse = new HashMap<>();
    private final Map<UUID, Long> nextTridentUse = new HashMap<>();
    private int tridentCooldownTicks;
    private double lightningDamage;
    private int lightningSpacingTicks;
    private int tridentFireTicks;
    private final Map<UUID, FrozenPlayer> frozenPlayers = new HashMap<>();
    private int windCooldownTicks;
    private int freezeDurationTicks;
    private boolean windFreezeOverlay;

    private static final class FrozenPlayer {
        final Location anchor;
        final int previousFreezeTicks;
        long releaseTick;
        FrozenPlayer(Location anchor, int previousFreezeTicks, long releaseTick) {
            this.anchor = anchor;
            this.previousFreezeTicks = previousFreezeTicks;
            this.releaseTick = releaseTick;
        }
    }
    private final Map<UUID, Long> nextTntUse = new HashMap<>();
    private NamespacedKey riptideTridentKey;
    private NamespacedKey fireworksKey;
    private final Map<UUID, Long> nextRiptideAbility = new HashMap<>();
    private final Map<UUID, Long> riptideActiveUntil = new HashMap<>();
    private final Map<UUID, Long> nextFireworkUse = new HashMap<>();
    private final List<HomingRocket> activeRockets = new ArrayList<>();
    private int grappleCooldownTicks;
    private double grapplePullHorizontal;
    private double grapplePullVertical;
    private int riptideCooldownTicks;
    private int fireworkCooldownTicks;
    private int fireworkCount;
    private double fireworkDamage;
    private double fireworkTargetRange;
    private int fireworkSkyDelay;
    private double fireworkSpeed;

    private static final class HomingRocket {
        final Firework rocket;
        final UUID targetId;
        final UUID ownerId;
        final long expiresAt;
        HomingRocket(Firework rocket, UUID targetId, UUID ownerId, long expiresAt) {
            this.rocket = rocket;
            this.targetId = targetId;
            this.ownerId = ownerId;
            this.expiresAt = expiresAt;
        }
    }


    private int snowballCooldownTicks;
    private int slownessTicks;
    private double snowballHorizontal;
    private double snowballVertical;
    private int tntCooldownTicks;
    private int tntFuseTicks;
    private double tntRadius;
    private float tntPower;
    private boolean tntBreakBlocks;
    private double tntDamage;

    @Override
    public void onEnable() {
        snowballKey = new NamespacedKey(this, "punch_snowball");
        tntKey = new NamespacedKey(this, "infinite_tnt");
        tntOwnerKey = new NamespacedKey(this, "tnt_owner");
        freezeChargeKey = new NamespacedKey(this, "freeze_windcharge");
        // Existing Storm Tridents from v1.5 become Grapple Tridents.
        tridentKey = new NamespacedKey(this, "storm_trident");
        riptideTridentKey = new NamespacedKey(this, "riptide_trident");
        fireworksKey = new NamespacedKey(this, "homing_firework");
        saveDefaultConfig();
        loadSettings();
        loadTrustedPlayers();
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            tickFrozenPlayers();
            tickHomingRockets();
        }, 1L, 1L);
    }

    @Override
    public void onDisable() {
        for (UUID uuid : List.copyOf(frozenPlayers.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) releaseFrozen(player);
        }
        frozenPlayers.clear();
        for (HomingRocket rocket : activeRockets) {
            if (rocket.rocket.isValid()) rocket.rocket.remove();
        }
        activeRockets.clear();
    }

    private long ticksPassed = 0;

    private void tickFrozenPlayers() {
        ticksPassed++;
        for (UUID uuid : List.copyOf(frozenPlayers.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            FrozenPlayer frozen = frozenPlayers.get(uuid);
            if (frozen == null) continue;
            if (player == null || !player.isOnline() || player.isDead() || ticksPassed >= frozen.releaseTick) {
                if (player != null) releaseFrozen(player);
                else frozenPlayers.remove(uuid);
                continue;
            }
            player.setVelocity(new Vector(0, 0, 0));
            if (windFreezeOverlay) {
                player.setFreezeTicks(player.getMaxFreezeTicks());
            }
            if (ticksPassed % 5 == 0) {
                player.getWorld().spawnParticle(Particle.SNOWFLAKE,
                        player.getLocation().add(0, 1.0, 0), 8,
                        0.35, 0.65, 0.35, 0.008);
            }
        }
    }

    private boolean isFrozen(Player player) {
        return frozenPlayers.containsKey(player.getUniqueId());
    }

    private void freezePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        FrozenPlayer alreadyFrozen = frozenPlayers.get(uuid);
        if (alreadyFrozen == null) {
            alreadyFrozen = new FrozenPlayer(player.getLocation().clone(),
                    player.getFreezeTicks(), ticksPassed + freezeDurationTicks);
            frozenPlayers.put(uuid, alreadyFrozen);
        } else {
            alreadyFrozen.releaseTick = ticksPassed + freezeDurationTicks;
        }
        player.setVelocity(new Vector());
        if (windFreezeOverlay) player.setFreezeTicks(player.getMaxFreezeTicks());
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_POWDER_SNOW_BREAK, 1.0f, 0.8f);
    }

    private void releaseFrozen(Player player) {
        FrozenPlayer frozen = frozenPlayers.remove(player.getUniqueId());
        if (frozen != null && windFreezeOverlay) {
            player.setFreezeTicks(Math.min(frozen.previousFreezeTicks, player.getMaxFreezeTicks()));
        }
    }

    private void loadTrustedPlayers() {
        trustedPlayers.clear();
        trustFile = new File(getDataFolder(), "trusted.yml");
        trustFileData = YamlConfiguration.loadConfiguration(trustFile);
        ConfigurationSection section = trustFileData.getConfigurationSection("trusted");
        if (section == null) return;
        for (String ownerString : section.getKeys(false)) {
            UUID owner;
            try { owner = UUID.fromString(ownerString); }
            catch (IllegalArgumentException ignored) { continue; }
            ConfigurationSection members = section.getConfigurationSection(ownerString);
            if (members == null) continue;
            Map<UUID, String> entries = new HashMap<>();
            for (String targetString : members.getKeys(false)) {
                try {
                    UUID target = UUID.fromString(targetString);
                    entries.put(target, members.getString(targetString, targetString));
                } catch (IllegalArgumentException ignored) { }
            }
            if (!entries.isEmpty()) trustedPlayers.put(owner, entries);
        }
    }

    private void persistTrustedPlayers() {
        trustFileData.set("trusted", null);
        for (Map.Entry<UUID, Map<UUID, String>> owner : trustedPlayers.entrySet()) {
            for (Map.Entry<UUID, String> trusted : owner.getValue().entrySet()) {
                trustFileData.set("trusted." + owner.getKey() + "." + trusted.getKey(), trusted.getValue());
            }
        }
        try { trustFileData.save(trustFile); }
        catch (IOException ex) { getLogger().severe("Unable to write trusted.yml: " + ex.getMessage()); }
    }

    private boolean trusts(UUID owner, UUID possibleFriend) {
        return owner != null && trustedPlayers.getOrDefault(owner, Map.of()).containsKey(possibleFriend);
    }
    private boolean trusts(Player owner, Player possibleFriend) {
        return owner != null && possibleFriend != null && trusts(owner.getUniqueId(), possibleFriend.getUniqueId());
    }

    private ItemStack guiItem(Material type, String name, String... lines) {
        ItemStack item = new ItemStack(type);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + name);
        List<String> lore = new ArrayList<>();
        for (String line : lines) lore.add(ChatColor.GRAY + line);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void fillMenu(Inventory inv) {
        ItemStack filler = guiItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
    }

    private String groupLabel(String group) {
        return switch (group) {
            case "snowball" -> "Punch Snowball";
            case "tnt" -> "Infinite TNT";
            case "windcharge" -> "Freezing Wind Charge";
            case "grapple" -> "Grapple Trident";
            case "riptide" -> "Riptide Trident";
            case "firework" -> "Homing Fireworks";
            default -> group;
        };
    }

    private Material groupMaterial(String group) {
        return switch (group) {
            case "snowball" -> Material.SNOWBALL;
            case "tnt" -> Material.TNT;
            case "windcharge" -> Material.WIND_CHARGE;
            case "grapple", "riptide" -> Material.TRIDENT;
            case "firework" -> Material.FIREWORK_ROCKET;
            default -> Material.PAPER;
        };
    }

    private void openConfigHome(Player player) {
        FrostMenu menu = new FrostMenu("");
        fillMenu(menu.inventory);
        for (Map.Entry<Integer, String> entry : MENU_GROUPS.entrySet()) {
            String group = entry.getValue();
            menu.inventory.setItem(entry.getKey(),
                    guiItem(groupMaterial(group), groupLabel(group), "Click to edit weapon settings"));
        }
        menu.inventory.setItem(49, guiItem(Material.BOOK, "How to use",
                "Left click: increase", "Right click: decrease",
                "Shift click: change 5 steps", "Values save immediately"));
        player.openInventory(menu.inventory);
    }

    private ItemStack settingItem(GuiSetting setting) {
        if (setting.toggle()) {
            boolean enabled = getConfig().getBoolean(setting.path());
            return guiItem(setting.icon(), setting.title(),
                    "Current: " + (enabled ? "ON" : "OFF"), "Click to toggle");
        }
        double value = getConfig().getDouble(setting.path());
        String display = setting.integer() ? String.valueOf((int) Math.round(value))
                : String.format(Locale.US, "%.2f", value);
        return guiItem(setting.icon(), setting.title(),
                "Current: " + display, "Left click: +" + setting.step(),
                "Right click: -" + setting.step(), "Shift: x5 change");
    }

    private void openConfigGroup(Player player, String group) {
        List<GuiSetting> settings = GUI_SETTINGS.get(group);
        if (settings == null) return;
        FrostMenu menu = new FrostMenu(group);
        fillMenu(menu.inventory);
        for (int i = 0; i < settings.size(); i++) {
            menu.inventory.setItem(SETTING_SLOTS[i], settingItem(settings.get(i)));
        }
        menu.inventory.setItem(49, guiItem(Material.ARROW, "Back", "Return to weapon selection"));
        menu.inventory.setItem(53, guiItem(Material.EMERALD, "Saved automatically",
                "Changes take effect immediately", "Reopen your item to update its lore"));
        player.openInventory(menu.inventory);
    }

    @EventHandler
    public void onConfigMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof FrostMenu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.hasPermission("frost.config")) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;
        if (menu.group.isEmpty()) {
            String group = MENU_GROUPS.get(slot);
            if (group != null) Bukkit.getScheduler().runTask(this, () -> openConfigGroup(player, group));
            return;
        }
        if (slot == 49) {
            Bukkit.getScheduler().runTask(this, () -> openConfigHome(player));
            return;
        }
        List<GuiSetting> settings = GUI_SETTINGS.get(menu.group);
        if (settings == null) return;
        int idx = -1;
        for (int i = 0; i < settings.size(); i++) {
            if (SETTING_SLOTS[i] == slot) { idx = i; break; }
        }
        if (idx < 0) return;
        GuiSetting setting = settings.get(idx);
        if (setting.toggle()) {
            getConfig().set(setting.path(), !getConfig().getBoolean(setting.path()));
        } else {
            double direction = event.getClick().isRightClick() ? -1 : 1;
            double increment = event.isShiftClick() ? 5 : 1;
            double updated = getConfig().getDouble(setting.path()) + direction * increment * setting.step();
            updated = Math.max(setting.minimum(), Math.min(setting.maximum(), updated));
            if (setting.integer()) getConfig().set(setting.path(), (int) Math.round(updated));
            else getConfig().set(setting.path(), Math.round(updated * 100.0) / 100.0);
        }
        saveConfig();
        loadSettings();
        menu.inventory.setItem(slot, settingItem(setting));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.65f, 1.2f);
    }

    @EventHandler
    public void onConfigMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof FrostMenu) event.setCancelled(true);
    }

    private void loadSettings() {
        snowballCooldownTicks = Math.max(1, getConfig().getInt("snowball.cooldown-seconds", 7) * 20);
        slownessTicks = Math.max(1, getConfig().getInt("snowball.slowness-seconds", 2) * 20);
        snowballHorizontal = Math.max(0.0, getConfig().getDouble("snowball.horizontal-velocity", 2.45));
        snowballVertical = Math.max(0.0, getConfig().getDouble("snowball.vertical-velocity", 0.40));
        tntCooldownTicks = Math.max(1, getConfig().getInt("tnt.cooldown-seconds", 10) * 20);
        tntFuseTicks = Math.max(1, getConfig().getInt("tnt.fuse-seconds", 3) * 20);
        tntRadius = Math.max(0.5, getConfig().getDouble("tnt.radius-blocks", 4.0));
        tntPower = (float) Math.max(0.0, Math.min(4.0, getConfig().getDouble("tnt.explosion-power", 2.5)));
        tntBreakBlocks = getConfig().getBoolean("tnt.break-blocks", true);
        tntDamage = Math.max(0.0, getConfig().getDouble("tnt.damage-health-points", 4.0));
        windCooldownTicks = Math.max(1, getConfig().getInt("windcharge.cooldown-seconds", 20) * 20);
        freezeDurationTicks = Math.max(1, getConfig().getInt("windcharge.freeze-seconds", 3) * 20);
        windFreezeOverlay = getConfig().getBoolean("windcharge.powder-snow-overlay", true);
        tridentCooldownTicks = Math.max(1, getConfig().getInt("trident.cooldown-seconds", 20) * 20);
        lightningDamage = Math.max(0.0, getConfig().getDouble("trident.damage-per-strike-health-points", 3.0));
        lightningSpacingTicks = Math.max(1, getConfig().getInt("trident.ticks-between-strikes", 8));
        tridentFireTicks = Math.max(0, getConfig().getInt("trident.fire-seconds", 8) * 20);
        grappleCooldownTicks = Math.max(1, getConfig().getInt("grapple.cooldown-seconds", 20) * 20);
        grapplePullHorizontal = Math.max(0.1, Math.min(3.0, getConfig().getDouble("grapple.horizontal-velocity", 1.5)));
        grapplePullVertical = Math.max(0.0, Math.min(1.2, getConfig().getDouble("grapple.vertical-velocity", 0.35)));
        riptideCooldownTicks = Math.max(1, getConfig().getInt("riptide.cooldown-seconds", 20) * 20);
        lightningDamage = Math.max(0.0, getConfig().getDouble("riptide.damage-per-strike-health-points", 3.0));
        lightningSpacingTicks = Math.max(1, getConfig().getInt("riptide.ticks-between-strikes", 8));
        fireworkCooldownTicks = Math.max(1, getConfig().getInt("firework.cooldown-seconds", 45) * 20);
        fireworkCount = Math.max(1, Math.min(20, getConfig().getInt("firework.missiles", 5)));
        fireworkDamage = Math.max(0.0, getConfig().getDouble("firework.damage-health-points", 1.0));
        fireworkTargetRange = Math.max(1.0, getConfig().getDouble("firework.target-range-blocks", 32.0));
        fireworkSkyDelay = Math.max(5, getConfig().getInt("firework.sky-delay-ticks", 25));
        fireworkSpeed = Math.max(0.2, Math.min(2.0, getConfig().getDouble("firework.homing-speed", 0.85)));
    }

    private ItemStack makeSnowball() {
        ItemStack item = new ItemStack(Material.SNOWBALL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "Punch Snowball");
        meta.setLore(List.of(
                ChatColor.GRAY + "Infinite throws",
                ChatColor.GRAY + "" + (snowballCooldownTicks / 20) + "-second cooldown",
                ChatColor.LIGHT_PURPLE + "Strong knockback + Slowness II"));
        meta.getPersistentDataContainer().set(snowballKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeTnt() {
        ItemStack item = new ItemStack(Material.TNT);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Infinite TNT");
        meta.setLore(List.of(
                ChatColor.GRAY + "Right-click a block to ignite",
                ChatColor.GRAY + "Reusable - never consumed",
                ChatColor.YELLOW + "" + (tntFuseTicks / 20) + "-second fuse",
                ChatColor.RED + "" + (tntCooldownTicks / 20) + "-second cooldown",
                ChatColor.LIGHT_PURPLE + "Small blast, configurable damage"));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(tntKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeWindCharge() {
        ItemStack item = new ItemStack(Material.WIND_CHARGE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "Freezing Wind Charge");
        meta.setLore(List.of(
                ChatColor.GRAY + "Infinite throws",
                ChatColor.BLUE + "Freezes hit players for " + (freezeDurationTicks / 20) + " seconds",
                ChatColor.GRAY + "Prevents moving and using items",
                ChatColor.YELLOW + "" + (windCooldownTicks / 20) + "-second cooldown"));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(freezeChargeKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeTrident() {
        ItemStack item = new ItemStack(Material.TRIDENT);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "Grapple Trident");
        meta.addEnchant(Enchantment.IMPALING, 6, true);
        meta.addEnchant(Enchantment.FIRE_ASPECT, 2, true);
        meta.addEnchant(Enchantment.LOYALTY, 3, true);
        meta.setUnbreakable(true);
        meta.setLore(List.of(
                ChatColor.GRAY + "Impaling VI | Fire Aspect II | Loyalty III",
                ChatColor.GRAY + "Unbreakable",
                ChatColor.AQUA + "Pulls hit players toward you",
                ChatColor.YELLOW + "Grapple cooldown: " + (grappleCooldownTicks / 20) + "s"));
        meta.getPersistentDataContainer().set(tridentKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeRiptideTrident() {
        ItemStack item = new ItemStack(Material.TRIDENT);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_AQUA + "Riptide Lightning Trident");
        meta.addEnchant(Enchantment.RIPTIDE, 3, true);
        meta.addEnchant(Enchantment.IMPALING, 5, true);
        meta.setUnbreakable(true);
        meta.setLore(List.of(
                ChatColor.GRAY + "Riptide III | Impaling V | Unbreakable",
                ChatColor.AQUA + "Riptide collision: 3 lightning strikes",
                ChatColor.GRAY + "Each strike: 1.5 hearts through armor",
                ChatColor.YELLOW + "Lightning cooldown: " + (riptideCooldownTicks / 20) + "s",
                ChatColor.GRAY + "Requires water or rain to use Riptide"));
        meta.getPersistentDataContainer().set(riptideTridentKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeFirework() {
        ItemStack item = new ItemStack(Material.FIREWORK_ROCKET);
        FireworkMeta meta = (FireworkMeta) item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Homing Firework Barrage");
        meta.setEnchantmentGlintOverride(true);
        meta.setPower(1);
        meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE)
                .withColor(Color.AQUA, Color.PURPLE).withFade(Color.WHITE).trail(true).build());
        meta.setLore(List.of(
                ChatColor.GRAY + "Right-click a block to launch skyward",
                ChatColor.GRAY + "Reusable: never consumed",
                ChatColor.LIGHT_PURPLE + "Shoots " + fireworkCount + " homing fireworks at the nearest player",
                ChatColor.YELLOW + "Cooldown: " + (fireworkCooldownTicks / 20) + "s"));
        meta.getPersistentDataContainer().set(fireworksKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    // Health-based damage gives a predictable amount through armor and
    // damage-invulnerability frames. It is intended for this server's custom PvP.
    private void dealTrueDamage(Player target, double amount) {
        if (!target.isOnline() || target.isDead() || amount <= 0.0
                || target.getGameMode() == org.bukkit.GameMode.SPECTATOR
                || target.getGameMode() == org.bukkit.GameMode.CREATIVE) return;
        target.setHealth(Math.max(0.0, target.getHealth() - amount));
    }

    private void strikePlayerThreeTimes(Player target, UUID ownerId) {
        UUID targetId = target.getUniqueId();
        for (int n = 0; n < 3; n++) {
            long delay = 1L + (long) n * lightningSpacingTicks;
            Bukkit.getScheduler().runTaskLater(this, () -> {
                Player current = Bukkit.getPlayer(targetId);
                if (current == null || !current.isOnline() || current.isDead()
                        || trusts(ownerId, current.getUniqueId())) return;
                current.getWorld().strikeLightningEffect(current.getLocation());
                dealTrueDamage(current, lightningDamage);
            }, delay);
        }
    }

    private boolean startGrappleCooldown(Player owner) {
        UUID id = owner.getUniqueId();
        if (ticksPassed < nextTridentUse.getOrDefault(id, 0L)) return false;
        nextTridentUse.put(id, ticksPassed + grappleCooldownTicks);
        return true;
    }

    private void pullVictimToShooter(Player victim, Player shooter) {
        if (trusts(shooter, victim) || !victim.getWorld().equals(shooter.getWorld())) return;
        Vector toward = shooter.getLocation().toVector().add(new Vector(0, 0.75, 0))
                .subtract(victim.getLocation().toVector());
        if (toward.lengthSquared() <= 0.3) return;
        Vector horizontal = toward.clone().setY(0);
        if (horizontal.lengthSquared() < 0.001) horizontal = new Vector(0, 0, 1);
        Vector velocity = horizontal.normalize().multiply(grapplePullHorizontal);
        velocity.setY(grapplePullVertical);
        // Velocity rather than teleportation, so other server collision rules still apply.
        Bukkit.getScheduler().runTask(this, () -> {
            if (victim.isOnline() && shooter.isOnline()) victim.setVelocity(velocity);
        });
        victim.getWorld().spawnParticle(Particle.CLOUD, victim.getLocation().add(0, 1, 0), 16, .3, .4, .3, .02);
        shooter.playSound(shooter.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, .8f);
    }

    private boolean isSpecial(ItemStack item, Material expected, NamespacedKey key) {
        return item != null && item.getType() == expected && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer()
                .has(key, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onThrow(PlayerLaunchProjectileEvent event) {
        if (event.getProjectile() instanceof WindCharge charge
                && isSpecial(event.getItemStack(), Material.WIND_CHARGE, freezeChargeKey)) {
            Player player = event.getPlayer();
            UUID owner = player.getUniqueId();
            if (isFrozen(player) || ticksPassed < nextWindUse.getOrDefault(owner, 0L)
                    || player.hasCooldown(Material.WIND_CHARGE)) {
                event.setCancelled(true);
                return;
            }
            nextWindUse.put(owner, ticksPassed + windCooldownTicks);
            charge.getPersistentDataContainer().set(freezeChargeKey, PersistentDataType.BYTE, (byte) 1);
            event.setShouldConsume(false);
            // Minecraft applies the normal wind-charge cooldown AFTER the launch
            // event. Applying 20 seconds on the next tick avoids that override.
            Bukkit.getScheduler().runTask(this, () -> {
                if (player.isOnline()) player.setCooldown(Material.WIND_CHARGE, windCooldownTicks);
            });
            return;
        }
        if (!(event.getProjectile() instanceof Snowball snowball)) return;
        if (!isSpecial(event.getItemStack(), Material.SNOWBALL, snowballKey)) return;
        Player player = event.getPlayer();
        if (isFrozen(player) || player.hasCooldown(Material.SNOWBALL)) {
            event.setCancelled(true);
            return;
        }
        snowball.getPersistentDataContainer().set(snowballKey, PersistentDataType.BYTE, (byte) 1);
        event.setShouldConsume(false);
        player.setCooldown(Material.SNOWBALL, snowballCooldownTicks);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTridentThrown(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Trident trident)) return;
        if (!isSpecial(trident.getItemStack(), Material.TRIDENT, tridentKey)) return;
        if (!(trident.getShooter() instanceof Player player)) return;
        if (isFrozen(player)) { event.setCancelled(true); return; }
        trident.getPersistentDataContainer().set(tridentKey, PersistentDataType.BYTE, (byte) 1);
        trident.setLoyaltyLevel(3);
    }

    // Riptide requires an actual Riptide activation, not normal melee attacks.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRiptideStart(PlayerRiptideEvent event) {
        Player player = event.getPlayer();
        if (!isSpecial(event.getItem(), Material.TRIDENT, riptideTridentKey)) return;
        UUID id = player.getUniqueId();
        if (isFrozen(player) || ticksPassed < nextRiptideAbility.getOrDefault(id, 0L)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Riptide lightning is on cooldown.");
            return;
        }
        riptideActiveUntil.put(id, ticksPassed + 35L);
    }

    // Cancel vanilla hit damage too, not only Frost special effects.
    // Without this, a trusted friend could still take vanilla trident damage.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void preventFriendlyFrostDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (event.getDamager() instanceof Player attacker) {
            ItemStack weapon = attacker.getInventory().getItemInMainHand();
            if ((isSpecial(weapon, Material.TRIDENT, tridentKey)
                    || isSpecial(weapon, Material.TRIDENT, riptideTridentKey))
                    && trusts(attacker, victim)) event.setCancelled(true);
            return;
        }
        if (event.getDamager() instanceof Projectile shot && shot.getShooter() instanceof Player shooter) {
            boolean isFrostProjectile =
                    (shot instanceof Trident trident
                        && (shot.getPersistentDataContainer().has(tridentKey, PersistentDataType.BYTE)
                        || isSpecial(trident.getItemStack(), Material.TRIDENT, tridentKey)))
                    || shot.getPersistentDataContainer().has(freezeChargeKey, PersistentDataType.BYTE)
                    || shot.getPersistentDataContainer().has(snowballKey, PersistentDataType.BYTE);
            if (isFrostProjectile && trusts(shooter, victim)) event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTridentPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim) || attacker.equals(victim)) return;
        if (isFrozen(attacker) || trusts(attacker, victim)) return;
        ItemStack hand = attacker.getInventory().getItemInMainHand();
        if (isSpecial(hand, Material.TRIDENT, tridentKey)) {
            if (startGrappleCooldown(attacker)) pullVictimToShooter(victim, attacker);
            return;
        }
        // Other melee hits NEVER trigger the lightning. Only damage during
        // the short window opened by the Riptide activation event can trigger it.
        if (!isSpecial(hand, Material.TRIDENT, riptideTridentKey)) return;
        UUID id = attacker.getUniqueId();
        if (ticksPassed > riptideActiveUntil.getOrDefault(id, -1L)) return;
        if (ticksPassed < nextRiptideAbility.getOrDefault(id, 0L)) return;
        if (!attacker.isRiptiding()) return;
        nextRiptideAbility.put(id, ticksPassed + riptideCooldownTicks);
        riptideActiveUntil.remove(id);
        UUID victimId = victim.getUniqueId();
        Bukkit.getScheduler().runTask(this, () -> {
            Player current = Bukkit.getPlayer(victimId);
                if (current != null && current.isOnline() && !current.isDead()
                        && !trusts(attacker.getUniqueId(), current.getUniqueId()))
                    strikePlayerThreeTimes(current, attacker.getUniqueId());
        });
    }

    @EventHandler
    public void onHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (projectile instanceof Trident trident
                && (trident.getPersistentDataContainer().has(tridentKey, PersistentDataType.BYTE)
                || isSpecial(trident.getItemStack(), Material.TRIDENT, tridentKey))) {
            if (event.getHitEntity() instanceof Player target
                    && trident.getShooter() instanceof Player shooter && !shooter.equals(target)
                    && !trusts(shooter, target)) {
                target.setFireTicks(Math.max(target.getFireTicks(), tridentFireTicks));
                if (startGrappleCooldown(shooter)) pullVictimToShooter(target, shooter);
            }
            return;
        }
        if (projectile instanceof WindCharge
                && projectile.getPersistentDataContainer().has(freezeChargeKey, PersistentDataType.BYTE)) {
            // Remove the special wind charge immediately to suppress its ordinary wind blast.
            // Freeze only the directly hit player; block hits do nothing.
            event.setCancelled(true);
            if (event.getHitEntity() instanceof Player target
                    && projectile.getShooter() instanceof Player shooter
                    && !shooter.equals(target) && !trusts(shooter, target)) {
                freezePlayer(target);
            }
            projectile.remove();
            return;
        }
        if (!(projectile instanceof Snowball)) return;
        if (!projectile.getPersistentDataContainer().has(snowballKey, PersistentDataType.BYTE)) return;
        if (!(event.getHitEntity() instanceof Player target)) return;
        if (projectile.getShooter() instanceof Player shooter
                && (shooter.equals(target) || trusts(shooter, target))) return;

        Vector direction = projectile.getVelocity().clone().setY(0);
        if (direction.lengthSquared() < 0.0001 && projectile.getShooter() instanceof Player shooter) {
            direction = target.getLocation().toVector()
                    .subtract(shooter.getLocation().toVector()).setY(0);
        }
        if (direction.lengthSquared() < 0.0001) direction = new Vector(0, 0, 1);
        direction.normalize().multiply(snowballHorizontal).setY(snowballVertical);
        target.setVelocity(direction);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                slownessTicks, 1, false, true, true));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void stopWindExplosion(ExplosionPrimeEvent event) {
        if (event.getEntity() instanceof WindCharge charge
                && charge.getPersistentDataContainer().has(freezeChargeKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFrozenMove(PlayerMoveEvent event) {
        FrozenPlayer frozen = frozenPlayers.get(event.getPlayer().getUniqueId());
        if (frozen == null || event.getTo() == null) return;
        // Server-enforced position and yaw/pitch. Vanilla clients may still
        // rotate briefly before the server corrects their view.
        event.setTo(frozen.anchor.clone());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void preventFrozenUse(PlayerInteractEvent event) {
        if (isFrozen(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void preventFrozenEntityUse(PlayerInteractEntityEvent event) {
        if (isFrozen(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void preventFrozenConsumption(PlayerItemConsumeEvent event) {
        if (isFrozen(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void preventFrozenAnimation(PlayerAnimationEvent event) {
        if (isFrozen(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void preventFrozenPlace(BlockPlaceEvent event) {
        if (isFrozen(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void preventFrozenBreak(BlockBreakEvent event) {
        if (isFrozen(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void preventFrozenBlockDamage(BlockDamageEvent event) {
        if (isFrozen(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void preventFrozenDrop(PlayerDropItemEvent event) {
        if (isFrozen(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void preventFrozenSwap(PlayerSwapHandItemsEvent event) {
        if (isFrozen(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void preventFrozenAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && isFrozen(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void preventFreezingDamage(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FREEZE
                && event.getEntity() instanceof Player player && isFrozen(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFrozenQuit(PlayerQuitEvent event) {
        releaseFrozen(event.getPlayer());
    }

    // The custom firework is manually spawned; the item is never consumed.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFireworkUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!isSpecial(event.getItem(), Material.FIREWORK_ROCKET, fireworksKey)) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) return;
        event.setCancelled(true);
        Player user = event.getPlayer();
        if (isFrozen(user)) return;
        UUID id = user.getUniqueId();
        long until = nextFireworkUse.getOrDefault(id, 0L);
        if (ticksPassed < until) return;
        Location location;
        if (event.getClickedBlock() != null) {
            location = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5, 0.2, 0.5);
        } else {
            location = user.getLocation().add(user.getLocation().getDirection().multiply(1.5));
            location.add(0, 0.3, 0);
        }
        nextFireworkUse.put(id, ticksPassed + fireworkCooldownTicks);
        user.setCooldown(event.getItem(), fireworkCooldownTicks);
        launchBarrage(location, user);
    }

    private Firework spawnDisplayFirework(Location spot, Color color1, Color color2, int life) {
        Firework fw = spot.getWorld().spawn(spot, Firework.class);
        FireworkMeta meta = fw.getFireworkMeta();
        meta.clearEffects();
        meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL)
                .withColor(color1, color2).withFade(Color.WHITE).trail(true).build());
        meta.setPower(2);
        fw.setFireworkMeta(meta);
        fw.setTicksToDetonate(life);
        fw.getPersistentDataContainer().set(fireworksKey, PersistentDataType.BYTE, (byte) 1);
        return fw;
    }

    private void launchBarrage(Location spot, Player owner) {
        Firework launch = spawnDisplayFirework(spot, Color.AQUA, Color.PURPLE, fireworkSkyDelay + 5);
        launch.setVelocity(new Vector(0, 1.1, 0));
        UUID ownerId = owner.getUniqueId();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!launch.isValid()) return;
            Location high = launch.getLocation().clone();
            launch.detonate();
            Player nearest = findNearestEnemy(high, ownerId, fireworkTargetRange);
            if (nearest == null) {
                Player user = Bukkit.getPlayer(ownerId);
                if (user != null) user.sendMessage(ChatColor.GRAY + "Firework burst: no other player in range.");
                return;
            }
            UUID targetId = nearest.getUniqueId();
            for (int i = 0; i < fireworkCount; i++) {
                int index = i;
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    Player enemy = Bukkit.getPlayer(targetId);
                    if (enemy == null || !enemy.isOnline() || enemy.isDead() ||
                            !enemy.getWorld().equals(high.getWorld())) return;
                    double angle = (Math.PI * 2 * index) / Math.max(1, fireworkCount);
                    Location spawnAt = high.clone().add(Math.cos(angle) * 1.2, 0.5, Math.sin(angle) * 1.2);
                    Firework missile = spawnDisplayFirework(spawnAt, Color.RED, Color.ORANGE, 100);
                    missile.setShotAtAngle(true);
                    activeRockets.add(new HomingRocket(missile, targetId, ownerId, ticksPassed + 90));
                }, i * 4L);
            }
        }, fireworkSkyDelay);
    }

    private Player findNearestEnemy(Location location, UUID owner, double radius) {
        Player selected = null;
        double minDist = radius * radius;
        for (Player candidate : location.getWorld().getPlayers()) {
            if (candidate.getUniqueId().equals(owner) || trusts(owner, candidate.getUniqueId())
                    || !candidate.isOnline() || candidate.isDead()
                    || candidate.getGameMode() == GameMode.SPECTATOR
                    || candidate.getGameMode() == GameMode.CREATIVE) continue;
            double dist = candidate.getLocation().distanceSquared(location);
            if (dist < minDist) { selected = candidate; minDist = dist; }
        }
        return selected;
    }

    private void tickHomingRockets() {
        Iterator<HomingRocket> iter = activeRockets.iterator();
        while (iter.hasNext()) {
            HomingRocket active = iter.next();
            Firework rocket = active.rocket;
            Player target = Bukkit.getPlayer(active.targetId);
            if (!rocket.isValid() || ticksPassed >= active.expiresAt || target == null
                    || !target.isOnline() || target.isDead() || trusts(active.ownerId, active.targetId)
                    || !rocket.getWorld().equals(target.getWorld())) {
                if (rocket.isValid()) rocket.detonate();
                iter.remove();
                continue;
            }
            Vector toTarget = target.getEyeLocation().toVector().subtract(rocket.getLocation().toVector());
            if (toTarget.lengthSquared() <= 2.25) {
                // One hit per missile. 1 health point = half a heart by default.
                dealTrueDamage(target, fireworkDamage);
                rocket.detonate();
                iter.remove();
                continue;
            }
            rocket.setVelocity(toTarget.normalize().multiply(fireworkSpeed));
        }
    }

    // The fireworks apply only our configured damage when they touch a target.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void cancelVanillaFireworkDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework firework
                && firework.getPersistentDataContainer().has(fireworksKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlaceTnt(PlayerInteractEvent event) {
        // Only process the main hand; prevents the single click being handled twice.
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        if (!isSpecial(event.getItem(), Material.TNT, tntKey)) return;

        // Prevent vanilla placement AND consumption. It remains in inventory.
        event.setCancelled(true);
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        if (now < nextTntUse.getOrDefault(player.getUniqueId(), 0L)
                || player.hasCooldown(Material.TNT)) return;

        Block placeBlock = event.getClickedBlock().getRelative(event.getBlockFace());
        if (!placeBlock.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No space to place Infinite TNT here.");
            return;
        }

        Location spot = placeBlock.getLocation().add(0.5, 0, 0.5);
        TNTPrimed primed = spot.getWorld().spawn(spot, TNTPrimed.class);
        primed.setSource(player);
        primed.setFuseTicks(tntFuseTicks);
        primed.setYield(tntPower); // Actual explosion size; 4.0 = vanilla TNT.
        primed.setIsIncendiary(false);
        primed.getPersistentDataContainer().set(tntKey, PersistentDataType.BYTE, (byte) 1);
        primed.getPersistentDataContainer().set(tntOwnerKey, PersistentDataType.STRING,
                player.getUniqueId().toString());

        nextTntUse.put(player.getUniqueId(), now + (tntCooldownTicks * 50L));
        player.setCooldown(Material.TNT, tntCooldownTicks);
    }

    // Block damage: a real TNT explosion, limited to the selected radius.
    // Apply separately controlled player damage on the next tick.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed primed)) return;
        if (!primed.getPersistentDataContainer().has(tntKey, PersistentDataType.BYTE)) return;

        Location center = event.getLocation().clone();
        if (!tntBreakBlocks) {
            event.blockList().clear();
        } else {
            event.blockList().removeIf(block ->
                    block.getLocation().add(0.5, 0.5, 0.5).distanceSquared(center)
                            > tntRadius * tntRadius);
        }

        String ownerId = primed.getPersistentDataContainer()
                .get(tntOwnerKey, PersistentDataType.STRING);
        UUID ownerUuid = null;
        if (ownerId != null) {
            try { ownerUuid = UUID.fromString(ownerId); }
            catch (IllegalArgumentException ignored) { }
        }
        double effectRadius = tntRadius;
        double effectDamage = tntDamage;

        // Identify the victims NOW, before vanilla explosion knockback moves them away.
        List<UUID> victims = new ArrayList<>();
        for (Player player : center.getWorld().getPlayers()) {
            Location middle = player.getLocation().add(0, player.getHeight() / 2.0, 0);
            if (middle.distanceSquared(center) <= effectRadius * effectRadius
                    && !trusts(ownerUuid, player.getUniqueId())) {
                victims.add(player.getUniqueId());
            }
        }
        UUID finalOwnerUuid = ownerUuid;
        Bukkit.getScheduler().runTask(this, () -> {
            for (UUID victimId : victims) {
                Player victim = Bukkit.getPlayer(victimId);
                if (victim != null && victim.isOnline() && !trusts(finalOwnerUuid, victimId))
                    dealTrueDamage(victim, effectDamage);
            }
        });
    }

    // Prevent vanilla TNT's larger, armor-dependent damage. Custom damage is
    // handled in onExplosion, for a consistent setting in config.yml.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVanillaExplosionDamage(EntityDamageEvent event) {
        Entity direct = event.getDamageSource().getDirectEntity();
        if (direct instanceof TNTPrimed primed
                && primed.getPersistentDataContainer().has(tntKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);
            return;
        }
        if (event instanceof EntityDamageByEntityEvent byEntity
                && byEntity.getDamager() instanceof TNTPrimed primed
                && primed.getPersistentDataContainer().has(tntKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    private OfflinePlayer lookupKnownPlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        // Uses the server's known player data. Don't assume nonexistent names are valid.
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline.hasPlayedBefore() ? offline : null;
    }

    private boolean manageTrust(CommandSender sender, String action, String[] args) {
        if (!(sender instanceof Player owner)) {
            sender.sendMessage(ChatColor.RED + "Trust commands must be used in-game.");
            return true;
        }
        if (!owner.hasPermission("frost.trust")) {
            owner.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        UUID uuid = owner.getUniqueId();
        if (action.equals("trusted")) {
            Map<UUID, String> names = trustedPlayers.getOrDefault(uuid, Map.of());
            owner.sendMessage(ChatColor.AQUA + "Trusted players (" + names.size() + "): "
                    + ChatColor.WHITE + (names.isEmpty() ? "None" : String.join(", ", names.values())));
            return true;
        }
        if (args.length != 2) {
            owner.sendMessage(ChatColor.YELLOW + "Usage: /frost " + action + " <player>");
            return true;
        }
        OfflinePlayer target = lookupKnownPlayer(args[1]);
        if (target == null) {
            owner.sendMessage(ChatColor.RED + "Player not found in server history. They need to join once.");
            return true;
        }
        if (target.getUniqueId().equals(uuid)) {
            owner.sendMessage(ChatColor.RED + "You don't need to trust yourself.");
            return true;
        }
        Map<UUID, String> names = trustedPlayers.computeIfAbsent(uuid, ignored -> new HashMap<>());
        String displayName = target.getName() == null ? args[1] : target.getName();
        if (action.equals("trust")) {
            names.put(target.getUniqueId(), displayName);
            owner.sendMessage(ChatColor.GREEN + "Trusted " + displayName
                    + ". Your Frost weapons won't harm or target them.");
        } else {
            if (names.remove(target.getUniqueId()) == null) {
                owner.sendMessage(ChatColor.YELLOW + displayName + " was not on your trust list.");
                return true;
            }
            if (names.isEmpty()) trustedPlayers.remove(uuid);
            owner.sendMessage(ChatColor.YELLOW + "Untrusted " + displayName + ".");
        }
        persistTrustedPlayers();
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("frost")) return false;
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(ChatColor.AQUA + "Frost commands:");
            sender.sendMessage(ChatColor.GRAY + "/frost givesnowball [player]");
            sender.sendMessage(ChatColor.GRAY + "/frost givetnt [player]");
            sender.sendMessage(ChatColor.GRAY + "/frost givewindcharge [player]");
            sender.sendMessage(ChatColor.GRAY + "/frost givetrident [player]" + ChatColor.YELLOW + " (Grapple)");
            sender.sendMessage(ChatColor.GRAY + "/frost giveriptide [player]" + ChatColor.YELLOW + " (Lightning)");
            sender.sendMessage(ChatColor.GRAY + "/frost givefirework [player]");
            sender.sendMessage(ChatColor.GRAY + "/frost config" + ChatColor.YELLOW + " (OP GUI)");
            sender.sendMessage(ChatColor.GRAY + "/frost trust <player>");
            sender.sendMessage(ChatColor.GRAY + "/frost untrust <player>");
            sender.sendMessage(ChatColor.GRAY + "/frost trusted" + ChatColor.YELLOW + " (list)");
            sender.sendMessage(ChatColor.GRAY + "/frost reload");
            return true;
        }
        String action = args[0].toLowerCase(java.util.Locale.ROOT);
        if (action.equals("trust") || action.equals("untrust") || action.equals("trusted")) {
            return manageTrust(sender, action, args);
        }
        if (action.equals("config")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "The config GUI is available in-game only.");
                return true;
            }
            if (!player.hasPermission("frost.config")) {
                player.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }
            openConfigHome(player);
            return true;
        }
        if (action.equals("reload")) {
            if (!sender.hasPermission("frost.reload")) {
                sender.sendMessage(ChatColor.RED + "No permission."); return true;
            }
            reloadConfig(); loadSettings();
            sender.sendMessage(ChatColor.GREEN + "Frost config reloaded!");
            return true;
        }
        if (!sender.hasPermission("frost.give")) {
            sender.sendMessage(ChatColor.RED + "No permission."); return true;
        }
        List<String> valid = List.of("givesnowball", "givetnt", "givewindcharge",
                "givetrident", "givegrapple", "giveriptide", "givefirework");
        if (!valid.contains(action) || args.length > 2) {
            sender.sendMessage(ChatColor.RED + "Use /frost help"); return true;
        }
        Player target;
        if (args.length == 2) target = Bukkit.getPlayerExact(args[1]);
        else if (sender instanceof Player player) target = player;
        else target = null;
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found. Console: /frost " + action + " <player>");
            return true;
        }
        ItemStack item = switch (action) {
            case "givesnowball" -> makeSnowball();
            case "givetnt" -> makeTnt();
            case "givewindcharge" -> makeWindCharge();
            case "givetrident", "givegrapple" -> makeTrident();
            case "giveriptide" -> makeRiptideTrident();
            default -> makeFirework();
        };
        target.getInventory().addItem(item).values().forEach(leftover ->
                target.getWorld().dropItemNaturally(target.getLocation(), leftover));
        sender.sendMessage(ChatColor.GREEN + "Gave " + item.getItemMeta().getDisplayName()
                + ChatColor.GREEN + " to " + target.getName());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> choices = new ArrayList<>(List.of("help"));
            if (sender.hasPermission("frost.trust")) choices.addAll(List.of("trust", "untrust", "trusted"));
            if (sender.hasPermission("frost.config")) choices.add("config");
            if (sender.hasPermission("frost.give")) choices.addAll(List.of(
                    "givesnowball", "givetnt", "givewindcharge", "givetrident",
                    "givegrapple", "giveriptide", "givefirework"));
            if (sender.hasPermission("frost.reload")) choices.add("reload");
            return StringUtil.copyPartialMatches(args[0], choices, new ArrayList<>());
        }
        if (args.length == 2 && (sender.hasPermission("frost.give")
                || sender.hasPermission("frost.trust"))) {
            return StringUtil.copyPartialMatches(args[1],
                    Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), new ArrayList<>());
        }
        return Collections.emptyList();
    }
}
