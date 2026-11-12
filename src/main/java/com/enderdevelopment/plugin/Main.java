package com.enderdevelopment.plugin;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.entity.Vex;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

public final class Main extends JavaPlugin implements Listener {
    private static final String FIGHTING = "fighting";
    private static final String ALLY = "ally";
    private static final double BOSS_HEALTH = 300.0;
    private final Map<UUID, Encounter> encounters = new ConcurrentHashMap<>();
    private NamespacedKey markerKey;
    private NamespacedKey stateKey;
    private NamespacedKey starKey;
    private NamespacedKey eggKey;
    private NamespacedKey ownerKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        markerKey = new NamespacedKey(this, "preston");
        stateKey = new NamespacedKey(this, "state");
        starKey = new NamespacedKey(this, "nether_star");
        eggKey = new NamespacedKey(this, "dragon_egg");
        ownerKey = new NamespacedKey(this, "owner");
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("preston") != null) {
            getCommand("preston").setExecutor(this);
        }
        recoverLoadedBosses();
        Bukkit.getScheduler().runTaskTimer(this, this::tickEncounters, 20L, 20L);
    }

    @Override
    public void onDisable() {
        encounters.values().forEach(encounter -> encounter.bar.removeAll());
        encounters.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1 || !args[0].equalsIgnoreCase("spawn")) {
            sender.sendMessage(Component.text("Usage: /preston spawn", NamedTextColor.YELLOW));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only a player can start this encounter.", NamedTextColor.RED));
            return true;
        }
        if (player.getWorld().getEnvironment() != World.Environment.NETHER) {
            player.sendMessage(Component.text("Preston can only be summoned in the Nether.", NamedTextColor.RED));
            return true;
        }
        Location center = player.getLocation().getBlock().getLocation();
        buildCastle(center);
        spawnPreston(center.clone().add(0.5, 2.0, 0.5));
        player.sendMessage(Component.text("Preston waits within the Nether castle. Attack him to begin.", NamedTextColor.GOLD));
        return true;
    }

    private void buildCastle(Location center) {
        World world = center.getWorld();
        if (world == null) return;
        int x = center.getBlockX();
        int y = center.getBlockY() - 1;
        int z = center.getBlockZ();
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                world.getBlockAt(x + dx, y, z + dz).setType(Material.NETHER_BRICKS, false);
                if (Math.abs(dx) == 6 || Math.abs(dz) == 6) {
                    for (int dy = 1; dy <= 5; dy++) {
                        Material wall = dy == 5 && ((dx + dz) & 1) == 0
                                ? Material.NETHER_BRICK_FENCE : Material.NETHER_BRICKS;
                        world.getBlockAt(x + dx, y + dy, z + dz).setType(wall, false);
                    }
                }
            }
        }
        for (int dx : new int[]{-4, 4}) {
            for (int dz : new int[]{-4, 4}) {
                for (int dy = 1; dy <= 7; dy++) {
                    world.getBlockAt(x + dx, y + dy, z + dz).setType(Material.POLISHED_BLACKSTONE_BRICKS, false);
                }
            }
        }
        world.getBlockAt(x, y + 6, z).setType(Material.MAGMA_BLOCK, false);
    }

    private void spawnPreston(Location location) {
        Vex boss = location.getWorld().spawn(location, Vex.class);
        boss.setCustomName("Preston, the Jester of War");
        boss.setCustomNameVisible(true);
        boss.setPersistent(true);
        boss.setRemoveWhenFarAway(false);
        setAttribute(boss, Attribute.MAX_HEALTH, BOSS_HEALTH);
        setAttribute(boss, Attribute.ATTACK_DAMAGE, 16.0);
        setAttribute(boss, Attribute.MOVEMENT_SPEED, 0.42);
        boss.setHealth(BOSS_HEALTH);
        equipPreston(boss);
        PersistentDataContainer data = boss.getPersistentDataContainer();
        data.set(markerKey, PersistentDataType.STRING, "boss");
        data.set(stateKey, PersistentDataType.STRING, "neutral");
        encounters.put(boss.getUniqueId(), new Encounter(boss));
    }

    private void equipPreston(Vex boss) {
        if (boss.getEquipment() == null) return;
        ItemStack hood = new ItemStack(Material.LEATHER_HELMET);
        ItemMeta hoodMeta = hood.getItemMeta();
        if (hoodMeta instanceof LeatherArmorMeta leather) {
            leather.setColor(Color.fromRGB(157, 33, 184));
            leather.displayName(Component.text("Jester Hood", NamedTextColor.LIGHT_PURPLE));
            hood.setItemMeta(leather);
        }
        ItemStack wings = new ItemStack(Material.LEATHER_CHESTPLATE);
        ItemMeta wingMeta = wings.getItemMeta();
        if (wingMeta instanceof LeatherArmorMeta leather) {
            leather.setColor(Color.fromRGB(105, 105, 112));
            wings.setItemMeta(leather);
        }
        ItemStack axe = new ItemStack(Material.NETHERITE_AXE);
        ItemMeta axeMeta = axe.getItemMeta();
        if (axeMeta != null) {
            axeMeta.displayName(Component.text("Warcleaver", NamedTextColor.RED));
            Enchantment sharpness = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("sharpness"));
            if (sharpness != null) axeMeta.addEnchant(sharpness, 4, true);
            axe.setItemMeta(axeMeta);
        }
        boss.getEquipment().setHelmet(hood);
        boss.getEquipment().setChestplate(wings);
        boss.getEquipment().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
        boss.getEquipment().setBoots(new ItemStack(Material.NETHERITE_BOOTS));
        boss.getEquipment().setItemInMainHand(axe);
        boss.getEquipment().setHelmetDropChance(0.0f);
        boss.getEquipment().setChestplateDropChance(0.0f);
        boss.getEquipment().setLeggingsDropChance(0.0f);
        boss.getEquipment().setBootsDropChance(0.0f);
        boss.getEquipment().setItemInMainHandDropChance(0.0f);
    }

    private void setAttribute(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    @EventHandler
    public void onBossDamaged(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Vex boss) || !isPreston(boss)) return;
        PersistentDataContainer data = boss.getPersistentDataContainer();
        if (ALLY.equals(data.get(stateKey, PersistentDataType.STRING))) {
            event.setCancelled(true);
            return;
        }
        LivingEntity attacker = getLivingAttacker(event.getDamager());
        if (attacker == null) return;
        if (attacker instanceof Player player) {
            data.set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
        }
        beginFight(boss, attacker);
        if (boss.getHealth() - event.getFinalDamage() <= 0.0) {
            event.setCancelled(true);
            Player owner = attacker instanceof Player player ? player : getStoredOwner(data);
            becomeAlly(boss, owner);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Vex boss) || !isPreston(boss)) return;
        ItemStack held = event.getHand() == EquipmentSlot.HAND
                ? event.getPlayer().getInventory().getItemInMainHand()
                : event.getPlayer().getInventory().getItemInOffHand();
        PersistentDataContainer data = boss.getPersistentDataContainer();
        if (ALLY.equals(data.get(stateKey, PersistentDataType.STRING))) return;
        if (held.getType() == Material.NETHER_STAR && !Boolean.TRUE.equals(data.get(starKey, PersistentDataType.BOOLEAN))) {
            consumeOne(held);
            data.set(starKey, PersistentDataType.BOOLEAN, true);
            event.getPlayer().sendMessage(Component.text("Preston accepts the Nether star.", NamedTextColor.LIGHT_PURPLE));
        } else if (held.getType() == Material.DRAGON_EGG && !Boolean.TRUE.equals(data.get(eggKey, PersistentDataType.BOOLEAN))) {
            consumeOne(held);
            data.set(eggKey, PersistentDataType.BOOLEAN, true);
            event.getPlayer().sendMessage(Component.text("Preston accepts the dragon egg.", NamedTextColor.LIGHT_PURPLE));
        } else {
            return;
        }
        event.setCancelled(true);
        if (Boolean.TRUE.equals(data.get(starKey, PersistentDataType.BOOLEAN))
                && Boolean.TRUE.equals(data.get(eggKey, PersistentDataType.BOOLEAN))) {
            becomeAlly(boss, event.getPlayer());
        }
    }

    @EventHandler
    public void onPrestonDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Vex boss && isPreston(boss)) {
            Encounter encounter = encounters.remove(boss.getUniqueId());
            if (encounter != null) encounter.bar.removeAll();
        }
    }

    @EventHandler
    public void protectAllyOwner(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        LivingEntity attacker = getLivingAttacker(event.getDamager());
        if (!(attacker instanceof Monster)) return;
        for (Entity nearby : player.getNearbyEntities(40, 24, 40)) {
            if (nearby instanceof Vex ally && isPreston(ally)
                    && ALLY.equals(ally.getPersistentDataContainer().get(stateKey, PersistentDataType.STRING))
                    && player.getUniqueId().toString().equals(ally.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING))) {
                ally.setTarget(attacker);
            }
        }
    }

    private void beginFight(Vex boss, LivingEntity target) {
        PersistentDataContainer data = boss.getPersistentDataContainer();
        if (!FIGHTING.equals(data.get(stateKey, PersistentDataType.STRING))) {
            data.set(stateKey, PersistentDataType.STRING, FIGHTING);
            Encounter encounter = encounters.computeIfAbsent(boss.getUniqueId(), ignored -> new Encounter(boss));
            encounter.fightTicks = 0;
            for (Player player : boss.getWorld().getPlayers()) {
                if (player.getLocation().distanceSquared(boss.getLocation()) <= 4096.0) {
                    encounter.bar.addPlayer(player);
                    playBossMusic(player);
                }
            }
        }
        boss.setTarget(target);
    }

    private void playBossMusic(Player player) {
        String sound = getConfig().getString("war-sound", "preston:war");
        player.playSound(player.getLocation(), sound,
                (float) getConfig().getDouble("war-volume", 1.0),
                (float) getConfig().getDouble("war-pitch", 1.0));
    }

    private void becomeAlly(Vex boss, Player owner) {
        PersistentDataContainer data = boss.getPersistentDataContainer();
        if (owner == null || !owner.isOnline()) {
            data.set(stateKey, PersistentDataType.STRING, ALLY);
            return;
        }
        data.set(stateKey, PersistentDataType.STRING, ALLY);
        data.set(ownerKey, PersistentDataType.STRING, owner.getUniqueId().toString());
        Encounter encounter = encounters.computeIfAbsent(boss.getUniqueId(), ignored -> new Encounter(boss));
        encounter.bar.removeAll();
        setAttribute(boss, Attribute.MAX_HEALTH, 80.0);
        boss.setHealth(80.0);
        boss.setTarget(null);
        boss.setCustomName("Preston, the Warbound Ally");
        owner.sendMessage(Component.text("Preston has joined you and will defend you from hostile mobs.", NamedTextColor.GREEN));
    }

    private void tickEncounters() {
        for (Encounter encounter : encounters.values()) {
            Vex boss = encounter.boss;
            if (!boss.isValid() || boss.isDead()) {
                encounter.bar.removeAll();
                encounters.remove(boss.getUniqueId());
                continue;
            }
            PersistentDataContainer data = boss.getPersistentDataContainer();
            String state = data.get(stateKey, PersistentDataType.STRING);
            if (ALLY.equals(state)) {
                tickAlly(boss, data);
                emitJesterEffects(boss);
            } else if (FIGHTING.equals(state)) {
                tickBossFight(encounter, boss);
            } else {
                emitJesterEffects(boss);
            }
        }
    }

    private void tickBossFight(Encounter encounter, Vex boss) {
        encounter.fightTicks += 20;
        AttributeInstance maxHealthAttribute = boss.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = maxHealthAttribute == null ? BOSS_HEALTH : maxHealthAttribute.getValue();
        encounter.bar.setProgress(Math.max(0.0, Math.min(1.0, boss.getHealth() / maxHealth)));
        for (Player player : boss.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(boss.getLocation()) <= 4096.0) {
                if (!encounter.bar.getPlayers().contains(player)) {
                    encounter.bar.addPlayer(player);
                    playBossMusic(player);
                }
            } else {
                encounter.bar.removePlayer(player);
            }
        }
        emitJesterEffects(boss);
        LivingEntity target = boss.getTarget();
        Player player = target instanceof Player candidate && candidate.isOnline() && !candidate.isDead()
                ? candidate : null;
        if (player == null) {
            player = boss.getWorld().getPlayers().stream()
                    .filter(candidate -> candidate.getLocation().distanceSquared(boss.getLocation()) <= 1296.0)
                    .min(Comparator.comparingDouble(candidate -> candidate.getLocation().distanceSquared(boss.getLocation())))
                    .orElse(null);
            if (player != null) boss.setTarget(player);
        }
        if (player == null) return;
        if (encounter.fightTicks % 160 == 0) spearRain(boss, player);
        if (encounter.fightTicks % 240 == 0) boss.getWorld().strikeLightning(player.getLocation());
    }

    private void spearRain(Vex boss, Player target) {
        World world = boss.getWorld();
        for (int i = 0; i < 5; i++) {
            Location landing = target.getLocation().clone().add(
                    ThreadLocalRandom.current().nextDouble(-4.0, 4.0), 0.0,
                    ThreadLocalRandom.current().nextDouble(-4.0, 4.0));
            Location origin = landing.clone().add(0.0, 15.0, 0.0);
            Vector direction = landing.toVector().subtract(origin.toVector()).normalize();
            Trident trident = world.spawnArrow(origin, direction, 1.8f, 0.0f, Trident.class);
            trident.setShooter((ProjectileSource) boss);
            trident.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        }
        world.spawnParticle(Particle.CRIT, target.getLocation().add(0.0, 1.0, 0.0), 30, 3.0, 0.5, 3.0, 0.05);
    }

    private void tickAlly(Vex ally, PersistentDataContainer data) {
        Player owner = getStoredOwner(data);
        if (owner == null || !owner.isOnline() || owner.getWorld() != ally.getWorld()) {
            ally.setTarget(null);
            return;
        }
        if (ally.getLocation().distanceSquared(owner.getLocation()) > 196.0) {
            ally.teleport(owner.getLocation().clone().add(2.0, 1.5, 2.0));
        }
        LivingEntity currentTarget = ally.getTarget();
        if (currentTarget == null || !currentTarget.isValid() || currentTarget.isDead()) {
            Monster hostile = owner.getNearbyEntities(20, 12, 20).stream()
                    .filter(Monster.class::isInstance)
                    .map(Monster.class::cast)
                    .filter(entity -> !isPreston(entity))
                    .min(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(owner.getLocation())))
                    .orElse(null);
            ally.setTarget(hostile);
        }
    }

    private void emitJesterEffects(Vex boss) {
        Location location = boss.getLocation().add(0.0, 0.7, 0.0);
        boss.getWorld().spawnParticle(Particle.WITCH, location, 3, 0.25, 0.35, 0.25, 0.0);
        boss.getWorld().spawnParticle(Particle.SMOKE, location.clone().subtract(0.0, 0.6, 0.0), 2, 0.2, 0.2, 0.2, 0.01);
    }

    private LivingEntity getLivingAttacker(Entity damager) {
        if (damager instanceof LivingEntity living) return living;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity living) return living;
        return null;
    }

    private Player getStoredOwner(PersistentDataContainer data) {
        String ownerId = data.get(ownerKey, PersistentDataType.STRING);
        if (ownerId == null) return null;
        try {
            return Bukkit.getPlayer(UUID.fromString(ownerId));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean isPreston(Entity entity) {
        return entity.getPersistentDataContainer().has(markerKey, PersistentDataType.STRING);
    }

    private void consumeOne(ItemStack stack) {
        stack.setAmount(Math.max(0, stack.getAmount() - 1));
    }

    private void recoverLoadedBosses() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Vex boss && isPreston(boss)) {
                    Encounter encounter = new Encounter(boss);
                    encounters.put(boss.getUniqueId(), encounter);
                    if (FIGHTING.equals(boss.getPersistentDataContainer().get(stateKey, PersistentDataType.STRING))) {
                        for (Player player : world.getPlayers()) encounter.bar.addPlayer(player);
                    }
                }
            }
        }
    }

    private static final class Encounter {
        private final Vex boss;
        private final BossBar bar;
        private int fightTicks;

        private Encounter(Vex boss) {
            this.boss = boss;
            this.bar = Bukkit.createBossBar("Preston, the Jester of War", BarColor.PURPLE, BarStyle.SEGMENTED_10);
            this.bar.setVisible(true);
        }
    }
}
