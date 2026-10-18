package com.customrpg.listeners;

import com.customrpg.CustomRPG;
import com.customrpg.managers.ManaManager;
import com.customrpg.weaponSkills.SkillTriggerType;
import com.customrpg.weaponSkills.managers.SkillManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class SkillInteractionTest {
    @Test void bothSkillListenersIgnoreOffhandEvent() {
        SkillManager weaponSkills = mock(SkillManager.class);
        com.customrpg.managers.SkillManager legacySkills = mock(com.customrpg.managers.SkillManager.class);
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getHand()).thenReturn(EquipmentSlot.OFF_HAND);
        new SkillTriggerListener(weaponSkills).onInteract(event);
        new SkillListener(mock(CustomRPG.class), legacySkills, mock(ManaManager.class)).onPlayerInteract(event);
        verifyNoInteractions(weaponSkills, legacySkills);
        verify(event, never()).getPlayer();
    }

    @Test void mainHandStillCastsNormally() {
        SkillManager weaponSkills = mock(SkillManager.class);
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack item = mock(ItemStack.class);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_AIR);
        when(event.getPlayer()).thenReturn(player);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(item);
        when(item.getType()).thenReturn(Material.STICK);
        when(weaponSkills.tryCastWeaponSkill(player, SkillTriggerType.RIGHT_CLICK, item)).thenReturn(true);
        new SkillTriggerListener(weaponSkills).onInteract(event);
        verify(weaponSkills).tryCastWeaponSkill(player, SkillTriggerType.RIGHT_CLICK, item);
        verify(event).setCancelled(true);
    }
}
