package com.customrpg;

import com.customrpg.commands.EquipmentCommand;
import com.customrpg.commands.MobCommand;
import com.customrpg.commands.StatsCommand;
import com.customrpg.commands.StatsShortcutCommand;
import com.customrpg.commands.WeaponCommand;
import com.customrpg.equipment.EquipmentManager;
import com.customrpg.gui.EquipmentGUI;
import com.customrpg.gui.StatsGUI;
import com.customrpg.listeners.DamageDisplayListener;
import com.customrpg.listeners.EquipmentSyncListener;
import com.customrpg.listeners.HealthDisplayListener;
import com.customrpg.listeners.MobListener;
import com.customrpg.listeners.StatsListener;
import com.customrpg.listeners.WeaponListener;
import com.customrpg.listeners.SkillTriggerListener;
import com.customrpg.managers.ConfigManager;
import com.customrpg.managers.DamageDisplayManager;
import com.customrpg.managers.HealthDisplayManager;
import com.customrpg.managers.ManaManager;
import com.customrpg.managers.ManaDisplayManager;
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
    private EquipmentManager equipmentManager;
    private EquipmentGUI equipmentGUI;
    private com.customrpg.equipment.ArmorManager armorManager;
    private HealthDisplayManager healthDisplayManager;
    private DamageDisplayManager damageDisplayManager;
    private ManaManager manaManager;
    private ManaDisplayManager manaDisplayManager;

    // Talent skill system (legacy)
    private com.customrpg.managers.SkillManager talentSkillManager;

    // Talent system
    private com.customrpg.managers.TalentManager talentManager;
    private com.customrpg.gui.TalentGUI talentGUI;
    private com.customrpg.managers.TalentPassiveEffectManager talentPassiveEffectManager;

    // New skill system (weapon skills)
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

        // 儲存天賦數據並清理
        if (talentManager != null) {
            talentManager.shutdown();
            getLogger().info("- TalentManager shutdown");
        }

        // 清理裝備GUI
        if (equipmentGUI != null) {
            equipmentGUI.cleanup();
            getLogger().info("- EquipmentGUI cleanup");
        }

        // 清理天賦GUI
        if (talentGUI != null) {
            talentGUI.cleanup();
            getLogger().info("- TalentGUI cleanup");
        }

        // 清理天賦被動效果管理器
        if (talentPassiveEffectManager != null) {
            talentPassiveEffectManager.shutdown();
            getLogger().info("- TalentPassiveEffectManager shutdown");
        }

        // 停止血量顯示任務
        if (healthDisplayManager != null) {
            healthDisplayManager.shutdown();
            getLogger().info("- HealthDisplayManager shutdown");
        }

        // 停止傷害顯示任務
        if (damageDisplayManager != null) {
            damageDisplayManager.shutdown();
            getLogger().info("- DamageDisplayManager shutdown");
        }

        // 停止魔力回復任務
        if (manaManager != null) {
            manaManager.stopRegenTask();
            getLogger().info("- ManaManager shutdown");
        }

        // 停止魔力顯示任務
        if (manaDisplayManager != null) {
            manaDisplayManager.shutdown();
            getLogger().info("- ManaDisplayManager shutdown");
        }

        // New skill system cooldowns are in-memory; stopping the plugin clears them.

        // Cleanup managers
        configManager = null;
        weaponManager = null;
        mobManager = null;
        statsManager = null;
        equipmentManager = null;
        equipmentGUI = null;
        talentSkillManager = null;
        newSkillManager = null;
        healthDisplayManager = null;
        damageDisplayManager = null;
        manaManager = null;
        manaDisplayManager = null;

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

        // Initialize EquipmentManager
        equipmentManager = new EquipmentManager(this);
        getLogger().info("- EquipmentManager initialized");

        // Initialize EquipmentGUI
        equipmentGUI = new EquipmentGUI(this, equipmentManager);
        getLogger().info("- EquipmentGUI initialized");

        // Initialize ArmorManager
        armorManager = new com.customrpg.equipment.ArmorManager(this);
        getLogger().info("- ArmorManager initialized");

        // Initialize ManaManager
        manaManager = new ManaManager(this, statsManager);
        getLogger().info("- ManaManager initialized");

        // Initialize TalentSkillManager (legacy skill system for talent skills)
        talentSkillManager = new com.customrpg.managers.SkillManager(this, configManager);
        getLogger().info("- TalentSkillManager initialized with " + talentSkillManager.getSkillCount() + " talent skills");

        // ===== Talent System =====
        talentManager = new com.customrpg.managers.TalentManager(this);
        getLogger().info("- TalentManager initialized with " + talentManager.getTotalTalentCount() + " talents");

        talentGUI = new com.customrpg.gui.TalentGUI(this, talentManager);
        getLogger().info("- TalentGUI initialized");

        talentPassiveEffectManager = new com.customrpg.managers.TalentPassiveEffectManager(this, talentManager, statsManager);
        getLogger().info("- TalentPassiveEffectManager initialized");

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

        // Initialize HealthDisplayManager
        healthDisplayManager = new HealthDisplayManager(this, mobManager);
        getLogger().info("- HealthDisplayManager initialized");

        // Initialize DamageDisplayManager
        damageDisplayManager = new DamageDisplayManager(this);
        getLogger().info("- DamageDisplayManager initialized");

        // Initialize ManaDisplayManager
        manaDisplayManager = new ManaDisplayManager(this, statsManager);
        getLogger().info("- ManaDisplayManager initialized");

        // 設置管理器之間的關聯，避免顯示衝突
        healthDisplayManager.setDamageDisplayManager(damageDisplayManager);
        healthDisplayManager.setManaDisplayManager(manaDisplayManager);
        manaDisplayManager.setDamageDisplayManager(damageDisplayManager);
        manaDisplayManager.setHealthDisplayManager(healthDisplayManager);
    }

    /**
     * Register all event listeners
     */
    private void registerListeners() {
        getLogger().info("Registering event listeners...");

        getServer().getPluginManager().registerEvents(new WeaponListener(this, weaponManager, statsManager), this);
        getLogger().info("- WeaponListener registered");

        // SkillListener for talent skills (uses mana)
        getServer().getPluginManager().registerEvents(new com.customrpg.listeners.SkillListener(this, talentSkillManager, manaManager), this);
        getLogger().info("- SkillListener (talent skills) registered");

        getServer().getPluginManager().registerEvents(new MobListener(this, mobManager, statsManager, weaponManager), this);
        getLogger().info("- MobListener registered");

        getServer().getPluginManager().registerEvents(new SkillTriggerListener(newSkillManager), this);
        getLogger().info("- SkillTriggerListener registered");

        getServer().getPluginManager().registerEvents(new StatsListener(this, statsManager), this);
        getLogger().info("- StatsListener registered");

        getServer().getPluginManager().registerEvents(statsGUI, this);
        getLogger().info("- StatsGUI registered");

        // Register EquipmentGUI listener
        getServer().getPluginManager().registerEvents(equipmentGUI, this);
        getLogger().info("- EquipmentGUI registered");

        // Register EquipmentSyncListener
        getServer().getPluginManager().registerEvents(new EquipmentSyncListener(this, equipmentManager), this);
        getLogger().info("- EquipmentSyncListener registered");

        // Talent system listeners
        getServer().getPluginManager().registerEvents(talentGUI, this);
        getLogger().info("- TalentGUI registered");

        getServer().getPluginManager().registerEvents(talentPassiveEffectManager, this);
        getLogger().info("- TalentPassiveEffectManager registered");

        // 註冊TalentListener來處理玩家登入時的天賦效果應用
        getServer().getPluginManager().registerEvents(new com.customrpg.listeners.TalentListener(talentManager), this);
        getLogger().info("- TalentListener registered");

        getServer().getPluginManager().registerEvents(new HealthDisplayListener(healthDisplayManager), this);
        getLogger().info("- HealthDisplayListener registered");

        getServer().getPluginManager().registerEvents(new DamageDisplayListener(damageDisplayManager), this);
        getLogger().info("- DamageDisplayListener registered");

        getServer().getPluginManager().registerEvents(new com.customrpg.listeners.ManaListener(), this);
        getLogger().info("- ManaListener registered");
    }

    /**
     * Register all plugin commands
     */
    private void registerCommands() {
        getLogger().info("Registering commands...");

        org.bukkit.command.PluginCommand weaponCommand = getCommand("weapon");
        if (weaponCommand != null) {
            WeaponCommand weaponCommandExecutor = new WeaponCommand(this, weaponManager);
            weaponCommand.setExecutor(weaponCommandExecutor);
            weaponCommand.setTabCompleter(weaponCommandExecutor);
            getLogger().info("- /weapon command registered");
        } else {
            getLogger().warning("- Failed to register /weapon command: command not defined in plugin.yml");
        }

        org.bukkit.command.PluginCommand mobCommand = getCommand("custommob");
        if (mobCommand != null) {
            MobCommand mobCommandExecutor = new MobCommand(this, mobManager);
            mobCommand.setExecutor(mobCommandExecutor);
            mobCommand.setTabCompleter(mobCommandExecutor);
            getLogger().info("- /custommob command registered");
        } else {
            getLogger().warning("- Failed to register /custommob command: command not defined in plugin.yml");
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

        // Talent system command
        org.bukkit.command.PluginCommand talentCommand = getCommand("talent");
        if (talentCommand != null) {
            com.customrpg.commands.TalentCommand talentCommandExecutor = new com.customrpg.commands.TalentCommand(this, talentManager, talentGUI);
            talentCommand.setExecutor(talentCommandExecutor);
            talentCommand.setTabCompleter(talentCommandExecutor);
            getLogger().info("- /talent command registered");
        } else {
            getLogger().warning("- Failed to register /talent command: command not defined in plugin.yml");
        }

        // Equipment system command
        org.bukkit.command.PluginCommand equipmentCommand = getCommand("equipment");
        if (equipmentCommand != null) {
            EquipmentCommand equipmentCommandExecutor = new EquipmentCommand(this);
            equipmentCommand.setExecutor(equipmentCommandExecutor);
            equipmentCommand.setTabCompleter(equipmentCommandExecutor);
            getLogger().info("- /equipment command registered");
        } else {
            getLogger().warning("- Failed to register /equipment command: command not defined in plugin.yml");
        }

        // Stat display command
        org.bukkit.command.PluginCommand statCommand = getCommand("stat");
        if (statCommand != null) {
            com.customrpg.commands.StatCommand statCommandExecutor = new com.customrpg.commands.StatCommand(this);
            statCommand.setExecutor(statCommandExecutor);
            getLogger().info("- /stat command registered");
        } else {
            getLogger().warning("- Failed to register /stat command: command not defined in plugin.yml");
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
     * Get the PlayerStatsManager instance
     * @return PlayerStatsManager instance
     */
    public PlayerStatsManager getPlayerStatsManager() {
        return statsManager;
    }

    /**
     * Get the MobManager instance
     * @return MobManager instance
     */
    public MobManager getMobManager() {
        return mobManager;
    }

    /**
     * Get the EquipmentManager instance
     * @return EquipmentManager instance
     */
    public EquipmentManager getEquipmentManager() {
        return equipmentManager;
    }

    /**
     * Get the EquipmentGUI instance
     * @return EquipmentGUI instance
     */
    public EquipmentGUI getEquipmentGUI() {
        return equipmentGUI;
    }

    /**
     * Get the TalentManager instance
     * @return TalentManager instance
     */
    public com.customrpg.managers.TalentManager getTalentManager() {
        return talentManager;
    }

    /**
     * Get the ArmorManager instance
     * @return ArmorManager instance
     */
    public com.customrpg.equipment.ArmorManager getArmorManager() {
        return armorManager;
    }
}
