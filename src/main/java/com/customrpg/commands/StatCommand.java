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
 * - 種族加乘（武器傷害加成）
 */
public class StatCommand implements CommandExecutor {

    private final PlayerStatsManager statsManager;

    public StatCommand(CustomRPG plugin) {
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

        sendHeader(player, "種族加乘");
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

        // 總屬性
        calc.totalStrength = stats.getTotalStrength();
        calc.totalMagic = stats.getTotalMagic();
        calc.totalAgility = stats.getTotalAgility();
        calc.totalVitality = stats.getTotalVitality();
        calc.totalDefense = stats.getTotalDefense();

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
        // ID
        player.sendMessage(ChatColor.GRAY + "ID: " + ChatColor.WHITE + player.getName());

        // 種族（目前固定為人類，未來可擴展）
        player.sendMessage(ChatColor.GRAY + "種族: " + ChatColor.YELLOW + "人類");

        // 攜帶技能（暫時顯示為無，未來可以從天賦系統獲取）
        player.sendMessage(ChatColor.GRAY + "攜帶技能: " + ChatColor.DARK_GRAY + "無");

        // 經驗值
        long currentExp = stats.getExp();
        long requiredExp = statsManager.getRequiredExp(stats.getLevel());
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

        // 第三行：精神（映射為魔法）
        player.sendMessage(formatStatLine("精神", calc.totalMagic, calc.equipMagic));
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
     * 發送種族加乘
     */
    private void sendRacialBonus(Player player) {
        // 目前固定顯示人類的武器加乘
        // 未來可以根據玩家種族動態調整
        player.sendMessage(ChatColor.GRAY + "木劍: " + ChatColor.GREEN + "+5 傷害");
        player.sendMessage(ChatColor.GRAY + "石劍: " + ChatColor.GREEN + "+5 傷害");
        player.sendMessage(ChatColor.GRAY + "鐵劍: " + ChatColor.GREEN + "+5 傷害");
        player.sendMessage(ChatColor.GRAY + "黃金劍: " + ChatColor.GREEN + "+5 傷害");
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
        int equipStrength, equipMagic, equipAgility, equipVitality, equipDefense;

        // 總屬性
        int totalStrength, totalMagic, totalAgility, totalVitality, totalDefense;

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

