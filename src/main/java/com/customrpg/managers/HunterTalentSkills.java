package com.customrpg.managers;

import com.customrpg.CustomRPG;
import com.customrpg.talents.*;
import com.customrpg.utils.SkillDamageGuard;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import java.util.*;
import java.util.function.LongSupplier;

/** Hunter-inspired Nen abilities. One bounded task; no block edits or display entities. */
public final class HunterTalentSkills implements Listener {
    private static final Set<String> ACTIVE = Set.of("nen_rock", "nen_scissors", "nen_paper", "nen_bungee",
            "nen_lightning", "nen_godspeed", "nen_chain", "nen_en", "nen_bodhisattva",
            "nen_gon_combo", "nen_gon_ko", "nen_gon_rush", "nen_killua_palm", "nen_killua_step", "nen_killua_yoyo", "nen_killua_whirlwind",
            "nen_kurapika_dowsing", "nen_kurapika_heal", "nen_kurapika_judgment", "nen_kurapika_emperor",
            "nen_hisoka_cards", "nen_hisoka_rebound", "nen_hisoka_snare", "nen_netero_prayer", "nen_netero_first", "nen_netero_ninetynine", "nen_netero_zero", "nen_zetsu");
    private static final Set<String> PASSIVE = Set.of("nen_ten", "nen_ren", "nen_gyo", "nen_gon_instinct", "nen_killua_reflex", "nen_kurapika_vow", "nen_hisoka_misdirection", "nen_netero_discipline");
    private static final Set<String> EFFECTS = Set.of("baseDamage", "strengthScaling", "magicScaling", "range", "radius",
            "duration", "charge", "moveSpeed", "knockback", "slowDuration", "slowAmplifier", "baseHeal", "spiritScaling", "distance", "reduction", "charges");
    private static final NamespacedKey SPEED = new NamespacedKey("customrpg", "nen_godspeed");
    interface SpeedEffects { void add(Player player, double amount); void remove(Player player); }
    private record Key(UUID owner, String skill) { }
    private static final class Effect {
        final Player source; final Talent talent; final int level; final Location origin;
        final double damage; final long until; long nextPulse, nextStrike;
        LivingEntity target; Location position; Vector direction; double traveled; int pulses, strikes; Set<UUID> seen = new HashSet<>();
        Effect(Player player, Talent talent, int level, double damage, long now) {
            source = player; this.talent = talent; this.level = level; this.damage = damage;
            origin = player.getLocation().clone(); position = player.getEyeLocation().clone(); direction = position.getDirection().normalize();
            until = now + (long)(value(talent, level, charged(talent.getId()) ? "charge" : "duration", 3) * 1000);
            nextPulse = now + 500;
        }
    }
    private final CustomRPG plugin;
    private final ExpandedTalentSkills combat;
    private final LongSupplier clock;
    private final SpeedEffects speed;
    private final Map<Key,Effect> effects = new LinkedHashMap<>();
    private BukkitTask task;
    private final HunterVisuals visuals = new HunterVisuals();
    private final Map<EntityDamageEvent,Effect> deflections = new IdentityHashMap<>();
    private final Map<UUID,Long> auraUntil = new HashMap<>();

