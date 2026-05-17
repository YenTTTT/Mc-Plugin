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
    private com.customrpg.commands.ManaCommand manaCommand;
    private ManaDisplayManager manaDisplayManager;
    private com.customrpg.managers.BloodManager bloodManager;
    private com.customrpg.managers.DistanceSpawnManager distanceSpawnManager;
    private com.customrpg.managers.ProtectionAreaManager protectionAreaManager;
    private com.customrpg.managers.ZoneManager zoneManager;
    private com.customrpg.managers.SafeZoneManager safeZoneManager;
    private com.customrpg.managers.BossZoneManager bossZoneManager;

    // BossBar system
    private com.customrpg.managers.BossBarManager bossBarManager;

    // Race system
    private com.customrpg.races.RaceManager raceManager;
    private com.customrpg.gui.RaceGUI raceGUI;

    // Talent skill system (legacy)
    private com.customrpg.managers.SkillManager talentSkillManager;

    // Talent system
    private com.customrpg.managers.TalentManager talentManager;
    private com.customrpg.gui.TalentTreeGUI talentTreeGUI;
    private com.customrpg.gui.TalentMainMenuGUI talentMainMenuGUI;
    private com.customrpg.managers.TalentPassiveEffectManager talentPassiveEffectManager;
    private com.customrpg.managers.SkillSwitchManager skillSwitchManager;
    private com.customrpg.managers.TalentSkillManager activeTalentSkillManager;

    // Beast system
    private com.customrpg.managers.BeastManager beastManager;

    // Bow talent system
    private com.customrpg.managers.FocusManager focusManager;

    // Skill bar (hotkey casting) system
    private com.customrpg.managers.SkillBarManager skillBarManager;
    private com.customrpg.listeners.SkillBarListener skillBarListenerInstance;

    // New skill system (weapon skills)
    private SkillManager newSkillManager;

    // Menu GUI
    private com.customrpg.gui.MenuGUI menuGUI;

    // BeautyQuests integration
    private com.customrpg.integration.BeautyQuestsHook beautyQuestsHook;

    // NPC system
    private com.customrpg.integration.npc.NpcManager npcManager;
    private com.customrpg.integration.npc.QuestBindGUI questBindGUI;


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

        // 儲存任務冷卻資料
        if (beautyQuestsHook != null) {
            beautyQuestsHook.shutdown();
            getLogger().info("- Quest cooldowns saved");
        }

        // 儲存種族數據
        if (raceManager != null) {
            raceManager.saveAll();
            getLogger().info("- All race data saved");
        }

        // 儲存天賦數據並清理
        if (talentManager != null) {
            talentManager.shutdown();
            getLogger().info("- TalentManager shutdown");
        }

        // 關閉怪物生成系統
        if (distanceSpawnManager != null) {
            distanceSpawnManager.shutdown();
            getLogger().info("- DistanceSpawnManager shutdown");
        }

        if (bossZoneManager != null) {
            bossZoneManager.shutdown();
            getLogger().info("- BossZoneManager shutdown");
        }

        // 清理裝備GUI
        if (equipmentGUI != null) {
            equipmentGUI.cleanup();
            getLogger().info("- EquipmentGUI cleanup");
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

        // 停止 BossBar 管理器
        if (bossBarManager != null) {
            bossBarManager.shutdown();
            getLogger().info("- BossBarManager shutdown");
        }

        // 清理快捷技能列
        if (skillBarManager != null) {
            skillBarManager.cleanup();
            getLogger().info("- SkillBarManager cleanup");
        }
        if (skillBarListenerInstance != null) {
            skillBarListenerInstance.cleanup();
        }

        // Cleanup managers
        configManager = null;
        weaponManager = null;
        mobManager = null;
        distanceSpawnManager = null;
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

        // Initialize ZoneManager (mob zone system)
        zoneManager = new com.customrpg.managers.ZoneManager(this);
        getLogger().info("- ZoneManager initialized with " + zoneManager.getZoneCount() + " zones");

        safeZoneManager = new com.customrpg.managers.SafeZoneManager(this);
        getLogger().info("- SafeZoneManager initialized with " + safeZoneManager.getZoneCount() + " zones");

        distanceSpawnManager = new com.customrpg.managers.DistanceSpawnManager(this, mobManager, safeZoneManager);
        getLogger().info("- DistanceSpawnManager initialized");

        bossZoneManager = new com.customrpg.managers.BossZoneManager(this, mobManager, distanceSpawnManager);
        distanceSpawnManager.setBossZoneManager(bossZoneManager);
        getLogger().info("- BossZoneManager initialized with " + bossZoneManager.getZoneCount() + " zones");

        protectionAreaManager = new com.customrpg.managers.ProtectionAreaManager(this);
        getLogger().info("- ProtectionAreaManager initialized");

        // Initialize Race System
        raceManager = new com.customrpg.races.RaceManager(this);
        getLogger().info("- RaceManager initialized with " + raceManager.getRaceCount() + " races");

        raceGUI = new com.customrpg.gui.RaceGUI(this, raceManager);
        getLogger().info("- RaceGUI initialized");

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

        // Initialize BloodManager
        bloodManager = new com.customrpg.managers.BloodManager(this);
        getLogger().info("- BloodManager initialized");

        // Initialize TalentSkillManager (legacy skill system for talent skills)
        talentSkillManager = new com.customrpg.managers.SkillManager(this, configManager);
        getLogger().info("- TalentSkillManager initialized with " + talentSkillManager.getSkillCount() + " talent skills");

        // ===== Talent System =====
        talentManager = new com.customrpg.managers.TalentManager(this);
        getLogger().info("- TalentManager initialized with " + talentManager.getTotalTalentCount() + " talents");

        talentTreeGUI = new com.customrpg.gui.TalentTreeGUI(this, talentManager);
        talentMainMenuGUI = new com.customrpg.gui.TalentMainMenuGUI(this, talentManager, talentTreeGUI);
        getLogger().info("- Talent GUIs initialized");

        talentPassiveEffectManager = new com.customrpg.managers.TalentPassiveEffectManager(this, talentManager, statsManager);
        getLogger().info("- TalentPassiveEffectManager initialized");

        skillSwitchManager = new com.customrpg.managers.SkillSwitchManager(this);
        getLogger().info("- SkillSwitchManager initialized");

        activeTalentSkillManager = new com.customrpg.managers.TalentSkillManager(this);
        beastManager = new com.customrpg.managers.BeastManager(this);
        focusManager = new com.customrpg.managers.FocusManager(this);
        getLogger().info("- TalentSkillManager initialized");

        // SkillBar (hotkey casting) manager
        skillBarManager = new com.customrpg.managers.SkillBarManager();
        getLogger().info("- SkillBarManager initialized");

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

        // Initialize MenuGUI
        menuGUI = new com.customrpg.gui.MenuGUI(this);
        getLogger().info("- MenuGUI initialized");

        // Initialize BossBarManager
        bossBarManager = new com.customrpg.managers.BossBarManager(this, mobManager);
        getLogger().info("- BossBarManager initialized");

        // Initialize BeautyQuests integration hook（延遲 1 tick 確保 BQ 完成載入）
        beautyQuestsHook = new com.customrpg.integration.BeautyQuestsHook(this, mobManager);

        // NPC system — 先建立 NpcManager，再傳入 hook，BQ 就緒後呼叫 spawnAllNpcs
        npcManager = new com.customrpg.integration.npc.NpcManager(this);
        questBindGUI = new com.customrpg.integration.npc.QuestBindGUI(this, npcManager);
        beautyQuestsHook.setNpcManager(npcManager);
        org.bukkit.Bukkit.getScheduler().runTask(this, () -> beautyQuestsHook.tryEnable());
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
        getServer().getPluginManager().registerEvents(talentTreeGUI, this);
        getServer().getPluginManager().registerEvents(talentMainMenuGUI, this);
        getLogger().info("- Talent GUIs registered");

        getServer().getPluginManager().registerEvents(talentPassiveEffectManager, this);
        getLogger().info("- TalentPassiveEffectManager registered");

        // 註冊TalentListener來處理玩家登入時的天賦效果應用
        getServer().getPluginManager().registerEvents(new com.customrpg.listeners.TalentListener(talentManager), this);
        getLogger().info("- TalentListener registered");

        // 註冊SkillSwitchHintListener來顯示技能切換提示
        getServer().getPluginManager().registerEvents(new com.customrpg.listeners.SkillSwitchHintListener(this), this);
        getLogger().info("- SkillSwitchHintListener registered");

        getServer().getPluginManager().registerEvents(new HealthDisplayListener(healthDisplayManager), this);
        getLogger().info("- HealthDisplayListener registered");

        getServer().getPluginManager().registerEvents(new DamageDisplayListener(damageDisplayManager), this);
        getLogger().info("- DamageDisplayListener registered");

        getServer().getPluginManager().registerEvents(new com.customrpg.listeners.ManaListener(), this);
        getLogger().info("- ManaListener registered");

        getServer().getPluginManager().registerEvents(new com.customrpg.listeners.ProtectAreaListener(this, protectionAreaManager), this);
        getLogger().info("- ProtectAreaListener registered");

        getServer().getPluginManager().registerEvents(new com.customrpg.listeners.SafeZoneSpawnListener(safeZoneManager), this);
        getLogger().info("- SafeZoneSpawnListener registered");

        // SetMob zone listener
        getServer().getPluginManager().registerEvents(new com.customrpg.listeners.SetMobListener(this, zoneManager), this);
        getLogger().info("- SetMobListener registered");

        // Race system listeners
        getServer().getPluginManager().registerEvents(raceGUI, this);
        getLogger().info("- RaceGUI registered");

        getServer().getPluginManager().registerEvents(new com.customrpg.listeners.RaceListener(this, raceManager), this);
        getLogger().info("- RaceListener registered");

        // Menu GUI + compass listener
        getServer().getPluginManager().registerEvents(new com.customrpg.listeners.BowTalentListener(
                this, focusManager, talentManager), this);
        getLogger().info("- BowTalentListener registered");

        // SkillBar listener
        skillBarListenerInstance = new com.customrpg.listeners.SkillBarListener(this, skillBarManager);
        getServer().getPluginManager().registerEvents(skillBarListenerInstance, this);
        getLogger().info("- SkillBarListener registered");

        getServer().getPluginManager().registerEvents(menuGUI, this);
        getServer().getPluginManager().registerEvents(new com.customrpg.listeners.MenuListener(this, menuGUI), this);
        getLogger().info("- MenuGUI & MenuListener registered");

        // NPC interaction listener + quest bind GUI (need to be registered for Shift+右鍵 to work)
        getServer().getPluginManager().registerEvents(
                new com.customrpg.integration.npc.NpcListener(npcManager, questBindGUI, this), this);
        getServer().getPluginManager().registerEvents(questBindGUI, this);
        getLogger().info("- NpcListener & QuestBindGUI registered");

        // Quest cooldown listener (repeatable quest 30-min cooldown)
        if (getServer().getPluginManager().getPlugin("BeautyQuests") != null) {
            getServer().getPluginManager().registerEvents(
                    new com.customrpg.integration.QuestCooldownListener(
                            beautyQuestsHook.getQuestCooldownManager()), this);
            getLogger().info("- QuestCooldownListener registered");
        }
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
            com.customrpg.commands.TalentCommand talentCommandExecutor = new com.customrpg.commands.TalentCommand(this, talentManager, talentMainMenuGUI);
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

        // MobSpawn management command
        org.bukkit.command.PluginCommand mobSpawnCommand = getCommand("mobspawn");
        if (mobSpawnCommand != null) {
            com.customrpg.commands.MobSpawnCommand mobSpawnCommandExecutor = new com.customrpg.commands.MobSpawnCommand(this);
            mobSpawnCommand.setExecutor(mobSpawnCommandExecutor);
            mobSpawnCommand.setTabCompleter(mobSpawnCommandExecutor);
            getLogger().info("- /mobspawn command registered");
        } else {
            getLogger().warning("- Failed to register /mobspawn command: command not defined in plugin.yml");
        }

        org.bukkit.command.PluginCommand finishRpgCommand = getCommand("finishrpg");
        if (finishRpgCommand != null) {
            com.customrpg.commands.FinishRpgCommand executor = new com.customrpg.commands.FinishRpgCommand(this);
            finishRpgCommand.setExecutor(executor);
            finishRpgCommand.setTabCompleter(executor);
            getLogger().info("- /finishrpg command registered");
        } else {
            getLogger().warning("- Failed to register /finishrpg command: command not defined in plugin.yml");
        }

        // ProtectArea command
        org.bukkit.command.PluginCommand protectAreaCmd = getCommand("protectarea");
        if (protectAreaCmd != null) {
            com.customrpg.commands.ProtectAreaCommand protectAreaCommand = new com.customrpg.commands.ProtectAreaCommand(this, protectionAreaManager);
            protectAreaCmd.setExecutor(protectAreaCommand);
            protectAreaCmd.setTabCompleter(protectAreaCommand);
            getLogger().info("- /protectarea command registered");
        } else {
            getLogger().warning("- Failed to register /protectarea command: command not defined in plugin.yml");
        }

        // Race system command
        org.bukkit.command.PluginCommand raceCmd = getCommand("race");
        if (raceCmd != null) {
            com.customrpg.commands.RaceCommand raceCommand = new com.customrpg.commands.RaceCommand(this, raceManager, raceGUI);
            raceCmd.setExecutor(raceCommand);
            raceCmd.setTabCompleter(raceCommand);
            getLogger().info("- /race command registered");
        } else {
            getLogger().warning("- Failed to register /race command: command not defined in plugin.yml");
        }

        // SetMob zone command
        org.bukkit.command.PluginCommand setMobCmd = getCommand("setmob");
        if (setMobCmd != null) {
            com.customrpg.commands.SetMobCommand setMobCommand = new com.customrpg.commands.SetMobCommand(this, zoneManager);
            setMobCmd.setExecutor(setMobCommand);
            setMobCmd.setTabCompleter(setMobCommand);
            getLogger().info("- /setmob command registered");
        } else {
            getLogger().warning("- Failed to register /setmob command: command not defined in plugin.yml");
        }

        // Menu command
        org.bukkit.command.PluginCommand menuCmd = getCommand("menu");
        if (menuCmd != null) {
            com.customrpg.commands.MenuCommand menuCommand = new com.customrpg.commands.MenuCommand(this, menuGUI);
            menuCmd.setExecutor(menuCommand);
            menuCmd.setTabCompleter(menuCommand);
            getLogger().info("- /menu command registered");
        } else {
            getLogger().warning("- Failed to register /menu command: command not defined in plugin.yml");
        }

        // SkillBar command
        org.bukkit.command.PluginCommand skillBarCmd = getCommand("skillbar");
        if (skillBarCmd != null) {
            com.customrpg.commands.SkillBarCommand skillBarCommand = new com.customrpg.commands.SkillBarCommand(this, skillBarManager, skillBarListenerInstance);
            skillBarCmd.setExecutor(skillBarCommand);
            skillBarCmd.setTabCompleter(skillBarCommand);
            getLogger().info("- /skillbar command registered");
        } else {
            getLogger().warning("- Failed to register /skillbar command: command not defined in plugin.yml");
        }

        // Mana command
        org.bukkit.command.PluginCommand manaCmd = getCommand("mana");        if (manaCmd != null) {
            com.customrpg.commands.ManaCommand manaCommand = new com.customrpg.commands.ManaCommand(this, manaManager);
            manaCmd.setExecutor(manaCommand);
            manaCmd.setTabCompleter(manaCommand);
            this.manaCommand = manaCommand;
            getLogger().info("- /mana command registered");
        } else {
            getLogger().warning("- Failed to register /mana command: command not defined in plugin.yml");
        }

        // BossZone command
        org.bukkit.command.PluginCommand bossZoneCmd = getCommand("bosszone");
        if (bossZoneCmd != null) {
            com.customrpg.commands.BossZoneCommand bossZoneCommand = new com.customrpg.commands.BossZoneCommand(this);
            bossZoneCmd.setExecutor(bossZoneCommand);
            bossZoneCmd.setTabCompleter(bossZoneCommand);
            getLogger().info("- /bosszone command registered");
        } else {
            getLogger().warning("- Failed to register /bosszone command: command not defined in plugin.yml");
        }

        // NPC management command
        org.bukkit.command.PluginCommand rpgNpcCmd = getCommand("rpgnpc");
        if (rpgNpcCmd != null) {
            com.customrpg.commands.NpcCommand npcCommand = new com.customrpg.commands.NpcCommand(npcManager, questBindGUI);
            rpgNpcCmd.setExecutor(npcCommand);
            rpgNpcCmd.setTabCompleter(npcCommand);
            getLogger().info("- /rpgnpc command registered");
        } else {
            getLogger().warning("- Failed to register /rpgnpc command: command not defined in plugin.yml");
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

    public com.customrpg.commands.ManaCommand getManaCommand() {
        return manaCommand;
    }

    public com.customrpg.gui.TalentMainMenuGUI getTalentMainMenuGUI() {
        return talentMainMenuGUI;
    }

    public com.customrpg.gui.TalentTreeGUI getTalentTreeGUI() {
        return talentTreeGUI;
    }

    public ManaManager getManaManager() {
        return manaManager;
    }

    public com.customrpg.managers.BloodManager getBloodManager() {
        return bloodManager;
    }

    public com.customrpg.managers.TalentSkillManager getTalentSkillManager() {
        return activeTalentSkillManager;
    }

    public com.customrpg.managers.SkillSwitchManager getSkillSwitchManager() {
        return skillSwitchManager;
    }

    public com.customrpg.managers.BeastManager getBeastManager() {
        return beastManager;
    }

    public com.customrpg.managers.FocusManager getFocusManager() {
        return focusManager;
    }

    public com.customrpg.managers.SkillBarManager getSkillBarManager() {
        return skillBarManager;
    }

    public com.customrpg.weaponSkills.managers.SkillManager getNewSkillManager() {
        return newSkillManager;
    }
    public com.customrpg.equipment.ArmorManager getArmorManager() {
        return armorManager;
    }

    public com.customrpg.managers.DistanceSpawnManager getDistanceSpawnManager() {
        return distanceSpawnManager;
    }

    public com.customrpg.managers.ProtectionAreaManager getProtectionAreaManager() {
        return protectionAreaManager;
    }

    public com.customrpg.managers.SafeZoneManager getSafeZoneManager() {
        return safeZoneManager;
    }

    public com.customrpg.managers.BossZoneManager getBossZoneManager() {
        return bossZoneManager;
    }

    public com.customrpg.managers.ZoneManager getZoneManager() {
        return zoneManager;
    }

    public com.customrpg.races.RaceManager getRaceManager() {
        return raceManager;
    }

    public com.customrpg.gui.RaceGUI getRaceGUI() {
        return raceGUI;
    }

    public com.customrpg.gui.MenuGUI getMenuGUI() {
        return menuGUI;
    }

    public com.customrpg.managers.BossBarManager getBossBarManager() {
        return bossBarManager;
    }

    public com.customrpg.integration.BeautyQuestsHook getBeautyQuestsHook() {
        return beautyQuestsHook;
    }

    public StatsGUI getStatsGUI() {
        return statsGUI;
    }
}
