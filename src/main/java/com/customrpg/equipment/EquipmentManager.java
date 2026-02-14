package com.customrpg.equipment;

import com.customrpg.CustomRPG;
import com.customrpg.players.PlayerStats;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 裝備管理器
 * 負責裝備的載入、儲存、管理和計算
 */
public class EquipmentManager {

    private final CustomRPG plugin;

    // 裝備模板數據
    private Map<String, EquipmentData> equipmentTemplates;

    // 套裝數據
    private Map<String, SetData> setData;

    // 符文數據
    private Map<String, RuneData> runeTemplates;

    // 玩家裝備數據 - UUID -> Slot -> EquipmentData
    private Map<UUID, Map<EquipmentSlot, EquipmentData>> playerEquipment;

    // 配置文件
    private File equipmentConfigFile;
    private FileConfiguration equipmentConfig;
    private File setsConfigFile;
    private FileConfiguration setsConfig;
    private File runesConfigFile;
    private FileConfiguration runesConfig;

    public EquipmentManager(CustomRPG plugin) {
        this.plugin = plugin;
        this.equipmentTemplates = new ConcurrentHashMap<>();
        this.setData = new ConcurrentHashMap<>();
        this.runeTemplates = new ConcurrentHashMap<>();
        this.playerEquipment = new ConcurrentHashMap<>();

        initializeConfigs();
        loadAllData();
    }

    /**
     * 初始化配置文件
     */
    private void initializeConfigs() {
        // 創建配置目錄
        File configDir = new File(plugin.getDataFolder(), "equipment");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }

        // 裝備配置
        equipmentConfigFile = new File(configDir, "equipment.yml");
        if (!equipmentConfigFile.exists()) {
            plugin.saveResource("equipment/equipment.yml", false);
        }
        equipmentConfig = YamlConfiguration.loadConfiguration(equipmentConfigFile);

        // 套裝配置
        setsConfigFile = new File(configDir, "sets.yml");
        if (!setsConfigFile.exists()) {
            plugin.saveResource("equipment/sets.yml", false);
        }
        setsConfig = YamlConfiguration.loadConfiguration(setsConfigFile);

