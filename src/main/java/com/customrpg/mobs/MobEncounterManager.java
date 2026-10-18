package com.customrpg.mobs;

import com.customrpg.CustomRPG;
import com.customrpg.managers.MobManager;
import com.customrpg.utils.SkillDamageGuard;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import java.util.*;
import java.util.function.*;

/** Loaded entities only; one bounded shared timer, cancellable combat, no terrain edits or display actors. */
public final class MobEncounterManager implements Listener {
    private static final NamespacedKey SCALE = new NamespacedKey("customrpg", "encounter_scale");
    private static final NamespacedKey SPEED = new NamespacedKey("customrpg", "encounter_speed");
    private static final NamespacedKey SHOT_DAMAGE = new NamespacedKey("customrpg", "encounter_shot_damage");
    private final CustomRPG plugin;
    private final MobProfileRegistry registry;
    private final LongSupplier clock;
    private final BiConsumer<LivingEntity,MobProfile> appearance;
    private final Consumer<LivingEntity> clearAppearance;
    private final Map<UUID,State> states = new LinkedHashMap<>();
    private final ArrayDeque<UUID> queue = new ArrayDeque<>();
    private final Map<UUID,Shot> shots = new HashMap<>();
    private BukkitTask task;

    private static final class State {
        final LivingEntity mob;
        final MobProfile profile;
        long nextCast, nextAura, lastMelee, buffUntil, guardUntil;
        int move, phase = 1;
        Cast cast;
        Rush rush;
        UUID revenge;
        State(LivingEntity mob, MobProfile profile, long now) {
            this.mob = mob; this.profile = profile; nextCast = now + 2500 + Math.floorMod(mob.getUniqueId().hashCode(), 1500);
        }
    }
    private record Cast(MobAbility ability, Location origin, Location center, Vector direction, long due) {}
    private static final class Rush {
        final MobAbility ability;
        final long ends;
        final Set<UUID> hit = new HashSet<>();
        Location previous;
        Rush(MobAbility ability, Location origin, long ends) { this.ability = ability; previous = origin; this.ends = ends; }
    }
    private record Shot(Projectile projectile, UUID owner, long expires) {}

