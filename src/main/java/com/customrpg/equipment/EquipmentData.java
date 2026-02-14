package com.customrpg.equipment;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * 裝備數據類
 * 儲存裝備的所有屬性和狀態
 */
public class EquipmentData {

    // 基本信息
    private String id;
    private String name;
    private String description;
    private Material material;
    private EquipmentSlot slot;
    private EquipmentRarity rarity;
    private int level;
    private int enhanceLevel;
    private String setId; // 套裝ID

    // 屬性系統
    private Map<EquipmentAttribute, Double> baseAttributes;
    private Map<EquipmentAttribute, Double> randomAffixes; // 隨機詞條
    private Map<EquipmentAttribute, Double> enhanceAttributes; // 強化屬性

    // 符文系統
    private List<RuneData> equippedRunes;
    private int maxRuneSlots;

    // 特殊效果
    private List<String> specialEffects;
    private Map<String, Object> effectData;

    // 需求條件
    private int requiredLevel;
    private Map<EquipmentAttribute, Integer> requiredStats;

    // 創建時間和擁有者
    private long createTime;
    private UUID originalOwner;

    public EquipmentData(String id, String name, EquipmentSlot slot, EquipmentRarity rarity, Material material) {
        this.id = id;
        this.name = name;
        this.description = "";  // 初始化為空字串
        this.slot = slot;
        this.rarity = rarity;
        this.material = material;

        this.level = 1;
        this.enhanceLevel = 0;
        this.setId = "";  // 初始化為空字串
        this.baseAttributes = new HashMap<>();
        this.randomAffixes = new HashMap<>();
        this.enhanceAttributes = new HashMap<>();
        this.equippedRunes = new ArrayList<>();
        this.maxRuneSlots = rarity.getRuneSlots();
        this.specialEffects = new ArrayList<>();
        this.effectData = new HashMap<>();
        this.requiredStats = new HashMap<>();
        this.requiredLevel = 1;  // 初始化為1
        this.createTime = System.currentTimeMillis();
    }

    /**
     * 獲取總屬性值（基礎+隨機+強化+符文）
     */
    public double getTotalAttribute(EquipmentAttribute attribute) {
        double total = 0;

        // 基礎屬性
        total += baseAttributes.getOrDefault(attribute, 0.0);

        // 隨機詞條
        total += randomAffixes.getOrDefault(attribute, 0.0);

        // 強化屬性
        total += enhanceAttributes.getOrDefault(attribute, 0.0);

        // 符文屬性
        for (RuneData rune : equippedRunes) {
            if (rune != null) {
                total += rune.getAttribute(attribute);
            }
        }

        return total;
    }

    /**
     * 獲取所有非零屬性
     */
    public Map<EquipmentAttribute, Double> getAllAttributes() {
        Map<EquipmentAttribute, Double> allAttributes = new HashMap<>();

        for (EquipmentAttribute attr : EquipmentAttribute.values()) {
            double value = getTotalAttribute(attr);
            if (value != 0) {
                allAttributes.put(attr, value);
            }
        }

        return allAttributes;
    }

    /**
     * 強化裝備
     */
    public boolean enhance() {
        int maxLevel = rarity.getMaxEnhanceLevel();
        if (enhanceLevel >= maxLevel) {
            return false;
        }

        enhanceLevel++;
        calculateEnhanceAttributes();
        return true;
    }

    /**
     * 計算強化屬性
     */
    private void calculateEnhanceAttributes() {
        enhanceAttributes.clear();

        for (Map.Entry<EquipmentAttribute, Double> entry : baseAttributes.entrySet()) {
            EquipmentAttribute attr = entry.getKey();
            double baseValue = entry.getValue();

            // 強化每級增加基礎屬性的10%
            double enhanceValue = baseValue * enhanceLevel * 0.1;
            if (enhanceValue > 0) {
                enhanceAttributes.put(attr, enhanceValue);
            }
        }
    }

    /**
     * 添加隨機詞條
     */
    public void generateRandomAffixes() {
        randomAffixes.clear();
        int affixCount = rarity.getRandomAffixCount();

        if (affixCount <= 0) return;

        Random random = new Random();
        List<EquipmentAttribute> availableAttributes = new ArrayList<>(Arrays.asList(EquipmentAttribute.values()));

        // 移除已有基礎屬性，避免重複
        availableAttributes.removeAll(baseAttributes.keySet());

        for (int i = 0; i < affixCount && !availableAttributes.isEmpty(); i++) {
            EquipmentAttribute attr = availableAttributes.remove(random.nextInt(availableAttributes.size()));
            double value = generateRandomValue(attr);
            randomAffixes.put(attr, value);
        }
    }

    /**
     * 生成隨機屬性值
     */
    private double generateRandomValue(EquipmentAttribute attribute) {
        Random random = new Random();

        // 根據屬性類型生成不同範圍的隨機值
        if (attribute.isBasicAttribute()) {
            return 5 + random.nextInt(15) * level; // 5-20 * level
        } else if (attribute == EquipmentAttribute.CRITICAL_CHANCE ||
                   attribute == EquipmentAttribute.EVASION ||
                   attribute == EquipmentAttribute.ACCURACY) {
            return 1 + random.nextDouble() * 8; // 1%-9%
        } else if (attribute == EquipmentAttribute.MAX_HEALTH) {
            return 20 + random.nextInt(80) * level; // 20-100 * level
        } else if (attribute == EquipmentAttribute.MAX_MANA) {
            return 10 + random.nextInt(40) * level; // 10-50 * level
        } else {
            return 1 + random.nextInt(9) * level; // 1-10 * level
        }
    }

