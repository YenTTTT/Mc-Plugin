package com.customrpg.commands;
import com.customrpg.CustomRPG;
import com.customrpg.managers.DistanceSpawnManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import java.util.ArrayList;
import java.util.List;
/**
 * /finishrpg - 啟動整套 RPG 自訂怪物生成系統
 */
public class FinishRpgCommand implements CommandExecutor, TabCompleter {
    private final CustomRPG plugin;
    public FinishRpgCommand(CustomRPG plugin) {
        this.plugin = plugin;
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("customrpg.admin")) {
            sender.sendMessage(ChatColor.RED + "你沒有權限使用此指令！");
            return true;
        }
        DistanceSpawnManager manager = plugin.getDistanceSpawnManager();
        if (manager == null) {
            sender.sendMessage(ChatColor.RED + "DistanceSpawnManager 尚未初始化！");
            return true;
        }
        if (manager.isActive()) {
            sender.sendMessage(ChatColor.YELLOW + "RPG 自訂怪物生成系統已經啟動。\n" + ChatColor.GRAY + "使用 /mobspawn status 查看目前狀態。");
            return true;
        }
        if (!manager.activateSystem()) {
            sender.sendMessage(ChatColor.RED + "配置中已停用自訂怪物生成系統，請先檢查 mob_spawner.yml 的 enabled 設定。");
            return true;
        }
        sender.sendMessage(ChatColor.GREEN + "✓ 已啟動 RPG 自訂怪物生成系統");
        sender.sendMessage(ChatColor.YELLOW + "- 距離重生點越遠，怪物等級越高");
        sender.sendMessage(ChatColor.YELLOW + "- 安全區已開始阻止敵對怪物生成");
        sender.sendMessage(ChatColor.YELLOW + "- Boss 區域排他生成已生效");
        sender.sendMessage(ChatColor.GRAY + "可使用 /mobspawn status 查看診斷資訊。");
        return true;
    }
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return new ArrayList<>();
    }
}