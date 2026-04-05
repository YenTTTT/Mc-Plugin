package com.customrpg.listeners;

import com.customrpg.CustomRPG;
import com.customrpg.commands.MenuCommand;
import com.customrpg.gui.MenuGUI;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * MenuListener — 監聽主選單指南針的右鍵點擊
 */
public class MenuListener implements Listener {

    private final CustomRPG plugin;
    private final MenuGUI menuGUI;
    private final NamespacedKey compassKey;

    public MenuListener(CustomRPG plugin, MenuGUI menuGUI) {
        this.plugin = plugin;
        this.menuGUI = menuGUI;
        this.compassKey = new NamespacedKey(plugin, MenuCommand.COMPASS_KEY);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // 只監聽右鍵
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!isMenuCompass(item)) return;

        // 取消預設行為 & 開啟選單
        event.setCancelled(true);
        menuGUI.open(player);
    }

    /**
     * 檢查物品是否為主選單指南針
     */
    private boolean isMenuCompass(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(compassKey, PersistentDataType.BOOLEAN);
    }
}

