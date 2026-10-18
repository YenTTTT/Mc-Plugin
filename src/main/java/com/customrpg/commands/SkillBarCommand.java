package com.customrpg.commands;

import com.customrpg.CustomRPG;
import com.customrpg.listeners.SkillBarListener;
import com.customrpg.managers.SkillBarManager;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/**
 * SkillBarCommand - /skillbar 指令
 *
 * 切換快捷技能列功能 (開/關)。
 * 開啟後，鍵盤 1～5 直接施放對應技能槽的攜帶技能。
 * 關閉後恢復原生熱鍵欄行為，天賦與種族技能暫停快捷施放。
 */
public class SkillBarCommand implements CommandExecutor, TabCompleter {

    private final CustomRPG plugin;
    private final SkillBarManager skillBarManager;
    private final SkillBarListener skillBarListener;

    public SkillBarCommand(CustomRPG plugin, SkillBarManager skillBarManager, SkillBarListener skillBarListener) {
        this.plugin = plugin;
        this.skillBarManager = skillBarManager;
        this.skillBarListener = skillBarListener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "此指令只能由玩家執行！");
            return true;
        }

        boolean nowEnabled = skillBarManager.toggle(player);

        if (nowEnabled) {
            player.sendMessage(ChatColor.GREEN + "╔═══════════════════════════╗");
            player.sendMessage(ChatColor.GREEN + "  ⚡ 快捷技能列 §l已開啟§r§a！");
            player.sendMessage(ChatColor.YELLOW + "  §l1～4§r§e 天賦技能，§l5§r§e 種族技能；武器放 6～9");
            player.sendMessage(ChatColor.GRAY  + "  (使用 /skillbar 可再次關閉)");
            player.sendMessage(ChatColor.GREEN + "╚═══════════════════════════╝");
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.4f);

            // 立即顯示一次 HUD
            skillBarListener.showSkillBarHud(player, -1);
        } else {
            player.sendMessage(ChatColor.RED + "⚡ 快捷技能列 §l已關閉§r§c。");
            player.sendMessage(ChatColor.GRAY + "  已恢復物品欄 1～9；再次 /skillbar 可開啟技能快捷鍵。");
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.7f, 1.0f);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}

