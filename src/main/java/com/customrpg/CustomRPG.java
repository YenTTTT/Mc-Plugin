package com.customrpg;

import com.customrpg.commands.WeaponCommand;
import com.customrpg.listeners.MobListener;
import com.customrpg.listeners.WeaponListener;
import com.customrpg.listeners.SkillTriggerListener;
import com.customrpg.managers.ConfigManager;
import com.customrpg.managers.MobManager;
import com.customrpg.managers.WeaponManager;
import com.customrpg.weaponSkills.managers.SkillManager;
import com.customrpg.weaponSkills.skills.DashSkill;
import com.customrpg.weaponSkills.skills.FireNovaSkill;
import com.customrpg.weaponSkills.skills.ThornSpikeSkill;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * CustomRPG - Main plugin class
 *
 * This is the core plugin class that extends JavaPlugin and serves as the entry point
 * for the CustomRPG plugin. It handles plugin initialization, registration of managers,
 * listeners, and commands.
 *
 * Features:
 * - Custom weapons with special abilities
 * - Skill system with cooldowns
 * - Custom mob spawning and behavior
 *
 * @author CustomRPG Team
 * @version 1.0
 */
public class CustomRPG extends JavaPlugin {

    private ConfigManager configManager;
    private WeaponManager weaponManager;
    private MobManager mobManager;

    // New skill system
    private SkillManager newSkillManager;

    /**
     * Called when the plugin is enabled
     * Initializes all managers, registers listeners and commands
     */
    @Override
    public void onEnable() {
        getLogger().info("=================================");
        getLogger().info("   CustomRPG Plugin Starting");
        getLogger().info("=================================");

        // Initialize managers
        initializeManagers();

        // Register listeners
        registerListeners();

        // Register commands
        registerCommands();

        getLogger().info("CustomRPG has been enabled successfully!");
        getLogger().info("Version: " + getDescription().getVersion());
        getLogger().info("=================================");
    }

    /**
     * Called when the plugin is disabled
     * Cleanup and save data if necessary
     */
    @Override
    public void onDisable() {
        getLogger().info("=================================");
        getLogger().info("   CustomRPG Plugin Stopping");
        getLogger().info("=================================");

        // New skill system cooldowns are in-memory; stopping the plugin clears them.

        // Cleanup managers
        configManager = null;
        weaponManager = null;
        mobManager = null;
        newSkillManager = null;

        getLogger().info("CustomRPG has been disabled successfully!");
        getLogger().info("=================================");
    }

    /**
     * Initialize all plugin managers
     */
    private void initializeManagers() {
        getLogger().info("Initializing managers...");

        configManager = new ConfigManager(this);
        getLogger().info("- ConfigManager initialized");

        weaponManager = new WeaponManager(this, configManager);
        getLogger().info("- WeaponManager initialized with " + weaponManager.getWeaponCount() + " weapons");

        mobManager = new MobManager(this, configManager);
        getLogger().info("- MobManager initialized with " + mobManager.getMobTypeCount() + " custom mob types");

        // ===== New skill system (manager/service pattern) =====
        com.customrpg.weaponSkills.managers.CooldownManager cooldownManager = new com.customrpg.weaponSkills.managers.CooldownManager();
        com.customrpg.weaponSkills.managers.BuffManager buffManager = new com.customrpg.weaponSkills.managers.BuffManager();
        com.customrpg.weaponSkills.managers.DamageManager damageManager = new com.customrpg.weaponSkills.managers.DamageManager();
        com.customrpg.weaponSkills.util.AoEUtil aoeUtil = new com.customrpg.weaponSkills.util.AoEUtil();
        com.customrpg.weaponSkills.util.ParticleUtil particleUtil = new com.customrpg.weaponSkills.util.ParticleUtil();
        com.customrpg.weaponSkills.util.SoundUtil soundUtil = new com.customrpg.weaponSkills.util.SoundUtil();

        newSkillManager = new SkillManager(weaponManager, cooldownManager, damageManager, buffManager, aoeUtil, particleUtil, soundUtil);

        // register example skills
        newSkillManager.registerSkill(new DashSkill());
        newSkillManager.registerSkill(new FireNovaSkill());
        newSkillManager.registerSkill(new ThornSpikeSkill());

        // example weapon-to-skill binding (replace this with config-driven binding later)
        newSkillManager.bindWeaponSkill("iron_scythe", "thorn_spike");

        getLogger().info("- New SkillManager initialized with " + newSkillManager.getRegisteredSkillIds().size() + " skills");
    }

    /**
     * Register all event listeners
     */
    private void registerListeners() {
        getLogger().info("Registering event listeners...");

        getServer().getPluginManager().registerEvents(new WeaponListener(this, weaponManager), this);
        getLogger().info("- WeaponListener registered");

        // SkillListener (legacy) 已由 SkillTriggerListener 接管

        getServer().getPluginManager().registerEvents(new MobListener(this, mobManager), this);
        getLogger().info("- MobListener registered");

        getServer().getPluginManager().registerEvents(new SkillTriggerListener(newSkillManager), this);
        getLogger().info("- SkillTriggerListener registered");
    }

    /**
     * Register all plugin commands
     */
    private void registerCommands() {
        getLogger().info("Registering commands...");

        getCommand("weapon").setExecutor(new WeaponCommand(this, weaponManager));
        getLogger().info("- /weapon command registered");
    }

    /**
     * Get the ConfigManager instance
     * @return ConfigManager instance
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * Get the WeaponManager instance
     * @return WeaponManager instance
     */
    public WeaponManager getWeaponManager() {
        return weaponManager;
    }


    /**
     * Get the MobManager instance
     * @return MobManager instance
     */
    public MobManager getMobManager() {
        return mobManager;
    }
}
