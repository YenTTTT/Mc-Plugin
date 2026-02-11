package com.customrpg.managers;

import com.customrpg.CustomRPG;
import com.customrpg.utils.CooldownUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SkillManager - Manages all player skills and cooldowns
 *
 * This class handles skill registration, activation, and cooldown management.
 * Skills are triggered by specific items and actions (right-click, left-click).
 * Each skill has a cooldown period to prevent spam.
 *
 * Example skills:
 * - Fireball: Launch a fireball projectile
 * - Heal: Restore player health
 * - Dash: Quick forward movement
 */
public class SkillManager {

    private final CustomRPG plugin;
    private final Map<String, SkillData> skills;
    private final CooldownUtil cooldownUtil;

    /**
     * Constructor for SkillManager
     * @param plugin Main plugin instance
     */
    public SkillManager(CustomRPG plugin) {
        this.plugin = plugin;
        this.skills = new HashMap<>();
        this.cooldownUtil = new CooldownUtil();
        loadSkills();
    }

    /**
     * Load all skills from config.yml
     */
    private void loadSkills() {
        ConfigurationSection skillsSection = plugin.getConfig().getConfigurationSection("skills");
        if (skillsSection == null) {
            plugin.getLogger().warning("No skills section found in config.yml");
            return;
        }

        for (String skillKey : skillsSection.getKeys(false)) {
            ConfigurationSection skillConfig = skillsSection.getConfigurationSection(skillKey);
            if (skillConfig == null) continue;

            SkillData skillData = new SkillData(
                skillKey,
                skillConfig.getString("name", skillKey),
                Material.valueOf(skillConfig.getString("item", "STICK")),
                skillConfig.getString("activation", "RIGHT_CLICK"),
                skillConfig.getInt("cooldown", 10),
                skillConfig.getString("effect", "none")
            );

            skills.put(skillKey, skillData);
            plugin.getLogger().info("Loaded skill: " + skillData.getName());
        }
    }

    /**
     * Check if a player can use a skill (not on cooldown)
     * @param player The player
     * @param skillKey The skill identifier
     * @return true if skill can be used, false if on cooldown
     */
    public boolean canUseSkill(Player player, String skillKey) {
        SkillData skill = skills.get(skillKey);
        if (skill == null) {
            return false;
        }

        return cooldownUtil.canUse(player.getUniqueId(), skillKey);
    }

    /**
     * Use a skill and start its cooldown
     * @param player The player using the skill
     * @param skillKey The skill identifier
     * @return true if skill was used, false if on cooldown or not found
     */
    public boolean useSkill(Player player, String skillKey) {
        SkillData skill = skills.get(skillKey);
        if (skill == null) {
            return false;
        }

        if (!cooldownUtil.canUse(player.getUniqueId(), skillKey)) {
            return false;
        }

        cooldownUtil.setCooldown(player.getUniqueId(), skillKey, skill.getCooldown());
        return true;
    }

    /**
     * Get remaining cooldown time for a skill
     * @param player The player
     * @param skillKey The skill identifier
     * @return Remaining cooldown in seconds, 0 if ready
     */
    public long getRemainingCooldown(Player player, String skillKey) {
        return cooldownUtil.getRemainingCooldown(player.getUniqueId(), skillKey);
    }

    /**
     * Get skill data by key
     * @param skillKey The skill identifier
     * @return SkillData or null if not found
     */
    public SkillData getSkillData(String skillKey) {
        return skills.get(skillKey);
    }

    /**
     * Find skill by item material and activation type
     * @param material The item material
     * @param activation The activation type (RIGHT_CLICK or LEFT_CLICK)
     * @return Skill key if found, null otherwise
     */
    public String findSkillByItem(Material material, String activation) {
        for (Map.Entry<String, SkillData> entry : skills.entrySet()) {
            SkillData skill = entry.getValue();
            if (skill.getItem() == material && skill.getActivation().equals(activation)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Get the number of registered skills
     * @return Number of skills
     */
    public int getSkillCount() {
        return skills.size();
    }

    /**
     * Clear all cooldowns for all players
     */
    public void clearAllCooldowns() {
        cooldownUtil.clearAll();
    }

    /**
     * Inner class to store skill data
     */
    public static class SkillData {
        private final String key;
        private final String name;
        private final Material item;
        private final String activation;
        private final int cooldown;
        private final String effect;

        public SkillData(String key, String name, Material item, String activation, int cooldown, String effect) {
            this.key = key;
            this.name = name;
            this.item = item;
            this.activation = activation;
            this.cooldown = cooldown;
            this.effect = effect;
        }

        public String getKey() { return key; }
        public String getName() { return name; }
        public Material getItem() { return item; }
        public String getActivation() { return activation; }
        public int getCooldown() { return cooldown; }
        public String getEffect() { return effect; }
    }
}
