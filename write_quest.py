import os

base = r'D:\下載\Mc-Plugin\src\main\java\com\customrpg\quest'
cmd_base = r'D:\下載\Mc-Plugin\src\main\java\com\customrpg\commands'

quest_manager = r"""package com.customrpg.quest;

import com.customrpg.CustomRPG;
import com.customrpg.equipment.EquipmentData;
import com.customrpg.players.PlayerStats;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class QuestManager {

    public static final NamespacedKey EQUIPMENT_ID_KEY = new NamespacedKey("customrpg", "equipment_id");

    private final CustomRPG plugin;
    private final Map<String, QuestData> quests = new LinkedHashMap<>();
    private final Map<UUID, Map<String, PlayerQuestProgress>> playerProgress = new ConcurrentHashMap<>();
    private final File questsConfigDir;
    private final File playerDataDir;

    public QuestManager(CustomRPG plugin) {
        this.plugin = plugin;
        this.questsConfigDir = new File(plugin.getDataFolder(), "config/quests");
        this.playerDataDir   = new File(plugin.getDataFolder(), "data/quests");
        if (!questsConfigDir.exists()) questsConfigDir.mkdirs();
        if (!playerDataDir.exists())   playerDataDir.mkdirs();
        loadQuests();
    }

    public void loadQuests() {
        quests.clear();
        File[] files = questsConfigDir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return;
        for (File file : files) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection section = config.getConfigurationSection("quests");
            if (section == null) continue;
            for (String questId : section.getKeys(false)) {
                QuestData quest = loadQuestFromSection(questId, section.getConfigurationSection(questId));
                if (quest != null) quests.put(questId, quest);
            }
        }
        plugin.getLogger().info("[QuestManager] Loaded " + quests.size() + " quests.");
    }

    private QuestData loadQuestFromSection(String questId, ConfigurationSection sec) {
        if (sec == null) return null;
        QuestData quest = new QuestData(questId);
        quest.setName(sec.getString("name", questId));
        quest.setDescription(sec.getString("description", ""));
        quest.setNpcKey(sec.getString("npc", null));
        quest.setRepeatable(sec.getBoolean("repeatable", false));
        quest.setPrerequisites(sec.getStringList("prerequisites"));
        List<Map<?, ?>> objList = sec.getMapList("objectives");
        for (Map<?, ?> objMap : objList) {
            try {
                String typeStr = String.valueOf(objMap.get("type"));
                QuestObjectiveType type = QuestObjectiveType.valueOf(typeStr.toUpperCase());
                String target = String.valueOf(objMap.getOrDefault("target", ""));
                int amount = Integer.parseInt(String.valueOf(objMap.getOrDefault("amount", "1")));
                String desc = String.valueOf(objMap.getOrDefault("description", target + " {progress}/{total}"));
                quest.addObjective(new QuestObjective(type, target, amount, desc));
            } catch (Exception e) {
                plugin.getLogger().warning("[QuestManager] Bad objective in '" + questId + "': " + e.getMessage());
            }
        }
        ConfigurationSection rewardSec = sec.getConfigurationSection("rewards");
        if (rewardSec != null) {
            QuestReward reward = new QuestReward();
            reward.setExp(rewardSec.getLong("exp", 0));
            reward.setStatPoints(rewardSec.getInt("stat_points", 0));
            reward.setTalentPoints(rewardSec.getInt("talent_points", 0));
            reward.setEquipmentIds(rewardSec.getStringList("equipment"));
            quest.setReward(reward);
        }
        return quest;
    }

    public Map<String, PlayerQuestProgress> getPlayerProgress(UUID uuid) {
        return playerProgress.computeIfAbsent(uuid, this::loadPlayerProgress);
    }

    public QuestStatus getQuestStatus(Player player, String questId) {
        QuestData quest = quests.get(questId);
        if (quest == null) return QuestStatus.UNAVAILABLE;
        Map<String, PlayerQuestProgress> all = getPlayerProgress(player.getUniqueId());
        PlayerQuestProgress p = all.get(questId);
        if (p == null) {
            for (String prereq : quest.getPrerequisites()) {
                PlayerQuestProgress pp = all.get(prereq);
                if (pp == null || pp.getStatus() != QuestStatus.TURNED_IN) return QuestStatus.UNAVAILABLE;
            }
            return QuestStatus.AVAILABLE;
        }
        return p.getStatus();
    }

    public boolean acceptQuest(Player player, String questId) {
        if (getQuestStatus(player, questId) != QuestStatus.AVAILABLE) return false;
        QuestData quest = quests.get(questId);
        Map<String, PlayerQuestProgress> all = getPlayerProgress(player.getUniqueId());
        PlayerQuestProgress p = new PlayerQuestProgress(questId, quest.getObjectives().size());
        p.setStatus(QuestStatus.IN_PROGRESS);
        all.put(questId, p);
        savePlayerProgress(player.getUniqueId());
        player.sendMessage("\u00a76\u00a7l[\u4efb\u52d9] \u00a7r\u00a7a\u5df2\u63a5\u53d7\uff1a\u00a7f" + quest.getName());
        if (quest.getDescription() != null && !quest.getDescription().isEmpty())
            player.sendMessage("\u00a77" + quest.getDescription());
        return true;
    }

    public boolean turnInQuest(Player player, String questId) {
        QuestData quest = quests.get(questId);
        if (quest == null) return false;
        Map<String, PlayerQuestProgress> all = getPlayerProgress(player.getUniqueId());
        PlayerQuestProgress p = all.get(questId);
        if (p == null || p.getStatus() != QuestStatus.COMPLETED) return false;
        QuestReward reward = quest.getReward();
        PlayerStats stats = plugin.getPlayerStatsManager().getStats(player);
        if (reward.getExp() > 0) plugin.getPlayerStatsManager().addExp(player, reward.getExp());
        if (reward.getStatPoints() > 0) stats.setStatPoints(stats.getStatPoints() + reward.getStatPoints());
        if (reward.getTalentPoints() > 0) stats.addTalentPoints(reward.getTalentPoints());
        for (String equipId : reward.getEquipmentIds()) {
            EquipmentData data = plugin.getEquipmentManager().createEquipment(equipId);
            if (data == null) continue;
            ItemStack item = data.toItemStack();
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(EQUIPMENT_ID_KEY, PersistentDataType.STRING, equipId);
                item.setItemMeta(meta);
            }
            player.getInventory().addItem(item);
        }
        if (quest.isRepeatable()) {
            p.setStatus(QuestStatus.AVAILABLE);
            for (int i = 0; i < quest.getObjectives().size(); i++) p.setProgress(i, 0);
        } else {
            p.setStatus(QuestStatus.TURNED_IN);
        }
        savePlayerProgress(player.getUniqueId());
        if (reward.getStatPoints() > 0) plugin.getPlayerStatsManager().saveStats(player);
        player.sendMessage("\u00a76\u00a7l[\u4efb\u52d9\u5b8c\u6210] \u00a7r\u00a76" + quest.getName());
        if (reward.getExp() > 0) player.sendMessage("\u00a7a  +\u00a7f" + reward.getExp() + " \u00a7a\u7d93\u9a57\u503c");
        if (reward.getStatPoints() > 0) player.sendMessage("\u00a7a  +\u00a7f" + reward.getStatPoints() + " \u00a7a\u5c6c\u6027\u9ede");
        if (reward.getTalentPoints() > 0) player.sendMessage("\u00a7a  +\u00a7f" + reward.getTalentPoints() + " \u00a7a\u5929\u8ce6\u9ede");
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        return true;
    }

    public void onMobKilled(Player player, String mobKey) {
        processObjective(player, QuestObjectiveType.KILL_MOB, mobKey, 1);
    }

    public void onEquipmentObtained(Player player, String equipmentId) {
        processObjective(player, QuestObjectiveType.OBTAIN_EQUIPMENT, equipmentId, 1);
    }

    private void processObjective(Player player, QuestObjectiveType type, String target, int delta) {
        Map<String, PlayerQuestProgress> all = getPlayerProgress(player.getUniqueId());
        boolean dirty = false;
        for (Map.Entry<String, PlayerQuestProgress> entry : all.entrySet()) {
            PlayerQuestProgress p = entry.getValue();
            if (p.getStatus() != QuestStatus.IN_PROGRESS) continue;
            QuestData quest = quests.get(entry.getKey());
            if (quest == null) continue;
            List<QuestObjective> objs = quest.getObjectives();
            for (int i = 0; i < objs.size(); i++) {
                QuestObjective obj = objs.get(i);
                if (obj.getType() != type || !obj.getTarget().equals(target)) continue;
                if (p.getProgress(i) >= obj.getAmount()) continue;
                p.incrementProgress(i, delta);
                dirty = true;
                int prog = p.getProgress(i);
                player.sendMessage("\u00a7e[\u4efb\u52d9] \u00a7f" + quest.getName() + " \u00a77- " + obj.formatDescription(prog));
                if (checkComplete(quest, p)) {
                    p.setStatus(QuestStatus.COMPLETED);
                    player.sendMessage("\u00a76\u00a7l[\u4efb\u52d9] \u00a7r\u00a76\u76ee\u6a19\u5b8c\u6210\uff01\u00a77\u8acb\u81f3 NPC \u9818\u53d6\u734e\u52f5\u3002");
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
                }
            }
        }
        if (dirty) savePlayerProgress(player.getUniqueId());
    }

    private boolean checkComplete(QuestData quest, PlayerQuestProgress p) {
        List<QuestObjective> objs = quest.getObjectives();
        for (int i = 0; i < objs.size(); i++) {
            if (p.getProgress(i) < objs.get(i).getAmount()) return false;
        }
        return true;
    }

    private Map<String, PlayerQuestProgress> loadPlayerProgress(UUID uuid) {
        Map<String, PlayerQuestProgress> result = new LinkedHashMap<>();
        File file = new File(playerDataDir, uuid + ".yml");
        if (!file.exists()) return result;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("quests");
        if (section == null) return result;
        for (String questId : section.getKeys(false)) {
            QuestData quest = quests.get(questId);
            if (quest == null) continue;
            ConfigurationSection qSec = section.getConfigurationSection(questId);
            if (qSec == null) continue;
            PlayerQuestProgress p = new PlayerQuestProgress(questId, quest.getObjectives().size());
            try { p.setStatus(QuestStatus.valueOf(qSec.getString("status", "IN_PROGRESS"))); }
            catch (Exception ignored) {}
            List<Integer> progressList = qSec.getIntegerList("progress");
            for (int i = 0; i < progressList.size() && i < quest.getObjectives().size(); i++)
                p.setProgress(i, progressList.get(i));
            result.put(questId, p);
        }
        return result;
    }

    public void savePlayerProgress(UUID uuid) {
        Map<String, PlayerQuestProgress> progress = playerProgress.get(uuid);
        if (progress == null) return;
        File file = new File(playerDataDir, uuid + ".yml");
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<String, PlayerQuestProgress> entry : progress.entrySet()) {
            PlayerQuestProgress p = entry.getValue();
            String path = "quests." + entry.getKey();
            config.set(path + ".status", p.getStatus().name());
            config.set(path + ".progress", p.getAllProgress());
        }
        try { config.save(file); }
        catch (IOException e) { plugin.getLogger().warning("[QuestManager] Save failed for " + uuid + ": " + e.getMessage()); }
    }

    public void saveAll() { playerProgress.keySet().forEach(this::savePlayerProgress); }

    public void onPlayerQuit(UUID uuid) {
        savePlayerProgress(uuid);
        playerProgress.remove(uuid);
    }

    public void reload() {
        saveAll();
        playerProgress.clear();
        loadQuests();
    }

    public Collection<QuestData> getAllQuests()  { return quests.values(); }
    public QuestData getQuest(String id)         { return quests.get(id); }
    public Map<String, QuestData> getQuestsMap() { return quests; }
}
"""

