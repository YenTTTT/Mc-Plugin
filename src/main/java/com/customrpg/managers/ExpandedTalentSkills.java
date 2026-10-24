package com.customrpg.managers;

import com.customrpg.CustomRPG;
import com.customrpg.talents.*;
import com.customrpg.utils.SkillDamageGuard;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import java.util.*;
import java.util.function.LongSupplier;

/** Four additional trees share one bounded timer. All combat goes through cancellable Bukkit events. */
public final class ExpandedTalentSkills implements Listener {
    private static final Map<String,TalentBranch> ACTIVE = Map.ofEntries(
            Map.entry("frost_shard", TalentBranch.FROST), Map.entry("frost_nova", TalentBranch.FROST),
            Map.entry("frost_lance", TalentBranch.FROST), Map.entry("frost_blizzard", TalentBranch.FROST),
            Map.entry("storm_arc", TalentBranch.STORM), Map.entry("storm_gust", TalentBranch.STORM),
            Map.entry("storm_dash", TalentBranch.STORM), Map.entry("storm_tempest", TalentBranch.STORM),
            Map.entry("earth_guard", TalentBranch.EARTH), Map.entry("earth_shockwave", TalentBranch.EARTH),
            Map.entry("earth_grasp", TalentBranch.EARTH), Map.entry("earth_bastion", TalentBranch.EARTH),
            Map.entry("light_mend", TalentBranch.LIGHT), Map.entry("light_lance", TalentBranch.LIGHT),
            Map.entry("light_purge", TalentBranch.LIGHT), Map.entry("light_sanctuary", TalentBranch.LIGHT));
    private static final Set<String> PASSIVE = Set.of("frost_attunement", "frost_resolve", "storm_attunement",
            "storm_footwork", "earth_endurance", "earth_training", "light_devotion", "light_wisdom");
    private final CustomRPG plugin;
    private final LongSupplier clock;
    interface StatusEffects {
        boolean has(LivingEntity target, String id);
        void remove(Player target, String id);
        void slow(LivingEntity target, int ticks, int amplifier);
    }
    private final StatusEffects status;
    private final List<Field> fields = new ArrayList<>();
    private final Map<UUID, Guard> guards = new HashMap<>();
    private BukkitTask task;
    private record Guard(Player player, long until, double reduction) {}
    private static final class Field {
        final Player source; final Talent talent; final int level; final Location center;
        final long until; final double damage, healing; long nextPulse;
        Field(Player source, Talent talent, int level, long now, double damage, double healing) {
            this.source = source; this.talent = talent; this.level = level; this.center = source.getLocation().clone();
            this.until = now + (long)(value(talent, level, "duration", 5) * 1000);
            this.nextPulse = now + 1000; this.damage = damage; this.healing = healing;
        }
        Location position() { return talent.getId().equals("storm_tempest") ? source.getLocation() : center; }
    }
    public ExpandedTalentSkills(CustomRPG plugin) { this(plugin, System::currentTimeMillis, null); }
    ExpandedTalentSkills(CustomRPG plugin, LongSupplier clock, StatusEffects status) {
        this.plugin = plugin; this.clock = clock;
        this.status = status == null ? new StatusEffects() {
            private PotionEffectType type(String id) { return Objects.requireNonNull(PotionEffectType.getByKey(NamespacedKey.minecraft(id))); }
            public boolean has(LivingEntity target, String id) { return target.hasPotionEffect(type(id)); }
            public void remove(Player target, String id) { target.removePotionEffect(type(id)); }
            public void slow(LivingEntity target, int ticks, int amplifier) { target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, amplifier)); }
        } : status;
    }
    public static boolean handles(String id) { return ACTIVE.containsKey(id); }
    public static boolean isExpansionBranch(TalentBranch branch) {
        return Set.of(TalentBranch.FROST, TalentBranch.STORM, TalentBranch.EARTH, TalentBranch.LIGHT).contains(branch);
    }
    public static void validate(Talent talent) {
        if (talent.getType() == TalentType.ACTIVE) {
            if (ACTIVE.get(talent.getId()) != talent.getBranch()) throw new IllegalArgumentException("Unimplemented talent: " + talent.getId());
        } else if (talent.getType() != TalentType.PASSIVE || !PASSIVE.contains(talent.getId()))
            throw new IllegalArgumentException("Unknown passive: " + talent.getId());
        if (talent.getMaxLevel() < 1 || talent.getMaxLevel() > 3 || talent.getPointsPerLevel() < 1
                || talent.getGuiSlot() < 0 || talent.getGuiSlot() >= 53 || Material.matchMaterial(talent.getIcon()) == null)
            throw new IllegalArgumentException("Invalid talent layout/levels: " + talent.getId());
        bounded(talent.getManaCost(), 0, 500); bounded(talent.getCooldown(), 0, 120);
        bounded(talent.getHpCost(), 0, .5);
        if (talent.getType() == TalentType.ACTIVE && talent.getCooldown() < 1)
            throw new IllegalArgumentException("Active talent needs a cooldown");
        for (int level = 1; level <= talent.getMaxLevel(); level++) {
            var data = talent.getLevelData(level);
            if (data == null || data.effects.isEmpty()) throw new IllegalArgumentException("Missing level " + level + " for " + talent.getId());
            if (!data.scaling.isEmpty()) throw new IllegalArgumentException("Use flat magicScaling/strengthScaling/spiritScaling effects");
            Set<String> attributes = Set.of("statBonus_strength", "statBonus_magic", "statBonus_agility", "statBonus_vitality", "statBonus_defense", "statBonus_spirit");
            Set<String> effects = Set.of("baseDamage", "baseHeal", "magicScaling", "strengthScaling", "spiritScaling", "range", "radius", "duration", "slowDuration",
                    "slowAmplifier", "reduction", "distance", "chainCount", "chainRange", "chilledBonus", "undeadBonus", "knockback");
            for (String key : data.effects.keySet())
                if (!(talent.getType() == TalentType.PASSIVE ? attributes : effects).contains(key))
                    throw new IllegalArgumentException("Unimplemented effect: " + key);
            for (double number : data.effects.values()) bounded(number, 0, 200);
            for (double number : data.scaling.values()) bounded(number, 0, 2);
            bounded(value(talent, level, "radius", 4), 1, 6);
            bounded(value(talent, level, "range", 14), 2, 24);
            bounded(value(talent, level, "duration", 5), 1, 10);
            bounded(value(talent, level, "reduction", .2), 0, .4);
            bounded(value(talent, level, "distance", 4), 1, 6);
            bounded(value(talent, level, "chainCount", 3), 1, 5);
            bounded(value(talent, level, "chainRange", 4), 1, 6);
            bounded(value(talent, level, "slowDuration", 2), 0, 5);
            bounded(value(talent, level, "slowAmplifier", 0), 0, 3);
            bounded(value(talent, level, "knockback", .6), 0, 1);
            bounded(value(talent, level, "magicScaling", 0), 0, 2);
            bounded(value(talent, level, "strengthScaling", 0), 0, 2);
            bounded(value(talent, level, "spiritScaling", 0), 0, 2);
            bounded(value(talent, level, "chilledBonus", 0), 0, 1);
            bounded(value(talent, level, "undeadBonus", 0), 0, 1);
        }
    }
    private static void bounded(double number, double min, double max) {
        if (!Double.isFinite(number) || number < min || number > max) throw new IllegalArgumentException("Unsafe talent value: " + number);
    }
    private static double value(Talent talent, int level, String key, double fallback) {
        return talent.getEffectDouble(level, key, fallback);
    }
    public void start() {
        if (task == null) task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
    }
    /** Resource checks and cooldowns are owned by TalentSkillManager, before and after this dispatch. */
    public boolean cast(Player player, Talent talent, int level) {
        if (!handles(talent.getId()) || !valid(player)) return false;
        double radius = value(talent, level, "radius", 4), range = value(talent, level, "range", 14);
        double damage = damage(player, talent, level);
        String id = talent.getId();
        switch (id) {
            case "frost_shard", "frost_lance", "light_lance" -> {
                LivingEntity target = aim(player, range);
                if (target == null) return noTarget(player);
                if (id.equals("frost_lance") && status.has(target, "slowness")) damage *= 1 + value(talent, level, "chilledBonus", .35);
                if (id.equals("light_lance") && undead(target)) damage *= 1 + value(talent, level, "undeadBonus", .25);
                beam(player.getEyeLocation(), target.getLocation().add(0, .5, 0), color(talent));
                if (hit(player, target, damage) > 0 && id.equals("frost_shard")) slow(target, talent, level);
            }
            case "storm_arc" -> {
                LivingEntity first = aim(player, range); if (first == null) return noTarget(player);
                chain(player, first, talent, level, damage);
            }
            case "frost_nova", "storm_gust", "earth_shockwave", "earth_grasp" -> {
                Location center = player.getLocation();
                if (id.equals("earth_grasp")) {
                    var ray = player.getWorld().rayTraceBlocks(player.getEyeLocation(), player.getEyeLocation().getDirection(), range, FluidCollisionMode.NEVER, true);
                    if (ray == null) return noTarget(player);
                    center = ray.getHitPosition().toLocation(player.getWorld()).add(0, .1, 0);
                }
                for (LivingEntity target : enemies(player, center, radius)) {
                    Vector delta = target.getLocation().toVector().subtract(player.getLocation().toVector());
                    if (id.equals("storm_gust") && delta.lengthSquared() > .001
                            && delta.normalize().dot(player.getEyeLocation().getDirection()) < .35) continue;
                    if (hit(player, target, damage) <= 0) continue;
                    if (id.equals("frost_nova") || id.equals("earth_grasp")) slow(target, talent, level);
                    else push(player, target, value(talent, level, "knockback", .6));
                }
                ring(center, radius, color(talent));
            }
            case "storm_dash" -> { return dash(player, value(talent, level, "distance", 4)); }
            case "earth_guard" -> {
                guards.put(player.getUniqueId(), new Guard(player, clock.getAsLong() + (long)(value(talent, level, "duration", 6) * 1000), value(talent, level, "reduction", .2)));
                ring(player.getLocation(), 1.2, color(talent));
            }
            case "light_mend" -> {
                Player target = player;
                var ray = player.getWorld().rayTrace(player.getEyeLocation(), player.getEyeLocation().getDirection(), range,
                        FluidCollisionMode.NEVER, true, .4, e -> e instanceof Player other && ally(player, other) && !other.equals(player));
                if (ray != null && ray.getHitEntity() instanceof Player other) target = other;
                return heal(target, healing(player, talent, level));
            }
            case "light_purge" -> {
                boolean removed = false;
                for (String effect : List.of("poison", "wither", "slowness", "weakness", "blindness", "darkness"))
                    if (status.has(player, effect)) { status.remove(player, effect); removed = true; }
                return heal(player, healing(player, talent, level)) || removed;
            }
            case "frost_blizzard", "storm_tempest", "earth_bastion", "light_sanctuary" -> {
                boolean replaces = fields.stream().anyMatch(f -> f.source.equals(player) && f.talent.getId().equals(id));
                if (!replaces && (fields.size() >= 64 || fields.stream().filter(f -> f.source.equals(player)).count() >= 3)) {
                    player.sendMessage("§c目前持續領域已達上限，請稍後再施放。"); return false;
                }
                fields.removeIf(f -> f.source.equals(player) && f.talent.getId().equals(id));
                fields.add(new Field(player, talent, level, clock.getAsLong(), damage, healing(player, talent, level)));
                ring(player.getLocation(), radius, color(talent));
            }
            default -> { return false; }
        }
        player.getWorld().playSound(player.getLocation(), "block.amethyst_block.chime", .6f, 1.2f);
        return true;
    }
    boolean valid(Player player) {
        return player.isOnline() && !player.isDead() && (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE);
    }
    private boolean learned(Player source, Talent talent) {
        return valid(source) && plugin.getTalentManager().getPlayerTalents(source).getTalentLevel(talent.getId()) > 0;
    }
    boolean protectedAt(Location at) {
        return plugin.getSafeZoneManager() != null && plugin.getSafeZoneManager().isInSafeZone(at)
                || plugin.getProtectionAreaManager() != null && plugin.getProtectionAreaManager().isInProtectedArea2D(at);
    }
    private boolean sameTeam(Player source, Player other) {
        var board = source.getScoreboard(); if (board == null) return false;
        var team = board.getEntryTeam(source.getName()); return team != null && team.hasEntry(other.getName());
    }
    boolean ally(Player source, Player other) {
        return valid(other) && source.getWorld().equals(other.getWorld())
                && (source.equals(other) || sameTeam(source, other) && source.hasLineOfSight(other));
    }
    boolean enemy(Player source, LivingEntity target) {
        if (com.customrpg.utils.ControlledUndead.is(target) || target.hasMetadata("beast_owner") || source.equals(target) || target.isDead() || !target.isValid() || target.isInvulnerable() || target instanceof ArmorStand
                || !source.getWorld().equals(target.getWorld()) || protectedAt(source.getLocation()) || protectedAt(target.getLocation())
                || !source.hasLineOfSight(target)) return false;
        if(com.customrpg.utils.ControlledChicken.is(target)){
            var id=com.customrpg.utils.ControlledChicken.owner(target);var owner=id==null?null:plugin.getServer().getPlayer(id);
            return owner!=null&&!source.equals(owner)&&valid(owner)&&source.getWorld().getPVP()&&!sameTeam(source,owner)&&!com.customrpg.utils.ControlledChicken.preview(target);
        }
        if (target instanceof Player other) return valid(other) && source.getWorld().getPVP() && !sameTeam(source, other);
        if (target instanceof Tameable pet && pet.isTamed()) return false;
        return target instanceof Enemy || plugin.getMobManager() != null && plugin.getMobManager().getCustomMobKey(target) != null;
    }
    LivingEntity aim(Player source, double range) {
        var ray = source.getWorld().rayTrace(source.getEyeLocation(), source.getEyeLocation().getDirection(), range,
                FluidCollisionMode.NEVER, true, .35, e -> e instanceof LivingEntity target && enemy(source, target));
        return ray != null && ray.getHitEntity() instanceof LivingEntity target ? target : null;
    }
    List<LivingEntity> enemies(Player source, Location center, double radius) {
        List<LivingEntity> result = new ArrayList<>();
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (entity instanceof LivingEntity target && target.getLocation().distanceSquared(center) <= radius * radius
                    && enemy(source, target) && clearPath(center.clone().add(0, .5, 0), target.getEyeLocation())) {
                result.add(target); if (result.size() >= 32) break;
            }
        }
        return result;
    }
    private boolean clearPath(Location start, Location end) {
        Vector direction = end.toVector().subtract(start.toVector()); double length = direction.length();
        return length < .01 || start.getWorld().rayTraceBlocks(start, direction.normalize(), length, FluidCollisionMode.NEVER, true) == null;
    }
    double damage(Player source, Talent talent, int level) {
        var stats = plugin.getPlayerStatsManager().getStats(source);
        return Math.min(200, value(talent, level, "baseDamage", 0) + stats.getTotalMagic() * value(talent, level, "magicScaling", 0)
                + stats.getTotalStrength() * value(talent, level, "strengthScaling", 0));
    }
    private double healing(Player source, Talent talent, int level) {
        return Math.min(100, value(talent, level, "baseHeal", 0) + plugin.getPlayerStatsManager().getStats(source).getTotalSpirit() * value(talent, level, "spiritScaling", 0));
    }
    double hit(Player source, LivingEntity target, double damage) {
        if (!enemy(source, target)) return 0;
        boolean virtual = MobManager.hasVirtualHP(target);
        double before = virtual ? MobManager.getVirtualCurrentHP(target) : target.getHealth() + target.getAbsorptionAmount();
        SkillDamageGuard.damage(target, damage, source);
        double after = virtual ? MobManager.getVirtualCurrentHP(target) : target.getHealth() + target.getAbsorptionAmount();
        return Math.max(0, before - after);
    }
    private boolean heal(Player target, double amount) {
        if (!valid(target) || amount <= 0 || target.getHealth() >= target.getMaxHealth()) return false;
        var event = new EntityRegainHealthEvent(target, amount, EntityRegainHealthEvent.RegainReason.CUSTOM);
        plugin.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled() || !Double.isFinite(event.getAmount()) || event.getAmount() <= 0 || !valid(target)) return false;
        target.setHealth(Math.min(target.getMaxHealth(), target.getHealth() + event.getAmount())); return true;
    }
    void slow(LivingEntity target, Talent talent, int level) {
        if (target.isDead()) return;
        double duration = value(talent, level, "slowDuration", 2);
        var data = plugin.getMobManager() == null ? null : plugin.getMobManager().getMobData(plugin.getMobManager().getCustomMobKey(target));
        if (data != null && data.isBoss()) duration *= .5;
        status.slow(target, (int)(duration * 20), (int)value(talent, level, "slowAmplifier", 0));
    }
    void push(Player source, LivingEntity target, double strength) {
        if (target.isDead()) return;
        Vector away = target.getLocation().toVector().subtract(source.getLocation().toVector()).setY(0);
        if (away.lengthSquared() > .001) target.setVelocity(away.normalize().multiply(strength).setY(.2));
    }
    private void chain(Player source, LivingEntity first, Talent talent, int level, double damage) {
        Set<UUID> seen = new HashSet<>(); LivingEntity target = first; Location previous = source.getEyeLocation();
        for (int i = 0; i < (int)value(talent, level, "chainCount", 3) && target != null; i++) {
            seen.add(target.getUniqueId()); beam(previous, target.getLocation().add(0, .5, 0), color(talent));
            if (hit(source, target, damage * Math.pow(.8, i)) <= 0) break;
            LivingEntity last = target; previous = last.getEyeLocation();
            target = enemies(source, last.getLocation(), value(talent, level, "chainRange", 4)).stream()
                    .filter(e -> !seen.contains(e.getUniqueId()) && last.hasLineOfSight(e)
                            && e.getLocation().distanceSquared(source.getLocation()) <= 24 * 24)
                    .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(last.getLocation()))).orElse(null);
        }
    }
    boolean dash(Player player, double distance) {
        if (protectedAt(player.getLocation())) return false;
        Vector forward = player.getEyeLocation().getDirection().setY(0);
        if (forward.lengthSquared() < .001) return false;
        forward.normalize(); Location start = player.getLocation(), last = null;
        for (double d = .25; d <= distance; d += .25) {
            Location at = start.clone().add(forward.clone().multiply(d)); boolean loaded = true;
            for (int x = ((int)Math.floor(at.getX() - .4)) >> 4; x <= ((int)Math.floor(at.getX() + .4)) >> 4; x++)
                for (int z = ((int)Math.floor(at.getZ() - .4)) >> 4; z <= ((int)Math.floor(at.getZ() + .4)) >> 4; z++)
                    loaded &= at.getWorld().isChunkLoaded(x, z);
            if (!loaded || !at.getWorld().getWorldBorder().isInside(at) || protectedAt(at) || player.collidesAt(at)
                    || at.getBlock().isLiquid() || at.clone().add(0, 1, 0).getBlock().isLiquid()) break;
            last = at;
        }
        if (last == null || last.distanceSquared(start) < .25 || !player.teleport(last, PlayerTeleportEvent.TeleportCause.PLUGIN)) return false;
        ring(last, .8, 0xFFDC55); return true;
    }
    private boolean noTarget(Player player) { player.sendMessage("§c請瞄準射程內、未被方塊遮擋的有效目標。"); return false; }
    private boolean undead(LivingEntity entity) {
        return entity instanceof Zombie || entity instanceof AbstractSkeleton || entity instanceof Phantom || entity instanceof Wither || entity instanceof Zoglin;
    }
    private int color(Talent talent) {
        return switch (talent.getBranch()) { case FROST -> 0x72DDF7; case STORM -> 0xFFDC55; case EARTH -> 0xB89968; default -> 0xFFF3B0; };
    }
    void ring(Location center, double radius, int color) {
        var dust = new Particle.DustOptions(Color.fromRGB(color), 1);
        for (int i = 0; i < 20; i++) { double angle = Math.PI * i / 10;
            center.getWorld().spawnParticle(Particle.DUST, center.clone().add(Math.cos(angle) * radius, .15, Math.sin(angle) * radius), 1, 0, 0, 0, 0, dust);
        }
    }
    void beam(Location start, Location end, int color) {
        Vector delta = end.toVector().subtract(start.toVector()); double length = delta.length();
        if (length < .01) return; delta.normalize();
        for (double d = 0; d <= Math.min(24, length); d += .6)
            start.getWorld().spawnParticle(Particle.DUST, start.clone().add(delta.clone().multiply(d)), 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(color), 1));
    }
    private void tick() {
        long now = clock.getAsLong();
        guards.values().removeIf(g -> g.until <= now || !valid(g.player)
                || plugin.getTalentManager().getPlayerTalents(g.player).getTalentLevel("earth_guard") <= 0);
        for (Field field : new ArrayList<>(fields)) {
            if (now > field.until || !learned(field.source, field.talent) || !field.source.getWorld().equals(field.center.getWorld())) { fields.remove(field); continue; }
            if (field.nextPulse > now) continue;
            field.nextPulse = now + 1000; // Never replay missed damage/healing after a lag spike.
            Location center = field.position(); double radius = value(field.talent, field.level, "radius", 4);
            if (!center.getWorld().isChunkLoaded(center.getBlockX() >> 4, center.getBlockZ() >> 4)) { fields.remove(field); continue; }
            ring(center, radius, color(field.talent));
            if (field.talent.getId().equals("light_sanctuary")) {
                int count = 0;
                for (Player ally : center.getWorld().getNearbyPlayers(center, radius)) {
                    if (++count > 16) break;
                    if (ally(field.source, ally) && ally.getLocation().distanceSquared(center) <= radius * radius
                            && clearPath(center.clone().add(0, .5, 0), ally.getEyeLocation())) heal(ally, field.healing);
                }
            } else if (!field.talent.getId().equals("earth_bastion")) {
                for (LivingEntity target : enemies(field.source, center, radius))
                    if (hit(field.source, target, field.damage) > 0 && field.talent.getId().equals("frost_blizzard")) slow(target, field.talent, field.level);
            }
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (SkillDamageGuard.isTransfer(event.getEntity())) return;
        if (!(event.getEntity() instanceof Player player) || !valid(player)
                || event.getCause() == EntityDamageEvent.DamageCause.VOID || event.getCause() == EntityDamageEvent.DamageCause.KILL
                || event.getCause() == EntityDamageEvent.DamageCause.SUICIDE) return;
        long now = clock.getAsLong(); double reduction = 0;
        Guard guard = guards.get(player.getUniqueId());
        if (guard != null && guard.until > now && plugin.getTalentManager().getPlayerTalents(player).getTalentLevel("earth_guard") > 0) reduction = guard.reduction;
        for (Field field : fields) if (field.talent.getId().equals("earth_bastion") && field.until > now
                && learned(field.source, field.talent) && field.center.getWorld().equals(player.getWorld()) && ally(field.source, player)
                && clearPath(field.center.clone().add(0, .5, 0), player.getEyeLocation())
                && player.getLocation().distanceSquared(field.center) <= Math.pow(value(field.talent, field.level, "radius", 4), 2))
            reduction = Math.max(reduction, value(field.talent, field.level, "reduction", .15));
        if (reduction > 0) event.setDamage(event.getDamage() * (1 - reduction));
    }
    public void cancel(Player player) { fields.removeIf(f -> f.source.equals(player)); guards.remove(player.getUniqueId()); }
    public void clear() { fields.clear(); guards.clear(); }
    @EventHandler public void onQuit(PlayerQuitEvent event) { cancel(event.getPlayer()); }
    @EventHandler public void onDeath(PlayerDeathEvent event) { cancel(event.getEntity()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) { cancel(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) { cancel(event.getPlayer()); }
    public void shutdown() { if (task != null) { task.cancel(); task = null; } clear(); }
}
