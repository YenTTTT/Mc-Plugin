package com.customrpg.balance;

import com.customrpg.CustomRPG;
import com.customrpg.equipment.EquipmentData;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

/** Lazy, metadata-only item display refresh; never reissues items or changes amount/enchantments/durability. */
public final class BalanceItemRefresh implements Listener {
    private static final NamespacedKey VERSION=new NamespacedKey("customrpg","item_balance_version");
    private static final NamespacedKey SPEED=new NamespacedKey("customrpg","balanced_attack_speed");
    private final CustomRPG plugin;
    public BalanceItemRefresh(CustomRPG plugin) { this.plugin=plugin; }

    public void refresh(ItemStack item) {
        if (item==null||item.getType()==org.bukkit.Material.AIR||!item.hasItemMeta()) return;
        var meta=item.getItemMeta();
        if (Integer.valueOf(2).equals(meta.getPersistentDataContainer().get(VERSION,PersistentDataType.INTEGER))) return;
        var weapons=plugin.getWeaponManager();
        String weapon=weapons.getWeaponKey(item);
        ItemStack canonical=null;
        if (weapon!=null&&weapons.getWeaponData(weapon)!=null) canonical=weapons.createWeapon(weapon);
        else {
            String id=meta.getPersistentDataContainer().get(EquipmentData.PDC_EQUIPMENT_ID,PersistentDataType.STRING);
            if (id!=null) {
                var data=plugin.getEquipmentManager().createEquipment(id);
                if (data!=null) {
                    Integer enhancement=meta.getPersistentDataContainer().get(EquipmentData.PDC_ENHANCE_LEVEL,PersistentDataType.INTEGER);
                    for (int i=0;enhancement!=null&&i<Math.min(20,enhancement);i++) if (!data.enhance()) break;
                    canonical=data.toItemStack();
                }
            }
        }
        if (canonical==null||!canonical.hasItemMeta()) return;
        meta.lore(canonical.getItemMeta().lore());
        meta.getPersistentDataContainer().set(VERSION,PersistentDataType.INTEGER,2);
        item.setItemMeta(meta);
    }

    public void updateSpeed(Player player) {
        var attribute=player.getAttribute(Attribute.ATTACK_SPEED);
        if (attribute==null) return;
        removeSpeed(player);
        var item=player.getInventory().getItemInMainHand();
        String id=plugin.getWeaponManager().getWeaponKey(item);
        var data=id==null?null:plugin.getWeaponManager().getWeaponData(id);
        var category=data==null?com.customrpg.managers.WeaponManager.WeaponCategory.detectFromMaterial(item.getType(),false):data.getCategory();
        if (category==com.customrpg.managers.WeaponManager.WeaponCategory.UNKNOWN) return;
        double target=BalanceItemRules.weapon(category).attacksPerSecond;
        double scalar=0, multiply=1;
        for (var modifier:attribute.getModifiers()) {
            if (modifier.getOperation()==AttributeModifier.Operation.ADD_SCALAR) scalar+=modifier.getAmount();
            if (modifier.getOperation()==AttributeModifier.Operation.MULTIPLY_SCALAR_1) multiply*=1+modifier.getAmount();
        }
        double factor=Math.max(.01,(1+scalar)*multiply);
        attribute.addTransientModifier(new AttributeModifier(SPEED,(target-attribute.getValue())/factor,AttributeModifier.Operation.ADD_NUMBER));
    }
    public void removeSpeed(Player player) {
        var attribute=player.getAttribute(Attribute.ATTACK_SPEED);
        if (attribute!=null) for (var modifier:new ArrayList<>(attribute.getModifiers())) if (SPEED.equals(modifier.getKey())) attribute.removeModifier(modifier);
    }
    private void later(Player player) {
        plugin.getServer().getScheduler().runTask(plugin,()->{
            if (!player.isOnline()) return;
            for (ItemStack item:player.getInventory().getContents()) refresh(item);
            updateSpeed(player);
        });
    }
    @EventHandler(priority=EventPriority.MONITOR) public void join(PlayerJoinEvent event) { later(event.getPlayer()); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void held(PlayerItemHeldEvent event) { later(event.getPlayer()); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void swap(PlayerSwapHandItemsEvent event) { later(event.getPlayer()); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void open(InventoryOpenEvent event) {
        // Crafting/merchant UI outputs have transaction-sensitive identity: refresh on receipt/use instead.
        if (event.getInventory().getType()==org.bukkit.event.inventory.InventoryType.MERCHANT
                ||event.getInventory().getType()==org.bukkit.event.inventory.InventoryType.WORKBENCH) return;
        if (event.getPlayer() instanceof Player player) later(player);
    }
    @EventHandler public void quit(PlayerQuitEvent event) { removeSpeed(event.getPlayer()); }
}
