package com.customrpg.managers;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/** Default-on hotkeys, with an explicit per-player opt-out stored in the player's vanilla data. */
public class SkillBarManager {
    private static final NamespacedKey ENABLED = new NamespacedKey("customrpg", "skillbar_enabled");

    public boolean isEnabled(Player player) {
        Byte value = player.getPersistentDataContainer().get(ENABLED, PersistentDataType.BYTE);
        return value == null || value != 0;
    }
    public boolean toggle(Player player) {
        boolean enabled = !isEnabled(player);
        player.getPersistentDataContainer().set(ENABLED, PersistentDataType.BYTE, (byte) (enabled ? 1 : 0));
        if (enabled) prepareHeldSlot(player);
        return enabled;
    }
    /** A non-reserved anchor is required: selecting an already-held vanilla slot sends no packet. */
    public void prepareHeldSlot(Player player) {
        if (isEnabled(player) && player.getInventory().getHeldItemSlot() < 5) player.getInventory().setHeldItemSlot(5);
    }
    public void disable(Player player) {
        player.getPersistentDataContainer().set(ENABLED, PersistentDataType.BYTE, (byte) 0);
    }
    public void cleanup() { /* Preferences live in player PDC; nothing to discard on logout. */ }
}
