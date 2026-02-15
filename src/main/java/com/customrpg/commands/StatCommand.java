package com.customrpg.commands;

import com.customrpg.CustomRPG;
import com.customrpg.managers.PlayerStatsManager;
import com.customrpg.players.PlayerStats;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * StatCommand - 聊天室顯示完整角色資訊
 *
 * 用法: /stat
 *
 * 顯示內容：
 * - 角色基本資訊（ID、種族、經驗）
 * - 主屬性（力量、智慧、敏捷、體力、精神）
 * - 防禦屬性（物理防禦、魔法防禦）
 * - 副屬性（血量、Mana、暴擊率、暴擊傷害、移動速度、穿透、迴避）
 * - 武器加成（武器傷害加成）
 */
public class StatCommand implements CommandExecutor {
    private final CustomRPG plugin;
    private final PlayerStatsManager statsManager;

    public StatCommand(CustomRPG plugin) {
        this.plugin = plugin;
        this.statsManager = plugin.getPlayerStatsManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "只有玩家可以使用此指令！");
            return true;
        }

        displayPlayerStats(player);
        return true;
    }

    /**
     * 顯示玩家完整屬性資訊
     */
    private void displayPlayerStats(Player player) {
        PlayerStats stats = statsManager.getStats(player);

        // 計算所有屬性
        StatCalculation calc = calculateAllStats(player, stats);

        // 發送訊息
        sendHeader(player, "角色資訊");
        sendBasicInfo(player, stats);

        sendHeader(player, "主屬性");
        sendMainStats(player, calc);

        sendHeader(player, "防禦");
        sendDefenseStats(player, calc);

        sendHeader(player, "副屬性");
        sendSecondaryStats(player, calc);

        sendHeader(player, "武器傷害加成");
        sendRacialBonus(player);
    }

    /**
     * 計算所有屬性
     */
    private StatCalculation calculateAllStats(Player player, PlayerStats stats) {
        StatCalculation calc = new StatCalculation();

        // 裝備加成
        calc.equipStrength = stats.getEquipmentStrength();
        calc.equipMagic = stats.getEquipmentMagic();
        calc.equipAgility = stats.getEquipmentAgility();
        calc.equipVitality = stats.getEquipmentVitality();
        calc.equipDefense = stats.getEquipmentDefense();
        calc.equipSpirit = stats.getEquipmentSpirit();

        // 總屬性
        calc.totalStrength = stats.getTotalStrength();
        calc.totalMagic = stats.getTotalMagic();
        calc.totalAgility = stats.getTotalAgility();
        calc.totalVitality = stats.getTotalVitality();
        calc.totalDefense = stats.getTotalDefense();
        calc.totalSpirit = stats.getTotalSpirit();

        // 副屬性計算
        calc.maxHealth = player.getHealth();
        calc.currentHealth = player.getHealth();
        calc.maxMana = stats.getMaxMana();
        calc.currentMana = stats.getCurrentMana();

        // 暴擊率：基礎 5% + 每點敏捷 0.5%
        calc.critChance = 5.0 + (calc.totalAgility * 0.5) + stats.getBonusCritChance();

        // 暴擊傷害：基礎 150% + 每點力量 1%
        calc.critDamage = 150.0 + (calc.totalStrength * 1.0) + stats.getBonusCritDamage();

        // 移動速度：基礎 0% + 每點敏捷 0.3%
        calc.moveSpeed = calc.totalAgility * 0.3;

        // 穿透：每 10 點力量 1 點穿透
        calc.penetration = calc.totalStrength / 10;

        // 迴避：基礎 3% + 每點敏捷 0.4%
        calc.evasion = 3.0 + (calc.totalAgility * 0.4);

        // 防禦值
        calc.physicalDefense = calc.totalDefense + stats.getBonusDefenseValue();
        calc.magicDefense = (calc.totalMagic / 2.0) + stats.getBonusDefenseValue();

        return calc;
    }

    /**
     * 發送標題分隔線
     */
    private void sendHeader(Player player, String title) {
        player.sendMessage(ChatColor.GOLD + "====== " + ChatColor.YELLOW + title + ChatColor.GOLD + " ======");
    }

    /**
     * 發送基本資訊
     */
    private void sendBasicInfo(Player player, PlayerStats stats) {
        long currentExp = stats.getExp();
        long requiredExp = statsManager.getRequiredExp(stats.getLevel());
        int level = stats.getLevel();

        // ID
        player.sendMessage(ChatColor.GRAY + "ID: " + ChatColor.WHITE + player.getName());

        // 種族
        player.sendMessage(ChatColor.GRAY + "種族: " + ChatColor.YELLOW + "人類 " + level + "等級");

        // 攜帶技能 (天賦系統選取的技能)
        com.customrpg.managers.TalentManager tm = plugin.getTalentManager();
        if (tm != null) {
            com.customrpg.players.PlayerTalents pt = tm.getPlayerTalents(player);
            String[] selected = pt.getSelectedSkills();
            java.util.List<String> names = new java.util.ArrayList<>();
            for (String id : selected) {
                if (id != null) {
                    com.customrpg.talents.Talent t = tm.findTalent(id);
                    if (t != null) names.add(t.getName());
                }
            }
            if (names.isEmpty()) {
                player.sendMessage(ChatColor.GRAY + "攜帶技能: " + ChatColor.DARK_GRAY + "無");
            } else {
                player.sendMessage(ChatColor.GRAY + "攜帶技能: " + ChatColor.LIGHT_PURPLE + String.join(", ", names));
            }
        } else {
            player.sendMessage(ChatColor.GRAY + "攜帶技能: " + ChatColor.DARK_GRAY + "無");
        }

        // 經驗值
        player.sendMessage(ChatColor.GRAY + "經驗值: " +
                          ChatColor.GREEN + currentExp + ChatColor.GRAY + " / " +
                          ChatColor.YELLOW + requiredExp);
    }

    /**
     * 發送主屬性
     */
    private void sendMainStats(Player player, StatCalculation calc) {
        // 第一行：力量 智慧
        player.sendMessage(formatStatLine("力量", calc.totalStrength, calc.equipStrength) + "  " +
                          formatStatLine("智慧", calc.totalMagic, calc.equipMagic));

        // 第二行：敏捷 體力
        player.sendMessage(formatStatLine("敏捷", calc.totalAgility, calc.equipAgility) + "  " +
                          formatStatLine("體力", calc.totalVitality, calc.equipVitality));

        // 第三行：精神
        player.sendMessage(formatStatLine("精神", calc.totalSpirit, calc.equipSpirit));
    }

    /**
     * 發送防禦屬性
     */
    private void sendDefenseStats(Player player, StatCalculation calc) {
        player.sendMessage(ChatColor.GRAY + "物理防禦: " + ChatColor.WHITE + String.format("%.0f", calc.physicalDefense) + "  " +
                          ChatColor.GRAY + "魔法防禦: " + ChatColor.WHITE + String.format("%.0f", calc.magicDefense));
    }

    /**
     * 發送副屬性
     */
    private void sendSecondaryStats(Player player, StatCalculation calc) {
        // 血量
        player.sendMessage(ChatColor.RED + "總血量: " + ChatColor.WHITE +
                          String.format("%.0f", calc.currentHealth) + ChatColor.GRAY + " / " +
                          ChatColor.WHITE + String.format("%.0f", calc.maxHealth));

        // Mana
        player.sendMessage(ChatColor.AQUA + "Mana: " + ChatColor.WHITE +
                          String.format("%.0f", calc.currentMana) + ChatColor.GRAY + " / " +
                          ChatColor.WHITE + String.format("%.0f", calc.maxMana));

        // 暴擊率
        player.sendMessage(ChatColor.YELLOW + "暴擊率: " + ChatColor.WHITE +
                          String.format("%.1f%%", calc.critChance));

        // 暴擊傷害
        player.sendMessage(ChatColor.GOLD + "暴擊傷害: " + ChatColor.WHITE +
                          String.format("%.0f%%", calc.critDamage));

        // 移動速度
        player.sendMessage(ChatColor.GREEN + "移動速度: " + ChatColor.WHITE +
                          (calc.moveSpeed > 0 ? "+" : "") + String.format("%.0f%%", calc.moveSpeed));

        // 穿透
        player.sendMessage(ChatColor.DARK_RED + "穿透: " + ChatColor.WHITE +
                          String.format("%d", calc.penetration));

        // 迴避
        player.sendMessage(ChatColor.LIGHT_PURPLE + "迴避: " + ChatColor.WHITE +
                          String.format("%.1f%%", calc.evasion));
    }

    /**
     * 發送武器傷害加成
     */
    private void sendRacialBonus(Player player) {
        com.customrpg.managers.TalentManager tm = plugin.getTalentManager();
        com.customrpg.players.PlayerStats stats = statsManager.getStats(player);
        
        if (tm == null || stats == null) {
            player.sendMessage(ChatColor.GRAY + "無法獲取天賦數據");
            return;
        }

        double meleeBonus = calculateCategoryBonus(player, tm, stats, "weapon_melee_mastery", null, 0);
        double firearmBonus = calculateCategoryBonus(player, tm, stats, "weapon_firearms_mastery", "AGILITY", 0.5);
        double magicBonus = calculateCategoryBonus(player, tm, stats, "weapon_magic_mastery", "MAGIC", 0.4);
        double bowBonus = calculateCategoryBonus(player, tm, stats, "infinite_arrows", null, 0);

        player.sendMessage(ChatColor.GOLD + "--- 武器天賦加成 ---");
        boolean hasAny = false;
        if (meleeBonus > 0) { player.sendMessage(ChatColor.GRAY + "近戰武器: " + ChatColor.GREEN + "+" + String.format("%.1f", meleeBonus)); hasAny = true; }
        if (firearmBonus > 0) { player.sendMessage(ChatColor.GRAY + "火槍: " + ChatColor.GREEN + "+" + String.format("%.1f", firearmBonus)); hasAny = true; }
        if (magicBonus > 0) { player.sendMessage(ChatColor.GRAY + "法杖: " + ChatColor.GREEN + "+" + String.format("%.1f", magicBonus)); hasAny = true; }
        if (bowBonus > 0) { player.sendMessage(ChatColor.GRAY + "弓箭: " + ChatColor.GREEN + "+" + String.format("%.1f", bowBonus)); hasAny = true; }
        
        if (!hasAny) {
            player.sendMessage(ChatColor.GRAY + "目前沒有任何武器加成");
        }
    }

    private double calculateCategoryBonus(Player player, com.customrpg.managers.TalentManager tm, 
                                        com.customrpg.players.PlayerStats stats, String talentId, 
                                        String scalingStat, double defaultMultiplier) {
        com.customrpg.players.PlayerTalents pt = tm.getPlayerTalents(player);
        int level = pt.getTalentLevel(talentId);
        if (level <= 0) return 0;

        com.customrpg.talents.Talent talent = tm.findTalent(talentId);
        if (talent == null) return 0;

        com.customrpg.talents.Talent.TalentLevelData data = talent.getLevelData(level);
        if (data == null) return 0;

        double bonus = data.effects.getOrDefault("weaponDamageBonus", 0.0);
        
        // 處理縮放
        if (scalingStat != null) {
            double multiplier = data.scaling.getOrDefault(scalingStat, 0.0);
            // 如果配置中沒有明確的 multiplier，但在 WeaponListener 中有硬編碼的邏輯
            // 這裡我們優先使用配置中的，如果配置中也沒有，則不計算（除非我們想在這裡也硬編碼）
            // 為了保持一致性，我們查看 WeaponListener 的邏輯
            
            int statValue = 0;
            if (scalingStat.equals("AGILITY")) statValue = stats.getAgility();
            else if (scalingStat.equals("MAGIC")) statValue = stats.getMagic();
            
            bonus += statValue * multiplier;
        }
        
        return bonus;
    }

    /**
     * 格式化屬性行（顯示總值和裝備加成）
     */
    private String formatStatLine(String name, int total, int equipBonus) {
        StringBuilder sb = new StringBuilder();
        sb.append(ChatColor.YELLOW).append(name).append(": ");
        sb.append(ChatColor.WHITE).append(total);

        if (equipBonus > 0) {
            sb.append(ChatColor.DARK_GRAY).append(" (");
            sb.append(ChatColor.GREEN).append("+").append(equipBonus);
            sb.append(ChatColor.DARK_GRAY).append(")");
        }

        return sb.toString();
    }

    /**
     * 屬性計算結果類
     */
    private static class StatCalculation {
        // 裝備加成
        int equipStrength, equipMagic, equipAgility, equipVitality, equipDefense, equipSpirit;

        // 總屬性
        int totalStrength, totalMagic, totalAgility, totalVitality, totalDefense, totalSpirit;

        // 副屬性
        double maxHealth, currentHealth;
        double maxMana, currentMana;
        double critChance, critDamage;
        double moveSpeed;
        int penetration;
        double evasion;

        // 防禦
        double physicalDefense, magicDefense;
    }
}

