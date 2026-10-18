package com.customrpg.commands;
import com.customrpg.CustomRPG;
import com.customrpg.managers.DistanceSpawnManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
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
            sender.sendMessage(ChatColor.YELLOW + "RPG 自訂怪物生成系統已經啟動。");
            sender.sendMessage(ChatColor.GRAY + "使用 /mobspawn status 查看目前狀態。");
            // 即使已啟動，也檢查並提示常見問題
            checkAndWarnIssues(sender);
            return true;
        }
        if (!manager.activateSystem()) {
            sender.sendMessage(ChatColor.RED + "配置中已停用自訂怪物生成系統，請先檢查 mob_spawner.yml 的 enabled 設定。");
            return true;
        }
        sender.sendMessage(ChatColor.GREEN + "✓ 已啟動 RPG 自訂怪物生成系統");
        sender.sendMessage(ChatColor.YELLOW + "- 依世界出生點水平距離分區，不使用玩家等級縮放");
        sender.sendMessage(ChatColor.YELLOW + (manager.isVanillaMode()
                ? "- 原版自然生成 → 環境 / 時段 / 等級匹配後替換" : "- 玩家周圍定時補怪模式"));
        sender.sendMessage(ChatColor.YELLOW + "- 安全區與追擊範圍限制已套用；舊地圖 Boss 區域需另行啟用");
        sender.sendMessage(ChatColor.GRAY + "可使用 /mobspawn status 查看診斷資訊。");
        // 啟動後立即做環境檢查，提前告知常見問題
        checkAndWarnIssues(sender);
        return true;
    }

    /**
     * 主動檢查並提示可能導致怪物不生成的常見問題
     */
    private void checkAndWarnIssues(CommandSender sender) {
        boolean hasWarning = false;

        // 1. 檢查怪物是否已載入
        int mobCount = plugin.getMobManager().getNormalMobKeys().size();
        if (mobCount == 0) {
            sender.sendMessage(ChatColor.RED + "⚠ 警告：沒有載入任何普通怪物！");
            sender.sendMessage(ChatColor.YELLOW + "  請確認 config/mobs/types/ 資料夾下有 .yml 設定檔。");
            hasWarning = true;
        } else {
            sender.sendMessage(ChatColor.DARK_GRAY + "  已載入 " + mobCount + " 種普通怪物。");
        }

        if (sender instanceof Player player) {
            var warnings = plugin.getDistanceSpawnManager().getSpawnWarnings(player);
            if (!warnings.isEmpty()) hasWarning = true;
            for (String warning : warnings) sender.sendMessage("§c⚠ " + warning);
        }
        if (!hasWarning) {
            sender.sendMessage(ChatColor.GREEN + (plugin.getDistanceSpawnManager().isVanillaMode()
                    ? "  環境檢查通過，請在原版允許敵怪生成的位置等待自然生成（需符合黑暗、距離及數量規則）。"
                    : "  環境檢查通過，怪物將依配置的補怪週期嘗試生成。"));
            sender.sendMessage(ChatColor.GRAY + "  若仍無怪物，請執行 /mobspawn debug 後等待 30 秒查看 console 輸出。");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return new ArrayList<>();
    }
}
