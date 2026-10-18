package com.customrpg.races;

import com.customrpg.CustomRPG;
import com.customrpg.managers.MobManager;
import com.customrpg.utils.SkillDamageGuard;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.function.Function;
import java.util.function.LongSupplier;

/** Main-thread ability sessions. Only our keyed speed modifier and flight grant are removed. */
public final class RacialSkillManager implements Listener {
    static final NamespacedKey READY_AT = new NamespacedKey("customrpg", "racial_skill_ready_at");
    static final NamespacedKey FLIGHT_GRANTED = new NamespacedKey("customrpg", "racial_flight_granted");
    static final NamespacedKey LANDING_UNTIL = new NamespacedKey("customrpg", "racial_landing_until");
    private static final NamespacedKey SPEED = new NamespacedKey("customrpg", "racial_skill_speed");
    private static final NamespacedKey CUSTOM_MOB = new NamespacedKey("customrpg", "custom_mob_type");
    private final CustomRPG plugin;
    private final LongSupplier clock;
    private final Function<Player, AttributeInstance> movement, health;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Long> landingGrace = new HashMap<>();
    private final Map<EntityDamageByEntityEvent, Hit> pendingHits = new IdentityHashMap<>();
    private BukkitTask task;

    private static final class Session {
        final Player player;
        final RacialSkill skill;
        final long endsAt;
        boolean armed = true;
        Session(Player player, RacialSkill skill, long endsAt) {
            this.player = player; this.skill = skill; this.endsAt = endsAt;
        }
    }
    private record Hit(Session session, LivingEntity target, double virtualBefore, int fireSeconds, boolean consume) {}

