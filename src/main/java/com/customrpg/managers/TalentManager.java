package com.customrpg.managers;

import com.customrpg.CustomRPG;
import com.customrpg.players.PlayerTalents;
import com.customrpg.talents.*;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.Sound;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TalentManager - 天賦系統管理器
 *
 * 功能：
 * - 載入天賦配置與樹狀結構
 * - 管理玩家天賦數據
 * - 處理天賦升級與重置
 * - 計算天賦效果加成
 */
public class TalentManager {

    private final CustomRPG plugin;
    private final Map<TalentBranch, TalentTree> talentTrees;
    private final Map<UUID, PlayerTalents> playerTalentsCache;
    private final File playerTalentFolder;
    private final File talentConfigFile;

    public TalentManager(CustomRPG plugin) {
        this.plugin = plugin;
        this.talentTrees = new HashMap<>();
        this.playerTalentsCache = new ConcurrentHashMap<>();

        // 設置資料夾
        this.playerTalentFolder = new File(plugin.getDataFolder(), "data/talents");
        this.talentConfigFile = new File(plugin.getDataFolder(), "config/talents.yml");

        // 確保資料夾存在
        if (!playerTalentFolder.exists()) {
            playerTalentFolder.mkdirs();
        }

        try {
            // 初始化天賦樹
            initializeTalentTrees();

            // 載入天賦配置
            loadTalentConfigurations();

            plugin.getLogger().info("TalentManager 初始化完成，載入了 " + getTotalTalentCount() + " 個天賦");
        } catch (Exception e) {
            plugin.getLogger().severe("TalentManager 初始化失敗: " + e.getMessage());
            throw new IllegalStateException("Talent definitions are invalid; startup stopped before player talents can be applied", e);
        }
    }

    /**
     * 初始化三個天賦樹
     */
    private void initializeTalentTrees() {
        for (TalentBranch branch : TalentBranch.values()) {
            TalentTree tree = new TalentTree(branch, branch.getDisplayName(), branch.getDescription());
            talentTrees.put(branch, tree);
        }
    }