quest_gui = r"""package com.customrpg.quest;

import com.customrpg.CustomRPG;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class QuestGUI implements Listener {

    private static final String TITLE_PREFIX = ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "\u4efb\u52d9\u5217\u8868";
    private final CustomRPG plugin;
    private final QuestManager questManager;
    private final Map<UUID, List<String>> openQuestIds = new HashMap<>();

    public QuestGUI(CustomRPG plugin, QuestManager questManager) {
        this.plugin = plugin;
        this.questManager = questManager;
    }

    public void open(Player player) { open(player, null); }

    public void open(Player player, String npcKey) {
        List<QuestData> visible = new ArrayList<>();
        for (QuestData q : questManager.getAllQuests()) {
            if (npcKey != null && !npcKey.equals(q.getNpcKey())) continue;
            QuestStatus status = questManager.getQuestStatus(player, q.getId());
            if (status != QuestStatus.UNAVAILABLE) visible.add(q);
        }
        int size = Math.max(9, ((visible.size() / 9) + 1) * 9);
        if (size > 54) size = 54;
        String title = TITLE_PREFIX + (npcKey != null ? " \u00a77[" + npcKey + "]" : "");
        Inventory inv = Bukkit.createInventory(null, size, title);
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < visible.size() && i < size; i++) {
            QuestData q = visible.get(i);
            ids.add(q.getId());
            inv.setItem(i, buildQuestItem(player, q));
        }
        openQuestIds.put(player.getUniqueId(), ids);
        player.openInventory(inv);
    }

    private ItemStack buildQuestItem(Player player, QuestData quest) {
        QuestStatus status = questManager.getQuestStatus(player, quest.getId());
        Material mat;
        String statusTag;
        switch (status) {
            case AVAILABLE   -> { mat = Material.LIME_WOOL;              statusTag = ChatColor.GREEN + "[\u53ef\u63a5\u53d7]"; }
            case IN_PROGRESS -> { mat = Material.YELLOW_WOOL;            statusTag = ChatColor.YELLOW + "[\u9032\u884c\u4e2d]"; }
            case COMPLETED   -> { mat = Material.GOLD_BLOCK;             statusTag = ChatColor.GOLD + "[\u53ef\u9818\u734e]"; }
            case TURNED_IN   -> { mat = Material.GRAY_WOOL;              statusTag = ChatColor.GRAY + "[\u5df2\u5b8c\u6210]"; }
            default          -> { mat = Material.RED_STAINED_GLASS_PANE; statusTag = ChatColor.RED + "[\u689d\u4ef6\u4e0d\u8db3]"; }
        }
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + quest.getName() + " " + statusTag);
        List<String> lore = new ArrayList<>();
        if (quest.getDescription() != null && !quest.getDescription().isEmpty())
            lore.add(ChatColor.GRAY + quest.getDescription());
        lore.add("");
        Map<String, PlayerQuestProgress> all = questManager.getPlayerProgress(player.getUniqueId());
        PlayerQuestProgress p = all.get(quest.getId());
        lore.add(ChatColor.YELLOW + "\u4efb\u52d9\u76ee\u6a19:");
        List<QuestObjective> objs = quest.getObjectives();
        for (int i = 0; i < objs.size(); i++) {
            QuestObjective obj = objs.get(i);
            int prog = (p != null) ? p.getProgress(i) : 0;
            boolean done = prog >= obj.getAmount();
            String check = done ? ChatColor.GREEN + "\u2714 " : ChatColor.GRAY + "\u25cb ";
            lore.add(check + ChatColor.WHITE + obj.formatDescription(prog));
        }
        QuestReward reward = quest.getReward();
        if (reward.getExp() > 0 || reward.getStatPoints() > 0 || !reward.getEquipmentIds().isEmpty()) {
            lore.add("");
            lore.add(ChatColor.AQUA + "\u734e\u52f5:");
            if (reward.getExp() > 0) lore.add(ChatColor.GREEN + "  +" + reward.getExp() + " \u7d93\u9a57\u503c");
            if (reward.getStatPoints() > 0) lore.add(ChatColor.GREEN + "  +" + reward.getStatPoints() + " \u5c6c\u6027\u9ede");
            if (reward.getTalentPoints() > 0) lore.add(ChatColor.GREEN + "  +" + reward.getTalentPoints() + " \u5929\u8ce6\u9ede");
            for (String eid : reward.getEquipmentIds())
                lore.add(ChatColor.GREEN + "  \u88dd\u5099: " + eid);
        }
        if (status == QuestStatus.AVAILABLE) { lore.add(""); lore.add(ChatColor.DARK_GREEN + "\u2192 \u9ede\u64ca\u63a5\u53d7\u4efb\u52d9"); }
        if (status == QuestStatus.COMPLETED) { lore.add(""); lore.add(ChatColor.GOLD + "\u2192 \u9ede\u64ca\u9818\u53d6\u734e\u52f5"); }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getCurrentItem() == null) return;
        String title = event.getView().getTitle();
        if (!title.startsWith(TITLE_PREFIX)) return;
        event.setCancelled(true);
        List<String> ids = openQuestIds.get(player.getUniqueId());
        if (ids == null) return;
        int slot = event.getSlot();
        if (slot < 0 || slot >= ids.size()) return;
        String questId = ids.get(slot);
        QuestStatus status = questManager.getQuestStatus(player, questId);
        if (status == QuestStatus.AVAILABLE) {
            questManager.acceptQuest(player, questId);
            player.closeInventory();
        } else if (status == QuestStatus.COMPLETED) {
            questManager.turnInQuest(player, questId);
            player.closeInventory();
        } else if (status == QuestStatus.IN_PROGRESS) {
            player.sendMessage(ChatColor.YELLOW + "[\u4efb\u52d9] \u4efb\u52d9\u5c1a\u672a\u5b8c\u6210\uff0c\u7e7c\u7e8c\u52aa\u529b\uff01");
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        openQuestIds.remove(event.getPlayer().getUniqueId());
    }
}
"""