    public MobEncounterManager(CustomRPG plugin, MobProfileRegistry registry) { this(plugin, registry, System::currentTimeMillis, null, null); }
    MobEncounterManager(CustomRPG plugin, MobProfileRegistry registry, LongSupplier clock,
                        BiConsumer<LivingEntity,MobProfile> appearance, Consumer<LivingEntity> clearAppearance) {
        this.plugin = plugin; this.registry = registry; this.clock = clock;
        this.appearance = appearance == null ? this::applyAppearance : appearance;
        this.clearAppearance = clearAppearance == null ? mob -> {
            modifier(mob, Attribute.SCALE, SCALE, 0); modifier(mob, Attribute.MOVEMENT_SPEED, SPEED, 0);
        } : clearAppearance;
    }
    public void start() {
        if (task != null) return;
        for (World world : plugin.getServer().getWorlds()) for (LivingEntity mob : world.getLivingEntities()) track(mob);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
    }
    public boolean usesNewBehavior(String id) {
        var profile = registry.get(id); return profile != null && !profile.legacyBehavior();
    }
    public void track(LivingEntity mob) {
        String id = plugin.getMobManager().getCustomMobKey(mob);
        if (id == null || !mob.isValid() || mob.isDead() || states.containsKey(mob.getUniqueId())) return;
        var profile = registry.get(id);
        if (profile != null) appearance.accept(mob, profile);
        states.put(mob.getUniqueId(), new State(mob, profile, clock.getAsLong())); queue.addLast(mob.getUniqueId());
    }
    public Collection<LivingEntity> legacyMobs() {
        return states.values().stream().filter(s -> (s.profile == null || s.profile.legacyBehavior())
                && s.mob.isValid() && !s.mob.isDead() && target(s) != null).map(s -> s.mob).toList();
    }
    private void tick() {
        long now = clock.getAsLong();
        int budget = Math.min(128, queue.size());
        for (int i = 0; i < budget; i++) {
            UUID id = queue.removeFirst(); State state = states.get(id);
            if (state == null) continue;
            if (!state.mob.isValid() || state.mob.isDead()) { forget(id); continue; }
            try { update(state, now); }
            catch (RuntimeException error) {
                plugin.getLogger().warning("[MobEncounters] Suspended " + id + ": " + error.getMessage());
                forget(id); continue;
            }
            if (states.containsKey(id)) queue.addLast(id);
        }
        for (var shot : new ArrayList<>(shots.values()))
            if (shot.expires <= now || !shot.projectile.isValid() || !states.containsKey(shot.owner)) {
                if (shot.projectile.isValid()) shot.projectile.remove(); shots.remove(shot.projectile.getUniqueId());
            }
    }
    private void update(State state, long now) {
        var mob = state.mob; var profile = state.profile;
        if (profile == null) return;
        if (mob instanceof Tameable pet && pet.isTamed()) { state.cast = null; return; }
        Player target = target(state);
        if (target == null) { state.cast = null; state.rush = null; return; }
        if (state.nextAura <= now) { aura(mob, profile); state.nextAura = now + 2000; }
        if (profile.legacyBehavior()) return;
        if (profile.temperament().equals("timid")) { flee(mob, target); return; }
        if (state.rush != null) { advanceRush(state, now); if (state.rush != null) return; }
        int phase = healthFraction(mob) <= 0.25 && isBoss(mob) ? 3
                : healthFraction(mob) <= (isBoss(mob) ? 0.5 : profile.rageThreshold()) ? 2 : 1;
        if (phase > state.phase) {
            state.phase = phase;
            mob.getWorld().playSound(mob.getLocation(), "entity.ravager.roar", 0.7f, 1.1f);
            target.sendMessage("§c" + plainName(mob) + "進入狂暴階段！注意加快的蓄力招式。");
        }
        // Native animal silhouettes may lack a hostile goal. Navigation + a paced melee fallback makes them functional.
        if (mob instanceof Mob ai && !(mob instanceof Enemy)) {
            ai.setTarget(target);
            if (mob.getLocation().distanceSquared(target.getLocation()) > 4) ai.getPathfinder().moveTo(target, 1.1);
            else if (state.lastMelee + 1200 <= now && mob.hasLineOfSight(target)) {
                state.lastMelee = now; target.damage(0.1, mob);
            }
        }
        if (state.cast != null) {
            if (state.cast.due > now) { telegraph(state); return; }
            Cast cast = state.cast; state.cast = null;
            if (now - cast.due <= 3000) execute(state, cast, target);
        }
        if (state.nextCast > now || state.cast != null) return;
        MobAbility ability = profile.abilities().get(state.move++ % profile.abilities().size());
        Location origin = mob.getLocation().clone(), center = target.getLocation().clone();
        Vector direction = target.getEyeLocation().toVector().subtract(mob.getEyeLocation().toVector());
        if (direction.lengthSquared() < 0.001) direction = new Vector(0, 0, 1); else direction.normalize();
        if (!Set.of(MobAbility.ARCANE_BURST, MobAbility.THUNDER_STRIKE, MobAbility.LEAP, MobAbility.POISON_CLOUD).contains(ability)) center = origin.clone();
        state.cast = new Cast(ability, origin, center, direction, now + (long)(profile.windup() * 1000));
        state.nextCast = now + (long)(profile.cooldown() * 1000 * (state.phase == 3 ? 0.55 : state.phase == 2 ? 0.75 : 1));
        mob.getWorld().playSound(origin, "block.note_block.pling", 0.6f, 0.6f);
        target.sendActionBar(Component.text(plainName(mob) + " 正在蓄力：" + ability.displayName()));
        telegraph(state);
    }
    private Player target(State state) {
        var mob = state.mob;
        if (state.profile != null && state.profile.temperament().equals("neutral")) {
            Player player = state.revenge == null ? null : plugin.getServer().getPlayer(state.revenge);
            return player != null && eligible(mob, player, state.profile.range() + 8) ? player : null;
        }
        double range = state.profile == null ? 20 : state.profile.range() + 8;
        if (mob instanceof Mob ai && ai.getTarget() instanceof Player player && eligible(mob, player, range)) return player;
        return mob.getWorld().getNearbyPlayers(mob.getLocation(), range).stream().filter(p -> eligible(mob, p, range))
                .min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(mob.getLocation()))).orElse(null);
    }
    private boolean eligible(LivingEntity mob, Player player, double range) {
        if (!player.isOnline() || player.isDead() || !player.getWorld().equals(mob.getWorld())
                || (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE)
                || player.getLocation().distanceSquared(mob.getLocation()) > range * range || !mob.hasLineOfSight(player)) return false;
        if (plugin.getSafeZoneManager() != null && (plugin.getSafeZoneManager().isInSafeZone(player.getLocation())
                || plugin.getSafeZoneManager().isInSafeZone(mob.getLocation()))) return false;
        if (plugin.getProtectionAreaManager() != null && plugin.getProtectionAreaManager().isInProtectedArea2D(player.getLocation())) return false;
        return !(mob instanceof Mob ai) || plugin.getDistanceSpawnManager() == null || !plugin.getDistanceSpawnManager().shouldCancelTarget(ai, player);
    }
    private boolean isBoss(LivingEntity mob) {
        var data = plugin.getMobManager().getMobData(plugin.getMobManager().getCustomMobKey(mob));
        return data != null && data.isBoss();
    }
    private double healthFraction(LivingEntity mob) {
        double max = MobManager.hasVirtualHP(mob) ? MobManager.getVirtualMaxHP(mob) : mob.getMaxHealth();
        return max <= 0 ? 1 : (MobManager.hasVirtualHP(mob) ? MobManager.getVirtualCurrentHP(mob) : mob.getHealth()) / max;
    }
    private String plainName(LivingEntity mob) { return ChatColor.stripColor(mob.getCustomName() == null ? "怪物" : mob.getCustomName()); }
    private void execute(State state, Cast cast, Player target) {
        var mob = state.mob; var profile = state.profile; long now = clock.getAsLong();
        if (!eligible(mob, target, profile.range() + 8) || !mob.getWorld().equals(cast.origin.getWorld())
                || mob.getLocation().distanceSquared(cast.origin) > 64) return;
        if (cast.ability == MobAbility.FLEE) { flee(mob, target); return; }
        if (cast.ability == MobAbility.FORTIFY) {
            state.guardUntil = now + 4000; mob.getWorld().playSound(mob.getLocation(), "item.shield.block", 0.8f, 0.7f); return;
        }
        if (cast.ability == MobAbility.HOWL) {
            for (State ally : states.values()) if (ally.mob.getWorld().equals(mob.getWorld())
                    && ally.mob.getLocation().distanceSquared(mob.getLocation()) <= 64 && ally.profile != null
                    && ally.profile.abilities().contains(MobAbility.HOWL)) ally.buffUntil = now + 8000;
            mob.getWorld().playSound(mob.getLocation(), "entity.wolf.howl", 0.8f, 0.8f); return;
        }
        if (cast.ability == MobAbility.VOLLEY) {
            for (double angle : new double[]{-0.14, 0, 0.14}) {
                if (shots.size() >= 256) break;
                Arrow arrow = mob.launchProjectile(Arrow.class, cast.direction.clone().rotateAroundY(angle).multiply(1.5));
                arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                arrow.getPersistentDataContainer().set(SHOT_DAMAGE, PersistentDataType.DOUBLE, damage(state) * 0.65);
                shots.put(arrow.getUniqueId(), new Shot(arrow, mob.getUniqueId(), now + 8000));
            }
            return;
        }
        if (Set.of(MobAbility.FROST_BOLT, MobAbility.SHADOW_LANCE, MobAbility.SOUL_DRAIN).contains(cast.ability)) {
            Location eye = cast.origin.clone().add(0, mob.getEyeHeight(), 0);
            var ray = mob.getWorld().rayTrace(eye, cast.direction, profile.range(), FluidCollisionMode.NEVER, true,
                    0.4, e -> e instanceof Player p && eligible(mob, p, profile.range()));
            double length = ray == null ? profile.range() : ray.getHitPosition().distance(eye.toVector());
            for (double d = 0; d <= length; d += 0.6) dust(eye.clone().add(cast.direction.clone().multiply(d)), profile.color(), 1);
            if (ray != null && ray.getHitEntity() instanceof Player victim) {
                double dealt = strike(state, victim, damage(state));
                if (dealt > 0 && cast.ability == MobAbility.FROST_BOLT) victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 0));
                if (dealt > 0 && cast.ability == MobAbility.SHADOW_LANCE) victim.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 30, 0));
                if (dealt > 0 && cast.ability == MobAbility.SOUL_DRAIN) heal(mob, dealt * 0.5);
            }
            return;
        }
        Location center = cast.center;
        if (cast.ability == MobAbility.CHARGE || cast.ability == MobAbility.LEAP) {
            Vector impulse = cast.direction.clone().setY(0);
            if (impulse.lengthSquared() > 0.001) impulse.normalize().multiply(cast.ability == MobAbility.CHARGE ? 0.85 : 0.65);
            mob.setVelocity(impulse.setY(cast.ability == MobAbility.LEAP ? 0.4 : 0.15));
            state.rush = new Rush(cast.ability, mob.getLocation().clone(), now + 1000);
            return; // Hits follow the body's actual path, never a projected strike through an obstacle.
        }
        int count = 0;
        for (Player victim : mob.getWorld().getNearbyPlayers(center, Math.max(profile.range(), profile.radius()) + 1)) {
            if (++count > 32) break;
            if (!eligible(mob, victim, profile.range() + 4)) continue;
            Vector delta = victim.getLocation().toVector().subtract(cast.origin.toVector());
            boolean hit;
            if (cast.ability == MobAbility.CLEAVE || cast.ability == MobAbility.FIRE_BREATH || cast.ability == MobAbility.WIND_GUST) {
                hit = delta.lengthSquared() <= profile.radius() * profile.radius()
                        && (delta.lengthSquared() < 0.01 || delta.normalize().dot(cast.direction) >= 0.35);
            } else hit = victim.getLocation().distanceSquared(center) <= profile.radius() * profile.radius();
            if (!hit || strike(state, victim, damage(state)) <= 0) continue;
            switch (cast.ability) {
                case POISON_CLOUD -> victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0));
                case FIRE_BREATH -> ignite(mob, victim);
                case STOMP, LEAP, EXPLOSIVE_PULSE -> victim.setVelocity(victim.getVelocity().setY(0.3));
                case WIND_GUST, CHARGE -> {
                    Vector away = victim.getLocation().toVector().subtract(mob.getLocation().toVector());
                    if (away.lengthSquared() > 0.001) victim.setVelocity(away.normalize().multiply(0.7).setY(0.2));
                }
                default -> { }
            }
        }
        mob.getWorld().spawnParticle(cast.ability == MobAbility.THUNDER_STRIKE ? Particle.ELECTRIC_SPARK : Particle.CRIT,
                center.clone().add(0, 0.5, 0), 18, profile.radius() / 2, 0.4, profile.radius() / 2, 0.03);
        mob.getWorld().playSound(center, "entity.generic.explode", 0.5f, 1.3f);
    }
    private void advanceRush(State state, long now) {
        Rush rush = state.rush; Location current = state.mob.getLocation();
        if (now > rush.ends) { state.rush = null; return; }
        if (!current.getWorld().equals(rush.previous.getWorld())) { state.rush = null; return; }
        Vector segment = current.toVector().subtract(rush.previous.toVector());
        double length = segment.lengthSquared();
        if (length > 64) { state.rush = null; return; } // External teleport / knockback is not a charge hit.
        if (length > 0.001 && current.getWorld().rayTraceBlocks(rush.previous.clone().add(0, 0.5, 0),
                segment.clone().normalize(), Math.sqrt(length), FluidCollisionMode.NEVER, true) != null) {
            state.rush = null; return;
        }
        for (Player victim : current.getWorld().getNearbyPlayers(current, 10)) {
            if (rush.hit.contains(victim.getUniqueId()) || !eligible(state.mob, victim, 10)) continue;
            Vector point = victim.getLocation().toVector().subtract(rush.previous.toVector());
            double fraction = length < 0.001 ? 0 : Math.clamp(point.dot(segment) / length, 0, 1);
            double distance = point.subtract(segment.clone().multiply(fraction)).lengthSquared();
            if (distance > (rush.ability == MobAbility.LEAP ? 4 : 2.25) || strike(state, victim, damage(state)) <= 0) continue;
            rush.hit.add(victim.getUniqueId());
            Vector away = victim.getLocation().toVector().subtract(current.toVector()).setY(0);
            if (away.lengthSquared() > 0.001) away.normalize().multiply(0.6);
            victim.setVelocity(away.setY(rush.ability == MobAbility.LEAP ? 0.3 : 0.2));
        }
        rush.previous = current.clone();
        if (now >= rush.ends) state.rush = null;
    }
    private double damage(State state) {
        var data = plugin.getMobManager().getMobData(plugin.getMobManager().getCustomMobKey(state.mob));
        if (data == null) return 1;
        return Math.max(1, data.calculateDamage(plugin.getMobManager().getMobLevel(state.mob)) * state.profile.damageMultiplier()
                * (state.phase == 3 ? 1.3 : state.phase == 2 ? 1.15 : 1) * (state.buffUntil > clock.getAsLong() ? 1.2 : 1));
    }
    private double strike(State state, Player victim, double amount) {
        if (!eligible(state.mob, victim, state.profile.range() + 8)) return 0;
        double before = victim.getHealth() + victim.getAbsorptionAmount();
        SkillDamageGuard.damage(victim, amount, state.mob);
        return Math.max(0, before - victim.getHealth() - victim.getAbsorptionAmount());
    }
    private void heal(LivingEntity mob, double amount) {
        if (amount <= 0 || mob.isDead()) return;
        var event = new EntityRegainHealthEvent(mob, amount, EntityRegainHealthEvent.RegainReason.CUSTOM);
        plugin.getServer().getPluginManager().callEvent(event);
        if (!event.isCancelled() && Double.isFinite(event.getAmount()) && event.getAmount() > 0)
            mob.setHealth(Math.min(mob.getMaxHealth(), mob.getHealth() + event.getAmount()));
    }
    private void ignite(LivingEntity mob, Player player) {
        var event = new EntityCombustByEntityEvent(mob, player, 3.0f); plugin.getServer().getPluginManager().callEvent(event);
        if (!event.isCancelled() && Float.isFinite(event.getDuration()) && event.getDuration() > 0)
            player.setFireTicks(Math.max(player.getFireTicks(), Math.min(100, (int)(event.getDuration() * 20))));
    }
    private void flee(LivingEntity mob, Player player) {
        Vector away = mob.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
        if (away.lengthSquared() < 0.01) return;
        Location destination = mob.getLocation().add(away.normalize().multiply(5));
        if (mob instanceof Mob ai && destination.getWorld().isChunkLoaded(destination.getBlockX() >> 4, destination.getBlockZ() >> 4)) ai.getPathfinder().moveTo(destination, 1.2);
    }
    private void telegraph(State state) {
        Cast cast = state.cast; if (cast == null) return;
        for (int i = 0; i < 16; i++) {
            double angle = Math.PI * 2 * i / 16;
            dust(cast.center.clone().add(Math.cos(angle) * state.profile.radius(), 0.15, Math.sin(angle) * state.profile.radius()), state.profile.color(), 1);
        }
        if (Set.of(MobAbility.CHARGE, MobAbility.FROST_BOLT, MobAbility.SHADOW_LANCE, MobAbility.SOUL_DRAIN, MobAbility.VOLLEY).contains(cast.ability))
            for (int i = 1; i <= Math.min(12, state.profile.range()); i++) dust(cast.origin.clone().add(cast.direction.clone().multiply(i)).add(0, 0.2, 0), state.profile.color(), 1);
    }
    private void aura(LivingEntity mob, MobProfile profile) {
        if (profile.particle().equals("DUST")) dust(mob.getLocation().add(0, Math.min(2, mob.getHeight() * 0.7), 0), profile.color(), 4);
        else mob.getWorld().spawnParticle(Particle.valueOf(profile.particle()), mob.getLocation().add(0, 1, 0), 3, 0.3, 0.4, 0.3, 0.01);
    }
    private void dust(Location location, int rgb, int count) {
        location.getWorld().spawnParticle(Particle.DUST, location, count, 0.05, 0.05, 0.05, 0, new Particle.DustOptions(Color.fromRGB(rgb), 1));
    }
    public void applyAppearance(LivingEntity mob, MobProfile profile) {
        modifier(mob, Attribute.SCALE, SCALE, profile.scale() - 1);
        modifier(mob, Attribute.MOVEMENT_SPEED, SPEED, profile.speedBonus());
        if (mob instanceof Ageable ageable) ageable.setAdult();
        if (mob instanceof Panda panda) { panda.setMainGene(Panda.Gene.BROWN); panda.setHiddenGene(Panda.Gene.BROWN); }
        if (mob instanceof Snowman snowman) snowman.setDerp(true);
        if (mob instanceof Creeper creeper && (profile.id().contains("thunder") || profile.id().contains("arctic") || profile.id().contains("ancient"))) creeper.setPowered(true);
        if (mob instanceof Phantom phantom) phantom.setSize(profile.id().startsWith("boss_") ? 4 : 2);
        if (mob instanceof Hoglin hoglin) hoglin.setImmuneToZombification(true);
        if (mob instanceof PiglinAbstract piglin) piglin.setImmuneToZombification(true);
        if (mob instanceof Zombie zombie) zombie.setShouldBurnInDay(false);
        if (mob instanceof AbstractSkeleton skeleton) skeleton.setShouldBurnInDay(false);
        if (mob instanceof Slime slime && !profile.legacyBehavior()) slime.setSize(profile.id().contains("giant") ? 4 : 2);
        EntityEquipment equipment = mob.getEquipment();
        if (equipment == null) return;
        for (var entry : profile.equipment().entrySet()) {
            EquipmentSlot slot = switch (entry.getKey()) {
                case "helmet" -> EquipmentSlot.HEAD; case "chestplate" -> EquipmentSlot.CHEST;
                case "leggings" -> EquipmentSlot.LEGS; case "boots" -> EquipmentSlot.FEET;
                case "off-hand" -> EquipmentSlot.OFF_HAND; default -> EquipmentSlot.HAND;
            };
            ItemStack current = equipment.getItem(slot);
            if (current == null || current.getType().isAir()) {
                var item = new ItemStack(Material.valueOf(entry.getValue()));
                if (item.getItemMeta() instanceof LeatherArmorMeta meta) { meta.setColor(Color.fromRGB(profile.color())); item.setItemMeta(meta); }
                equipment.setItem(slot, item); equipment.setDropChance(slot, 0);
            }
        }
    }
    private void modifier(LivingEntity mob, Attribute attribute, NamespacedKey key, double amount) {
        var instance = mob.getAttribute(attribute); if (instance == null) return;
        for (var modifier : new ArrayList<>(instance.getModifiers())) if (key.equals(modifier.getKey())) instance.removeModifier(modifier);
        if (amount != 0) instance.addTransientModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_SCALAR));
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDefense(EntityDamageEvent event) {
        State state = states.get(event.getEntity().getUniqueId());
        if (state != null && state.guardUntil > clock.getAsLong()) event.setDamage(event.getDamage() * 0.6);
        if (state != null && event instanceof EntityDamageByEntityEvent attack && attack.getDamager() instanceof Player player) state.revenge = player.getUniqueId();
    }
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onArrowDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Projectile projectile) || !(event.getEntity() instanceof Player victim)) return;
        Double amount = projectile.getPersistentDataContainer().get(SHOT_DAMAGE, PersistentDataType.DOUBLE);
        if (amount == null || !Double.isFinite(amount) || amount <= 0) return;
        State state = projectile.getShooter() instanceof LivingEntity owner ? states.get(owner.getUniqueId()) : null;
        if (state == null || state.profile == null || !eligible(state.mob, victim, state.profile.range() + 8)) {
            event.setCancelled(true); return;
        }
        event.setDamage(Math.min(500, amount));
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShot(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof LivingEntity mob)) return;
        State state = states.get(mob.getUniqueId());
        if (state != null && state.profile != null) {
            if (shots.size() >= 256) { event.setCancelled(true); return; }
            event.getEntity().getPersistentDataContainer().set(SHOT_DAMAGE, PersistentDataType.DOUBLE, damage(state) * 0.75);
            if (event.getEntity() instanceof Fireball fireball) { fireball.setIsIncendiary(false); fireball.setYield(0); }
            shots.put(event.getEntity().getUniqueId(), new Shot(event.getEntity(), mob.getUniqueId(), clock.getAsLong() + 8000));
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMelee(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player && event.getDamager() instanceof LivingEntity source) {
            State state = states.get(source.getUniqueId()); if (state != null) state.lastMelee = clock.getAsLong();
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrime(ExplosionPrimeEvent event) {
        String id = plugin.getMobManager().getCustomMobKey(event.getEntity());
        if (usesNewBehavior(id)) event.setCancelled(true); // Custom creepers use their dodgeable, terrain-free pulse.
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockChange(EntityChangeBlockEvent event) {
        if (registry.get(plugin.getMobManager().getCustomMobKey(event.getEntity())) != null) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockForm(EntityBlockFormEvent event) { if (customSource(event.getEntity())) event.setCancelled(true); }
    private boolean customSource(Entity entity) {
        if (entity instanceof Projectile shot && shot.getShooter() instanceof Entity owner) entity = owner;
        return entity != null && registry.get(plugin.getMobManager().getCustomMobKey(entity)) != null;
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent event) { if (customSource(event.getEntity())) event.blockList().clear(); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIgnite(org.bukkit.event.block.BlockIgniteEvent event) { if (customSource(event.getIgnitingEntity())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSplit(SlimeSplitEvent event) {
        if (usesNewBehavior(plugin.getMobManager().getCustomMobKey(event.getEntity()))) event.setCancelled(true);
    }
    @EventHandler public void onLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) if (entity instanceof LivingEntity living) track(living);
    }
    @EventHandler public void onRemove(EntityRemoveEvent event) { forget(event.getEntity().getUniqueId()); }
    private void forget(UUID id) {
        states.remove(id); queue.remove(id);
        for (var shot : new ArrayList<>(shots.values())) if (shot.owner.equals(id)) {
            if (shot.projectile.isValid()) shot.projectile.remove(); shots.remove(shot.projectile.getUniqueId());
        }
    }
    public boolean reloadProfiles() {
        if (!registry.reload()) return false;
        for (State state : new ArrayList<>(states.values())) {
            var mob = state.mob; forget(mob.getUniqueId());
            if (mob.isValid() && !mob.isDead()) { clearAppearance.accept(mob); track(mob); }
        }
        return true;
    }
    public void shutdown() {
        if (task != null) { task.cancel(); task = null; }
        for (State state : states.values()) if (state.mob.isValid()) clearAppearance.accept(state.mob);
        for (Shot shot : new ArrayList<>(shots.values())) if (shot.projectile.isValid()) shot.projectile.remove();
        shots.clear(); states.clear(); queue.clear();
    }
}