    /**
     * 裝備符文
     */
    public boolean equipRune(RuneData rune, int slot) {
        if (slot < 0 || slot >= maxRuneSlots) {
            return false;
        }

        // 確保列表大小足夠
        while (equippedRunes.size() <= slot) {
            equippedRunes.add(null);
        }

        equippedRunes.set(slot, rune);
        return true;
    }

    /**
     * 移除符文
     */
    public RuneData removeRune(int slot) {
        if (slot < 0 || slot >= equippedRunes.size()) {
            return null;
        }

        RuneData removed = equippedRunes.get(slot);
        equippedRunes.set(slot, null);
        return removed;
    }

    /**
     * 轉換為ItemStack
     */
    public ItemStack toItemStack() {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // 設置名稱
            String displayName = rarity.getColorCode() + name;
            if (enhanceLevel > 0) {
                displayName += " §7[+" + enhanceLevel + "]";
            }
            meta.setDisplayName(displayName);

            // 設置描述
            List<String> lore = new ArrayList<>();

            // 基本信息
            lore.add("§8" + slot.getDisplayName() + " | " + rarity.getDisplayName());
            if (description != null && !description.isEmpty()) {
                lore.add("§7" + description);
            }
            lore.add("");

            // 屬性列表
            Map<EquipmentAttribute, Double> allAttrs = getAllAttributes();
            if (!allAttrs.isEmpty()) {
                lore.add("§6屬性:");
                for (Map.Entry<EquipmentAttribute, Double> entry : allAttrs.entrySet()) {
                    EquipmentAttribute attr = entry.getKey();
                    double value = entry.getValue();
                    String valueStr = attr.formatValue(value);
                    lore.add("§7" + attr.getColoredDisplayName() + ": §f+" + valueStr);
                }
                lore.add("");
            }

            // 套裝信息
            if (setId != null && !setId.isEmpty()) {
                lore.add("§d套裝: §5" + setId);
                lore.add("");
            }

            // 符文信息
            if (!equippedRunes.isEmpty() || maxRuneSlots > 0) {
                lore.add("§9符文槽位 §7(" + getEquippedRuneCount() + "/" + maxRuneSlots + "):");
                for (int i = 0; i < maxRuneSlots; i++) {
                    if (i < equippedRunes.size() && equippedRunes.get(i) != null) {
                        RuneData rune = equippedRunes.get(i);
                        lore.add("§7" + (i + 1) + ". " + rune.getName());
                    } else {
                        lore.add("§8" + (i + 1) + ". 空槽位");
                    }
                }
                lore.add("");
            }

            // 需求條件
            if (requiredLevel > 1) {
                lore.add("§c需求等級: " + requiredLevel);
            }

            // 特殊效果
            if (!specialEffects.isEmpty()) {
                lore.add("§a特殊效果:");
                for (String effect : specialEffects) {
                    lore.add("§7- " + effect);
                }
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * 獲取已裝備符文數量
     */
    public int getEquippedRuneCount() {
        int count = 0;
        for (RuneData rune : equippedRunes) {
            if (rune != null) count++;
        }
        return count;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Material getMaterial() { return material; }
    public void setMaterial(Material material) { this.material = material; }

    public EquipmentSlot getSlot() { return slot; }
    public void setSlot(EquipmentSlot slot) { this.slot = slot; }

    public EquipmentRarity getRarity() { return rarity; }
    public void setRarity(EquipmentRarity rarity) { this.rarity = rarity; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public int getEnhanceLevel() { return enhanceLevel; }
    public void setEnhanceLevel(int enhanceLevel) {
        this.enhanceLevel = enhanceLevel;
        calculateEnhanceAttributes();
    }

    public String getSetId() { return setId; }
    public void setSetId(String setId) { this.setId = setId; }

    public Map<EquipmentAttribute, Double> getBaseAttributes() { return baseAttributes; }
    public void setBaseAttributes(Map<EquipmentAttribute, Double> baseAttributes) {
        this.baseAttributes = baseAttributes;
    }

    public Map<EquipmentAttribute, Double> getRandomAffixes() { return randomAffixes; }
    public void setRandomAffixes(Map<EquipmentAttribute, Double> randomAffixes) {
        this.randomAffixes = randomAffixes;
    }

    public List<RuneData> getEquippedRunes() { return equippedRunes; }
    public void setEquippedRunes(List<RuneData> equippedRunes) { this.equippedRunes = equippedRunes; }

    public int getMaxRuneSlots() { return maxRuneSlots; }
    public void setMaxRuneSlots(int maxRuneSlots) { this.maxRuneSlots = maxRuneSlots; }

    public List<String> getSpecialEffects() { return specialEffects; }
    public void setSpecialEffects(List<String> specialEffects) { this.specialEffects = specialEffects; }

    public int getRequiredLevel() { return requiredLevel; }
    public void setRequiredLevel(int requiredLevel) { this.requiredLevel = requiredLevel; }

    public Map<EquipmentAttribute, Integer> getRequiredStats() { return requiredStats; }
    public void setRequiredStats(Map<EquipmentAttribute, Integer> requiredStats) {
        this.requiredStats = requiredStats;
    }

    public long getCreateTime() { return createTime; }
    public void setCreateTime(long createTime) { this.createTime = createTime; }

    public UUID getOriginalOwner() { return originalOwner; }
    public void setOriginalOwner(UUID originalOwner) { this.originalOwner = originalOwner; }
}