quest_listener = r"""package com.customrpg.quest;

import com.customrpg.CustomRPG;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class QuestListener implements Listener {

    private final CustomRPG plugin;
    private final QuestManager questManager;

    public QuestListener(CustomRPG plugin, QuestManager questManager) {
        this.plugin = plugin;
        this.questManager = questManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack item = event.getItem().getItemStack();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        String equipId = meta.getPersistentDataContainer()
                .get(QuestManager.EQUIPMENT_ID_KEY, PersistentDataType.STRING);
        if (equipId == null) return;
        questManager.onEquipmentObtained(player, equipId);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        questManager.onPlayerQuit(event.getPlayer().getUniqueId());
    }
}
"""

quest_command = r"""package com.customrpg.commands;

import com.customrpg.CustomRPG;
import com.customrpg.quest.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class QuestCommand implements CommandExecutor, TabCompleter {

    private final CustomRPG plugin;
    private final QuestManager questManager;
    private final QuestGUI questGUI;

    public QuestCommand(CustomRPG plugin, QuestManager questManager, QuestGUI questGUI) {
        this.plugin = plugin;
        this.questManager = questManager;
        this.questGUI = questGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }
        if (args.length == 0) { questGUI.open(player); return true; }
        switch (args[0].toLowerCase()) {
            case "list" -> questGUI.open(player);
            case "info" -> {
                if (args.length < 2) { player.sendMessage(ChatColor.RED + "\u7528\u6cd5: /quest info <questId>"); return true; }
                QuestData q = questManager.getQuest(args[1]);
                if (q == null) { player.sendMessage(ChatColor.RED + "\u627e\u4e0d\u5230\u4efb\u52d9: " + args[1]); return true; }
                sendQuestInfo(player, q);
            }
            case "admin" -> {
                if (!player.hasPermission("customrpg.quest.admin")) { player.sendMessage(ChatColor.RED + "\u6b0a\u9650\u4e0d\u8db3\u3002"); return true; }
                handleAdmin(player, args);
            }
            default -> player.sendMessage(ChatColor.RED + "\u7528\u6cd5: /quest [list|info <id>|admin ...]");
        }
        return true;
    }

    private void sendQuestInfo(Player player, QuestData q) {
        QuestStatus status = questManager.getQuestStatus(player, q.getId());
        player.sendMessage(ChatColor.GOLD + "== " + q.getName() + " ==");
        if (q.getDescription() != null && !q.getDescription().isEmpty())
            player.sendMessage(ChatColor.GRAY + q.getDescription());
        player.sendMessage(ChatColor.YELLOW + "\u72c0\u614b: " + statusString(status));
        Map<String, PlayerQuestProgress> all = questManager.getPlayerProgress(player.getUniqueId());
        PlayerQuestProgress p = all.get(q.getId());
        player.sendMessage(ChatColor.YELLOW + "\u76ee\u6a19:");
        List<QuestObjective> objs = q.getObjectives();
        for (int i = 0; i < objs.size(); i++) {
            QuestObjective obj = objs.get(i);
            int prog = (p != null) ? p.getProgress(i) : 0;
            player.sendMessage("  " + (prog >= obj.getAmount() ? ChatColor.GREEN + "\u2714" : ChatColor.GRAY + "\u25cb")
                    + " " + ChatColor.WHITE + obj.formatDescription(prog));
        }
    }

    private String statusString(QuestStatus s) {
        return switch (s) {
            case AVAILABLE   -> ChatColor.GREEN + "\u53ef\u63a5\u53d7";
            case IN_PROGRESS -> ChatColor.YELLOW + "\u9032\u884c\u4e2d";
            case COMPLETED   -> ChatColor.GOLD + "\u5f85\u9818\u734e";
            case TURNED_IN   -> ChatColor.GRAY + "\u5df2\u5b8c\u6210";
            default          -> ChatColor.RED + "\u689d\u4ef6\u4e0d\u8db3";
        };
    }

    private void handleAdmin(Player admin, String[] args) {
        if (args.length < 2) { admin.sendMessage(ChatColor.YELLOW + "\u5b50\u6307\u4ee4: give <player> <questId> | complete <player> <questId> | reload"); return; }
        switch (args[1].toLowerCase()) {
            case "reload" -> { questManager.reload(); admin.sendMessage(ChatColor.GREEN + "[\u4efb\u52d9] \u5df2\u91cd\u8f09\u3002"); }
            case "give" -> {
                if (args.length < 4) { admin.sendMessage(ChatColor.RED + "\u7528\u6cd5: /quest admin give <player> <questId>"); return; }
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) { admin.sendMessage(ChatColor.RED + "\u73a9\u5bb6\u4e0d\u5728\u7dda: " + args[2]); return; }
                QuestData q = questManager.getQuest(args[3]);
                if (q == null) { admin.sendMessage(ChatColor.RED + "\u4efb\u52d9\u4e0d\u5b58\u5728: " + args[3]); return; }
                Map<String, PlayerQuestProgress> all = questManager.getPlayerProgress(target.getUniqueId());
                PlayerQuestProgress p = new PlayerQuestProgress(args[3], q.getObjectives().size());
                p.setStatus(QuestStatus.AVAILABLE);
                all.put(args[3], p);
                questManager.savePlayerProgress(target.getUniqueId());
                admin.sendMessage(ChatColor.GREEN + "\u5df2\u89e3\u9396\u4efb\u52d9 " + args[3] + " \u7d66 " + target.getName());
                target.sendMessage(ChatColor.GOLD + "[\u4efb\u52d9] \u7ba1\u7406\u54e1\u5df2\u89e3\u9396\u65b0\u4efb\u52d9\uff1a" + q.getName());
            }
            case "complete" -> {
                if (args.length < 4) { admin.sendMessage(ChatColor.RED + "\u7528\u6cd5: /quest admin complete <player> <questId>"); return; }
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) { admin.sendMessage(ChatColor.RED + "\u73a9\u5bb6\u4e0d\u5728\u7dda: " + args[2]); return; }
                QuestData q = questManager.getQuest(args[3]);
                if (q == null) { admin.sendMessage(ChatColor.RED + "\u4efb\u52d9\u4e0d\u5b58\u5728: " + args[3]); return; }
                Map<String, PlayerQuestProgress> all = questManager.getPlayerProgress(target.getUniqueId());
                PlayerQuestProgress p = all.computeIfAbsent(args[3], k -> new PlayerQuestProgress(k, q.getObjectives().size()));
                for (int i = 0; i < q.getObjectives().size(); i++) p.setProgress(i, q.getObjectives().get(i).getAmount());
                p.setStatus(QuestStatus.COMPLETED);
                questManager.savePlayerProgress(target.getUniqueId());
                admin.sendMessage(ChatColor.GREEN + "\u5df2\u5b8c\u6210\u4efb\u52d9 " + args[3] + " \u7d66 " + target.getName());
            }
            default -> admin.sendMessage(ChatColor.RED + "\u672a\u77e5\u5b50\u6307\u4ee4: " + args[1]);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) return Arrays.asList("list", "info", "admin");
        if (args.length == 2 && args[0].equalsIgnoreCase("info")) return new ArrayList<>(questManager.getQuestsMap().keySet());
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) return Arrays.asList("give", "complete", "reload");
        if (args.length == 3 && args[0].equalsIgnoreCase("admin")) return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
        if (args.length == 4 && args[0].equalsIgnoreCase("admin")) return new ArrayList<>(questManager.getQuestsMap().keySet());
        return Collections.emptyList();
    }
}
"""

writes = [
    (os.path.join(base, 'QuestManager.java'), quest_manager),
    (os.path.join(base, 'QuestGUI.java'), quest_gui),
    (os.path.join(base, 'QuestListener.java'), quest_listener),
    (os.path.join(cmd_base, 'QuestCommand.java'), quest_command),
]

for path, content in writes:
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    first = open(path, 'rb').read(1).hex()
    print(f'Written {os.path.basename(path)}: first_byte={first}')

