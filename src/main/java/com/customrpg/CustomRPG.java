package com.customrpg;

import com.customrpg.commands.WeaponCommand;
import com.customrpg.listeners.MobListener;
import com.customrpg.listeners.SkillListener;
import com.customrpg.listeners.WeaponListener;
import com.customrpg.managers.MobManager;
import com.customrpg.managers.SkillManager;
import com.customrpg.managers.WeaponManager;
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

    private WeaponManager weaponManager;
    private SkillManager skillManager;
    private MobManager mobManager;

    /**
     * Called when the plugin is enabled
     * Initializes all managers, registers listeners and commands
     */
    @Override
    public void onEnable() {
        getLogger().info("=================================");
        getLogger().info("   CustomRPG Plugin Starting");
        getLogger().info("=================================");

        // Save default config if it doesn't exist
        saveDefaultConfig();
        getLogger().info("Configuration loaded successfully");

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

        // Clear cooldowns
        if (skillManager != null) {
            skillManager.clearAllCooldowns();
            getLogger().info("Cleared all skill cooldowns");
        }

        // Cleanup managers
        weaponManager = null;
        skillManager = null;
        mobManager = null;

        getLogger().info("CustomRPG has been disabled successfully!");
        getLogger().info("=================================");
    }

    /**
     * Initialize all plugin managers
     */
    private void initializeManagers() {
        getLogger().info("Initializing managers...");

        weaponManager = new WeaponManager(this);
        getLogger().info("- WeaponManager initialized with " + weaponManager.getWeaponCount() + " weapons");

        skillManager = new SkillManager(this);
        getLogger().info("- SkillManager initialized with " + skillManager.getSkillCount() + " skills");

        mobManager = new MobManager(this);
        getLogger().info("- MobManager initialized with " + mobManager.getMobTypeCount() + " custom mob types");
    }

    /**
     * Register all event listeners
     */
    private void registerListeners() {
        getLogger().info("Registering event listeners...");

        getServer().getPluginManager().registerEvents(new WeaponListener(this, weaponManager), this);
        getLogger().info("- WeaponListener registered");

        getServer().getPluginManager().registerEvents(new SkillListener(this, skillManager), this);
        getLogger().info("- SkillListener registered");

        getServer().getPluginManager().registerEvents(new MobListener(this, mobManager), this);
        getLogger().info("- MobListener registered");
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
     * Get the WeaponManager instance
     * @return WeaponManager instance
     */
    public WeaponManager getWeaponManager() {
        return weaponManager;
    }

    /**
     * Get the SkillManager instance
     * @return SkillManager instance
     */
    public SkillManager getSkillManager() {
        return skillManager;
    }

    /**
     * Get the MobManager instance
     * @return MobManager instance
     */
    public MobManager getMobManager() {
        return mobManager;
    }
}
