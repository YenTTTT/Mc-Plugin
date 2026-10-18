package com.customrpg.listeners;

import com.customrpg.managers.TalentManager;
import com.customrpg.managers.TalentSkillManager;
import com.customrpg.players.PlayerTalents;
import com.customrpg.talents.Talent;
import com.customrpg.talents.TalentType;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TalentItemListenerTest {
    TalentManager talents; TalentSkillManager skills; TalentItemListener listener;
    Player player; PlayerTalents investment; ItemStack nugget;

    @BeforeEach void setup() {
        talents = mock(TalentManager.class); skills = mock(TalentSkillManager.class);
        listener = new TalentItemListener(talents, skills); player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID()); when(player.isOnline()).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        investment = new PlayerTalents(); when(talents.getPlayerTalents(player)).thenReturn(investment);
        nugget = mock(ItemStack.class); when(nugget.getType()).thenReturn(Material.GOLD_NUGGET);
        equip("star_shatter", "金粒", "LEFT_CLICK", 0);
    }

    private Talent equip(String id, String material, String trigger, int slot) {
        Talent talent = mock(Talent.class); when(talent.getId()).thenReturn(id); when(talent.getName()).thenReturn(id);
        when(talent.getMechanism()).thenReturn(material); when(talent.getTriggerType()).thenReturn(trigger);
        when(talent.getType()).thenReturn(TalentType.ACTIVE); when(talents.findTalent(id)).thenReturn(talent);
        investment.setTalentLevel(id, 1); investment.setSelectedSkill(slot, id); return talent;
    }

    private PlayerInteractEvent click(Action action) {
        Block block = action == Action.LEFT_CLICK_BLOCK || action == Action.RIGHT_CLICK_BLOCK ? mock(Block.class) : null;
        return new PlayerInteractEvent(player, action, nugget, block, BlockFace.UP, EquipmentSlot.HAND);
    }

    @Test void matchingItemAndClickReachTheSharedCastingService() {
        var event = click(Action.LEFT_CLICK_AIR); listener.onInteract(event);
        verify(skills).castSkillDirectly(player, "star_shatter", nugget);
        assertEquals(Event.Result.DENY, event.useItemInHand());
    }
    @Test void wrongItemOrWrongClickDoNotCastOrConsumeTheInteraction() {
        var wrongClick = click(Action.RIGHT_CLICK_AIR); listener.onInteract(wrongClick);
        when(nugget.getType()).thenReturn(Material.DIRT); var wrongItem = click(Action.LEFT_CLICK_AIR);
        listener.onInteract(wrongItem); verifyNoInteractions(skills);
        assertNotEquals(Event.Result.DENY, wrongItem.useItemInHand());
    }
    @Test void sneakRightClickCyclesEquippedSkillsWithoutCastingAndUsesTheNewSelection() {
        equip("chain_lightning_lv2", "金粒", "LEFT_CLICK", 1);
        when(player.isSneaking()).thenReturn(true); var cycle = click(Action.RIGHT_CLICK_AIR);
        listener.onInteract(cycle); verifyNoInteractions(skills); assertEquals(Event.Result.DENY, cycle.useItemInHand());
        when(player.isSneaking()).thenReturn(false); listener.onInteract(click(Action.LEFT_CLICK_AIR));
        verify(skills).castSkillDirectly(player, "chain_lightning_lv2", nugget);
    }
    @Test void sneakRightClickWithOneSkillOnlyShowsSelectionAndDoesNotCast() {
        when(player.isSneaking()).thenReturn(true); listener.onInteract(click(Action.RIGHT_CLICK_AIR));
        verifyNoInteractions(skills); verify(player).sendMessage(contains("star_shatter"));
    }
    @Test void sneakLeftClickHonorsTheDedicatedTrigger() {
        equip("star_shatter", "金粒", "LEFT_CLICK_SNEAK", 0);
        listener.onInteract(click(Action.LEFT_CLICK_AIR)); verifyNoInteractions(skills);
        when(player.isSneaking()).thenReturn(true); listener.onInteract(click(Action.LEFT_CLICK_AIR));
        verify(skills).castSkillDirectly(player, "star_shatter", nugget);
    }
    @Test void resetAndUnequippedSkillsCannotUseAStaleSelection() {
        investment.setTalentLevel("star_shatter", 0); listener.onInteract(click(Action.LEFT_CLICK_AIR));
        investment.setTalentLevel("star_shatter", 1); investment.setSelectedSkill(0, null);
        listener.onInteract(click(Action.LEFT_CLICK_AIR)); verifyNoInteractions(skills);
    }
    @Test void passiveTalentsAreNeverItemCastable() {
        when(talents.findTalent("star_shatter").getType()).thenReturn(TalentType.PASSIVE);
        listener.onInteract(click(Action.LEFT_CLICK_AIR)); verifyNoInteractions(skills);
    }
    @Test void offhandAndSpectatorsCannotCast() {
        var offhand = new PlayerInteractEvent(player, Action.LEFT_CLICK_AIR, nugget, null, BlockFace.UP, EquipmentSlot.OFF_HAND);
        listener.onInteract(offhand); when(player.getGameMode()).thenReturn(GameMode.SPECTATOR);
        listener.onInteract(click(Action.LEFT_CLICK_AIR)); verifyNoInteractions(skills);
    }
    @Test void deniedItemsAndCancelledBlockInteractionsAreRespected() {
        var denied = click(Action.LEFT_CLICK_AIR); denied.setUseItemInHand(Event.Result.DENY);
        listener.onInteract(denied); var block = click(Action.LEFT_CLICK_BLOCK); block.setCancelled(true);
        listener.onInteract(block); verifyNoInteractions(skills);
    }
    @Test void vanillaAirPredictionDoesNotBlockItemCasting() {
        equip("star_shatter", "金粒", "RIGHT_CLICK", 0);
        var air = click(Action.RIGHT_CLICK_AIR); air.setUseInteractedBlock(Event.Result.DENY);
        listener.onInteract(air); verify(skills).castSkillDirectly(player, "star_shatter", nugget);
    }
    @Test void failedCastsClaimTheClickSoWeaponRoutesCannotAlsoFire() {
        var event = click(Action.LEFT_CLICK_AIR); listener.onInteract(event);
        var weaponSkills = mock(com.customrpg.weaponSkills.managers.SkillManager.class);
        new SkillTriggerListener(weaponSkills).onInteract(event);
        assertEquals(Event.Result.DENY, event.useItemInHand()); verifyNoInteractions(weaponSkills);
    }
    @Test void removingTheChosenSkillAndLoggingOutBothResetSelectionSafely() {
        equip("chain_lightning_lv2", "金粒", "LEFT_CLICK", 1); when(player.isSneaking()).thenReturn(true);
        listener.onInteract(click(Action.RIGHT_CLICK_AIR)); investment.setSelectedSkill(1, null);
        when(player.isSneaking()).thenReturn(false); listener.onInteract(click(Action.LEFT_CLICK_AIR));
        verify(skills).castSkillDirectly(player, "star_shatter", nugget);
        investment.setSelectedSkill(1, "chain_lightning_lv2"); when(player.isSneaking()).thenReturn(true);
        listener.onInteract(click(Action.RIGHT_CLICK_AIR)); listener.onQuit(new PlayerQuitEvent(player, ""));
        when(player.isSneaking()).thenReturn(false); listener.onInteract(click(Action.LEFT_CLICK_AIR));
        verify(skills, times(2)).castSkillDirectly(player, "star_shatter", nugget);
    }
}
