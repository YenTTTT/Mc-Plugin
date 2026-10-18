package com.customrpg.crafting;

import com.customrpg.CustomRPG;
import com.customrpg.managers.*;
import com.customrpg.players.PlayerStats;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CraftingRangedListenerTest {
    CraftingRangedListener listener; CustomRPG plugin; CraftingCatalog catalog; CraftingItems items; Player player; PlayerStats stats; ItemStack weapon; Arrow arrow;
    @BeforeEach void setup() throws Exception {
        catalog = new CraftingCatalog(CraftingCatalogTest.RESOURCE.toFile()); items = CraftingTestItems.factory(catalog); plugin = mock(CustomRPG.class);
        var playerStats = mock(PlayerStatsManager.class); when(plugin.getPlayerStatsManager()).thenReturn(playerStats); player = mock(Player.class);
        stats = new PlayerStats(); stats.setLevel(60); when(playerStats.getStats(player)).thenReturn(stats);
        var weapons = mock(WeaponManager.class); when(plugin.getWeaponManager()).thenReturn(weapons);
        when(weapons.getWeaponData(anyString())).thenAnswer(i -> catalog.weapon(catalog.gear().get(i.getArgument(0))));
        weapon = items.product("forge_plains_t7_bow"); arrow = mock(Arrow.class); when(arrow.getShooter()).thenReturn(player);
        var data = CraftingTestItems.data(new HashMap<>()); when(arrow.getPersistentDataContainer()).thenReturn(data);
        listener = new CraftingRangedListener(plugin, catalog, items, () -> .99);
    }
    EntityShootBowEvent shoot(float force) {
        var event = mock(EntityShootBowEvent.class); when(event.getEntity()).thenReturn(player); when(event.getBow()).thenReturn(weapon);
        when(event.getProjectile()).thenReturn(arrow); when(event.getForce()).thenReturn(force); return event;
    }
    double damage() {
        var event = mock(EntityDamageByEntityEvent.class); when(event.getDamager()).thenReturn(arrow); listener.onHit(event);
        var value = org.mockito.ArgumentCaptor.forClass(Double.class); verify(event).setDamage(value.capture()); return value.getValue();
    }
    @Test void bowUsesWeaponTierAndLaunchSnapshotRatherThanLaterHeldEquipment() {
        listener.onBow(shoot(1)); double damage = damage(); assertTrue(damage > 20);
        stats.setAgility(500); stats.setLevel(1); assertEquals(damage, damage());
    }
    @Test void bowChargeScalesDamageAndBelowLevelCannotFire() {
        listener.onBow(shoot(1)); double full = damage(); listener.onBow(shoot(.5f)); assertEquals(full * .5, damage());
        stats.setLevel(1); var blocked = shoot(1); listener.onBow(blocked); verify(blocked).setCancelled(true);
    }
    @Test void thrownSpearHonorsLevelRequirementAndGetsItsOwnDamageSnapshot() {
        var spear = items.product("forge_plains_t7_spear"); var trident = mock(Trident.class); when(trident.getItemStack()).thenReturn(spear); when(trident.getShooter()).thenReturn(player);
        var data = CraftingTestItems.data(new HashMap<>()); when(trident.getPersistentDataContainer()).thenReturn(data);
        var event = mock(ProjectileLaunchEvent.class); when(event.getEntity()).thenReturn(trident); stats.setLevel(1); listener.onLaunch(event); verify(event).setCancelled(true);
        stats.setLevel(60); clearInvocations(event); listener.onLaunch(event); verify(event, never()).setCancelled(true);
        var hit = mock(EntityDamageByEntityEvent.class); when(hit.getDamager()).thenReturn(trident); listener.onHit(hit); verify(hit).setDamage(doubleThat(d -> d > 20));
    }
    @Test void vanillaArrowsAreNotModified() {
        var hit = mock(EntityDamageByEntityEvent.class); when(hit.getDamager()).thenReturn(arrow); listener.onHit(hit); verify(hit, never()).setDamage(anyDouble());
    }
}