    public HunterTalentSkills(CustomRPG plugin, ExpandedTalentSkills combat) { this(plugin, combat, System::currentTimeMillis, null); }
    HunterTalentSkills(CustomRPG plugin, ExpandedTalentSkills combat, LongSupplier clock, SpeedEffects speed) {
        this.plugin = plugin; this.combat = combat; this.clock = clock;
        this.speed = speed != null ? speed : new SpeedEffects() {
            public void remove(Player player) {
                var attribute = player.getAttribute(Attribute.MOVEMENT_SPEED);
                if (attribute != null) for (var modifier : new ArrayList<>(attribute.getModifiers()))
                    if (SPEED.equals(modifier.getKey())) attribute.removeModifier(modifier);
            }
            public void add(Player player, double amount) {
                remove(player); var attribute = player.getAttribute(Attribute.MOVEMENT_SPEED);
                if (attribute != null) attribute.addTransientModifier(new AttributeModifier(SPEED, amount, AttributeModifier.Operation.ADD_SCALAR));
            }
        };
    }
    private static boolean charged(String id) { return id.equals("nen_rock") || id.equals("nen_netero_zero"); }
    public static boolean handles(String id) { return ACTIVE.contains(id); }
    private static double value(Talent talent, int level, String key, double fallback) { return talent.getEffectDouble(level, key, fallback); }
    public static void validate(Talent talent) {
        if (talent.getBranch() != TalentBranch.HUNTER || !(talent.getType() == TalentType.ACTIVE ? ACTIVE : PASSIVE).contains(talent.getId())
                || talent.getType() != TalentType.ACTIVE && talent.getType() != TalentType.PASSIVE)
            throw new IllegalArgumentException("Unknown Nen talent: " + talent.getId());
        if (talent.getMaxLevel() < 1 || talent.getMaxLevel() > 3 || talent.getPointsPerLevel() < 1
                || talent.getGuiSlot() < 9 || talent.getGuiSlot() >= 53 || talent.getGuiSlot() == 45 || talent.getGuiSlot() == 46 || Material.matchMaterial(talent.getIcon()) == null)
            throw new IllegalArgumentException("Invalid Nen layout: " + talent.getId());
        bound(talent.getManaCost(), 0, 200); bound(talent.getCooldown(), talent.getType() == TalentType.ACTIVE ? 1 : 0, 120);
        if (talent.getHpCost() != 0) throw new IllegalArgumentException("Nen talents do not use HP costs");
        for (int level = 1; level <= talent.getMaxLevel(); level++) {
            var data = talent.getLevelData(level);
            if (data == null || data.effects.isEmpty() || !data.scaling.isEmpty()) throw new IllegalArgumentException("Missing/invalid Nen level");
            for (var entry : data.effects.entrySet()) {
                boolean allowed = talent.getType() == TalentType.ACTIVE ? EFFECTS.contains(entry.getKey())
                        : Set.of("statBonus_strength", "statBonus_magic", "statBonus_agility", "statBonus_vitality", "statBonus_defense", "statBonus_spirit").contains(entry.getKey());
                if (!allowed) throw new IllegalArgumentException("Unknown Nen effect: " + entry.getKey());
                bound(entry.getValue(), 0, 100);
            }
            bound(value(talent, level, "range", 12), 2, 24); bound(value(talent, level, "radius", 4), 1, 6);
            bound(value(talent, level, "duration", 3), 1, 8); bound(value(talent, level, "charge", 2), .5, 3);
            bound(value(talent, level, "moveSpeed", .25), 0, .35); bound(value(talent, level, "knockback", .5), 0, .8);
            bound(value(talent, level, "slowDuration", 2), 0, 3); bound(value(talent, level, "slowAmplifier", 1), 0, 3);
            bound(value(talent, level, "distance", 4), 1, 6); bound(value(talent, level, "reduction", .2), 0, .4);
            bound(value(talent, level, "spiritScaling", 0), 0, 1.5); bound(value(talent, level, "charges", 3), 1, 3);
            bound(value(talent, level, "strengthScaling", 0), 0, 1.5); bound(value(talent, level, "magicScaling", 0), 0, 1.5);
        }
    }
    private static void bound(double number, double low, double high) {
        if (!Double.isFinite(number) || number < low || number > high) throw new IllegalArgumentException("Unsafe Nen value: " + number);
    }
    public void start() { if (task == null) task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L); }
    // Caster eligibility follows TalentSkillManager; combat.valid excludes creative players as targets.
    private boolean validCaster(Player player) {
        return player.isOnline() && !player.isDead() && player.getGameMode() != GameMode.SPECTATOR;
    }
    public boolean cast(Player player, Talent talent, int level) {
        if (!handles(talent.getId()) || !validCaster(player)) return false;
        if (combat.protectedAt(player.getLocation())) {
            player.sendMessage("§c安全區或保護區內無法施放念能力，請離開該區域後再試。");
            return false;
        }
        String id = talent.getId(); double range = value(talent, level, "range", 12);
        double damage = combat.damage(player, talent, level);
        boolean sustained = !Set.of("nen_scissors", "nen_lightning", "nen_chain", "nen_killua_palm", "nen_killua_step", "nen_gon_rush", "nen_kurapika_heal", "nen_netero_first").contains(id);
        Key key = new Key(player.getUniqueId(), id);
        int required = id.equals("nen_hisoka_cards") ? 4 : 1;
        long replacing = effects.entrySet().stream().filter(e -> e.getKey().owner.equals(player.getUniqueId()) && e.getValue().talent.getId().equals(id)).count();
        if (sustained && effects.size() - replacing + required > 64) {
            player.sendMessage("§c目前念能力持續效果已達上限，請稍後再試。"); return false;
        }
        Effect effect = new Effect(player, talent, level, damage, clock.getAsLong());
        switch (id) {
            case "nen_scissors" -> {
                var targets = frontal(player, range);
                if (targets.isEmpty()) return noTarget(player);
                for (var target : targets) { combat.beam(player.getEyeLocation(), target.getEyeLocation(), 0x64ED95); combat.hit(player, target, damage); }
            }
            case "nen_lightning", "nen_chain", "nen_bungee", "nen_killua_palm", "nen_kurapika_dowsing", "nen_kurapika_judgment", "nen_netero_first" -> {
                LivingEntity target = combat.aim(player, range); if (target == null) return noTarget(player);
                visuals.link(player, id, player.getEyeLocation(), target.getEyeLocation());
                if (combat.hit(player, target, damage) > 0 && !target.isDead()) {
                    if (id.equals("nen_bungee") && !boss(target)) { effect.target = target; effects.put(key, effect); }
                    else if (id.equals("nen_kurapika_dowsing") || id.equals("nen_kurapika_judgment")) { effect.target = target; effect.position = target.getLocation().clone(); effects.put(key, effect); }
                    else if (id.equals("nen_netero_first")) { palm(player, 1); if (!boss(target)) combat.push(player, target, .5); }
                    else if (!id.equals("nen_bungee")) combat.slow(target, talent, level);
                }
            }
            case "nen_godspeed" -> {
                speed.add(player, value(talent, level, "moveSpeed", .25)); effects.put(key, effect);
            }
            case "nen_gon_rush", "nen_killua_step" -> {
                Location before = player.getLocation().clone();
                if (!combat.dash(player, value(talent, level, "distance", 4))) {
                    player.sendMessage("§c前方沒有可安全移動的位置，或傳送被阻止，無法施放突進。");
                    return false;
                }
                visuals.afterimage(player, before); visuals.afterimage(player, player.getLocation());
                if (id.equals("nen_gon_rush")) for (var target : frontal(player, 3)) combat.hit(player, target, damage);
            }
            case "nen_kurapika_heal" -> {
                double heal = value(talent, level, "baseHeal", 30) + plugin.getPlayerStatsManager().getStats(player).getTotalSpirit()
                        * value(talent, level, "spiritScaling", .5);
                if (plugin.getCombatBalance().heal(player, heal) <= 0) {
                    player.sendMessage("§c目前無法恢復生命：生命已滿或治療被阻止。");
                    return false;
                }
            }
            case "nen_hisoka_cards" -> {
                for (var old : new ArrayList<>(effects.entrySet()))
                    if (old.getKey().owner.equals(player.getUniqueId()) && old.getValue().talent.getId().equals(id)) remove(old.getKey());
                Set<UUID> seen = new HashSet<>();
                for (int i = 0; i < 4; i++) {
                    var card = new Effect(player, talent, level, damage, clock.getAsLong());
                    card.direction.rotateAroundY((i - 1.5) * .15); card.seen = seen;
                    effects.put(new Key(player.getUniqueId(), id + ":" + i), card);
                }
            }
            case "nen_rock", "nen_paper", "nen_en", "nen_bodhisattva", "nen_gon_combo", "nen_gon_ko", "nen_killua_yoyo", "nen_killua_whirlwind",
                    "nen_kurapika_emperor", "nen_hisoka_rebound", "nen_hisoka_snare", "nen_netero_prayer", "nen_netero_ninetynine", "nen_netero_zero", "nen_zetsu" -> effects.put(key, effect);
            default -> { return false; }
        }
        if (!id.equals("nen_zetsu")) remove(new Key(player.getUniqueId(), "nen_zetsu"));
        visuals.cast(player, id);
        if (id.equals("nen_en")) visuals.pulse(player, "nen_gyo", player.getLocation(), 0);
        if (charged(id)) player.sendMessage("§e蓄力中！移動超過1格、受傷或傳送會中斷。");
        else player.sendMessage("§d[念能力] §f" + talent.getName());
        return true;
    }
    private boolean noTarget(Player player) { player.sendMessage("§c請瞄準未被方塊遮擋、射程內的敵人。"); return false; }
    private boolean alive(Effect effect) {
        return validCaster(effect.source) && effect.source.getWorld().equals(effect.origin.getWorld())
                && !combat.protectedAt(effect.source.getLocation())
                && plugin.getTalentManager().getPlayerTalents(effect.source).getTalentLevel(effect.talent.getId()) > 0
                && Arrays.asList(plugin.getTalentManager().getPlayerTalents(effect.source).getSelectedSkills()).contains(effect.talent.getId());
    }
    private boolean boss(LivingEntity target) {
        var manager = plugin.getMobManager(); var data = manager == null ? null : manager.getMobData(manager.getCustomMobKey(target));
        return target instanceof Wither || target instanceof EnderDragon || target instanceof Warden || data != null && data.isBoss();
    }
    private List<LivingEntity> frontal(Player source, double range) {
        return combat.enemies(source, source.getLocation(), range).stream().filter(target -> {
            Vector delta = target.getEyeLocation().toVector().subtract(source.getEyeLocation().toVector());
            return delta.lengthSquared() < .01 || delta.normalize().dot(source.getEyeLocation().getDirection()) >= .7;
        }).limit(12).toList();
    }
    private void remove(Key key) { var effect = effects.remove(key); if (effect != null && effect.talent.getId().equals("nen_godspeed")) speed.remove(effect.source); }
    void tick() {
        long now = clock.getAsLong();
        for (var entry : new ArrayList<>(effects.entrySet())) {
            Key key = entry.getKey(); Effect effect = entry.getValue(); String id = effect.talent.getId();
            if (!alive(effect)) { remove(key); continue; }
            if (charged(id)) {
                if (effect.source.getLocation().distanceSquared(effect.origin) > 1) { remove(key); effect.source.sendMessage("§c移動使猜猜拳蓄力中斷。"); continue; }
                if (now >= effect.until) {
                    remove(key); var target = combat.aim(effect.source, value(effect.talent, effect.level, "range", 3));
                    if (id.equals("nen_netero_zero")) { zero(effect, target); continue; }
                    if (target != null && combat.hit(effect.source, target, effect.damage) > 0 && !boss(target)) combat.push(effect.source, target, .6);
                } else visuals.pulse(effect.source, id, effect.source.getLocation(), (int)(now / 250));
                continue;
            }
            if (id.equals("nen_kurapika_judgment") && effect.target != null && combat.enemy(effect.source, effect.target)
                    && (now >= effect.until || effect.target.getLocation().distanceSquared(effect.position) > 9)) {
                visuals.link(effect.source, id, effect.position.clone().add(0, 1, 0), effect.target.getEyeLocation());
                combat.hit(effect.source, effect.target, effect.damage * 1.5); remove(key); continue;
            }
            if (now >= effect.until) { remove(key); continue; }
            if (id.equals("nen_paper") || id.equals("nen_hisoka_cards")) { projectile(key, effect); continue; }
            if (now < effect.nextPulse) continue;
            effect.nextPulse = now + (id.equals("nen_en") ? 1000 : 500); // Never replay missed pulses after lag.
            effect.pulses++; visuals.pulse(effect.source, id, effect.source.getLocation(), effect.pulses);
            switch (id) {
                case "nen_bungee" -> {
                    LivingEntity target = effect.target;
                    if (!combat.enemy(effect.source, target) || target.getLocation().distanceSquared(effect.source.getLocation()) > 256) { remove(key); break; }
                    Vector pull = effect.source.getLocation().toVector().subtract(target.getLocation().toVector());
                    if (pull.lengthSquared() > 4) target.setVelocity(pull.normalize().multiply(.55).setY(.1));
                    visuals.link(effect.source, id, effect.source.getEyeLocation(), target.getEyeLocation());
                }
                case "nen_godspeed" -> combat.ring(effect.source.getLocation(), .7, 0xADDFFF);
                case "nen_gon_combo" -> {
                    if (effect.pulses < 3) {
                        for (var target : frontal(effect.source, effect.pulses == 1 ? 3 : 5))
                            combat.hit(effect.source, target, effect.damage * (effect.pulses == 1 ? 1.2 : 1));
                    } else {
                        var target = combat.aim(effect.source, value(effect.talent, effect.level, "range", 14));
                        if (target != null) { visuals.link(effect.source, "nen_paper", effect.source.getEyeLocation(), target.getEyeLocation()); combat.hit(effect.source, target, effect.damage); }
                        remove(key);
                    }
                }
                case "nen_killua_yoyo" -> {
                    if (effect.pulses == 2 || effect.pulses == 4)
                        for (var target : frontal(effect.source, value(effect.talent, effect.level, "radius", 5))) combat.hit(effect.source, target, effect.damage);
                    if (effect.pulses >= 4) remove(key);
                }
                case "nen_kurapika_dowsing", "nen_kurapika_judgment" -> {
                    if (effect.target == null || !combat.enemy(effect.source, effect.target)
                            || effect.target.getLocation().distanceSquared(effect.source.getLocation()) > 24 * 24) { remove(key); break; }
                    visuals.link(effect.source, id, effect.source.getEyeLocation(), effect.target.getEyeLocation());
                }
                case "nen_hisoka_snare" -> {
                    visuals.pulse(effect.source, id, effect.origin, effect.pulses);
                    for (var target : combat.enemies(effect.source, effect.origin, value(effect.talent, effect.level, "radius", 3)).stream().limit(12).toList())
                        if (combat.hit(effect.source, target, effect.damage) > 0) combat.slow(target, effect.talent, effect.level);
                }
                case "nen_zetsu" -> {
                    for (var target : combat.enemies(effect.source, effect.source.getLocation(), 6))
                        if (target instanceof Mob mob && effect.source.equals(mob.getTarget())) mob.setTarget(null);
                }
                case "nen_en" -> {
                    for (var target : combat.enemies(effect.source, effect.source.getLocation(), value(effect.talent, effect.level, "radius", 6)).stream().limit(12).toList())
                        effect.source.spawnParticle(Particle.DUST, target.getEyeLocation(), 6, .25, .3, .25, 0, new Particle.DustOptions(Color.fromRGB(0xBB87FF), 1));
                    combat.ring(effect.source.getLocation(), value(effect.talent, effect.level, "radius", 6), 0xBB87FF);
                }
                case "nen_bodhisattva" -> {
                    palm(effect.source, effect.pulses); combat.ring(effect.source.getLocation(), 2, 0xFFD75D);
                    for (var target : frontal(effect.source, value(effect.talent, effect.level, "radius", 6))) {
                        combat.beam(effect.source.getEyeLocation().add(0, 1, 0), target.getEyeLocation(), 0xFFD75D);
                        if (combat.hit(effect.source, target, effect.damage * (effect.pulses == 3 ? 1.4 : 1)) > 0 && effect.pulses == 3 && !boss(target))
                            combat.push(effect.source, target, .5);
                    }
                    if (effect.pulses >= 3) remove(key);
                }
                case "nen_netero_ninetynine" -> {
                    palm(effect.source, effect.pulses % 3 + 1);
                    for (var target : frontal(effect.source, value(effect.talent, effect.level, "radius", 6))) combat.hit(effect.source, target, effect.damage);
                    if (effect.pulses >= 5) remove(key);
                }
                default -> { }
            }
        }
    }
    private void zero(Effect effect, LivingEntity target) {
        visuals.pulse(effect.source, effect.talent.getId(), effect.source.getLocation(), 3);
        Location end = target == null ? effect.source.getEyeLocation().add(effect.source.getEyeLocation().getDirection().multiply(2)) : target.getEyeLocation();
        visuals.link(effect.source, effect.talent.getId(), effect.source.getEyeLocation(), end);
        if (target != null) for (var victim : combat.enemies(effect.source, target.getLocation(), 2).stream().limit(12).toList())
            combat.hit(effect.source, victim, effect.damage);
    }
    /** Called on learning, so passive character training also has a visible activation. */
    public void training(Player player, Talent talent) { visuals.cast(player, talent.getId()); }
    private void palm(Player source, int strike) {
        Vector forward = source.getEyeLocation().getDirection().setY(0);
        if (forward.lengthSquared() < .001) forward = new Vector(0, 0, 1);
        forward.normalize(); Vector right = new Vector(forward.getZ(), 0, -forward.getX());
        Location anchor = source.getEyeLocation().add(0, 1, 0).add(forward)
                .add(right.clone().multiply(strike == 3 ? 0 : strike == 1 ? -1.2 : 1.2));
        var dust = new Particle.DustOptions(Color.fromRGB(0xFFD75D), 1.2f);
        for (int finger = -2; finger <= 2; finger++) {
            int height = 6 - Math.abs(finger);
            for (int y = 0; y < height; y++) {
                Location point = anchor.clone().add(right.clone().multiply(finger * .24)).add(0, y * .24, 0);
                source.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, dust);
            }
        }
    }
    private void projectile(Key key, Effect effect) {
        double distance = Math.min(4, value(effect.talent, effect.level, "range", 18) - effect.traveled);
        if (distance <= 0) { remove(key); return; }
        for (double d = 0; d <= distance; d += .5) {
            Location sample = effect.position.clone().add(effect.direction.clone().multiply(d));
            if (!sample.getWorld().isChunkLoaded(sample.getBlockX() >> 4, sample.getBlockZ() >> 4)
                    || !sample.getWorld().getWorldBorder().isInside(sample)) { remove(key); return; }
        }
        var ray = effect.position.getWorld().rayTrace(effect.position, effect.direction, distance, FluidCollisionMode.NEVER, true, .3,
                entity -> entity instanceof LivingEntity target && combat.enemy(effect.source, target) && !effect.seen.contains(target.getUniqueId()));
        Location end = ray == null ? effect.position.clone().add(effect.direction.clone().multiply(distance)) : ray.getHitPosition().toLocation(effect.source.getWorld());
        visuals.link(effect.source, effect.talent.getId(), effect.position, end);
        if (ray != null) {
            if (ray.getHitEntity() instanceof LivingEntity target) { effect.seen.add(target.getUniqueId()); combat.hit(effect.source, target, effect.damage); }
            remove(key);
        } else { effect.position = end; effect.traveled += distance; }
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIncoming(EntityDamageEvent event) {
        if(SkillDamageGuard.isTransfer(event.getEntity()))return;
        if (!(event.getEntity() instanceof Player player) || event.getCause() == EntityDamageEvent.DamageCause.VOID
                || event.getCause() == EntityDamageEvent.DamageCause.KILL || event.getCause() == EntityDamageEvent.DamageCause.SUICIDE) return;
        long now = clock.getAsLong();
        var ko = effects.get(new Key(player.getUniqueId(), "nen_gon_ko"));
        if (ko != null && now < ko.until && alive(ko)) event.setDamage(event.getDamage() * 1.15);
        var prayer = effects.get(new Key(player.getUniqueId(), "nen_netero_prayer"));
        if (prayer != null && now < prayer.until && alive(prayer))
            com.customrpg.utils.TalentMitigation.reduce(event,value(prayer.talent, prayer.level, "reduction", .2));
        if (!(event instanceof EntityDamageByEntityEvent hit) || !(hit.getDamager() instanceof LivingEntity attacker)
                || SkillDamageGuard.isSkillDamage(attacker) || !combat.enemy(player, attacker)) return;
        for (String id : List.of("nen_killua_whirlwind", "nen_hisoka_rebound")) {
            var guard = effects.get(new Key(player.getUniqueId(), id));
            if (guard != null && now < guard.until && alive(guard)) {
                com.customrpg.utils.TalentMitigation.reduce(event,value(guard.talent, guard.level, "reduction", .25));
                deflections.put(event, guard); break;
            }
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getTarget() instanceof Player player) || !(event.getEntity() instanceof LivingEntity attacker)) return;
        var hidden = effects.get(new Key(player.getUniqueId(), "nen_zetsu"));
        if (hidden != null && clock.getAsLong() < hidden.until && alive(hidden) && combat.enemy(player, attacker)) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent event) {
        if(SkillDamageGuard.isTransfer(event.getEntity()))return;
        var reflection = deflections.remove(event);
        if (event.isCancelled() || event.getFinalDamage() <= 0) return;
        if (event.getEntity() instanceof Player player) {
            remove(new Key(player.getUniqueId(), "nen_rock")); remove(new Key(player.getUniqueId(), "nen_netero_zero"));
            remove(new Key(player.getUniqueId(), "nen_zetsu"));
            passiveAura(player, "nen_ten");
        }
        if (reflection != null && alive(reflection) && clock.getAsLong() < reflection.until && event instanceof EntityDamageByEntityEvent hit
                && hit.getDamager() instanceof LivingEntity attacker && combat.enemy(reflection.source, attacker)) {
            remove(new Key(reflection.source.getUniqueId(), reflection.talent.getId()));
            visuals.link(reflection.source, reflection.talent.getId(), reflection.source.getEyeLocation(), attacker.getEyeLocation());
            if (combat.hit(reflection.source, attacker, reflection.damage) > 0) combat.slow(attacker, reflection.talent, reflection.level);
        }
        if (!(event instanceof EntityDamageByEntityEvent hit) || !(hit.getDamager() instanceof Player source)
                || SkillDamageGuard.isSkillDamage(source) || !(event.getEntity() instanceof LivingEntity target)) return;
        remove(new Key(source.getUniqueId(), "nen_zetsu")); long now = clock.getAsLong();
        passiveAura(source, "nen_ren");
        for (String id : List.of("nen_godspeed", "nen_gon_ko", "nen_kurapika_emperor")) {
            var effect = effects.get(new Key(source.getUniqueId(), id));
            if (effect == null || now >= effect.until || now < effect.nextStrike || !alive(effect) || !combat.enemy(source, target)) continue;
            effect.nextStrike = now + (id.equals("nen_godspeed") ? 1000 : 500);
            if (combat.hit(source, target, effect.damage) > 0) {
                visuals.link(source, id, source.getEyeLocation(), target.getEyeLocation());
                if (id.equals("nen_godspeed")) combat.slow(target, effect.talent, effect.level);
                else if (++effect.strikes >= (id.equals("nen_gon_ko") ? 1 : (int)value(effect.talent, effect.level, "charges", 3)))
                    remove(new Key(source.getUniqueId(), id));
            }
        }
    }
    private void passiveAura(Player player, String id) {
        long now = clock.getAsLong();
        if (!validCaster(player) || plugin.getTalentManager().getPlayerTalents(player).getTalentLevel(id) <= 0
                || auraUntil.getOrDefault(player.getUniqueId(), 0L) > now) return;
        auraUntil.put(player.getUniqueId(), now + 1000); visuals.pulse(player, id, player.getLocation(), 0);
    }
    public void cancel(Player player) {
        for (var key : new ArrayList<>(effects.keySet())) if (key.owner.equals(player.getUniqueId())) remove(key);
        auraUntil.remove(player.getUniqueId());
    }
    public void clear() { for (var key : new ArrayList<>(effects.keySet())) remove(key); deflections.clear(); auraUntil.clear(); }
    @EventHandler public void onQuit(PlayerQuitEvent event) { cancel(event.getPlayer()); }
    @EventHandler public void onDeath(PlayerDeathEvent event) { cancel(event.getEntity()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onTeleport(PlayerTeleportEvent event) { cancel(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onGameMode(PlayerGameModeChangeEvent event) { cancel(event.getPlayer()); }
    public void shutdown() { if (task != null) { task.cancel(); task = null; } clear(); }
}
