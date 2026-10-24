package com.customrpg.managers;

import com.customrpg.CustomRPG;
import com.customrpg.players.*;
import com.customrpg.talents.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TalentHotkeyCastingTest {
    CustomRPG plugin; Player player; Talent talent; PlayerTalents selection; TalentSkillManager skills; ManaManager mana; ItemStack air;
    @BeforeEach void setup() {
        plugin = mock(CustomRPG.class); var manager = mock(TalentManager.class); when(plugin.getTalentManager()).thenReturn(manager);
        selection = new PlayerTalents(); player = mock(Player.class); when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        when(player.isOnline()).thenReturn(true); when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(manager.getPlayerTalents(player)).thenReturn(selection);
        var stats = mock(PlayerStatsManager.class); when(plugin.getPlayerStatsManager()).thenReturn(stats); when(stats.getStats(player)).thenReturn(new PlayerStats());
        mana = mock(ManaManager.class); when(plugin.getManaManager()).thenReturn(mana); when(mana.hasMana(any(), anyDouble())).thenReturn(true);
        var world = mock(World.class); when(player.getWorld()).thenReturn(world); when(player.getEyeLocation()).thenReturn(new Location(world, 0, 65, 0));
        talent = mock(Talent.class); when(talent.getId()).thenReturn("star_shatter"); when(talent.getType()).thenReturn(TalentType.ACTIVE);
        when(talent.getManaCost()).thenReturn(20.0); when(talent.getCooldown()).thenReturn(30.0);
        when(talent.getLevelData(1)).thenReturn(new Talent.TalentLevelData()); when(manager.findTalent("star_shatter")).thenReturn(talent);
        air = mock(ItemStack.class); when(air.getType()).thenReturn(Material.AIR); when(air.clone()).thenReturn(air);
        skills = new TalentSkillManager(plugin);
    }
    @Test void formerlyGoldNuggetSkillCastsWithEmptyHandAndStillHonorsManaAndCooldown() {
        selection.setTalentLevel("star_shatter", 1); selection.setSelectedSkill(0, "star_shatter");
        assertTrue(skills.castSkillDirectly(player, "star_shatter", air));
        assertFalse(skills.castSkillDirectly(player, "star_shatter", air));
        verify(mana, times(1)).consumeMana(player, 20.0);
    }
    @Test void learnedButUnequippedAndPassiveTalentsCannotBeInvoked() {
        selection.setTalentLevel("star_shatter", 1);
        assertFalse(skills.castSkillDirectly(player, "star_shatter", air));
        selection.setSelectedSkill(0, "star_shatter"); when(talent.getType()).thenReturn(TalentType.PASSIVE);
        assertFalse(skills.castSkillDirectly(player, "star_shatter", air)); verifyNoInteractions(mana);
    }
    @Test void talentResetDoesNotAllowStaleSelectedSkillToCast() {
        selection.setSelectedSkill(0, "star_shatter");
        assertFalse(skills.castSkillDirectly(player, "star_shatter", air)); verifyNoInteractions(mana);
    }
    @Test void newEarthGuardUsesTheExistingManaAndCooldownGate() {
        when(talent.getId()).thenReturn("earth_guard"); when(talent.getBranch()).thenReturn(TalentBranch.EARTH);
        when(talent.getEffectDouble(anyInt(), anyString(), anyDouble())).thenAnswer(i -> i.getArgument(2));
        var position = new Location(player.getWorld(), 0, 64, 0); when(player.getLocation()).thenReturn(position);
        when(plugin.getTalentManager().findTalent("earth_guard")).thenReturn(talent);
        selection.setTalentLevel("earth_guard", 1); selection.setSelectedSkill(0, "earth_guard");
        assertTrue(skills.castSkillDirectly(player, "earth_guard", air));
        assertFalse(skills.castSkillDirectly(player, "earth_guard", air)); verify(mana, times(1)).consumeMana(player, 20.0);
    }
    @Test void insufficientManaCannotStartANewTalentEffect() {
        when(talent.getId()).thenReturn("earth_guard"); when(plugin.getTalentManager().findTalent("earth_guard")).thenReturn(talent);
        selection.setTalentLevel("earth_guard", 1); selection.setSelectedSkill(0, "earth_guard");
        when(mana.hasMana(player, 20.0)).thenReturn(false);
        assertFalse(skills.castSkillDirectly(player, "earth_guard", air)); verify(mana, never()).consumeMana(any(), anyDouble());
    }
    private void castFromItem() {
        when(talent.getMechanism()).thenReturn("金粒"); when(talent.getTriggerType()).thenReturn("LEFT_CLICK");
        var nugget = mock(ItemStack.class); when(nugget.getType()).thenReturn(Material.GOLD_NUGGET); when(nugget.clone()).thenReturn(nugget);
        var event = new org.bukkit.event.player.PlayerInteractEvent(player, org.bukkit.event.block.Action.LEFT_CLICK_AIR,
                nugget, null, org.bukkit.block.BlockFace.UP, org.bukkit.inventory.EquipmentSlot.HAND);
        new com.customrpg.listeners.TalentItemListener(plugin.getTalentManager(), skills).onInteract(event);
        assertEquals(org.bukkit.event.Event.Result.DENY, event.useItemInHand());
    }
    @Test void itemThenHotkeyShareCooldownAndPayManaOnlyOnce() {
        selection.setTalentLevel("star_shatter", 1); selection.setSelectedSkill(0, "star_shatter");
        castFromItem(); assertFalse(skills.castSkillDirectly(player, "star_shatter", air));
        verify(mana, times(1)).consumeMana(player, 20.0);
    }
    @Test void hotkeyThenItemCannotBypassCooldown() {
        selection.setTalentLevel("star_shatter", 1); selection.setSelectedSkill(0, "star_shatter");
        assertTrue(skills.castSkillDirectly(player, "star_shatter", air)); castFromItem();
        verify(mana, times(1)).consumeMana(player, 20.0);
    }
    @Test void insufficientManaItemAttemptDoesNotConsumeManaOrStartCooldown() {
        selection.setTalentLevel("star_shatter", 1); selection.setSelectedSkill(0, "star_shatter");
        when(mana.hasMana(player, 20.0)).thenReturn(false); castFromItem();
        verify(mana, never()).consumeMana(any(), anyDouble());
        when(mana.hasMana(player, 20.0)).thenReturn(true);
        assertTrue(skills.castSkillDirectly(player, "star_shatter", air));
    }
    @Test void increasingMaximumManaDoesNotIncreaseTheFixedCost() {
        selection.setTalentLevel("star_shatter", 1); selection.setSelectedSkill(0, "star_shatter");
        when(talent.getCooldown()).thenReturn(0.0);
        var stats = plugin.getPlayerStatsManager().getStats(player);
        stats.setMaxMana(80);
        assertTrue(skills.castSkillDirectly(player, "star_shatter", air));
        stats.setMaxMana(800);
        castFromItem();
        verify(mana, times(2)).hasMana(player, 20.0);
        verify(mana, times(2)).consumeMana(player, 20.0);
    }
    @Test void zeroCostSkillsStayFreeEvenWithLargeMaximumMana() {
        selection.setTalentLevel("star_shatter", 1); selection.setSelectedSkill(0, "star_shatter");
        when(talent.getManaCost()).thenReturn(0.0);
        plugin.getPlayerStatsManager().getStats(player).setMaxMana(800);
        assertTrue(skills.castSkillDirectly(player, "star_shatter", air));
        verify(mana).hasMana(player, 0.0); verify(mana).consumeMana(player, 0.0);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = GameMode.class, names = {"SURVIVAL", "ADVENTURE", "CREATIVE"})
    void hunterMovesUseTheExistingFixedManaAndCooldownGateThroughBothEntrypoints(GameMode mode) {
        when(player.getGameMode()).thenReturn(mode);
        when(talent.getId()).thenReturn("nen_en"); when(talent.getBranch()).thenReturn(TalentBranch.HUNTER);
        when(talent.getMechanism()).thenReturn("紫水晶碎片"); when(talent.getTriggerType()).thenReturn("RIGHT_CLICK");
        when(talent.getEffectDouble(anyInt(), anyString(), anyDouble())).thenAnswer(i -> i.getArgument(2));
        var location = new Location(player.getWorld(), 0, 64, 0); when(player.getLocation()).thenReturn(location);
        when(plugin.getTalentManager().findTalent("nen_en")).thenReturn(talent);
        selection.setTalentLevel("nen_en", 1); selection.setSelectedSkill(0, "nen_en");
        var crystal = mock(ItemStack.class); when(crystal.getType()).thenReturn(Material.AMETHYST_SHARD); when(crystal.clone()).thenReturn(crystal);
        var event = new org.bukkit.event.player.PlayerInteractEvent(player, org.bukkit.event.block.Action.RIGHT_CLICK_AIR,
                crystal, null, org.bukkit.block.BlockFace.UP, org.bukkit.inventory.EquipmentSlot.HAND);
        new com.customrpg.listeners.TalentItemListener(plugin.getTalentManager(), skills).onInteract(event);
        assertEquals(org.bukkit.event.Event.Result.DENY, event.useItemInHand());
        assertFalse(skills.castSkillDirectly(player, "nen_en", air)); verify(mana).consumeMana(player, 20.0);
    }
    @Test void hunterTargetFailureAndInsufficientManaDoNotStartEffectsOrConsumeResources() {
        when(talent.getId()).thenReturn("nen_lightning"); when(talent.getBranch()).thenReturn(TalentBranch.HUNTER);
        when(talent.getEffectDouble(anyInt(), anyString(), anyDouble())).thenAnswer(i -> i.getArgument(2));
        var location = new Location(player.getWorld(), 0, 64, 0); when(player.getLocation()).thenReturn(location);
        when(plugin.getTalentManager().findTalent("nen_lightning")).thenReturn(talent);
        selection.setTalentLevel("nen_lightning", 1); selection.setSelectedSkill(0, "nen_lightning");
        assertFalse(skills.castSkillDirectly(player, "nen_lightning", air));
        when(mana.hasMana(player, 20.0)).thenReturn(false);
        assertFalse(skills.castSkillDirectly(player, "nen_lightning", air)); verify(mana, never()).consumeMana(any(), anyDouble());
    }
    @Test void chickenEggUsesThreeManaAndTheFullFractionalCooldown()throws Exception{
        when(talent.getId()).thenReturn("chicken_egg");when(talent.getManaCost()).thenReturn(3.0);when(talent.getCooldown()).thenReturn(1.5);when(plugin.getTalentManager().findTalent("chicken_egg")).thenReturn(talent);when(player.getLocation()).thenAnswer(i->new Location(player.getWorld(),0,64,0));
        var egg=mock(org.bukkit.entity.Egg.class);when(egg.isValid()).thenReturn(true);when(egg.getUniqueId()).thenReturn(java.util.UUID.randomUUID());var origin=player.getEyeLocation();when(egg.getLocation()).thenReturn(origin);when(egg.getVelocity()).thenReturn(new org.bukkit.util.Vector(0,.14,1.05));when(player.launchProjectile(eq(org.bukkit.entity.Egg.class),any(org.bukkit.util.Vector.class),any())).thenReturn(egg);
        selection.setTalentLevel("chicken_egg",1);selection.setSelectedSkill(0,"chicken_egg");assertTrue(skills.castSkillDirectly(player,"chicken_egg",air));assertFalse(skills.castSkillDirectly(player,"chicken_egg",air));verify(mana).consumeMana(player,3.0);
        var field=TalentSkillManager.class.getDeclaredField("cooldownManager");field.setAccessible(true);var cooldowns=(com.customrpg.weaponSkills.managers.CooldownManager)field.get(skills);long remaining=cooldowns.getRemainingCooldown(player,"talent:chicken_egg:"+player.getUniqueId());assertTrue(remaining>1000&&remaining<=1500);
    }
    @Test void failedGodRefundClearsOnlyItsOwnCooldown()throws Exception{
        var field=TalentSkillManager.class.getDeclaredField("cooldownManager");field.setAccessible(true);var cooldowns=(com.customrpg.weaponSkills.managers.CooldownManager)field.get(skills);String god="talent:chicken_god:"+player.getUniqueId(),other="talent:necro_gate:"+player.getUniqueId();cooldowns.applyCooldown(player,god,120000);cooldowns.applyCooldown(player,other,120000);
        skills.refundFailedChickenGod(player);assertTrue(cooldowns.canCast(player,god));assertFalse(cooldowns.canCast(player,other));verify(mana).restoreMana(player,75);
    }

}
