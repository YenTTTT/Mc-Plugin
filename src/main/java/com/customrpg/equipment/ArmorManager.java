package com.customrpg.equipment;

import com.customrpg.CustomRPG;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 裝甲管理器
 *
 * 負責：
 * - 載入裝甲配置
 * - 管理裝甲模板
 * - 創建裝甲實例
 * - 儲存玩家裝甲數據
 */
public class ArmorManager {

    private final CustomRPG plugin;
    private final Map<String, ArmorData> armorTemplates;
    private final File armorConfigFile;
    private FileConfiguration armorConfig;

    public ArmorManager(CustomRPG plugin) {
        this.plugin = plugin;
        this.armorTemplates = new ConcurrentHashMap<>();

        // 初始化配置文件
        File configDir = new File(plugin.getDataFolder(), "config/equipment");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }

        this.armorConfigFile = new File(configDir, "armors.yml");
        initializeConfig();
        loadArmorTemplates();
    }

    /**
     * 初始化配置文件
     */
    private void initializeConfig() {
        if (!armorConfigFile.exists()) {
            try {
                // 嘗試從資源文件複製
                plugin.saveResource("config/equipment/armors.yml", false);
                plugin.getLogger().info("已從資源文件創建裝甲配置");
            } catch (Exception e) {
                // 如果資源文件不存在，記錄錯誤
                plugin.getLogger().severe("無法從資源複製配置文件: " + e.getMessage());
                armorConfig = new YamlConfiguration();
                return;
            }
        }
        armorConfig = YamlConfiguration.loadConfiguration(armorConfigFile);
    }

    /**
     * 載入裝甲模板
     */
    public void loadArmorTemplates() {
        armorTemplates.clear();

        ConfigurationSection armorsSection = armorConfig.getConfigurationSection("armors");
        if (armorsSection == null) {
            plugin.getLogger().warning("未找到裝甲配置區段！");
            return;
        }

        for (String armorId : armorsSection.getKeys(false)) {
            try {
                ArmorData armor = loadArmorFromConfig(armorId, armorsSection.getConfigurationSection(armorId));
                if (armor != null) {
                    armorTemplates.put(armorId, armor);
                    plugin.getLogger().info("載入裝甲: " + armorId);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("載入裝甲 " + armorId + " 時發生錯誤: " + e.getMessage());
            }
        }

        plugin.getLogger().info("裝甲管理器載入完成，共 " + armorTemplates.size() + " 件裝甲");
    }

    /**
     * 從配置載入裝甲
     */
    private ArmorData loadArmorFromConfig(String id, ConfigurationSection section) {
        if (section == null) return null;

        String name = section.getString("name", id);
        String typeStr = section.getString("type", "LIGHT");
        String slotStr = section.getString("slot", "CHESTPLATE");
        String materialStr = section.getString("material", "LEATHER_CHESTPLATE");

        try {
            ArmorType armorType = ArmorType.valueOf(typeStr.toUpperCase());
            EquipmentSlot slot = EquipmentSlot.valueOf(slotStr.toUpperCase());
            Material material = Material.valueOf(materialStr.toUpperCase());

            ArmorData armor = new ArmorData(id, name, armorType, slot, material);

            // 需求
            armor.setRequiredLevel(section.getInt("required_level", 1));
            String requiredClassStr = section.getString("required_class", "NONE");
            armor.setRequiredClass(PlayerClass.valueOf(requiredClassStr.toUpperCase()));

            // 防禦
            armor.setPhysicalDefense(section.getDouble("physical_defense", 0));
            armor.setMagicalDefense(section.getDouble("magical_defense", 0));

            // 屬性加成
            ConfigurationSection attributesSection = section.getConfigurationSection("attributes");
            if (attributesSection != null) {
                for (String attrKey : attributesSection.getKeys(false)) {
                    try {
                        EquipmentAttribute attr = EquipmentAttribute.fromShortName(attrKey.toUpperCase());
                        if (attr != null) {
                            double value = attributesSection.getDouble(attrKey);
                            armor.setAttribute(attr, value);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("載入屬性 " + attrKey + " 失敗: " + e.getMessage());
                    }
                }
            }

            // 其他屬性
            armor.setDescription(section.getString("description", ""));
            String rarityStr = section.getString("rarity", "COMMON");
            armor.setRarity(EquipmentRarity.valueOf(rarityStr.toUpperCase()));
            armor.setMaxDurability(section.getInt("max_durability", 100));

            return armor;

        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("裝甲 " + id + " 配置錯誤: " + e.getMessage());
            return null;
        }
    }

    /**
     * 創建裝甲實例
     */
    public ArmorData createArmor(String templateId) {
        ArmorData template = armorTemplates.get(templateId);
        if (template == null) {
            plugin.getLogger().warning("未找到裝甲模板: " + templateId);
            return null;
        }
        return template.copy();
    }

    /**
     * 獲取裝甲模板
     */
    public ArmorData getArmorTemplate(String id) {
        return armorTemplates.get(id);
    }

    /**
     * 獲取所有裝甲模板
     */
    public Map<String, ArmorData> getArmorTemplates() {
        return new HashMap<>(armorTemplates);
    }

    /**
     * 獲取所有裝甲ID
     */
    public List<String> getArmorIds() {
        return new ArrayList<>(armorTemplates.keySet());
    }

    /**
     * 重新載入配置
     */
    public void reload() {
        armorConfig = YamlConfiguration.loadConfiguration(armorConfigFile);
        loadArmorTemplates();
    }
}


