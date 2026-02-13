package com.customrpg;

import com.customrpg.commands.StatsCommand;
import com.customrpg.commands.StatsShortcutCommand;
import com.customrpg.commands.WeaponCommand;
import com.customrpg.gui.StatsGUI;
import com.customrpg.listeners.MobListener;
import com.customrpg.listeners.StatsListener;
import com.customrpg.listeners.WeaponListener;
import com.customrpg.listeners.SkillTriggerListener;
import com.customrpg.managers.ConfigManager;
import com.customrpg.managers.MobManager;
import com.customrpg.managers.PlayerStatsManager;
import com.customrpg.managers.WeaponManager;
import com.customrpg.weaponSkills.managers.SkillManager;
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
    private PlayerStatsManager statsManager;
    private StatsGUI statsGUI;

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

        // 儲存所有玩家數據
        if (statsManager != null) {
            statsManager.saveAllStats();
            getLogger().info("- All player stats saved");
        }

        // New skill system cooldowns are in-memory; stopping the plugin clears them.

        // Cleanup managers
        configManager = null;
        weaponManager = null;
        mobManager = null;
        statsManager = null;
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

        statsManager = new PlayerStatsManager(this);
        getLogger().info("- PlayerStatsManager initialized");

        statsGUI = new StatsGUI(statsManager);
        getLogger().info("- StatsGUI initialized");

        // ===== New skill system (manager/service pattern) =====
        com.customrpg.weaponSkills.managers.CooldownManager cooldownManager = new com.customrpg.weaponSkills.managers.CooldownManager();
        com.customrpg.weaponSkills.managers.BuffManager buffManager = new com.customrpg.weaponSkills.managers.BuffManager();
        com.customrpg.weaponSkills.managers.DamageManager damageManager = new com.customrpg.weaponSkills.managers.DamageManager();

        // 注入 PlayerStatsManager 到 DamageManager
        damageManager.setStatsManager(statsManager);

        com.customrpg.weaponSkills.util.AoEUtil aoeUtil = new com.customrpg.weaponSkills.util.AoEUtil();
        com.customrpg.weaponSkills.util.ParticleUtil particleUtil = new com.customrpg.weaponSkills.util.ParticleUtil();
        com.customrpg.weaponSkills.util.SoundUtil soundUtil = new com.customrpg.weaponSkills.util.SoundUtil();

        newSkillManager = new SkillManager(weaponManager, cooldownManager, damageManager, buffManager, aoeUtil, particleUtil, soundUtil);

        // auto-register weapon skills from config/weapons/skills/*.yml
        newSkillManager.registerSkillsFromConfig(configManager.getAllWeaponSkills());

        getLogger().info("- New SkillManager initialized with " + newSkillManager.getRegisteredSkillIds().size() + " skills");
    }

    /**
     * Register all event listeners
     */
    private void registerListeners() {
        getLogger().info("Registering event listeners...");

        getServer().getPluginManager().registerEvents(new WeaponListener(this, weaponManager, statsManager), this);
        getLogger().info("- WeaponListener registered");

        // SkillListener (legacy) 已由 SkillTriggerListener 接管

        getServer().getPluginManager().registerEvents(new MobListener(this, mobManager), this);
        getLogger().info("- MobListener registered");

        getServer().getPluginManager().registerEvents(new SkillTriggerListener(newSkillManager), this);
        getLogger().info("- SkillTriggerListener registered");

        getServer().getPluginManager().registerEvents(new StatsListener(this, statsManager), this);
        getLogger().info("- StatsListener registered");

        getServer().getPluginManager().registerEvents(statsGUI, this);
        getLogger().info("- StatsGUI registered");
    }

    /**
     * Register all plugin commands
     */
    private void registerCommands() {
        getLogger().info("Registering commands...");

        org.bukkit.command.PluginCommand weaponCommand = getCommand("weapon");
        if (weaponCommand != null) {
            weaponCommand.setExecutor(new WeaponCommand(this, weaponManager));
            getLogger().info("- /weapon command registered");
        } else {
            getLogger().warning("- Failed to register /weapon command: command not defined in plugin.yml");
        }

        org.bukkit.command.PluginCommand rpgCommand = getCommand("rpg");
        if (rpgCommand != null) {
            StatsCommand statsCommand = new StatsCommand(this, statsManager, statsGUI);
            rpgCommand.setExecutor(statsCommand);
            rpgCommand.setTabCompleter(statsCommand);
            getLogger().info("- /rpg command registered");

            // 註冊 /stats 快捷指令
            org.bukkit.command.PluginCommand statsShortcut = getCommand("stats");
            if (statsShortcut != null) {
                StatsShortcutCommand shortcutCommand = new StatsShortcutCommand(statsCommand, statsGUI);
                statsShortcut.setExecutor(shortcutCommand);
                statsShortcut.setTabCompleter(shortcutCommand);
                getLogger().info("- /stats command registered");
            }
        } else {
            getLogger().warning("- Failed to register /rpg command: command not defined in plugin.yml");
        }
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
