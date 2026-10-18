package com.customrpg.crafting;

import com.customrpg.CustomRPG;
import com.customrpg.managers.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CraftingLootListenerTest {
    CustomRPG plugin; MobManager mobs; MobManager.MobData template; Player killer; LivingEntity mob;
    CraftingItems items; CraftingLootListener listener; EntityDeathEvent death; List<ItemStack> drops;
    @BeforeEach void setup() throws Exception {
        var catalog = new CraftingCatalog(CraftingCatalogTest.RESOURCE.toFile()); plugin = mock(CustomRPG.class);
        mobs = mock(MobManager.class); when(plugin.getMobManager()).thenReturn(mobs); template = mock(MobManager.MobData.class);
        mob = mock(Zombie.class); killer = mock(Player.class); when(killer.isOnline()).thenReturn(true); when(killer.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(mob.getKiller()).thenReturn(killer); when(mobs.getCustomMobKey(mob)).thenReturn("test"); when(mobs.getMobData("test")).thenReturn(template);
        when(mobs.getMobLevel(mob)).thenReturn(22); when(template.getTags()).thenReturn(List.of("forest"));
        items = mock(CraftingItems.class); var material = mock(ItemStack.class); when(items.material(anyString(), anyInt())).thenReturn(material);
        var random = mock(Random.class); when(random.nextDouble()).thenReturn(0.0);
        listener = new CraftingLootListener(plugin, catalog, items, random, l -> "plains");
        death = mock(EntityDeathEvent.class); when(death.getEntity()).thenReturn(mob); drops = new ArrayList<>(); when(death.getDrops()).thenReturn(drops);
    }
    @Test void ordinaryCustomKillAddsMaterialsWithoutClearingExistingLoot() {
        var previous = mock(ItemStack.class); drops.add(previous); listener.onDeath(death);
        assertEquals(3, drops.size()); assertSame(previous, drops.getFirst());
        verify(items).material("forest_essence_t3", 1); verify(items).material("monster_binding_t3", 1); verify(items, never()).material(startsWith("boss_core"), anyInt());
    }
    @Test void bossesAlsoDropTheirOwnTierCoreAndLargerAmounts() {
        when(template.isBoss()).thenReturn(true); listener.onDeath(death);
        verify(items).material("forest_essence_t3", 3); verify(items).material("monster_binding_t3", 2); verify(items).material("boss_core_t3", 1);
    }
    @Test void vanillaHostileDropsUseLocalRegionLevelRatherThanPlayerLevel() {
        when(mobs.getCustomMobKey(mob)).thenReturn(null); var zones = mock(WorldZoneManager.class); when(plugin.getWorldZoneManager()).thenReturn(zones);
        when(zones.getBaseLevel(any())).thenReturn(38); listener.onDeath(death); verify(items).material("plains_essence_t5", 1);
    }
    @Test void eventOnlyAndSummonOnlyMobsCannotLeakOrdinaryCraftingRewards() {
        when(template.getTags()).thenReturn(List.of("event-only")); listener.onDeath(death);
        when(template.getTags()).thenReturn(List.of("summon-only")); listener.onDeath(death); verifyNoInteractions(items);
    }
    @Test void NoPlayerCreditAndCreativeKillsDoNotDropProgressionMaterials() {
        when(mob.getKiller()).thenReturn(null); listener.onDeath(death);
        when(mob.getKiller()).thenReturn(killer); when(killer.getGameMode()).thenReturn(GameMode.CREATIVE); listener.onDeath(death); verifyNoInteractions(items);
    }
    @Test void petsAreExcludedButOwnedBeastKillCreditCanAwardMaterials() {
        var pet = mock(Wolf.class); when(pet.isTamed()).thenReturn(true); when(death.getEntity()).thenReturn(pet); listener.onDeath(death); verifyNoInteractions(items);
        when(death.getEntity()).thenReturn(mob); when(mob.getKiller()).thenReturn(null); when(mob.hasMetadata("beast_kill_owner")).thenReturn(true);
        UUID owner = UUID.randomUUID(); var metadata = mock(MetadataValue.class); when(metadata.asString()).thenReturn(owner.toString()); when(mob.getMetadata("beast_kill_owner")).thenReturn(List.of(metadata));
        var server = mock(Server.class); when(plugin.getServer()).thenReturn(server); when(server.getPlayer(owner)).thenReturn(killer);
        listener.onDeath(death); verify(items).material("forest_essence_t3", 1);
    }
}
