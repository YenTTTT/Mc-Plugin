package com.customrpg.equipment;

import com.customrpg.CustomRPG;
import com.customrpg.players.PlayerStats;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

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

    // 保管欄位的實體物品 - UUID -> Slot -> ItemStack（寫入 data/equipment/<uuid>.yml）
    private final Map<UUID, Map<EquipmentSlot, ItemStack>> storedItems = new ConcurrentHashMap<>();
    private final Set<UUID> equipmentLoadFailed = ConcurrentHashMap.newKeySet();

    // 配置文件
    private File equipmentConfigFile;
    private FileConfiguration equipmentConfig;
    private File accessoriesConfigFile;
    private FileConfiguration accessoriesConfig;
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
        // 創建配置目錄 - 使用 config/equipment 目錄
        File configDir = new File(plugin.getDataFolder(), "config/equipment");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }

        // 裝備配置 - 使用 armors.yml
        equipmentConfigFile = new File(configDir, "armors.yml");
        if (!equipmentConfigFile.exists()) {
            plugin.saveResource("config/equipment/armors.yml", false);
        }
        equipmentConfig = YamlConfiguration.loadConfiguration(equipmentConfigFile);

        // 飾品配置 - accessories.yml
        accessoriesConfigFile = new File(configDir, "accessories.yml");
        if (!accessoriesConfigFile.exists()) {
            plugin.saveResource("config/equipment/accessories.yml", false);
        }
        accessoriesConfig = YamlConfiguration.loadConfiguration(accessoriesConfigFile);

        // 套裝配置 - 也放在 config/equipment 目錄
        setsConfigFile = new File(configDir, "sets.yml");
        if (!setsConfigFile.exists()) {
            // 嘗試從 equipment/sets.yml 複製
            try {
                plugin.saveResource("equipment/sets.yml", false);
                // saveResource 會把檔案存到 equipment/sets.yml，需要手動複製到目標位置
                File srcFile = new File(plugin.getDataFolder(), "equipment/sets.yml");
                if (srcFile.exists()) {
                    java.nio.file.Files.copy(srcFile.toPath(), setsConfigFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("無法複製套裝配置: " + e.getMessage());
            }
        }
        setsConfig = YamlConfiguration.loadConfiguration(setsConfigFile);

        // 符文配置 - 也放在 config/equipment 目錄
        runesConfigFile = new File(configDir, "runes.yml");
        if (!runesConfigFile.exists()) {
            // 嘗試從 equipment/runes.yml 複製
            try {
                plugin.saveResource("equipment/runes.yml", false);
                // saveResource 會把檔案存到 equipment/runes.yml，需要手動複製到目標位置
                File srcFile = new File(plugin.getDataFolder(), "equipment/runes.yml");
                if (srcFile.exists()) {
                    java.nio.file.Files.copy(srcFile.toPath(), runesConfigFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("無法複製符文配置: " + e.getMessage());
            }
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

        // 載入護甲 (armors.yml)
        ConfigurationSection equipmentSection = equipmentConfig.getConfigurationSection("armors");
        if (equipmentSection != null) {
            plugin.getLogger().info("開始從 armors.yml 載入裝備...");
            for (String equipId : equipmentSection.getKeys(false)) {
                try {
                    EquipmentData equipment = loadEquipmentFromConfig(equipId, equipmentSection.getConfigurationSection(equipId));
                    if (equipment != null) {
                        equipmentTemplates.put(equipId, equipment);
                        plugin.getLogger().info("成功載入裝備: " + equipId + " - " + equipment.getName());
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("載入裝備 " + equipId + " 時發生錯誤: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } else {
            plugin.getLogger().warning("警告: 在 armors.yml 中找不到 'armors' 配置區段！");
        }

        // 載入飾品 (accessories.yml)
        if (accessoriesConfig != null) {
            ConfigurationSection accessoriesSection = accessoriesConfig.getConfigurationSection("accessories");
            if (accessoriesSection != null) {
                for (String accId : accessoriesSection.getKeys(false)) {
                    try {
                        EquipmentData accessory = loadEquipmentFromConfig(accId, accessoriesSection.getConfigurationSection(accId));
                        if (accessory != null) {
                            equipmentTemplates.put(accId, accessory);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("載入飾品 " + accId + " 時發生錯誤: " + e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * 從配置載入裝備數據
     */
    private EquipmentData loadEquipmentFromConfig(String id, ConfigurationSection section) {
        if (section == null) {
            plugin.getLogger().warning("裝備 " + id + " 的配置區段為 null");
            return null;
        }

        String name = section.getString("name", id);
        String description = section.getString("description", "");
        String materialStr = section.getString("material", "STONE");
        String slotStr = section.getString("slot", "HELMET");
        String rarityStr = section.getString("rarity", "COMMON");

        plugin.getLogger().info("  載入裝備 " + id + ": name=" + name + ", material=" + materialStr + ", slot=" + slotStr);

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
                plugin.getLogger().info("    載入屬性:");
                for (String attrKey : attributesSection.getKeys(false)) {
                    EquipmentAttribute attr = EquipmentAttribute.fromName(attrKey.toUpperCase());
                    if (attr != null) {
                        double value = attributesSection.getDouble(attrKey);
                        attributes.put(attr, value);
                        plugin.getLogger().info("      " + attrKey + " = " + value + " -> " + attr.name());
                    } else {
                        plugin.getLogger().warning("      無法識別屬性: " + attrKey);
                    }
                }
                equipment.setBaseAttributes(attributes);
            } else {
                plugin.getLogger().info("    沒有屬性配置");
            }

            // 載入特殊效果
            List<String> effects = section.getStringList("special_effects");
            equipment.setSpecialEffects(effects);

            return equipment;

        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("裝備 " + id + " 配置錯誤: " + e.getMessage());
            e.printStackTrace();
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
                                EquipmentAttribute attr = EquipmentAttribute.fromName(attrKey.toUpperCase());
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
                    EquipmentAttribute attr = EquipmentAttribute.fromName(attrKey.toUpperCase());
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

    // ===== 物品識別 =====

    private static boolean slotMatches(EquipmentSlot templateSlot, EquipmentSlot slot) {
        if (templateSlot == slot) return true;
        return (templateSlot == EquipmentSlot.RING_1 || templateSlot == EquipmentSlot.RING_2)
                && (slot == EquipmentSlot.RING_1 || slot == EquipmentSlot.RING_2);
    }

    private static String stripName(String name) {
        String stripped = org.bukkit.ChatColor.stripColor(name);
        return stripped == null ? "" : stripped.replaceAll("\\s*\\[\\+\\d+]$", "").trim();
    }

    /**
     * 依物品 PDC 中的模板 ID 找出裝備模板。
     * 舊版物品（沒有 ID）只在顯示名稱完全相同且帶有 lore 時才接受：鐵砧只能改名、無法加入 lore，
     * 因此改名的原版物品不會被當成自訂裝備。
     */
    public EquipmentData findTemplate(ItemStack item, EquipmentSlot slot) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        String id = meta.getPersistentDataContainer().get(EquipmentData.PDC_EQUIPMENT_ID, PersistentDataType.STRING);
        if (id != null) {
            EquipmentData template = equipmentTemplates.get(id);
            return template != null && template.getMaterial() == item.getType()
                    && slotMatches(template.getSlot(), slot) ? template : null;
        }
        if (!meta.hasDisplayName() || !meta.hasLore()) return null;
        String itemName = stripName(meta.getDisplayName());
        for (EquipmentData template : equipmentTemplates.values()) {
            if (template.getMaterial() == item.getType() && slotMatches(template.getSlot(), slot)
                    && itemName.equals(stripName(template.getName()))) {
                return template;
            }
        }
        return null;
    }

    /**
     * 將物品轉換為裝備數據：自訂裝備使用模板（並還原強化等級），其他物品給予原版材質的基本屬性。
     */
    public EquipmentData toEquipmentData(ItemStack item, EquipmentSlot slot) {
        if (item == null || item.getType() == Material.AIR) return null;
        EquipmentData template = findTemplate(item, slot);
        EquipmentData equipment;
        if (template != null) {
            equipment = createEquipment(template.getId());
            equipment.setSlot(slot);
        } else {
            equipment = createBasicEquipment(item.getType(), slot);
        }
        Integer enhance = item.hasItemMeta() ? item.getItemMeta().getPersistentDataContainer()
                .get(EquipmentData.PDC_ENHANCE_LEVEL, PersistentDataType.INTEGER) : null;
        for (int i = 0; enhance != null && i < enhance; i++) {
            if (!equipment.enhance()) break;
        }
        return equipment;
    }

    private EquipmentData createBasicEquipment(Material material, EquipmentSlot slot) {
        EquipmentData equipment = new EquipmentData(
                "minecraft_" + material.name().toLowerCase(), "§f" + material.name().replace("_", " "),
                slot, EquipmentRarity.COMMON, material);
        Map<EquipmentAttribute, Double> attributes = new HashMap<>();
        String materialName = material.name();
        if (materialName.contains("DIAMOND")) {
            attributes.put(EquipmentAttribute.ATTACK_DAMAGE, 8.0);
            attributes.put(EquipmentAttribute.DEFENSE, 3.0);
        } else if (materialName.contains("IRON")) {
            attributes.put(EquipmentAttribute.ATTACK_DAMAGE, 6.0);
            attributes.put(EquipmentAttribute.DEFENSE, 2.0);
        } else if (materialName.contains("GOLD")) {
            attributes.put(EquipmentAttribute.ATTACK_DAMAGE, 4.0);
            attributes.put(EquipmentAttribute.LUCK, 2.0);
        } else {
            attributes.put(EquipmentAttribute.ATTACK_DAMAGE, 3.0);
        }
        equipment.setBaseAttributes(attributes);
        return equipment;
    }

    /**
     * 是否符合裝備需求（等級）；不符合時通知玩家
     */
    public boolean meetsRequirements(Player player, EquipmentData equipment) {
        return canEquip(player, equipment);
    }

    // ===== 實體物品（避免複製／遺失） =====

    /**
     * 非原版欄位（肩甲、披風、飾品、護符、腰帶）的實體物品由插件保管並寫入檔案；
     * 護甲欄位的實體物品在原版護甲槽；手持欄位只由主手／副手自動同步，不可透過 GUI 放入或取出。
     */
    public static boolean isStoredSlot(EquipmentSlot slot) {
        return !slot.isArmor() && !slot.isWeapon();
    }

    private Map<EquipmentSlot, ItemStack> getStoredItems(UUID playerUUID) {
        return storedItems.computeIfAbsent(playerUUID, k -> new EnumMap<>(EquipmentSlot.class));
    }

    /**
     * 以實際物品裝備到 GUI 可管理的欄位（護甲或保管欄位），保留物品原有 NBT（附魔、耐久、強化）。
     *
     * @return 原本在該欄位的實體物品（可能為 null），由呼叫端歸還給玩家；裝備失敗時回傳 source 本身不變且 success=false
     */
    public EquipResult equipFromItem(Player player, EquipmentSlot slot, ItemStack source) {
        if (slot.isWeapon() || source == null || source.getType() == Material.AIR) {
            return EquipResult.failed();
        }
        if (isStoredSlot(slot) && equipmentLoadFailed.contains(player.getUniqueId())) {
            player.sendMessage("§c裝備存檔讀取失敗，暫時無法變更保管欄位，請聯絡管理員。");
            return EquipResult.failed();
        }
        EquipmentData equipment = toEquipmentData(source, slot);
        if (equipment == null || !canEquip(player, equipment)) {
            return EquipResult.failed();
        }
        ItemStack single = source.clone();
        single.setAmount(1);

        ItemStack previous = takeBackingItem(player, slot);
        if (slot.isArmor()) {
            setArmorSlot(player, slot, single);
        } else {
            getStoredItems(player.getUniqueId()).put(slot, single);
        }
        getPlayerEquipment(player.getUniqueId()).put(slot, equipment);
        updatePlayerAttributes(player);
        return new EquipResult(true, previous, equipment);
    }

    /**
     * 卸除 GUI 可管理欄位的裝備，回傳實際的物品（不是重新產生的複本）。手持欄位不可卸除。
     */
    public ItemStack unequipToItem(Player player, EquipmentSlot slot) {
        if (slot.isWeapon()) {
            return null;
        }
        ItemStack item = takeBackingItem(player, slot);
        if (getPlayerEquipment(player.getUniqueId()).remove(slot) != null || item != null) {
            updatePlayerAttributes(player);
        }
        return item;
    }

    private ItemStack takeBackingItem(Player player, EquipmentSlot slot) {
        if (slot.isArmor()) {
            ItemStack current = getArmorSlot(player, slot);
            setArmorSlot(player, slot, null);
            return current == null || current.getType() == Material.AIR ? null : current;
        }
        Map<EquipmentSlot, ItemStack> stored = storedItems.get(player.getUniqueId());
        return stored == null ? null : stored.remove(slot);
    }

    private ItemStack getArmorSlot(Player player, EquipmentSlot slot) {
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        return switch (slot) {
            case HELMET -> inv.getHelmet();
            case CHESTPLATE -> inv.getChestplate();
            case LEGGINGS -> inv.getLeggings();
            case BOOTS -> inv.getBoots();
            default -> null;
        };
    }

    private void setArmorSlot(Player player, EquipmentSlot slot, ItemStack item) {
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        switch (slot) {
            case HELMET -> inv.setHelmet(item);
            case CHESTPLATE -> inv.setChestplate(item);
            case LEGGINGS -> inv.setLeggings(item);
            case BOOTS -> inv.setBoots(item);
            default -> { }
        }
    }

    /**
     * 強化成功後把強化等級寫回實際物品，讓結果在重新同步、重登與重啟後保留。
     */
    public void persistEnhancement(Player player, EquipmentSlot slot, EquipmentData equipment) {
        ItemStack item;
        if (slot.isArmor()) {
            item = getArmorSlot(player, slot);
        } else if (slot == EquipmentSlot.MAIN_HAND) {
            item = player.getInventory().getItemInMainHand();
        } else if (slot == EquipmentSlot.OFF_HAND) {
            item = player.getInventory().getItemInOffHand();
        } else {
            Map<EquipmentSlot, ItemStack> stored = storedItems.get(player.getUniqueId());
            item = stored == null ? null : stored.get(slot);
        }
        if (item != null && item.getType() != Material.AIR) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(EquipmentData.PDC_ENHANCE_LEVEL,
                        PersistentDataType.INTEGER, equipment.getEnhanceLevel());
                if (meta.hasDisplayName()) {
                    meta.setDisplayName(meta.getDisplayName().replaceAll("\\s*§7\\[\\+\\d+]$", "")
                            + " §7[+" + equipment.getEnhanceLevel() + "]");
                }
                item.setItemMeta(meta);
            }
        }
        updatePlayerAttributes(player);
    }

    // ===== 保管欄位存檔 =====

    private File getEquipmentFile(UUID playerUUID) {
        File folder = new File(plugin.getDataFolder(), "data/equipment");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return new File(folder, playerUUID + ".yml");
    }

    /**
     * 玩家登入時載入保管欄位的實體物品並重建裝備數據。
     */
    public void loadPlayerEquipment(Player player) {
        UUID uuid = player.getUniqueId();
        // A failed save on logout leaves the authoritative items in memory for a later retry.
        if (storedItems.containsKey(uuid)) {
            updatePlayerAttributes(player);
            return;
        }
        File file = getEquipmentFile(uuid);
        if (!file.exists()) {
            equipmentLoadFailed.remove(uuid);
            return;
        }
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException e) {
            equipmentLoadFailed.add(uuid);
            plugin.getLogger().severe("無法讀取玩家裝備存檔 " + file.getName() + "，本次不會覆寫: " + e.getMessage());
            return;
        }
        Map<EquipmentSlot, ItemStack> loadedItems = new EnumMap<>(EquipmentSlot.class);
        Map<EquipmentSlot, EquipmentData> loadedEquipment = new EnumMap<>(EquipmentSlot.class);
        try {
            ConfigurationSection section = config.getConfigurationSection("stored");
            if (config.contains("stored") && section == null) {
                throw new IllegalArgumentException("stored must be a section");
            }
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    EquipmentSlot slot = EquipmentSlot.fromConfigKey(key);
                    ItemStack item = section.getItemStack(key);
                    if (slot == null || !isStoredSlot(slot) || item == null || item.getType() == Material.AIR) {
                        throw new IllegalArgumentException("Invalid stored item: " + key);
                    }
                    loadedItems.put(slot, item);
                    EquipmentData data = toEquipmentData(item, slot);
                    if (data != null) {
                        loadedEquipment.put(slot, data);
                    }
                }
            }
        } catch (RuntimeException e) {
            equipmentLoadFailed.add(uuid);
            plugin.getLogger().severe("無法還原玩家裝備 " + uuid + "，保留原始存檔且禁止存入: " + e.getMessage());
            return;
        }
        equipmentLoadFailed.remove(uuid);
        storedItems.put(uuid, loadedItems);
        getPlayerEquipment(uuid).putAll(loadedEquipment);
        updatePlayerAttributes(player);
    }

    public void savePlayerEquipment(UUID uuid) {
        trySavePlayerEquipment(uuid);
    }

    private boolean trySavePlayerEquipment(UUID uuid) {
        if (equipmentLoadFailed.contains(uuid)) {
            plugin.getLogger().warning("略過儲存玩家裝備 " + uuid + "：原始存檔讀取失敗，避免覆寫");
            return false;
        }
        Map<EquipmentSlot, ItemStack> stored = storedItems.get(uuid);
        File file = getEquipmentFile(uuid);
        if ((stored == null || stored.isEmpty()) && !file.exists()) {
            return true;
        }
        YamlConfiguration config = new YamlConfiguration();
        if (stored != null) {
            for (Map.Entry<EquipmentSlot, ItemStack> entry : stored.entrySet()) {
                config.set("stored." + entry.getKey().getConfigKey(), entry.getValue());
            }
        }
        try {
            com.customrpg.managers.PlayerStatsManager.saveAtomically(config, file);
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("無法儲存玩家裝備 " + uuid + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * 玩家離線：儲存並釋放記憶體。
     */
    public void unloadPlayerEquipment(UUID uuid) {
        if (!trySavePlayerEquipment(uuid)) {
            Map<EquipmentSlot, EquipmentData> equipment = playerEquipment.get(uuid);
            if (equipment != null) {
                equipment.keySet().removeIf(slot -> !isStoredSlot(slot));
            }
            return;
        }
        storedItems.remove(uuid);
        playerEquipment.remove(uuid);
        equipmentLoadFailed.remove(uuid);
    }

    public void saveAllPlayerEquipment() {
        for (UUID uuid : new ArrayList<>(storedItems.keySet())) {
            savePlayerEquipment(uuid);
        }
    }

    public record EquipResult(boolean success, ItemStack previousItem, EquipmentData equipment) {
        static EquipResult failed() {
            return new EquipResult(false, null, null);
        }
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
    public void syncToMinecraftEquipment(Player player, EquipmentSlot slot, EquipmentData equipment) {
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
    public void updatePlayerAttributes(Player player) {
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
        stats.setEquipmentSpirit(0);

        stats.setBonusAttackDamage(0);
        stats.setBonusDefenseValue(0);
        stats.setBonusMaxHealth(0);
        stats.setBonusCritChance(0);
        stats.setBonusCritDamage(0);
        stats.setBonusMaxMana(0);
        stats.setBonusManaRegen(0);

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
                case SPIRIT:
                    stats.setEquipmentSpirit((int) value);
                    break;
                case ATTACK_DAMAGE:
                    stats.setBonusAttackDamage(value);
                    break;
                case MAX_HEALTH:
                    // 將 MAX_HEALTH 轉換為 VITALITY (除以 HP_PER_VITALITY = 2.0)
                    // 例如：+100 MAX_HEALTH = +50 VITALITY
                    int vitalityEquivalent = (int) Math.ceil(value / 2.0);
                    stats.setEquipmentVitality(stats.getEquipmentVitality() + vitalityEquivalent);
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
                case MAX_MANA:  // 最大魔力加成
                    stats.setBonusMaxMana(stats.getBonusMaxMana() + value);
                    break;
                case MANA_REGEN:  // 魔力回復加成
                    stats.setBonusManaRegen(stats.getBonusManaRegen() + value);
                    break;
                // 其他屬性可以根據需要添加
            }
        }

        // 更新最大血量（如果有生命力加成）
        plugin.getPlayerStatsManager().updateMaxHealth(player);
        plugin.getPlayerStatsManager().recalculateMana(player);

        // 保存數據
        plugin.getPlayerStatsManager().saveStats(player);

        // 調試信息
        plugin.getLogger().info("玩家 " + player.getName() + " 的裝備屬性已更新:");
        plugin.getLogger().info("  力量: +" + stats.getEquipmentStrength());
        plugin.getLogger().info("  魔法: +" + stats.getEquipmentMagic());
        plugin.getLogger().info("  敏捷: +" + stats.getEquipmentAgility());
        plugin.getLogger().info("  生命力: +" + stats.getEquipmentVitality() + " (包含從MAX_HEALTH轉換的體力)");
        plugin.getLogger().info("  防禦: +" + stats.getEquipmentDefense());
        plugin.getLogger().info("  總力量: " + stats.getTotalStrength());
        plugin.getLogger().info("  總魔法: " + stats.getTotalMagic());
        plugin.getLogger().info("  總生命力: " + stats.getTotalVitality());
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
        equipment.setArmorTrimPattern(template.getArmorTrimPattern());
        equipment.setArmorTrimMaterial(template.getArmorTrimMaterial());

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
        if (accessoriesConfigFile != null && accessoriesConfigFile.exists()) {
            accessoriesConfig = YamlConfiguration.loadConfiguration(accessoriesConfigFile);
        }
        setsConfig = YamlConfiguration.loadConfiguration(setsConfigFile);
        runesConfig = YamlConfiguration.loadConfiguration(runesConfigFile);
        loadAllData();
    }

    // Getters
    public Map<String, EquipmentData> getEquipmentTemplates() { return equipmentTemplates; }
    public Map<String, SetData> getSetData() { return setData; }
    public Map<String, RuneData> getRuneTemplates() { return runeTemplates; }
    public SetData getSetData(String setId) { return setData.get(setId); }

    /**
     * 玩家升級後，掃描背包和裝備槽中的所有裝備物品，
     * 動態加入或移除「需求等級」lore，使其與玩家當前等級同步。
     *
     * @param player   目標玩家
     * @param newLevel 玩家升級後的新等級
     */
    public void refreshRequiredLevelLore(Player player, int newLevel) {
        // 收集所有需要檢查的 ItemStack（背包 + 盔甲欄）
        List<ItemStack> allItems = new ArrayList<>();
        allItems.addAll(Arrays.asList(player.getInventory().getContents()));
        allItems.addAll(Arrays.asList(player.getInventory().getArmorContents()));

        boolean inventoryChanged = false;

        for (ItemStack item : allItems) {
            if (item == null || item.getType() == Material.AIR) continue;
            if (!item.hasItemMeta()) continue;

            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;

            // 只處理有 equipment_required_level PDC 的物品
            Integer requiredLevel = meta.getPersistentDataContainer()
                    .get(EquipmentData.PDC_REQUIRED_LEVEL, PersistentDataType.INTEGER);
            if (requiredLevel == null || requiredLevel <= 1) continue;

            List<String> lore = meta.getLore();
            if (lore == null) lore = new ArrayList<>();

            String loreLine = "§c需求等級: " + requiredLevel;
            boolean hasLine = lore.contains(loreLine);

            if (newLevel >= requiredLevel && hasLine) {
                // 玩家已達到需求等級 → 移除 lore
                lore.remove(loreLine);
                meta.setLore(lore);
                item.setItemMeta(meta);
                inventoryChanged = true;
            } else if (newLevel < requiredLevel && !hasLine) {
                // 玩家低於需求等級但 lore 不見了 → 補回來
                lore.add(loreLine);
                meta.setLore(lore);
                item.setItemMeta(meta);
                inventoryChanged = true;
            }
        }

        if (inventoryChanged) {
            player.updateInventory();
        }
    }
}