        // 符文配置
        runesConfigFile = new File(configDir, "runes.yml");
        if (!runesConfigFile.exists()) {
            plugin.saveResource("equipment/runes.yml", false);
        }
        runesConfig = YamlConfiguration.loadConfiguration(runesConfigFile);
    }

    /**
     * 載入所有數據
     */
    private void loadAllData() {
        loadEquipmentTemplates();
        loadSetData();
        loadRuneTemplates();
        plugin.getLogger().info("裝備系統數據載入完成 - 裝備: " + equipmentTemplates.size() +
                                ", 套裝: " + setData.size() + ", 符文: " + runeTemplates.size());
    }

    /**
     * 載入裝備模板
     */
    private void loadEquipmentTemplates() {
        equipmentTemplates.clear();

        ConfigurationSection equipmentSection = equipmentConfig.getConfigurationSection("equipment");
        if (equipmentSection == null) return;

        for (String equipId : equipmentSection.getKeys(false)) {
            try {
                EquipmentData equipment = loadEquipmentFromConfig(equipId, equipmentSection.getConfigurationSection(equipId));
                if (equipment != null) {
                    equipmentTemplates.put(equipId, equipment);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("載入裝備 " + equipId + " 時發生錯誤: " + e.getMessage());
            }
        }
    }

    /**
     * 從配置載入裝備數據
     */
    private EquipmentData loadEquipmentFromConfig(String id, ConfigurationSection section) {
        if (section == null) return null;

        String name = section.getString("name", id);
        String description = section.getString("description", "");
        String materialStr = section.getString("material", "STONE");
        String slotStr = section.getString("slot", "HELMET");
        String rarityStr = section.getString("rarity", "COMMON");

        try {
            org.bukkit.Material material = org.bukkit.Material.valueOf(materialStr.toUpperCase());
            EquipmentSlot slot = EquipmentSlot.valueOf(slotStr.toUpperCase());
            EquipmentRarity rarity = EquipmentRarity.valueOf(rarityStr.toUpperCase());

            EquipmentData equipment = new EquipmentData(id, name, slot, rarity, material);
            equipment.setDescription(description);
            equipment.setLevel(section.getInt("level", 1));
            equipment.setRequiredLevel(section.getInt("required_level", 1));
            equipment.setSetId(section.getString("set_id", ""));

            // 載入基礎屬性
            ConfigurationSection attributesSection = section.getConfigurationSection("attributes");
            if (attributesSection != null) {
                Map<EquipmentAttribute, Double> attributes = new HashMap<>();
                for (String attrKey : attributesSection.getKeys(false)) {
                    EquipmentAttribute attr = EquipmentAttribute.fromShortName(attrKey.toUpperCase());
                    if (attr != null) {
                        double value = attributesSection.getDouble(attrKey);
                        attributes.put(attr, value);
                    }
                }
                equipment.setBaseAttributes(attributes);
            }

            // 載入特殊效果
            List<String> effects = section.getStringList("special_effects");
            equipment.setSpecialEffects(effects);

            return equipment;

        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("裝備 " + id + " 配置錯誤: " + e.getMessage());
            return null;
        }
    }

    /**
     * 載入套裝數據
     */
    private void loadSetData() {
        setData.clear();

        ConfigurationSection setsSection = setsConfig.getConfigurationSection("sets");
        if (setsSection == null) return;

        for (String setId : setsSection.getKeys(false)) {
            try {
                SetData set = loadSetFromConfig(setId, setsSection.getConfigurationSection(setId));
                if (set != null) {
                    setData.put(setId, set);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("載入套裝 " + setId + " 時發生錯誤: " + e.getMessage());
            }
        }
    }

    /**
     * 從配置載入套裝數據
     */
    private SetData loadSetFromConfig(String id, ConfigurationSection section) {
        if (section == null) return null;

        String name = section.getString("name", id);
        String description = section.getString("description", "");
        List<String> equipmentIds = section.getStringList("equipment");

        SetData set = new SetData(id, name, description);
        set.setEquipmentIds(new ArrayList<>(equipmentIds));

        // 載入套裝效果
        ConfigurationSection bonusesSection = section.getConfigurationSection("bonuses");
        if (bonusesSection != null) {
            for (String pieceCountStr : bonusesSection.getKeys(false)) {
                try {
                    int pieceCount = Integer.parseInt(pieceCountStr);
                    ConfigurationSection bonusSection = bonusesSection.getConfigurationSection(pieceCountStr);

                    if (bonusSection != null) {
                        String bonusDesc = bonusSection.getString("description", "");
                        SetData.SetBonus bonus = new SetData.SetBonus(bonusDesc);

                        // 載入屬性加成
                        ConfigurationSection attrSection = bonusSection.getConfigurationSection("attributes");
                        if (attrSection != null) {
                            for (String attrKey : attrSection.getKeys(false)) {
                                EquipmentAttribute attr = EquipmentAttribute.fromShortName(attrKey.toUpperCase());
                                if (attr != null) {
                                    double value = attrSection.getDouble(attrKey);
                                    bonus.addAttributeBonus(attr, value);
                                }
                            }
                        }

                        // 載入特殊效果
                        List<String> effects = bonusSection.getStringList("effects");
                        for (String effect : effects) {
                            bonus.addSpecialEffect(effect);
                        }

                        set.addSetBonus(pieceCount, bonus);
                    }
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("套裝 " + id + " 的效果件數配置錯誤: " + pieceCountStr);
                }
            }
        }

        return set;
    }

    /**
     * 載入符文模板
     */
    private void loadRuneTemplates() {
        runeTemplates.clear();

        ConfigurationSection runesSection = runesConfig.getConfigurationSection("runes");
        if (runesSection == null) return;

        for (String runeId : runesSection.getKeys(false)) {
            try {
                RuneData rune = loadRuneFromConfig(runeId, runesSection.getConfigurationSection(runeId));
                if (rune != null) {
                    runeTemplates.put(runeId, rune);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("載入符文 " + runeId + " 時發生錯誤: " + e.getMessage());
            }
        }
    }

    /**
     * 從配置載入符文數據
     */
    private RuneData loadRuneFromConfig(String id, ConfigurationSection section) {
        if (section == null) return null;

        String name = section.getString("name", id);
        String description = section.getString("description", "");
        String typeStr = section.getString("type", "ATTACK");
        String rarityStr = section.getString("rarity", "BASIC");

        try {
            RuneData.RuneType type = RuneData.RuneType.valueOf(typeStr.toUpperCase());
            RuneData.RuneRarity rarity = RuneData.RuneRarity.valueOf(rarityStr.toUpperCase());

            RuneData rune = new RuneData(id, name, type, rarity);
            rune.setDescription(description);
            rune.setLevel(section.getInt("level", 1));

            // 載入屬性
            ConfigurationSection attributesSection = section.getConfigurationSection("attributes");
            if (attributesSection != null) {
                for (String attrKey : attributesSection.getKeys(false)) {
                    EquipmentAttribute attr = EquipmentAttribute.fromShortName(attrKey.toUpperCase());
                    if (attr != null) {
                        double value = attributesSection.getDouble(attrKey);
                        rune.setBaseAttribute(attr, value);
                    }
                }
            }

            rune.setSpecialEffect(section.getString("special_effect", ""));

            return rune;

        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("符文 " + id + " 配置錯誤: " + e.getMessage());
            return null;
        }
    }

    /**
     * 獲取玩家裝備
     */
    public Map<EquipmentSlot, EquipmentData> getPlayerEquipment(UUID playerUUID) {
        return playerEquipment.computeIfAbsent(playerUUID, k -> new EnumMap<>(EquipmentSlot.class));
    }

    /**
     * 裝備物品
     */
    public boolean equipItem(Player player, EquipmentSlot slot, EquipmentData equipment) {
        if (!canEquip(player, equipment)) {
            return false;
        }

        Map<EquipmentSlot, EquipmentData> playerEquip = getPlayerEquipment(player.getUniqueId());

        // 保存舊裝備（如果有）
        EquipmentData oldEquipment = playerEquip.get(slot);

        // 裝備新裝備
        playerEquip.put(slot, equipment);

        // 同步到Minecraft原生裝備槽位
        syncToMinecraftEquipment(player, slot, equipment);

        // 重新計算玩家屬性
        updatePlayerAttributes(player);

        return true;
    }

    /**
     * 卸除裝備
     */
    public EquipmentData unequipItem(Player player, EquipmentSlot slot) {
        Map<EquipmentSlot, EquipmentData> playerEquip = getPlayerEquipment(player.getUniqueId());
        EquipmentData removed = playerEquip.remove(slot);

        if (removed != null) {
            // 從Minecraft原生槽位移除
            syncToMinecraftEquipment(player, slot, null);

            // 重新計算玩家屬性
            updatePlayerAttributes(player);
        }

        return removed;
    }

    /**
     * 同步裝備到Minecraft原生槽位
     */
    private void syncToMinecraftEquipment(Player player, EquipmentSlot slot, EquipmentData equipment) {
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        ItemStack item = equipment != null ? equipment.toItemStack() : null;

        switch (slot) {
            case HELMET:
                inv.setHelmet(item);
                break;
            case CHESTPLATE:
                inv.setChestplate(item);
                break;
            case LEGGINGS:
                inv.setLeggings(item);
                break;
            case BOOTS:
                inv.setBoots(item);
                break;
            // 主手和副手已從裝備系統移除，不再同步
            // case MAIN_HAND:
            //     inv.setItemInMainHand(item != null ? item : new ItemStack(Material.AIR));
            //     break;
            // case OFF_HAND:
            //     inv.setItemInOffHand(item != null ? item : new ItemStack(Material.AIR));
            //     break;
            // 飾品和特殊槽位不需要同步到原生槽位
            default:
                break;
        }
    }

    /**
     * 檢查是否可以裝備
     */
    private boolean canEquip(Player player, EquipmentData equipment) {
        PlayerStats stats = plugin.getPlayerStatsManager().getStats(player);

        // 檢查等級需求
        if (stats.getLevel() < equipment.getRequiredLevel()) {
            player.sendMessage("§c等級不足！需要等級 " + equipment.getRequiredLevel());
            return false;
        }

        // 檢查屬性需求
        for (Map.Entry<EquipmentAttribute, Integer> entry : equipment.getRequiredStats().entrySet()) {
            // 這裡需要根據實際的屬性系統來檢查
            // 暫時跳過屬性檢查
        }

        return true;
    }

    /**
     * 更新玩家屬性
     */
    private void updatePlayerAttributes(Player player) {
        Map<EquipmentSlot, EquipmentData> equipment = getPlayerEquipment(player.getUniqueId());
        Map<EquipmentAttribute, Double> totalAttributes = new HashMap<>();

        // 計算基礎裝備屬性
        for (EquipmentData equip : equipment.values()) {
            if (equip != null) {
                for (Map.Entry<EquipmentAttribute, Double> entry : equip.getAllAttributes().entrySet()) {
                    totalAttributes.merge(entry.getKey(), entry.getValue(), Double::sum);
                }
            }
        }

        // 計算套裝效果
        Map<String, Integer> setCount = calculateSetCounts(equipment);
        for (Map.Entry<String, Integer> entry : setCount.entrySet()) {
            String setId = entry.getKey();
            int count = entry.getValue();

            SetData set = setData.get(setId);
            if (set != null) {
                for (int threshold : set.getSortedBonusThresholds()) {
                    if (count >= threshold) {
                        SetData.SetBonus bonus = set.getSetBonus(threshold);
                        for (Map.Entry<EquipmentAttribute, Double> attrEntry : bonus.getAttributeBonuses().entrySet()) {
                            totalAttributes.merge(attrEntry.getKey(), attrEntry.getValue(), Double::sum);
                        }
                    }
                }
            }
        }

        // 應用屬性到玩家
        applyAttributesToPlayer(player, totalAttributes);
    }

    /**
     * 計算套裝件數
     */
    private Map<String, Integer> calculateSetCounts(Map<EquipmentSlot, EquipmentData> equipment) {
        Map<String, Integer> setCount = new HashMap<>();

        for (EquipmentData equip : equipment.values()) {
            if (equip != null && equip.getSetId() != null && !equip.getSetId().isEmpty()) {
                setCount.merge(equip.getSetId(), 1, Integer::sum);
            }
        }

        return setCount;
    }

    /**
     * 應用屬性到玩家
     */
    private void applyAttributesToPlayer(Player player, Map<EquipmentAttribute, Double> attributes) {
        PlayerStats stats = plugin.getPlayerStatsManager().getStats(player);

        // 重置裝備加成（避免累加）
        stats.setEquipmentStrength(0);
        stats.setEquipmentMagic(0);
        stats.setEquipmentAgility(0);
        stats.setEquipmentVitality(0);
        stats.setEquipmentDefense(0);

        stats.setBonusAttackDamage(0);
        stats.setBonusDefenseValue(0);
        stats.setBonusMaxHealth(0);
        stats.setBonusCritChance(0);
        stats.setBonusCritDamage(0);

        // 應用裝備屬性加成
        for (Map.Entry<EquipmentAttribute, Double> entry : attributes.entrySet()) {
            EquipmentAttribute attr = entry.getKey();
            double value = entry.getValue();

            switch (attr) {
                case STRENGTH:
                    stats.setEquipmentStrength((int) value);
                    break;
                case INTELLIGENCE:  // 智力映射到魔法
                    stats.setEquipmentMagic((int) value);
                    break;
                case MAGIC_DAMAGE:  // 魔法攻擊也映射到魔法
                    stats.setEquipmentMagic(stats.getEquipmentMagic() + (int) value);
                    break;
                case AGILITY:
                    stats.setEquipmentAgility((int) value);
                    break;
                case VITALITY:
                    stats.setEquipmentVitality((int) value);
                    break;
                case DEFENSE:
                    stats.setEquipmentDefense((int) value);
                    break;
                case ATTACK_DAMAGE:
                    stats.setBonusAttackDamage(value);
                    break;
                case MAX_HEALTH:
                    stats.setBonusMaxHealth(value);
                    break;
                case CRITICAL_CHANCE:  // 正確的枚舉名稱
                    stats.setBonusCritChance(value);
                    break;
                case CRITICAL_DAMAGE:  // 正確的枚舉名稱
                    stats.setBonusCritDamage(value);
                    break;
                case PHYSICAL_RESISTANCE:  // 物理抗性映射到防禦加成
                    stats.setBonusDefenseValue(stats.getBonusDefenseValue() + value);
                    break;
                // 其他屬性可以根據需要添加
            }
        }

        // 更新最大血量（如果有生命力或最大血量加成）
        if (stats.getBonusMaxHealth() > 0 || stats.getEquipmentVitality() > 0) {
            plugin.getPlayerStatsManager().updateMaxHealth(player);
        }

        // 保存數據
        plugin.getPlayerStatsManager().saveStats(player);

        // 調試信息
        plugin.getLogger().info("玩家 " + player.getName() + " 的裝備屬性已更新:");
        plugin.getLogger().info("  力量: +" + stats.getEquipmentStrength());
        plugin.getLogger().info("  魔法: +" + stats.getEquipmentMagic());
        plugin.getLogger().info("  敏捷: +" + stats.getEquipmentAgility());
        plugin.getLogger().info("  生命力: +" + stats.getEquipmentVitality());
        plugin.getLogger().info("  防禦: +" + stats.getEquipmentDefense());
        plugin.getLogger().info("  總力量: " + stats.getTotalStrength());
        plugin.getLogger().info("  總魔法: " + stats.getTotalMagic());
    }

    /**
     * 創建裝備副本
     */
    public EquipmentData createEquipment(String templateId) {
        EquipmentData template = equipmentTemplates.get(templateId);
        if (template == null) return null;

        // 創建副本
        EquipmentData equipment = new EquipmentData(
            template.getId(),
            template.getName(),
            template.getSlot(),
            template.getRarity(),
            template.getMaterial()
        );

        equipment.setDescription(template.getDescription());
        equipment.setLevel(template.getLevel());
        equipment.setRequiredLevel(template.getRequiredLevel());
        equipment.setSetId(template.getSetId());
        equipment.setBaseAttributes(new HashMap<>(template.getBaseAttributes()));
        equipment.setSpecialEffects(new ArrayList<>(template.getSpecialEffects()));

        // 生成隨機詞條
        equipment.generateRandomAffixes();

        return equipment;
    }

    /**
     * 創建符文副本
     */
    public RuneData createRune(String templateId) {
        RuneData template = runeTemplates.get(templateId);
        if (template == null) return null;

        RuneData rune = new RuneData(
            template.getId(),
            template.getName(),
            template.getType(),
            template.getRarity()
        );

        rune.setDescription(template.getDescription());
        rune.setLevel(template.getLevel());
        rune.setAttributes(new HashMap<>(template.getAttributes()));
        rune.setSpecialEffect(template.getSpecialEffect());

        return rune;
    }

    /**
     * 保存配置
     */
    public void saveConfigs() {
        try {
            equipmentConfig.save(equipmentConfigFile);
            setsConfig.save(setsConfigFile);
            runesConfig.save(runesConfigFile);
        } catch (IOException e) {
            plugin.getLogger().severe("保存裝備配置時發生錯誤: " + e.getMessage());
        }
    }

    /**
     * 重載配置
     */
    public void reload() {
        equipmentConfig = YamlConfiguration.loadConfiguration(equipmentConfigFile);
        setsConfig = YamlConfiguration.loadConfiguration(setsConfigFile);
        runesConfig = YamlConfiguration.loadConfiguration(runesConfigFile);
        loadAllData();
    }

    // Getters
    public Map<String, EquipmentData> getEquipmentTemplates() { return equipmentTemplates; }
    public Map<String, SetData> getSetData() { return setData; }
    public Map<String, RuneData> getRuneTemplates() { return runeTemplates; }
    public SetData getSetData(String setId) { return setData.get(setId); }
}
