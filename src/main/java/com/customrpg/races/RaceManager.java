package com.customrpg.races;

import com.customrpg.CustomRPG;
import com.customrpg.managers.PlayerStatsManager;
import com.customrpg.players.PlayerStats;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.scheduler.BukkitTask;
import java.io.*;
import java.util.*;

/** Main-thread race definitions, durable selections, and one shared recovery timer. */
public class RaceManager {
    private final CustomRPG plugin;
    private final java.util.function.LongSupplier clock;
    private final java.util.function.Function<Player,AttributeInstance> movementAttribute, healthAttribute;
    private final Map<String,RaceData> raceRegistry=new LinkedHashMap<>();
    private final Map<UUID,String> playerRaces=new HashMap<>();
    private final Set<UUID> loaded=new HashSet<>(), loadFailed=new HashSet<>();
    private final Map<UUID,Long> combatUntil=new HashMap<>();
    private final File raceDataFolder;
    private final NamespacedKey speedKey=new NamespacedKey("customrpg","race_speed");
    private BukkitTask recoveryTask;
    private double recoveryBase=0.5;
    private long combatDelay=8000;
    private String lastError="";

    public RaceManager(CustomRPG plugin) {
        this(plugin,System::currentTimeMillis);
    }
    RaceManager(CustomRPG plugin,java.util.function.LongSupplier clock) {
        this(plugin,clock,player->player.getAttribute(Attribute.MOVEMENT_SPEED),player->player.getAttribute(Attribute.MAX_HEALTH));
    }
    RaceManager(CustomRPG plugin,java.util.function.LongSupplier clock,
                java.util.function.Function<Player,AttributeInstance> movementAttribute,
                java.util.function.Function<Player,AttributeInstance> healthAttribute) {
        this.plugin=plugin; this.clock=clock; this.movementAttribute=movementAttribute; this.healthAttribute=healthAttribute;
        raceDataFolder=new File(plugin.getDataFolder(),"data/races");
        if(!raceDataFolder.isDirectory()&&!raceDataFolder.mkdirs()) plugin.getLogger().severe("[RaceManager] Cannot create race data directory");
        loadRaceConfig();
    }
    private boolean loadRaceConfig() {
        try {
            var definitions=new LinkedHashMap<String,YamlConfiguration>();
            readDefinitions("config/races_additional.yml",definitions);
            YamlConfiguration main=readDefinitions("config/races.yml",definitions);
            var candidate=new LinkedHashMap<String,RaceData>();
            for(var entry:definitions.entrySet()) if(entry.getValue().getBoolean("enabled",true))
                candidate.put(entry.getKey(),parseRaceData(entry.getKey(),entry.getValue()));
            if(candidate.isEmpty()) throw new IllegalArgumentException("No enabled races");
            double nextBase=main.getDouble("settings.passive-health-regen-base",0.5);
            double delay=main.getDouble("settings.recovery-combat-delay-seconds",8);
            if(!Double.isFinite(nextBase)||nextBase<0||nextBase>10||!Double.isFinite(delay)||delay<0||delay>300) throw new IllegalArgumentException("Invalid recovery settings");
            raceRegistry.clear(); raceRegistry.putAll(candidate); recoveryBase=nextBase; combatDelay=(long)(delay*1000);
            lastError=""; plugin.getLogger().info("[RaceManager] Loaded "+raceRegistry.size()+" races"); return true;
        } catch(Exception error) {
            lastError="種族設定載入失敗："+error.getMessage(); plugin.getLogger().severe("[RaceManager] "+lastError+"; previous definitions retained"); return false;
        }
    }
    private YamlConfiguration readDefinitions(String resource,Map<String,YamlConfiguration> target) throws Exception {
        File file=new File(plugin.getDataFolder(),resource);
        if(!file.exists()) plugin.saveResource(resource,false);
        var yaml=new YamlConfiguration(); yaml.load(file);
        ConfigurationSection races=yaml.getConfigurationSection("races");
        if(races==null) throw new IllegalArgumentException(resource+": missing races section");
        Set<String> seen=new HashSet<>();
        for(String raw:races.getKeys(false)) {
            String id=raw.toLowerCase(Locale.ROOT);
            if(!seen.add(id)) throw new IllegalArgumentException("Duplicate race ID: "+raw);
            var section=races.getConfigurationSection(raw);
            if(section==null) throw new IllegalArgumentException("Invalid race definition: "+raw);
            var combined=target.computeIfAbsent(id,ignored->new YamlConfiguration());
            for(var entry:section.getValues(true).entrySet()) if(!(entry.getValue() instanceof ConfigurationSection))
                combined.set(entry.getKey(),entry.getValue());
        }
        return yaml;
    }
    private RaceData parseRaceData(String id,ConfigurationSection section) {
        var builder=new RaceData.Builder(id).displayName(section.getString("name","§f"+id)).description(section.getString("description",""));
        builder.baseStrength(section.getInt("base-stats.strength",5)).baseMagic(section.getInt("base-stats.magic",5))
                .baseAgility(section.getInt("base-stats.agility",5)).baseVitality(section.getInt("base-stats.vitality",5))
                .baseDefense(section.getInt("base-stats.defense",5)).baseSpirit(section.getInt("base-stats.spirit",5))
                .baseMana(section.getInt("base-stats.mana",0)).baseManaRegen(section.getDouble("base-stats.mana-regen",0));
        builder.growthStrength(section.getDouble("growth.strength",1)).growthMagic(section.getDouble("growth.magic",1))
                .growthAgility(section.getDouble("growth.agility",1)).growthVitality(section.getDouble("growth.vitality",1))
                .growthDefense(section.getDouble("growth.defense",1)).growthSpirit(section.getDouble("growth.spirit",1))
                .growthMana(section.getDouble("growth.mana",0)).growthManaRegen(section.getDouble("growth.mana-regen",0));
        Map<String,Double> weapons=new LinkedHashMap<>(); var bonuses=section.getConfigurationSection("weapon.bonuses");
        if(bonuses!=null) for(String key:bonuses.getKeys(false)) weapons.put(key,bonuses.getDouble(key,1));
        return builder.skills(section.getStringList("skills")).weaponBonuses(weapons).defaultWeaponBonus(section.getDouble("weapon.default",1))
                .bonusCritChance(section.getDouble("passives.crit-chance",0)).bonusCritDamage(section.getDouble("passives.crit-damage",0))
                .bonusMoveSpeed(section.getDouble("passives.move-speed",0)).bonusHealthRegen(section.getDouble("passives.health-regen",0)).build();
    }
    public Collection<RaceData> getAllRaces() { return List.copyOf(raceRegistry.values()); }
    public Set<String> getAllRaceIds() { return Collections.unmodifiableSet(new LinkedHashSet<>(raceRegistry.keySet())); }
    public RaceData getRaceData(String id) { return id==null?null:raceRegistry.get(id.toLowerCase(Locale.ROOT)); }
    public int getRaceCount() { return raceRegistry.size(); }
    public String getLastError() { return lastError; }
    public String getPlayerRaceId(Player player) { return getPlayerRaceId(player.getUniqueId()); }
    public String getPlayerRaceId(UUID id) { if(!loaded.contains(id)) loadPlayerRace(id); return playerRaces.get(id); }
    public RaceData getPlayerRaceData(Player player) { return getPlayerRaceData(player.getUniqueId()); }
    public RaceData getPlayerRaceData(UUID id) { return getRaceData(getPlayerRaceId(id)); }
    public boolean hasRace(Player player) {
        String id=getPlayerRaceId(player); return id!=null||loadFailed.contains(player.getUniqueId());
    }
    public boolean isPlayerDataAvailable(Player player) {
        getPlayerRaceId(player); return !loadFailed.contains(player.getUniqueId());
    }
    /** Ordinary selection is one-time even if a stale GUI tries again. */
    public boolean choosePlayerRace(Player player,String raceId) { return !hasRace(player)&&setPlayerRace(player,raceId); }
    /** Admin replacement commits directly; never reset the previous selection before saving. */
    public boolean setPlayerRace(Player player,String raceId) {
        UUID id=player.getUniqueId(); getPlayerRaceId(id); RaceData race=getRaceData(raceId);
        if(race==null||loadFailed.contains(id)) return false;
        if(!writeRace(id,race.getId())) return false;
        if(plugin.getRacialSkillManager()!=null) plugin.getRacialSkillManager().cancel(player);
        playerRaces.put(id,race.getId()); applyRaceStats(player); return true;
    }
    public void resetPlayerRace(Player player) { tryResetPlayerRace(player); }
    public boolean tryResetPlayerRace(Player player) {
        UUID id=player.getUniqueId(); getPlayerRaceId(id);
        if(loadFailed.contains(id)||!writeRace(id,"")) return false;
        if(plugin.getRacialSkillManager()!=null) plugin.getRacialSkillManager().cancel(player);
        playerRaces.remove(id); applyRaceStats(player); return true;
    }
    public void applyRaceStats(Player player) {
        RaceData race=getPlayerRaceData(player); PlayerStats stats=plugin.getPlayerStatsManager().getStats(player); int level=stats.getLevel();
        int[] bonus = race==null?new int[6]:com.customrpg.balance.BalancePlayerRules.bounded(new long[]{
                race.calculateStrengthBonus(level),race.calculateMagicBonus(level),race.calculateAgilityBonus(level),
                race.calculateVitalityBonus(level),race.calculateDefenseBonus(level),race.calculateSpiritBonus(level)},
                com.customrpg.balance.BalancePlayerRules.raceBudget(level));
        stats.setRaceStrength(bonus[0]); stats.setRaceMagic(bonus[1]); stats.setRaceAgility(bonus[2]);
        stats.setRaceVitality(bonus[3]); stats.setRaceDefense(bonus[4]); stats.setRaceSpirit(bonus[5]);
        plugin.getPlayerStatsManager().recalculateMana(player); plugin.getPlayerStatsManager().updateMaxHealth(player);
        removeSpeed(player); var attribute=movementAttribute.apply(player);
        if(race!=null&&attribute!=null&&race.getBonusMoveSpeed()!=0)
            attribute.addTransientModifier(new AttributeModifier(speedKey,Math.clamp(race.getBonusMoveSpeed(),-.03,.03),AttributeModifier.Operation.ADD_SCALAR));
    }
    private void removeSpeed(Player player) {
        var attribute=movementAttribute.apply(player);
        if(attribute!=null) for(var modifier:new ArrayList<>(attribute.getModifiers())) if(speedKey.equals(modifier.getKey())) attribute.removeModifier(modifier);
    }
    public double getWeaponDamageBonus(Player player,String weaponType) {
        RaceData race=getPlayerRaceData(player); return race==null?1:Math.clamp(race.getWeaponBonus(weaponType),.98,1.02);
    }
    public void loadPlayerRace(UUID id) {
        if(loaded.contains(id)) return;
        loaded.add(id); File file=new File(raceDataFolder,id+".yml"); if(!file.exists()) return;
        try {
            var yaml=new YamlConfiguration(); yaml.load(file);
            if(!yaml.isString("race")) throw new IOException("Missing or invalid race ID");
            String race=yaml.getString("race").toLowerCase(Locale.ROOT);
            if(!race.isEmpty()&&!race.matches("[a-z0-9_]+")) throw new IOException("Invalid race ID");
            if(!race.isEmpty()) playerRaces.put(id,race); // removed definitions still reserve the player's choice
        } catch(Exception error) {
            loadFailed.add(id); lastError="玩家種族資料讀取失敗；原檔保留："+id;
            plugin.getLogger().severe("[RaceManager] "+lastError+": "+error.getMessage());
        }
    }
    private boolean writeRace(UUID id,String race) {
        if(loadFailed.contains(id)) return false;
        var yaml=new YamlConfiguration(); yaml.set("race",race);
        try { PlayerStatsManager.saveAtomically(yaml,new File(raceDataFolder,id+".yml")); lastError=""; return true; }
        catch(IOException error) { lastError="種族儲存失敗；原有選擇未變更"; plugin.getLogger().severe("[RaceManager] "+lastError+": "+error.getMessage()); return false; }
    }
    public void savePlayerRace(UUID id) { if(playerRaces.containsKey(id)) writeRace(id,playerRaces.get(id)); }
    public void unloadPlayer(UUID id) {
        if(!playerRaces.containsKey(id)||writeRace(id,playerRaces.get(id))) { playerRaces.remove(id); loaded.remove(id); loadFailed.remove(id); }
        combatUntil.remove(id);
    }
    public void saveAll() { for(UUID id:new ArrayList<>(playerRaces.keySet())) savePlayerRace(id); }
    public void reload() { reloadConfiguration(); }
    public boolean reloadConfiguration() {
        if(!loadRaceConfig()) return false;
        for(Player player:plugin.getServer().getOnlinePlayers()) {
            UUID id=player.getUniqueId(); if(loadFailed.remove(id)) { loaded.remove(id); loadPlayerRace(id); }
            if(plugin.getRacialSkillManager()!=null) plugin.getRacialSkillManager().cancel(player);
            applyRaceStats(player);
        }
        return true;
    }
    public void markCombat(Player player) { combatUntil.put(player.getUniqueId(),clock.getAsLong()+combatDelay); }
    public double getRecoveryPerSecond(RaceData race) { return race==null?0:Math.max(0,recoveryBase+race.getBonusHealthRegen()); }
    public void start() {
        if(recoveryTask!=null) return;
        for(Player player:plugin.getServer().getOnlinePlayers()) { applyRaceStats(player); markCombat(player); }
        recoveryTask=plugin.getServer().getScheduler().runTaskTimer(plugin,this::recoverPlayers,20L,20L);
    }
    private void recoverPlayers() {
        long now=clock.getAsLong();
        for(Player player:plugin.getServer().getOnlinePlayers()) {
            if(!player.isOnline()||player.isDead()||(player.getGameMode()!=GameMode.SURVIVAL&&player.getGameMode()!=GameMode.ADVENTURE)
                    ||combatUntil.getOrDefault(player.getUniqueId(),0L)>now) continue;
            var health=healthAttribute.apply(player);
            double amount=health==null?0:health.getValue()*(.05+Math.clamp(getRecoveryPerSecond(getPlayerRaceData(player))*.001,0,.005));
            if(amount<=0||health==null||player.getHealth()>=health.getValue()) continue;
            var event=new EntityRegainHealthEvent(player,amount,EntityRegainHealthEvent.RegainReason.CUSTOM);
            plugin.getServer().getPluginManager().callEvent(event);
            if(!event.isCancelled()&&Double.isFinite(event.getAmount())&&event.getAmount()>0&&!player.isDead())
                player.setHealth(Math.min(health.getValue(),player.getHealth()+event.getAmount()));
        }
    }
    public void detach(Player player) { removeSpeed(player); unloadPlayer(player.getUniqueId()); }
    public void shutdown() {
        if(recoveryTask!=null) { recoveryTask.cancel(); recoveryTask=null; }
        for(Player player:plugin.getServer().getOnlinePlayers()) removeSpeed(player);
        saveAll(); combatUntil.clear();
    }
}
