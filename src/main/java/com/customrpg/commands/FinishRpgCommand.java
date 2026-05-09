package com.customrpg.commands;
import com.customrpg.CustomRPG;
import com.customrpg.managers.DistanceSpawnManager;
import com.customrpg.managers.SafeZoneManager;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
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
        sender.sendMessage(ChatColor.YELLOW + "- 距離重生點越遠，怪物等級越高");
        sender.sendMessage(ChatColor.YELLOW + "- 安全區已開始阻止敵對怪物生成");
        sender.sendMessage(ChatColor.YELLOW + "- Boss 區域排他生成已生效");
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

        // 2. 玩家相關檢查（只適用於玩家執行者）
        if (sender instanceof Player player) {
            // 2a. 遊戲模式檢查
            if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
                sender.sendMessage(ChatColor.RED + "⚠ 警告：你目前是 " + player.getGameMode().name() + " 模式！");
                sender.sendMessage(ChatColor.YELLOW + "  自訂怪物 " + ChatColor.BOLD + "不會" + ChatColor.YELLOW + " 在創造/旁觀者模式的玩家附近生成。");
                sender.sendMessage(ChatColor.YELLOW + "  請切換到生存模式 (/gamemode survival) 後再測試。");
                hasWarning = true;
            }

            // 2b. 安全區檢查
            SafeZoneManager safeZoneManager = plugin.getSafeZoneManager();
            if (safeZoneManager != null) {
                SafeZoneManager.SafeZone zone = safeZoneManager.getZoneAt(player.getLocation());
                if (zone != null) {
                    sender.sendMessage(ChatColor.RED + "⚠ 警告：你目前在安全區 [" + zone.getName() + "] 內！");
                    sender.sendMessage(ChatColor.YELLOW + "  安全區半徑為 " + (int)zone.getRadius() + " 格，安全區內 " + ChatColor.BOLD + "不會生成" + ChatColor.YELLOW + " 自訂怪物。");
                    sender.sendMessage(ChatColor.YELLOW + "  請移動到距當前位置 " + (int)zone.getRadius() + " 格以外的野外區域再測試。");
                    hasWarning = true;
                }
            }

            // 2c. 世界難度檢查
            if (player.getWorld().getDifficulty() == org.bukkit.Difficulty.PEACEFUL) {
                sender.sendMessage(ChatColor.RED + "⚠ 警告：當前世界難度為 PEACEFUL！");
                sender.sendMessage(ChatColor.YELLOW + "  大多數敵對怪物（殭屍、骷髏等）在和平模式下會被立即移除。");
                hasWarning = true;
            }
        }

        if (!hasWarning) {
            sender.sendMessage(ChatColor.GREEN + "  環境檢查通過，怪物應在 " + ChatColor.WHITE + "3~10 秒" + ChatColor.GREEN + " 內出現在你附近。");
            sender.sendMessage(ChatColor.GRAY + "  若仍無怪物，請執行 /mobspawn debug 後等待 30 秒查看 console 輸出。");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return new ArrayList<>();
    }
}