    public RacialSkillManager(CustomRPG plugin) {
        this(plugin, System::currentTimeMillis, p -> p.getAttribute(Attribute.MOVEMENT_SPEED), p -> p.getAttribute(Attribute.MAX_HEALTH));
    }
    RacialSkillManager(CustomRPG plugin, LongSupplier clock, Function<Player, AttributeInstance> movement,
                       Function<Player, AttributeInstance> health) {
        this.plugin = plugin; this.clock = clock; this.movement = movement; this.health = health;
    }
    public void start() {
        if (task != null) return;
        for (Player player : plugin.getServer().getOnlinePlayers()) { recoverFlight(player); restoreLanding(player); }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
    }
    public RacialSkill getSkill(Player player) {
        var race = plugin.getRaceManager().getPlayerRaceData(player);
        return race == null ? null : RacialSkill.forRace(race.getId());
    }
    public long remainingSeconds(Player player) {
        Long until = player.getPersistentDataContainer().get(READY_AT, PersistentDataType.LONG);
        return until == null ? 0 : Math.max(0, (until - clock.getAsLong() + 999) / 1000);
    }
    public boolean cast(Player player) {
        if (!player.isOnline() || player.isDead() || player.getGameMode() == GameMode.SPECTATOR) return false;
        var skill = getSkill(player);
        if (skill == null) { player.sendMessage("§c請先選擇有主動技能的種族：/race"); return false; }
        long remaining = remainingSeconds(player);
        if (remaining > 0) { player.sendMessage("§c種族技能冷卻中，剩餘 " + remaining + " 秒。"); return false; }
        // Resolve required targets before spending resources or changing the cooldown.
        LivingEntity target = null;
        if (skill == RacialSkill.UNDEAD) {
            var hit = player.getWorld().rayTrace(player.getEyeLocation(), player.getEyeLocation().getDirection(),
                    12, FluidCollisionMode.NEVER, true, 0.35, e -> e instanceof LivingEntity living && isEnemy(player, living));
            if (hit == null || !(hit.getHitEntity() instanceof LivingEntity living)) {
                player.sendMessage("§c請瞄準 12 格內未被方塊遮擋的敵人。"); return false;
            }
            target = living;
        }
        if (!plugin.getManaManager().consumeMana(player, skill.manaCost())) {
            player.sendMessage("§c魔力不足，需要 " + skill.manaCost() + " MANA。"); return false;
        }
        // One shared racial cooldown survives reconnect/reload and cannot be bypassed by changing race.
        player.getPersistentDataContainer().set(READY_AT, PersistentDataType.LONG, clock.getAsLong() + skill.cooldown() * 1000L);
        cancel(player);
        if (skill.duration() > 0) sessions.put(player.getUniqueId(), new Session(player, skill, clock.getAsLong() + skill.duration() * 1000L));
        switch (skill) {
            case HUMAN -> plugin.getManaManager().restoreMana(player, 15);
            case ELF -> speed(player, 0.30);
            case DWARF -> speed(player, -0.15);
            case UNDEAD -> heal(player, hit(player, target, 12));
            case DEMON -> burst(player, 5, 10, 4, false);
            case ANGEL -> {
                heal(player, 12);
                for (Player ally : player.getWorld().getPlayers())
                    if (!ally.equals(player) && !ally.isDead() && sameTeam(player, ally)
                            && ally.getLocation().distanceSquared(player.getLocation()) <= 36 && player.hasLineOfSight(ally)) heal(ally, 12);
            }
            case DRAGONKIN -> {
                if (!player.getAllowFlight()) {
                    player.getPersistentDataContainer().set(FLIGHT_GRANTED, PersistentDataType.BYTE, (byte) 1);
                    player.setAllowFlight(true);
                    player.setFlying(true);
                }
                player.sendMessage("§6龍翼展開！使用空白鍵升空、Shift 下降；12 秒後收翼。");
            }
            case BEASTKIN -> {
                Vector direction = player.getLocation().getDirection().setY(0);
                if (direction.lengthSquared() > 0.001) direction.normalize().multiply(1.1);
                player.setVelocity(direction.setY(0.45));
            }
            case HALFLING -> speed(player, 0.25);
            case GOLEM -> burst(player, 4, 8, 0, true);
            default -> { /* ORC / DARK_ELF are handled in the damage pipeline. */ }
        }
        player.sendMessage("§d[種族技能] §f" + skill.displayName() + " §7— " + skill.description());
        player.getWorld().playSound(player.getLocation(), "block.beacon.activate", 0.7f, 1.1f);
        return true;
    }
    private boolean sameTeam(Player a, Player b) {
        var team = a.getScoreboard().getEntryTeam(a.getName());
        return team != null && team.hasEntry(b.getName());
    }
    private boolean isEnemy(Player player, LivingEntity target) {
        if (target.equals(player) || target.isDead() || target instanceof ArmorStand || target.isInvulnerable()) return false;
        if (plugin.getSafeZoneManager() != null && (plugin.getSafeZoneManager().isInSafeZone(player.getLocation())
                || plugin.getSafeZoneManager().isInSafeZone(target.getLocation()))) return false;
        if (target instanceof Player other) return player.getWorld().getPVP() && !sameTeam(player, other)
                && other.getGameMode() != GameMode.CREATIVE && other.getGameMode() != GameMode.SPECTATOR;
        if (target instanceof Tameable pet && pet.isTamed()) return false;
        return target instanceof Monster || target instanceof Slime || target instanceof Ghast || target instanceof Phantom
                || target.getPersistentDataContainer().has(CUSTOM_MOB, PersistentDataType.STRING);
    }
    private void burst(Player player, double radius, double damage, int fireSeconds, boolean knockback) {
        int count = 0;
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof LivingEntity target) || !isEnemy(player, target)
                    || target.getLocation().distanceSquared(player.getLocation()) > radius * radius || !player.hasLineOfSight(target)) continue;
            if (++count > 32) break;
            if (hit(player, target, damage) <= 0) continue;
            if (fireSeconds > 0) ignite(player, target, fireSeconds);
            if (knockback) {
                Vector away = target.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
                if (away.lengthSquared() > 0.001) away.normalize().multiply(0.7);
                target.setVelocity(away.setY(0.4));
            }
        }
        player.getWorld().spawnParticle(fireSeconds > 0 ? Particle.FLAME : Particle.CRIT,
                player.getLocation().add(0, 1, 0), 40, radius / 2, 0.5, radius / 2, 0.02);
    }
    /** Actual health/absorption lost, rather than requested damage, determines drain and follow-up effects. */
    private double hit(Player source, LivingEntity target, double amount) {
        double before = target.getHealth() + target.getAbsorptionAmount();
        SkillDamageGuard.damage(target, amount, source);
        return Math.min(amount, Math.max(0, before - target.getHealth() - target.getAbsorptionAmount()));
    }
    private void heal(Player player, double amount) {
        var attribute = health.apply(player);
        if (amount <= 0 || player.isDead() || attribute == null || player.getHealth() >= attribute.getValue()) return;
        var event = new EntityRegainHealthEvent(player, amount, EntityRegainHealthEvent.RegainReason.CUSTOM);
        plugin.getServer().getPluginManager().callEvent(event);
        if (!event.isCancelled() && Double.isFinite(event.getAmount()) && event.getAmount() > 0 && !player.isDead())
            player.setHealth(Math.min(attribute.getValue(), player.getHealth() + event.getAmount()));
    }
    private void ignite(Player source, LivingEntity target, int seconds) {
        if (target.isDead()) return;
        var event = new EntityCombustByEntityEvent(source, target, (float) seconds);
        plugin.getServer().getPluginManager().callEvent(event);
        if (!event.isCancelled() && Float.isFinite(event.getDuration()) && event.getDuration() > 0)
            target.setFireTicks(Math.max(target.getFireTicks(), Math.min(200, (int) (event.getDuration() * 20))));
    }
    private void speed(Player player, double amount) {
        removeSpeed(player);
        var attribute = movement.apply(player);
        if (attribute != null) attribute.addTransientModifier(new AttributeModifier(SPEED, amount, AttributeModifier.Operation.ADD_SCALAR));
    }
    private void removeSpeed(Player player) {
        var attribute = movement.apply(player);
        if (attribute != null) for (var modifier : new ArrayList<>(attribute.getModifiers()))
            if (SPEED.equals(modifier.getKey())) attribute.removeModifier(modifier);
    }
    private Session active(Player player) {
        var session = sessions.get(player.getUniqueId());
        if (session != null && (session.endsAt <= clock.getAsLong() || getSkill(player) != session.skill)) {
            cancel(player); return null;
        }
        return session;
    }
    // Registered after weapon/stat damage calculation and BEFORE virtual-HP conversion.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player victim) {
            Long grace = landingGrace.get(victim.getUniqueId());
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL && grace != null && grace > clock.getAsLong()) {
                event.setCancelled(true); clearLanding(victim); return;
            }
            var defense = active(victim);
            if (defense != null) {
                if (defense.skill == RacialSkill.HALFLING && defense.armed && event instanceof EntityDamageByEntityEvent) {
                    defense.armed = false; event.setCancelled(true); victim.sendMessage("§a幸運閃避！"); return;
                }
                double multiplier = switch (defense.skill) {
                    case DWARF -> 0.65;
                    case DARK_ELF, ANGEL -> 0.8;
                    case GOLEM -> 0.75;
                    case ORC -> 1.15;
                    default -> 1;
                };
                event.setDamage(event.getDamage() * multiplier);
            }
        }
        if (!(event instanceof EntityDamageByEntityEvent attack) || !(event.getEntity() instanceof LivingEntity target)) return;
        Player source = attack.getDamager() instanceof Player p ? p
                : attack.getDamager() instanceof Projectile shot && shot.getShooter() instanceof Player p ? p : null;
        if (source == null || SkillDamageGuard.isSkillDamage(source)) return;
        var session = active(source);
        if (session == null || !isEnemy(source, target)) return;
        boolean melee = attack.getDamager() instanceof Player;
        double multiplier = switch (session.skill) {
            case HUMAN -> 1.2;
            case ELF -> melee ? 1 : 1.25;
            case ORC -> melee ? 1.35 : 1;
            case BEASTKIN -> melee && session.armed ? 1.6 : 1;
            case DARK_ELF -> melee && session.armed ? 1.5 : 1;
            default -> 1;
        };
        int fire = session.skill == RacialSkill.DRAGONKIN ? 3 : 0;
        attack.setDamage(attack.getDamage() * multiplier + (fire > 0 ? 4 : 0));
        boolean consume = melee && session.armed && (session.skill == RacialSkill.BEASTKIN || session.skill == RacialSkill.DARK_ELF);
        if (fire > 0 || consume) pendingHits.put(attack, new Hit(session, target,
                MobManager.hasVirtualHP(target) ? MobManager.getVirtualCurrentHP(target) : -1, fire, consume));
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void afterDamage(EntityDamageByEntityEvent event) {
        Hit hit = pendingHits.remove(event);
        if (hit == null || event.getFinalDamage() <= 0) return;
        // Virtual HP intentionally cancels its event after applying damage; protection cancellations do not change HP.
        if (event.isCancelled() && !(hit.virtualBefore >= 0 && MobManager.getVirtualCurrentHP(hit.target) < hit.virtualBefore)) return;
        if (hit.consume) hit.session.armed = false;
        if (hit.fireSeconds > 0) ignite(hit.session.player, hit.target, hit.fireSeconds);
    }
    private void tick() {
        for (var session : new ArrayList<>(sessions.values())) {
            var player = session.player;
            if (!player.isOnline() || player.isDead() || player.getGameMode() == GameMode.SPECTATOR) { cancel(player); continue; }
            if (active(player) == null) continue;
            if (session.skill == RacialSkill.DRAGONKIN) drawWings(player);
        }
        for (UUID id : new ArrayList<>(landingGrace.keySet())) {
            Player player = plugin.getServer().getPlayer(id);
            if (player == null) { landingGrace.remove(id); continue; }
            if (player.isDead() || player.isOnGround() || landingGrace.get(id) <= clock.getAsLong()) { clearLanding(player); continue; }
            if (!player.isFlying() && player.getVelocity().getY() < -0.35) player.setVelocity(player.getVelocity().setY(-0.35));
        }
    }
    private void drawWings(Player player) {
        Location center = player.getLocation().add(0, 1.35, 0);
        double yaw = Math.toRadians(center.getYaw());
        Vector right = new Vector(Math.cos(yaw), 0, Math.sin(yaw));
        Vector back = new Vector(Math.sin(yaw), 0, -Math.cos(yaw)).multiply(0.25);
        double flap = Math.sin(clock.getAsLong() / 240.0) * 0.25;
        var dust = new Particle.DustOptions(Color.fromRGB(240, 95, 25), 1.2f);
        for (int side : new int[]{-1, 1}) for (int segment = 1; segment <= 10; segment++) {
            double span = segment * 0.22;
            double top = Math.sin(segment / 10.0 * Math.PI) * 0.8 + flap * span;
            for (int edge = 0; edge < 2; edge++) {
                Location point = center.clone().add(back).add(right.clone().multiply(side * span))
                        .add(0, edge == 0 ? top : top - 0.65 * (1 - segment / 11.0), 0);
                player.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, dust);
            }
        }
    }
    public void cancel(Player player) {
        Session removed = sessions.remove(player.getUniqueId());
        if (removed != null) removeSpeed(player);
        recoverFlight(player);
    }
    private void recoverFlight(Player player) {
        var data = player.getPersistentDataContainer();
        if (!data.has(FLIGHT_GRANTED, PersistentDataType.BYTE)) return;
        data.remove(FLIGHT_GRANTED);
        if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
            player.setFlying(false); player.setAllowFlight(false);
            if (!player.isDead() && !player.isOnGround()) {
                player.setFallDistance(0);
                long until = clock.getAsLong() + 60000;
                landingGrace.put(player.getUniqueId(), until);
                data.set(LANDING_UNTIL, PersistentDataType.LONG, until);
            }
        }
    }
    private void restoreLanding(Player player) {
        Long until = player.getPersistentDataContainer().get(LANDING_UNTIL, PersistentDataType.LONG);
        if (until != null && until > clock.getAsLong()) landingGrace.put(player.getUniqueId(), until);
        else clearLanding(player);
    }
    private void clearLanding(Player player) {
        landingGrace.remove(player.getUniqueId()); player.getPersistentDataContainer().remove(LANDING_UNTIL);
    }
    @EventHandler public void onJoin(PlayerJoinEvent event) { recoverFlight(event.getPlayer()); restoreLanding(event.getPlayer()); removeSpeed(event.getPlayer()); }
    @EventHandler public void onQuit(PlayerQuitEvent event) { cancel(event.getPlayer()); landingGrace.remove(event.getPlayer().getUniqueId()); }
    @EventHandler public void onDeath(PlayerDeathEvent event) { cancel(event.getEntity()); clearLanding(event.getEntity()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) { cancel(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> { if (player.isOnline()) cancel(player); });
    }
    public void shutdown() {
        if (task != null) { task.cancel(); task = null; }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            cancel(player); removeSpeed(player);
            if (landingGrace.containsKey(player.getUniqueId()) && !player.isDead() && !player.hasPotionEffect(PotionEffectType.SLOW_FALLING))
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 1200, 0, false, true));
        }
        sessions.clear(); landingGrace.clear(); pendingHits.clear();
    }
}
