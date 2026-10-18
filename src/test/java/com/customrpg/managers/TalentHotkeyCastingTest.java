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
        verify(mana, times(1)).consumeMana(player, 20);
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
        assertFalse(skills.castSkillDirectly(player, "earth_guard", air)); verify(mana, times(1)).consumeMana(player, 20);
    }
    @Test void insufficientManaCannotStartANewTalentEffect() {
        when(talent.getId()).thenReturn("earth_guard"); when(plugin.getTalentManager().findTalent("earth_guard")).thenReturn(talent);
        selection.setTalentLevel("earth_guard", 1); selection.setSelectedSkill(0, "earth_guard");
        when(mana.hasMana(player, 20)).thenReturn(false);
        assertFalse(skills.castSkillDirectly(player, "earth_guard", air)); verify(mana, never()).consumeMana(any(), anyDouble());
    }
}
