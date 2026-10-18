package com.customrpg.commands;

import com.customrpg.CustomRPG;
import com.customrpg.gui.*;
import com.customrpg.managers.*;
import com.customrpg.talents.TalentBranch;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TalentCommandTest {
    CustomRPG plugin; TalentManager talents; TalentCommand command; Player player; TalentTreeGUI trees;
    @BeforeEach void setup() {
        plugin = mock(CustomRPG.class); talents = mock(TalentManager.class); trees = mock(TalentTreeGUI.class);
        when(plugin.getTalentTreeGUI()).thenReturn(trees); player = mock(Player.class);
        command = new TalentCommand(plugin, talents, mock(TalentMainMenuGUI.class));
    }
    @ParameterizedTest @CsvSource({"frost,FROST", "storm,STORM", "earth,EARTH", "light,LIGHT", "warrior,WEAPON", "mage,FIRE", "nature,NATURE"})
    void branchCommandsAndLegacyAliasesOpenRealTrees(String argument, TalentBranch branch) {
        assertTrue(command.onCommand(player, mock(Command.class), "talent", new String[]{argument})); verify(trees).open(player, branch);
    }
    @Test void tabCompletionIncludesAllFourNewTrees() {
        var result = command.onTabComplete(player, mock(Command.class), "talent", new String[]{""});
        assertTrue(result.containsAll(List.of("frost", "storm", "earth", "light"))); assertFalse(result.contains("reload"));
    }
    @Test void failedReloadRetainsRunningSessionsAndReportsFailure() {
        when(player.hasPermission("customrpg.talent.admin")).thenReturn(true);
        command.onCommand(player, mock(Command.class), "talent", new String[]{"reload"});
        verify(talents).reloadConfigurations(); verify(plugin, never()).getTalentSkillManager();
        verify(player).sendMessage(contains("已保留原設定"));
    }
    @Test void successfulReloadClearsSessionsAndActuallyReloadsDefinitions() {
        when(player.hasPermission("customrpg.talent.admin")).thenReturn(true); when(talents.reloadConfigurations()).thenReturn(true);
        var skills = mock(TalentSkillManager.class); var expansion = mock(ExpandedTalentSkills.class); when(skills.getExpansion()).thenReturn(expansion); when(plugin.getTalentSkillManager()).thenReturn(skills);
        var server = mock(Server.class); when(plugin.getServer()).thenReturn(server); doReturn(List.of()).when(server).getOnlinePlayers();
        command.onCommand(player, mock(Command.class), "talent", new String[]{"reload"});
        verify(talents).reloadConfigurations(); verify(expansion).clear();
    }
    @Test void nonAdminCannotReloadDefinitions() {
        command.onCommand(player, mock(Command.class), "talent", new String[]{"reload"}); verifyNoInteractions(talents);
    }
}