    /**
     * 從配置檔案載入天賦
     */
    private void loadTalentConfigurations() throws Exception {
        plugin.getLogger().info("開始載入天賦配置從: " + talentConfigFile.getAbsolutePath());

        if (!talentConfigFile.exists()) {
            plugin.getLogger().info("配置檔案不存在，正在創建預設配置...");
            createDefaultTalentConfig();
        }

        if (!talentConfigFile.exists()) {
            plugin.getLogger().severe("無法創建配置檔案！");
            throw new IOException("Cannot create talents.yml");
        }

        YamlConfiguration config = new YamlConfiguration();
        config.load(talentConfigFile);
        File additionalFile = new File(plugin.getDataFolder(), "config/talents_additional.yml");
        if (!additionalFile.exists()) plugin.saveResource("config/talents_additional.yml", false);
        YamlConfiguration additional = new YamlConfiguration();
        additional.load(additionalFile);
        File hunterFile = new File(plugin.getDataFolder(), "config/talents_hunter.yml");
        if (!hunterFile.exists()) plugin.saveResource("config/talents_hunter.yml", false);
        YamlConfiguration hunter = new YamlConfiguration(); hunter.load(hunterFile);
        completeHunterDefinitions(hunter);
        var frost = new YamlConfiguration();
        File frostFile=new File(plugin.getDataFolder(),"config/talents_frost.yml");
        if(!frostFile.exists()) plugin.saveResource("config/talents_frost.yml",false);
        if(frostFile.exists()) frost.load(frostFile);
        else try(var stream=TalentManager.class.getClassLoader().getResourceAsStream("config/talents_frost.yml")) {
            if(stream==null) throw new IOException("Missing Frost defaults");
            frost.load(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8));
        }
        var necro=new YamlConfiguration();File necroFile=new File(plugin.getDataFolder(),"config/talents_necro.yml");
        if(!necroFile.exists())plugin.saveResource("config/talents_necro.yml",false);
        if(necroFile.exists())necro.load(necroFile);
        else try(var stream=TalentManager.class.getClassLoader().getResourceAsStream("config/talents_necro.yml")){if(stream==null)throw new IOException("Missing Necromancer defaults");necro.load(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8));}
        var necroRoot=necro.getConfigurationSection("necromancer");
        if(necroRoot==null||!necroRoot.getKeys(false).equals(Arrays.stream(NecroSkill.values()).map(v->v.id).collect(java.util.stream.Collectors.toSet())))throw new IllegalArgumentException("talents_necro.yml must contain eight Necromancer skills");
        var chicken=new YamlConfiguration();File chickenFile=new File(plugin.getDataFolder(),"config/talents_chicken.yml");
        if(!chickenFile.exists())plugin.saveResource("config/talents_chicken.yml",false);
        if(chickenFile.exists())chicken.load(chickenFile);
        else try(var stream=TalentManager.class.getClassLoader().getResourceAsStream("config/talents_chicken.yml")){if(stream==null)throw new IOException("Missing Chicken defaults");chicken.load(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8));}
        // Upgrade the prior eight-skill file in memory; never overwrite deployed settings or player records.
        try(var defaultsStream=TalentManager.class.getClassLoader().getResourceAsStream("config/talents_chicken.yml")){var defaults=new YamlConfiguration();defaults.load(new java.io.InputStreamReader(defaultsStream,java.nio.charset.StandardCharsets.UTF_8));for(var skill:ChickenSkill.values()){String root="chicken."+skill.id;chicken.set(root+".description",defaults.getString(root+".description"));for(int rank=1;rank<=(skill==ChickenSkill.GOD?1:3);rank++){String field=root+".levels."+rank+".effect.spiritScaling";if(!chicken.contains(field))chicken.set(field,defaults.getDouble(field));}}}
        var chickenRoot=chicken.getConfigurationSection("chicken");
        if(chickenRoot==null||!chickenRoot.getKeys(false).equals(Arrays.stream(ChickenSkill.values()).map(v->v.id).collect(java.util.stream.Collectors.toSet())))throw new IllegalArgumentException("talents_chicken.yml must contain eight Chicken skills");
        var dark=new YamlConfiguration();File darkFile=new File(plugin.getDataFolder(),"config/talents_dark.yml");
        if(!darkFile.exists())plugin.saveResource("config/talents_dark.yml",false);
        if(darkFile.exists())dark.load(darkFile);
        else try(var stream=TalentManager.class.getClassLoader().getResourceAsStream("config/talents_dark.yml")){
            if(stream==null)throw new IOException("Missing Dark defaults");dark.load(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8));
        }
        var darkRoot=dark.getConfigurationSection("dark");if(darkRoot==null||!darkRoot.getKeys(false).equals(Arrays.stream(DarkSkill.values()).map(v->v.id).collect(java.util.stream.Collectors.toSet())))throw new IllegalArgumentException("talents_dark.yml must contain eight supported Dark skills");
        for(String id:DarkSkill.legacy().keySet()){config.set("dark."+id,null);additional.set("dark."+id,null);}
        for(var skill:DarkSkill.values()){config.set("dark."+skill.id,null);additional.set("dark."+skill.id,null);}
        for(var source:List.of(config,additional)){
            for(var entry:Map.of("dark_affinity",49,"dark_resilience",47,"life_leech",51).entrySet())if(source.contains("dark."+entry.getKey())){
                String path="dark."+entry.getKey();source.set(path+".gui.slot",entry.getValue());String pre=source.getString(path+".prerequisite.skill");if(pre!=null)source.set(path+".prerequisite.skill",DarkSkill.canonical(pre));
            }
        }
        var earth = new YamlConfiguration();
        File earthFile=new File(plugin.getDataFolder(),"config/talents_earth.yml");
        if(!earthFile.exists())plugin.saveResource("config/talents_earth.yml",false);
        if(earthFile.exists())earth.load(earthFile);
        else try(var stream=TalentManager.class.getClassLoader().getResourceAsStream("config/talents_earth.yml")){
            if(stream==null)throw new IOException("Missing Earth defaults");
            earth.load(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8));
        }
        var earthRoot=earth.getConfigurationSection("earth");
        if(earthRoot==null||!earthRoot.getKeys(false).equals(Arrays.stream(EarthSkill.values()).map(v->v.id).collect(java.util.stream.Collectors.toSet())))throw new IllegalArgumentException("talents_earth.yml must contain exactly eight supported Earth skills");
        for(var skill:EarthSkill.values()){config.set("earth."+skill.id,null);additional.set("earth."+skill.id,null);}
        additional.set("earth.earth_endurance.gui.slot",49);additional.set("earth.earth_training.gui.slot",47);
        // Explicit frost redesign overlay. Original IDs/ranks stay intact; deployed files are never overwritten.
        var frostRoot=frost.getConfigurationSection("frost");
        if(frostRoot==null||!frostRoot.getKeys(false).equals(Arrays.stream(FrostSkill.values()).map(s->s.id).collect(java.util.stream.Collectors.toSet())))
            throw new IllegalArgumentException("talents_frost.yml must contain exactly the eight supported frost skills");
        for(var skill:FrostSkill.values()) {
            config.set("frost."+skill.id,null);additional.set("frost."+skill.id,null);
            // Upgrade the previous Frost default in memory; preserve administrator bindings and files.
            String path="frost."+skill.id+".mechanism";
            String mechanism=frost.getString(path,"");
            if(mechanism.equals("雪球")||mechanism.equalsIgnoreCase("SNOWBALL")) frost.set(path,"木棒");
        }
        additional.set("frost.frost_attunement.gui.slot",49);
        additional.set("frost.frost_resolve.gui.slot",47);
        restoreItemBindings(config);
        restoreItemBindings(additional);
        restoreItemBindings(hunter);
        restoreItemBindings(frost);
        restoreItemBindings(earth);
        restoreItemBindings(dark);
        restoreItemBindings(necro);
        restoreItemBindings(chicken);
        plugin.getLogger().info("配置檔案載入完成，根節點數量: " + config.getKeys(false).size());

        // 如果配置檔案是空的，先跳過載入天賦
        if (config.getKeys(false).isEmpty()) {
            plugin.getLogger().warning("天賦配置檔案為空，請手動配置 talents.yml");
            plugin.getLogger().warning("配置檔案路徑: " + talentConfigFile.getAbsolutePath());
            throw new IllegalArgumentException("talents.yml is empty");
        }

        plugin.getLogger().info("找到配置分支: " + config.getKeys(false));

        Map<TalentBranch, TalentTree> next = new EnumMap<>(TalentBranch.class);
        Set<String> ids = new HashSet<>();
        for (TalentBranch branch : TalentBranch.values()) {
            TalentTree tree = new TalentTree(branch, branch.getDisplayName(), branch.getDescription());
            loadBranchTalents(config, branch, tree);
            loadBranchTalents(additional, branch, tree);
            loadBranchTalents(hunter, branch, tree);
            loadBranchTalents(frost, branch, tree);
            loadBranchTalents(earth, branch, tree);
            loadBranchTalents(dark, branch, tree);
            loadBranchTalents(necro, branch, tree);
            loadBranchTalents(chicken, branch, tree);
            Set<String> slots = new HashSet<>();
            for (Talent talent : tree.getAllTalents().values()) {
                if (!ids.add(talent.getId())) throw new IllegalArgumentException("Duplicate talent ID: " + talent.getId());
                if (checkedBranch(branch)) {
                    if (branch == TalentBranch.HUNTER) HunterTalentSkills.validate(talent);
                    else if(branch==TalentBranch.CHICKEN) ChickenTalentSkills.validate(talent);
                    else if(branch==TalentBranch.NECROMANCER) NecroTalentSkills.validate(talent);
                    else if(branch==TalentBranch.DARK) DarkTalentSkills.validate(talent);
                    else if(EarthSkill.handles(talent.getId())) EarthTalentSkills.validate(talent);
                    else if(FrostSkill.handles(talent.getId())) FrostTalentSkills.validate(talent);
                    else ExpandedTalentSkills.validate(talent);
                    String page = branch == TalentBranch.HUNTER ? HunterPath.of(talent.getId()).name() : branch.name();
                    if (!slots.add(page + ":" + talent.getGuiSlot())) throw new IllegalArgumentException("Duplicate GUI slot in " + page);
                }
            }
            next.put(branch, tree);
        }
        for (TalentTree tree : next.values()) for (Talent talent : tree.getAllTalents().values())
            for (Talent.Prerequisite pre : talent.getPrerequisites())
                if (checkedBranch(talent.getBranch())
                        && (tree.getTalent(pre.talentId) == null || pre.requiredLevel < 1
                        || pre.requiredLevel > tree.getTalent(pre.talentId).getMaxLevel()))
                    throw new IllegalArgumentException("Invalid prerequisite for " + talent.getId());
        for (TalentTree tree : next.values()) if (checkedBranch(tree.getBranch()))
            for (String id : tree.getAllTalents().keySet()) validatePath(tree, id, new HashSet<>());
        talentTrees.clear();
        talentTrees.putAll(next);
    }

    private void validatePath(TalentTree tree, String id, Set<String> path) {
        if (!path.add(id)) throw new IllegalArgumentException("Cyclic prerequisite: " + id);
        for (Talent.Prerequisite pre : tree.getTalent(id).getPrerequisites()) validatePath(tree, pre.talentId, path);
        path.remove(id);
    }
    private static boolean checkedBranch(TalentBranch branch) {
        return branch == TalentBranch.CHICKEN || branch == TalentBranch.NECROMANCER || branch == TalentBranch.DARK || branch == TalentBranch.HUNTER || ExpandedTalentSkills.isExpansionBranch(branch);
    }
    /** Append new built-ins in memory. Existing ranks and custom configuration bytes stay intact. */
    private void completeHunterDefinitions(YamlConfiguration hunter) throws IOException {
        try (var stream = TalentManager.class.getClassLoader().getResourceAsStream("config/talents_hunter.yml")) {
            if (stream == null) throw new IOException("Missing Hunter defaults");
            var defaults = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
            var root = defaults.getConfigurationSection("hunter");
            for (String id : root.getKeys(false)) {
                String path = "hunter." + id;
                if (!hunter.contains(path)) hunter.set(path, root.getConfigurationSection(id));
            }
            // Only known original prerequisite links are updated for the character routes.
            Map<String,String> previous = Map.of("nen_rock", "nen_ren", "nen_scissors", "nen_ren", "nen_paper", "nen_ren",
                    "nen_lightning", "nen_gyo", "nen_chain", "nen_gyo", "nen_bungee", "nen_gyo", "nen_bodhisattva", "nen_rock");
            previous.forEach((id, old) -> {
                String path = "hunter." + id + ".prerequisite.skill";
                if (old.equals(hunter.getString(path))) hunter.set(path, defaults.getString(path));
            });
            Map<String,Integer> oldSlots = Map.ofEntries(Map.entry("nen_ten", 49), Map.entry("nen_ren", 40), Map.entry("nen_gyo", 42),
                    Map.entry("nen_rock", 31), Map.entry("nen_scissors", 29), Map.entry("nen_paper", 33), Map.entry("nen_lightning", 20),
                    Map.entry("nen_chain", 22), Map.entry("nen_bungee", 24), Map.entry("nen_godspeed", 11), Map.entry("nen_en", 13), Map.entry("nen_bodhisattva", 15));
            oldSlots.forEach((id, old) -> {
                String path = "hunter." + id + ".gui.slot";
                if (hunter.getInt(path, -1) == old) hunter.set(path, defaults.getInt(path));
            });
        }
    }

    /** Upgrade hotkey-only bindings in memory, without replacing deployed balance or player files. */
    private void restoreItemBindings(YamlConfiguration config) throws IOException {
        for (String resource : List.of("config/talents.yml", "config/talents_additional.yml", "config/talents_hunter.yml")) {
            try (var stream = TalentManager.class.getClassLoader().getResourceAsStream(resource)) {
                if (stream == null) throw new IOException("Missing bundled talent bindings: " + resource);
                var defaults = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(
                        stream, java.nio.charset.StandardCharsets.UTF_8));
                for (String branch : config.getKeys(false)) {
                    var section = config.getConfigurationSection(branch);
                    if (section == null) continue;
                    for (String id : section.getKeys(false)) {
                        String path = branch + "." + id;
                        if (!"ACTIVE".equalsIgnoreCase(config.getString(path + ".type"))) continue;
                        if (!"HOTKEY".equalsIgnoreCase(config.getString(path + ".trigger.type"))) continue;
                        // Unknown administrator-defined hotkey talents keep their original binding.
                        if (!defaults.contains(path + ".trigger.type")) continue;
                        config.set(path + ".trigger.type", defaults.getString(path + ".trigger.type"));
                        String mechanism = config.getString(path + ".mechanism", "");
                        if (mechanism.contains("快捷鍵") || mechanism.equalsIgnoreCase("HOTKEY") || mechanism.isBlank())
                            config.set(path + ".mechanism", defaults.getString(path + ".mechanism"));
                    }
                }
            }
        }
    }

    /** Validate both files before replacing live trees. Player investment and bindings are retained. */
    public boolean reloadConfigurations() {
        try { loadTalentConfigurations(); return true; }
        catch (Exception error) {
            plugin.getLogger().severe("Talent reload rejected; previous trees retained: " + error.getMessage());
            return false;
        }
    }

    /**
     * 載入指定分支的天賦
     */
    private void loadBranchTalents(YamlConfiguration config, TalentBranch branch, TalentTree tree) {
        String branchKey = branch.name().toLowerCase(Locale.ROOT);
        plugin.getLogger().info("正在載入分支: " + branchKey);

        ConfigurationSection branchSection = config.getConfigurationSection(branchKey);

        if (branchSection == null) {
            if (config.contains(branchKey)) throw new IllegalArgumentException("Invalid branch: " + branchKey);
            return;
        }

        if (tree == null) {
            plugin.getLogger().severe("天賦樹 " + branch + " 未初始化！");
            return;
        }

        Set<String> talentKeys = branchSection.getKeys(false);
        plugin.getLogger().info("分支 " + branchKey + " 中找到 " + talentKeys.size() + " 個天賦: " + talentKeys);

        for (String talentId : talentKeys) {
            if (tree.getTalent(talentId) != null) continue; // Deployed primary definition takes priority.
            ConfigurationSection talentSection = branchSection.getConfigurationSection(talentId);
            if (talentSection == null) {
                throw new IllegalArgumentException("Invalid talent section: " + talentId);
            }

            try {
                Talent talent = loadTalentFromConfig(talentId, talentSection, branch);
                tree.registerTalent(talent);
                plugin.getLogger().info("成功載入天賦: " + talentId);
            } catch (Exception e) {
                plugin.getLogger().warning("載入天賦失敗 " + talentId + ": " + e.getMessage());
                throw new IllegalArgumentException("Invalid talent " + talentId, e);
            }
        }

        plugin.getLogger().info("載入 " + branch.getDisplayName() + " 分支完成: " + tree.getTalentCount() + " 個天賦");
    }

    /**
     * 從配置段載入單個天賦
     */
    private Talent loadTalentFromConfig(String talentId, ConfigurationSection section, TalentBranch branch) {
        if (checkedBranch(branch)) {
            for (String key : List.of("max-level", "points-per-level", "gui.slot", "cooldown", "manaCost", "hpCost"))
                if (section.contains(key) && !(section.get(key) instanceof Number))
                    throw new IllegalArgumentException("Expected a number at " + talentId + "." + key);
            var levels = section.getConfigurationSection("levels");
            if (levels == null) throw new IllegalArgumentException("Missing levels: " + talentId);
            for (var entry : levels.getValues(true).entrySet())
                if (!(entry.getValue() instanceof ConfigurationSection) && !(entry.getValue() instanceof Number))
                    throw new IllegalArgumentException("Expected a numeric effect at " + talentId + "." + entry.getKey());
        }
        String name = section.getString("name", talentId);
        String description = section.getString("description", "");
        TalentType type = TalentType.valueOf(section.getString("type", "PASSIVE").toUpperCase());
        int maxLevel = section.getInt("max-level", 1);
        int pointsPerLevel = section.getInt("points-per-level", 1);
        int guiSlot = section.getInt("gui.slot", 0);
        String icon = section.getString("gui.icon", "BOOK");

        // 新規格欄位
        String mechanism = section.getString("mechanism", "被動");
        double cooldown = section.getDouble("cooldown", 0);
        double manaCost = section.getDouble("manaCost", 0);
        double hpCost = section.getDouble("hpCost", 0);
        String triggerType = section.getString("trigger.type", "NONE");

        // 載入前置
        List<Talent.Prerequisite> prerequisites = new ArrayList<>();
        Talent.PrerequisiteMode prerequisiteMode = Talent.PrerequisiteMode.ALL;

        if (section.contains("prerequisite")) {
            ConfigurationSection preSection = section.getConfigurationSection("prerequisite");

            if (preSection != null) {
                // 檢查是否包含 anyOf
                if (preSection.contains("anyOf")) {
                    prerequisiteMode = Talent.PrerequisiteMode.ANY;
                    List<?> anyOfList = preSection.getList("anyOf");
                    if (anyOfList != null) {
                        for (Object obj : anyOfList) {
                            if (obj instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> preMap = (Map<String, Object>) obj;
                                String preId = (String) preMap.get("skill");
                                int preLevel = preMap.containsKey("level") ?
                                    ((Number) preMap.get("level")).intValue() : 1;
                                if (preId != null) {
                                    prerequisites.add(new Talent.Prerequisite(preId, preLevel));
                                }
                            }
                        }
                    }
                }
                // 單一前置條件格式
                else if (preSection.contains("skill")) {
                    String preId = preSection.getString("skill");
                    int preLevel = preSection.getInt("level", 1);
                    if (preId != null) {
                        prerequisites.add(new Talent.Prerequisite(preId, preLevel));
                    }
                }
            } else {
                // 舊版或簡單前置邏輯
                String preId = section.getString("prerequisite");
                if (preId != null && !preId.equalsIgnoreCase("none")) {
                    prerequisites.add(new Talent.Prerequisite(preId, 1));
                }
            }
        }

        Talent talent = new Talent(talentId, name, description, type, branch,
                maxLevel, pointsPerLevel, prerequisites, prerequisiteMode, guiSlot, icon,
                mechanism, cooldown, manaCost, hpCost, triggerType);

        // 載入等級數據
        ConfigurationSection levelsSection = section.getConfigurationSection("levels");
        if (levelsSection != null) {
            for (String levelKey : levelsSection.getKeys(false)) {
                int level = Integer.parseInt(levelKey);
                ConfigurationSection levelDataSection = levelsSection.getConfigurationSection(levelKey);
                Talent.TalentLevelData levelData = new Talent.TalentLevelData();

                ConfigurationSection effectSection = levelDataSection.getConfigurationSection("effect");
                if (effectSection != null) {
                    for (String effectKey : effectSection.getKeys(false)) {
                        if (effectSection.isConfigurationSection(effectKey)) {
                            ConfigurationSection subSection = effectSection.getConfigurationSection(effectKey);

                            // 處理 statBonus 類型效果 { SPIRIT: 25, WISDOM: 25 }
                            if (effectKey.equals("statBonus")) {
                                for (String statName : subSection.getKeys(false)) {
                                    double value = subSection.getDouble(statName);
                                    levelData.addEffect("statBonus_" + statName, value);
                                }
                            }
                            // 處理 scaling 類型效果 { stat: STRENGTH, multiplier: 0.5 }
                            else if (subSection.contains("stat") && subSection.contains("multiplier")) {
                                String stat = subSection.getString("stat");
                                double multiplier = subSection.getDouble("multiplier");
                                levelData.addScaling(stat, multiplier);
                            }
                            // 處理其他嵌套結構（如 selfEffect, healingPerSecond 等）
                            else {
                                for (String subKey : subSection.getKeys(false)) {
                                    Object value = subSection.get(subKey);
                                    if (value instanceof Number) {
                                        levelData.addEffect(effectKey + "_" + subKey, ((Number) value).doubleValue());
                                    } else if (value instanceof String) {
                                        levelData.addEffect(effectKey + "_" + subKey, 0.0); // 儲存為標記
                                    }
                                }
                            }
                        } else {
                            levelData.addEffect(effectKey, effectSection.getDouble(effectKey));
                        }
                    }
                }
                talent.addLevelData(level, levelData);
            }
        } else {
            // 相容舊版或簡單效果 (base-effects / level-scaling)
            for (int i = 1; i <= maxLevel; i++) {
                Talent.TalentLevelData levelData = new Talent.TalentLevelData();
                ConfigurationSection baseEffects = section.getConfigurationSection("base-effects");
                ConfigurationSection levelScaling = section.getConfigurationSection("level-scaling");

                if (baseEffects != null) {
                    for (String key : baseEffects.getKeys(false)) {
                        double base = baseEffects.getDouble(key);
                        double scaling = (levelScaling != null) ? levelScaling.getDouble(key, 0) : 0;
                        levelData.addEffect(key, base + (scaling * (i - 1)));
                    }
                }
                talent.addLevelData(i, levelData);
            }
        }

        return talent;
    }

    /**
     * 創建預設天賦配置檔案
     */
    private void createDefaultTalentConfig() {
        try {
            if (!talentConfigFile.getParentFile().exists()) {
                talentConfigFile.getParentFile().mkdirs();
            }

            if (!talentConfigFile.exists()) {
                // 嘗試從resources載入預設配置
                try {
                    plugin.saveResource("config/talents.yml", false);
                    plugin.getLogger().info("已從resources載入預設天賦配置檔案");
                } catch (Exception e) {
                    // 如果無法從resources載入，創建空檔案
                    talentConfigFile.createNewFile();
                    plugin.getLogger().warning("無法從resources載入配置，已創建空的天賦配置檔案: " + talentConfigFile.getPath());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("無法創建預設天賦配置: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ===== 玩家天賦數據管理 =====

    /**
     * 獲取玩家天賦數據
     * @param player 玩家
     * @return 天賦數據
     */
    public PlayerTalents getPlayerTalents(Player player) {
        return getPlayerTalents(player.getUniqueId());
    }

    /**
     * 獲取玩家天賦數據
     * @param uuid 玩家UUID
     * @return 天賦數據
     */
    public PlayerTalents getPlayerTalents(UUID uuid) {
        PlayerTalents cached = playerTalentsCache.get(uuid);
        if (cached != null) {
            // 同步天賦點數從 PlayerStats
            try {
                PlayerStatsManager statsManager = plugin.getPlayerStatsManager();
                if (statsManager != null) {
                    com.customrpg.players.PlayerStats playerStats = statsManager.getStats(uuid);
                    if (playerStats != null) {
                        cached.setAvailablePoints(playerStats.getTalentPoints());
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("同步天賦點數時發生錯誤: " + e.getMessage());
            }
            return cached;
        }
        return playerTalentsCache.computeIfAbsent(uuid, this::loadPlayerTalents);
    }

    /**
     * 從檔案載入玩家天賦數據
     */
    private PlayerTalents loadPlayerTalents(UUID uuid) {
        File playerFile = new File(playerTalentFolder, uuid + ".yml");

        PlayerTalents talents = new PlayerTalents();

        if (!playerFile.exists()) {
            // 新玩家，同步PlayerStats中的天賦點數
            try {
                PlayerStatsManager statsManager = plugin.getPlayerStatsManager();
                if (statsManager != null) {
                    com.customrpg.players.PlayerStats playerStats = statsManager.getStats(uuid);
                    if (playerStats != null) {
                        talents.setAvailablePoints(playerStats.getTalentPoints());
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("初始化玩家天賦數據時發生錯誤: " + e.getMessage());
            }
            return talents; // 返回預設數據
        }

        try {
            YamlConfiguration config = new YamlConfiguration();
            try {
                config.load(playerFile);
                if (config.getInt("loadout-version", 0) > 3)
                    throw new IOException("Unknown newer talent loadout version");
                talentLoadFailed.remove(uuid);
            } catch (IOException | org.bukkit.configuration.InvalidConfigurationException e) {
                // 讀取失敗時不可用空白天賦覆寫原檔
                talentLoadFailed.add(uuid);
                plugin.getLogger().severe("無法讀取玩家天賦數據 " + playerFile.getName()
                        + "，本次不會覆寫該檔案，請手動檢查: " + e.getMessage());
                return talents;
            }

            // 從PlayerStats同步最新的天賦點數，而不是從檔案讀取
            try {
                PlayerStatsManager statsManager = plugin.getPlayerStatsManager();
                if (statsManager != null) {
                    com.customrpg.players.PlayerStats playerStats = statsManager.getStats(uuid);
                    if (playerStats != null) {
                        talents.setAvailablePoints(playerStats.getTalentPoints());
                    }
                }
            } catch (Exception e) {
                // 如果無法同步，則從檔案讀取
                talents.setAvailablePoints(config.getInt("available-points", 0));
            }

            talents.setTotalPointsSpent(config.getInt("total-points-spent", 0));

            // 載入天賦等級
            ConfigurationSection talentsSection = config.getConfigurationSection("talents");
            if (talentsSection != null) {
                for (String talentId : talentsSection.getKeys(false)) {
                    int level = talentsSection.getInt(talentId, 0);
                    talents.setTalentLevel(talentId, level);
                }
            }

            // 載入分支點數
            ConfigurationSection branchSection = config.getConfigurationSection("branch-points");
            if (branchSection != null) {
                for (String branchName : branchSection.getKeys(false)) {
                    try {
                        TalentBranch branch = TalentBranch.valueOf(branchName.toUpperCase());
                        int points = branchSection.getInt(branchName, 0);
                        talents.getBranchPoints().put(branch, points);
                    } catch (IllegalArgumentException e) {
                        // 忽略無效的分支名稱
                    }
                }
            }

            // 載入選取的技能
            List<?> selected = config.getList("selected-skills", List.of());
            for (int i = 0; i < Math.min(selected.size(), 5); i++) {
                talents.loadSelectedSkill(i,selected.get(i) instanceof String id && !id.isBlank() ? id : null);
            }
            talents.setUltimateSkill(config.getString("ultimate-skill"));

            // 已套用到 PlayerStats 的天賦屬性加成；用於下次重新計算時先扣除，避免每次重啟重複疊加
            ConfigurationSection appliedSection = config.getConfigurationSection("applied-stat-bonus");
            Map<String, Integer> applied = new HashMap<>();
            if (appliedSection != null) {
                for (String attribute : STAT_ATTRIBUTES) {
                    applied.put(attribute, appliedSection.getInt(attribute, 0));
                }
            } else {
                // 舊版存檔沒有紀錄：舊流程在學習天賦時已把加成寫入屬性，視為目前加成已套用
                applied = computeTalentStatBonuses(talents);
            }
            talentBonusCache.put(uuid, applied);

        } catch (Exception e) {
            plugin.getLogger().warning("載入玩家天賦數據失敗 " + uuid + ": " + e.getMessage());
        }

        return talents;
    }

    /**
     * 儲存玩家天賦數據
     * @param uuid 玩家UUID
     * @param talents 天賦數據
     */
    public void savePlayerTalents(UUID uuid, PlayerTalents talents) {
        if (talentLoadFailed.contains(uuid)) {
            plugin.getLogger().warning("略過儲存玩家天賦數據 " + uuid + "：原始檔案讀取失敗，避免覆寫");
            return;
        }
        File playerFile = new File(playerTalentFolder, uuid + ".yml");

        try {
            YamlConfiguration config = new YamlConfiguration();
            if (playerFile.exists()) {
                var previous=new YamlConfiguration(); previous.load(playerFile);
                if (previous.getInt("loadout-version",0)>3) throw new IOException("Refusing to replace newer loadout");
                if (previous.getInt("loadout-version",0)<3) com.customrpg.balance.BalanceMigration.backup(playerFile.toPath(),
                        plugin.getDataFolder().toPath().resolve("data/talent-backups/v3"));
                if (previous.getInt("balance-version",0)<2) com.customrpg.balance.BalanceMigration.backup(playerFile.toPath(),
                        plugin.getDataFolder().toPath().resolve("data/balance-backups/v2/talents"));
            }
            config.set("balance-version",2);
            config.set("loadout-version",3);
            config.set("available-points", talents.getAvailablePoints());
            config.set("total-points-spent", talents.getTotalPointsSpent());

            // 儲存天賦等級
            for (Map.Entry<String, Integer> entry : talents.getTalentLevels().entrySet()) {
                config.set("talents." + entry.getKey(), entry.getValue());
            }

            // 儲存分支點數
            for (Map.Entry<TalentBranch, Integer> entry : talents.getBranchPoints().entrySet()) {
                config.set("branch-points." + entry.getKey().name().toLowerCase(), entry.getValue());
            }

            // 儲存選取的技能
            config.set("selected-skills", Arrays.asList(talents.storedSelectedSkills()));
            config.set("ultimate-skill", talents.storedUltimateSkill());

            Map<String, Integer> applied = talentBonusCache.get(uuid);
            if (applied != null) {
                for (String attribute : STAT_ATTRIBUTES) {
                    config.set("applied-stat-bonus." + attribute, applied.getOrDefault(attribute, 0));
                }
            }

            PlayerStatsManager.saveAtomically(config, playerFile);

        } catch (Exception e) {
            plugin.getLogger().warning("儲存玩家天賦數據失敗 " + uuid + ": " + e.getMessage());
        }
    }

    /**
     * 儲存所有快取的玩家數據
     */
    public void saveAllPlayerTalents() {
        for (Map.Entry<UUID, PlayerTalents> entry : playerTalentsCache.entrySet()) {
            savePlayerTalents(entry.getKey(), entry.getValue());
        }
    }

    // ===== 天賦操作 =====

    /**
     * 升級天賦
     * @param player 玩家
     * @param talentId 天賦ID
     * @return 升級是否成功
     */
    public boolean upgradeTalent(Player player, String talentId) {
        talentId=DarkSkill.canonical(talentId);
        PlayerTalents playerTalents = getPlayerTalents(player);
        Talent talent = findTalent(talentId);

        if (talent == null) {
            player.sendMessage(ChatColor.RED + "天賦不存在: " + talentId);
            return false;
        }

        int currentLevel = playerTalents.getTalentLevel(talentId);
        int targetLevel = currentLevel + 1;
        if (talentLoadFailed.contains(player.getUniqueId())) {
            player.sendMessage("§c天賦存檔讀取失敗，已停止學習以保護原始資料。"); return false;
        }
        // 檢查等級上限
        if (targetLevel > talent.getMaxLevel()) {
            player.sendMessage(ChatColor.RED + "天賦已達最大等級！");
            return false;
        }

        // 檢查前置條件
        if (!talent.canLearn(playerTalents.effectiveLevels(), targetLevel)) {
            TalentTree tree = talentTrees.get(talent.getBranch());
            String status = tree.getPrerequisiteStatus(talentId, playerTalents.effectiveLevels());
            player.sendMessage(ChatColor.RED + "無法升級天賦：" + status);
            return false;
        }

        // 檢查天賦點數 - 從 PlayerStats 同步檢查
        PlayerStatsManager statsManager = plugin.getPlayerStatsManager();
        com.customrpg.players.PlayerStats playerStats = statsManager.getStats(player);
        int pointsNeeded = talent.getPointsPerLevel();

        if (playerStats.getTalentPoints() < pointsNeeded) {
            player.sendMessage(ChatColor.RED + "天賦點數不足！需要 " + pointsNeeded + " 點，擁有 " + playerStats.getTalentPoints() + " 點");
            return false;
        }

        // 執行升級
        int newLevel = playerTalents.upgradeTalent(talentId, talent.getBranch(), pointsNeeded);

        // 同步扣除 PlayerStats 中的天賦點數
        if (playerStats.spendTalentPoints(pointsNeeded)) {
            statsManager.saveStats(player);
        }

        // 發送成功訊息
        player.sendMessage(ChatColor.GREEN + "成功升級 " + ChatColor.YELLOW + talent.getName() +
                          ChatColor.GREEN + " 到等級 " + ChatColor.GOLD + newLevel + ChatColor.GREEN + "！");
        player.playSound(player.getLocation(), "entity.player.levelup", 1.0f, 1.2f);

        // 應用天賦效果（如果需要即時應用）
        applyTalentEffects(player);

        if (talent.getBranch() == TalentBranch.HUNTER && plugin.getTalentSkillManager() != null)
            plugin.getTalentSkillManager().getHunter().training(player, talent);
        return true;
    }

    /**
     * 重置所有天賦
     * @param player 玩家
     * @return 重置是否成功
     */
    public boolean resetAllTalents(Player player) {
        PlayerTalents playerTalents = getPlayerTalents(player);

        if (playerTalents.getTotalPointsSpent() == 0) {
            player.sendMessage(ChatColor.YELLOW + "您沒有已學習的天賦！");
            return false;
        }

        // 計算要返還的點數
        int refundedPoints = playerTalents.getTotalPointsSpent();

        // 執行重置
        playerTalents.resetAllTalents();
        if (plugin.getTalentSkillManager() != null) plugin.getTalentSkillManager().getExpansion().cancel(player);
        if (plugin.getTalentSkillManager() != null) {
            plugin.getTalentSkillManager().getHunter().cancel(player);
            plugin.getTalentSkillManager().getFrost().cancel(player);
            if(plugin.getTalentSkillManager().getEarth()!=null)plugin.getTalentSkillManager().getEarth().cancel(player);
            if(plugin.getTalentSkillManager().getDark()!=null)plugin.getTalentSkillManager().getDark().cancel(player);if(plugin.getTalentSkillManager()!=null&&plugin.getTalentSkillManager().getNecro()!=null)plugin.getTalentSkillManager().getNecro().cancel(player);
        if(plugin.getTalentSkillManager()!=null&&plugin.getTalentSkillManager().getChicken()!=null)plugin.getTalentSkillManager().getChicken().cancel(player);
        }

        // 同步返還 PlayerStats 中的天賦點數
        PlayerStatsManager statsManager = plugin.getPlayerStatsManager();
        com.customrpg.players.PlayerStats playerStats = statsManager.getStats(player);
        playerStats.addTalentPoints(refundedPoints);
        statsManager.saveStats(player);

        // 重新應用效果
        applyTalentEffects(player);

        player.sendMessage(ChatColor.GREEN + "成功重置所有天賦！返還 " + ChatColor.GOLD + refundedPoints + ChatColor.GREEN + " 點天賦點數");
        player.playSound(player.getLocation(), "ui.button.click", 1.0f, 0.8f);

        return true;
    }

    /**
     * 給予玩家天賦點數
     * @param player 玩家
     * @param points 點數
     */
    public void givePoints(Player player, int points) {
        // 直接修改 PlayerStats 中的天賦點數
        PlayerStatsManager statsManager = plugin.getPlayerStatsManager();
        com.customrpg.players.PlayerStats playerStats = statsManager.getStats(player);
        playerStats.addTalentPoints(points);
        statsManager.saveStats(player);

        player.sendMessage(ChatColor.GREEN + "獲得 " + ChatColor.GOLD + points + ChatColor.GREEN + " 點天賦點數！");
        player.sendMessage(ChatColor.YELLOW + "可用天賦點數: " + playerStats.getTalentPoints());
    }

    // ===== 工具方法 =====

    /**
     * 尋找天賦
     * @param talentId 天賦ID
     * @return 天賦物件
     */
    public Talent findTalent(String talentId) {
        talentId=DarkSkill.canonical(talentId);
        if (talentId == null) return null;
        for (TalentTree tree : talentTrees.values()) {
            Talent talent = tree.getTalent(talentId);
            if (talent != null) return talent;
        }
        return null;
    }

    /**
     * 獲取天賦樹
     * @param branch 分支
     * @return 天賦樹
     */
    public TalentTree getTalentTree(TalentBranch branch) {
        TalentTree tree = talentTrees.get(branch);
        if (tree == null) {
            plugin.getLogger().warning("天賦樹 " + branch + " 為 null! 可用的天賦樹: " + talentTrees.keySet());
        } else {
            plugin.getLogger().info("獲取天賦樹 " + branch + ", 包含 " + tree.getTalentCount() + " 個天賦");
        }
        return tree;
    }

    /**
     * 獲取所有天賦樹
     * @return 天賦樹映射表
     */
    public Map<TalentBranch, TalentTree> getAllTalentTrees() {
        return talentTrees;
    }

    /**
     * 獲取總天賦數量
     * @return 天賦總數
     */
    public int getTotalTalentCount() {
        return talentTrees.values().stream().mapToInt(TalentTree::getTalentCount).sum();
    }
    public boolean bindSkill(Player player, String id, int slot, boolean ultimate) {
        id=DarkSkill.canonical(id);
        var investment = getPlayerTalents(player);
        if (talentLoadFailed.contains(player.getUniqueId())) return false;
        if (plugin.getCombatBalance() != null && plugin.getCombatBalance().inCombat(player)) {
            player.sendMessage("§c請脫戰八秒後再更換技能配置。"); return false;
        }
        Talent talent = findTalent(id);
        if (talent == null || investment.getTalentLevel(id) <= 0 || talent.getType() != TalentType.ACTIVE) {
            player.sendMessage("§c請選擇已學習的主動技能。"); return false;
        }
        if (ultimate != TalentLoadout.ultimate(id) || !ultimate && (slot < 0 || slot >= 5)) {
            player.sendMessage("§c普通技能使用1～4／F；大絕請放入Shift＋F大絕槽。"); return false;
        }
        if(ultimate) investment.setUltimateSkill(id);else investment.setSelectedSkill(slot, id);
        if(plugin.getTalentSkillManager()!=null&&plugin.getTalentSkillManager().getFrost()!=null)plugin.getTalentSkillManager().getFrost().cancel(player);
        if(plugin.getTalentSkillManager()!=null&&plugin.getTalentSkillManager().getEarth()!=null)plugin.getTalentSkillManager().getEarth().cancel(player);
        if(plugin.getTalentSkillManager()!=null&&plugin.getTalentSkillManager().getDark()!=null)plugin.getTalentSkillManager().getDark().cancel(player);if(plugin.getTalentSkillManager()!=null&&plugin.getTalentSkillManager().getNecro()!=null)plugin.getTalentSkillManager().getNecro().cancel(player);
        if(plugin.getTalentSkillManager()!=null&&plugin.getTalentSkillManager().getChicken()!=null)plugin.getTalentSkillManager().getChicken().cancel(player);
        savePlayerTalents(player.getUniqueId(), investment);
        player.sendMessage("§a已攜帶 " + talent.getName() + (ultimate ? "（大絕 Shift＋F）" : "（技能槽 " + (slot+1) + "）"));
        return true;
    }

    /**
     * 獲取插件實例
     */
    public CustomRPG getPlugin() {
        return plugin;
    }

    /**
     * 應用玩家的天賦效果（預留給PassiveEffectManager處理）
     * @param player 玩家
     */
    public void applyTalentEffects(Player player) {
        if (talentLoadFailed.contains(player.getUniqueId())) return;
        try {
            PlayerTalents playerTalents = getPlayerTalents(player);
            PlayerStatsManager statsManager = plugin.getPlayerStatsManager();
            com.customrpg.players.PlayerStats playerStats = statsManager.getStats(player);

            // 計算所有天賦提供的屬性加成
            Map<String, Integer> totals = computeTalentStatBonuses(playerTalents);
            int[] normalized = com.customrpg.balance.BalancePlayerRules.bounded(
                    java.util.Arrays.stream(STAT_ATTRIBUTES).mapToLong(key -> Math.max(0,totals.getOrDefault(key,0))).toArray(),
                    com.customrpg.balance.BalancePlayerRules.talentBudget(playerStats.getLevel()));
            int totalStrengthBonus = normalized[0];
            playerStats.setTalentStrength(totalStrengthBonus);
            int totalMagicBonus = normalized[1];
            playerStats.setTalentMagic(totalMagicBonus);
            int totalAgilityBonus = normalized[2];
            playerStats.setTalentAgility(totalAgilityBonus);
            int totalVitalityBonus = normalized[3];
            playerStats.setTalentVitality(totalVitalityBonus);
            int totalDefenseBonus = normalized[4];
            playerStats.setTalentDefense(totalDefenseBonus);
            int totalSpiritBonus = normalized[5];
            playerStats.setTalentSpirit(totalSpiritBonus);

            // 更新最大血量（基於新的vitality值）
            statsManager.updateMaxHealth(player);
            statsManager.recalculateMana(player);

            // 儲存更新後的屬性
            statsManager.saveStats(player);

            // 快取天賦加成值以便下次使用，並與天賦存檔一起持久化
            cacheTalentBonuses(player, totalStrengthBonus, totalMagicBonus, totalAgilityBonus, totalVitalityBonus, totalDefenseBonus, totalSpiritBonus);
            savePlayerTalents(player.getUniqueId(), playerTalents);

            // Learning, upgrading, resetting and reapplying talents must refresh combat passives immediately.
            if (plugin.getTalentPassiveEffectManager() != null)
                plugin.getTalentPassiveEffectManager().refreshPlayerEffects(player);

            player.sendMessage("§a天賦效果已應用！");
            player.sendMessage("§7力量: +" + totalStrengthBonus + ", 智慧: +" + totalMagicBonus +
                              ", 敏捷: +" + totalAgilityBonus + ", 體力: +" + totalVitalityBonus + ", 防禦: +" + totalDefenseBonus + ", 精神: +" + totalSpiritBonus);

        } catch (Exception e) {
            plugin.getLogger().warning("應用天賦效果時發生錯誤: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 快取玩家的天賦屬性加成（已套用到 PlayerStats 的值，會寫入天賦存檔）
    private final Map<UUID, Map<String, Integer>> talentBonusCache = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> talentLoadFailed = ConcurrentHashMap.newKeySet();
    private static final String[] STAT_ATTRIBUTES = {"strength", "magic", "agility", "vitality", "defense", "spirit"};

    /**
     * 計算天賦提供的六項屬性加成（ATTRIBUTE / PASSIVE 類型，支援舊格式與 statBonus_ 前綴）
     */
    private Map<String, Integer> computeTalentStatBonuses(PlayerTalents playerTalents) {
        Map<String, Integer> totals = new HashMap<>();
        for (String attribute : STAT_ATTRIBUTES) {
            totals.put(attribute, 0);
        }
        for (Map.Entry<String, Integer> entry : playerTalents.getTalentLevels().entrySet()) {
            int level = entry.getValue();
            if (level <= 0) continue;
            Talent talent = findTalent(entry.getKey());
            if (talent == null
                    || (talent.getType() != TalentType.ATTRIBUTE && talent.getType() != TalentType.PASSIVE)) {
                continue;
            }
            Talent.TalentLevelData data = talent.getLevelData(level);
            if (data == null) continue;
            for (String attribute : STAT_ATTRIBUTES) {
                int bonus = data.effects.getOrDefault(attribute, 0.0).intValue()
                        + data.effects.getOrDefault("statBonus_" + attribute, 0.0).intValue();
                totals.merge(attribute, bonus, Integer::sum);
            }
        }
        return totals;
    }

    /**
     * 玩家離線：儲存並釋放快取（加成紀錄已持久化，下次登入會重新載入）
     */
    public void unloadPlayer(UUID uuid) {
        PlayerTalents talents = playerTalentsCache.remove(uuid);
        if (talents != null) {
            savePlayerTalents(uuid, talents);
        }
        talentBonusCache.remove(uuid);
        talentLoadFailed.remove(uuid);
    }

    /**
     * 快取天賦屬性加成
     */
    private void cacheTalentBonuses(Player player, int str, int mag, int agi, int vit, int def, int spi) {
        Map<String, Integer> bonuses = new HashMap<>();
        bonuses.put("strength", str);
        bonuses.put("magic", mag);
        bonuses.put("agility", agi);
        bonuses.put("vitality", vit);
        bonuses.put("defense", def);
        bonuses.put("spirit", spi);
        talentBonusCache.put(player.getUniqueId(), bonuses);
    }

    /**
     * 獲取玩家的天賦屬性加成
     */
    private int getTalentAttributeBonus(Player player, String attribute) {
        Map<String, Integer> bonuses = talentBonusCache.get(player.getUniqueId());
        return bonuses != null ? bonuses.getOrDefault(attribute, 0) : 0;
    }

    /**
     * 計算玩家天賦加成
     * @param player 玩家
     * @param effectName 效果名稱
     * @return 加成值
     */
    public double calculateTalentBonus(Player player, String effectName) {
        PlayerTalents playerTalents = getPlayerTalents(player);
        double totalBonus = 0.0;

        for (Map.Entry<String, Integer> entry : playerTalents.getTalentLevels().entrySet()) {
            String talentId = entry.getKey();
            int level = entry.getValue();

            if (level > 0) {
                Talent talent = findTalent(talentId);
                if (talent != null) {
                    Talent.TalentLevelData data = talent.getLevelData(level);
                    if (data != null && data.effects.containsKey(effectName)) {
                        totalBonus += data.effects.get(effectName);
                    }
                }
            }
        }

        return totalBonus;
    }

    /**
     * 關閉管理器，儲存所有數據
     */
    public void shutdown() {
        saveAllPlayerTalents();
        playerTalentsCache.clear();
    }
}

