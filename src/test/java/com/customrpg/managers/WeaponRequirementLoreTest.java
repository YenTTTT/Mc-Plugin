package com.customrpg.managers;

import com.customrpg.CustomRPG;
import com.customrpg.balance.BalanceItemRefresh;
import com.customrpg.players.PlayerStats;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.*;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WeaponRequirementLoreTest {
    private static final NamespacedKey ID=new NamespacedKey("customrpg","custom_weapon_key");
    private static final NamespacedKey VERSION=new NamespacedKey("customrpg","item_balance_version");
    private static final String REQUIREMENT="§8🔒 等級需求: §c30";
    private static final String BORDER="§8━━━━━━━━━━━━━━━━━━━━━━";
    private CustomRPG plugin;
    private WeaponManager weapons;

    @BeforeEach void setup() {
        plugin=mock(CustomRPG.class);when(plugin.getName()).thenReturn("CustomRPG");when(plugin.namespace()).thenReturn("customrpg");
        var logger=java.util.logging.Logger.getAnonymousLogger();logger.setLevel(java.util.logging.Level.OFF);
        when(plugin.getLogger()).thenReturn(logger);
        var config=mock(ConfigManager.class);when(config.getAllWeapons()).thenReturn(Map.of());
        weapons=new WeaponManager(plugin,config);when(plugin.getWeaponManager()).thenReturn(weapons);
        weapons.registerSupplemental(new WeaponManager.WeaponData("test_sword","Sword",Material.IRON_SWORD,1,"none",
                List.of(),0,false,new HashMap<>(),30,WeaponManager.WeaponRarity.COMMON,WeaponManager.WeaponCategory.SWORD));
    }
    private record Stack(ItemStack item,ItemMeta meta,PersistentDataContainer data) {}
    private Stack stack(String id,String... lines) {
        var item=mock(ItemStack.class);var meta=mock(ItemMeta.class);var data=mock(PersistentDataContainer.class);
        when(item.hasItemMeta()).thenReturn(true);when(item.getType()).thenReturn(Material.IRON_SWORD);
        when(item.getItemMeta()).thenReturn(meta);when(meta.getPersistentDataContainer()).thenReturn(data);
        when(data.get(ID,PersistentDataType.STRING)).thenReturn(id);
        var lore=new AtomicReference<>(new ArrayList<>(List.of(lines)));
        when(meta.getLore()).thenAnswer(call->new ArrayList<>(lore.get()));
        doAnswer(call->{lore.set(new ArrayList<>(call.getArgument(0)));return null;}).when(meta).setLore(anyList());
        return new Stack(item,meta,data);
    }

    @ParameterizedTest @ValueSource(ints={29,30,31})
    void requirementDisappearsExactlyWhenTheRpgLevelIsHighEnough(int level) {
        var weapon=stack("test_sword","§7A sword",REQUIREMENT,BORDER);
        assertEquals(level>=30,weapons.refreshRequiredLevelLore(weapon.item,level));
        assertEquals(level<30,weapon.meta.getLore().contains(REQUIREMENT));
        assertEquals("§7A sword",weapon.meta.getLore().getFirst());
        assertEquals(BORDER,weapon.meta.getLore().getLast());
    }

    @Test void removesBothTheConfiguredAndGeneratedRequirementWithoutChangingOtherMetadata() {
        var weapon=stack("test_sword","§6等級需求: 30","§c需求等級：30","§7對等級需求高的武器進行研究",REQUIREMENT,BORDER);
        assertTrue(weapons.refreshRequiredLevelLore(weapon.item,30));
        assertEquals(List.of("§7對等級需求高的武器進行研究",BORDER),weapon.meta.getLore());
        verify(weapon.item,never()).setAmount(anyInt());verify(weapon.item,never()).setType(any());
        verify(weapon.meta,never()).setDisplayName(anyString());
        verify(weapon.data,never()).set(any(),any(),any());verify(weapon.data,never()).remove(any());
    }

    @Test void aLowerLevelRecipientGetsTheRequirementBackExactlyOnce() {
        var weapon=stack("test_sword","§7A sword",REQUIREMENT,BORDER);
        assertTrue(weapons.refreshRequiredLevelLore(weapon.item,30));
        assertTrue(weapons.refreshRequiredLevelLore(weapon.item,29));
        assertFalse(weapons.refreshRequiredLevelLore(weapon.item,29));
        assertEquals(List.of("§7A sword",REQUIREMENT,BORDER),weapon.meta.getLore());
    }

    @Test void vanillaUnknownItemsAndUnownedTemplatesKeepTheirLore() {
        var vanilla=stack(null,REQUIREMENT);var unknown=stack("removed_weapon",REQUIREMENT);
        var template=stack("test_sword",REQUIREMENT);
        assertFalse(weapons.refreshRequiredLevelLore(vanilla.item,60));
        assertFalse(weapons.refreshRequiredLevelLore(unknown.item,60));
        assertFalse(weapons.refreshRequiredLevelLore(template.item,-1));
        for(var item:List.of(vanilla,unknown,template))verify(item.item,never()).setItemMeta(any());
    }

    @Test void levelUpRefreshesStorageOffHandAndTheCursor() {
        var player=mock(Player.class);var inventory=mock(PlayerInventory.class);when(player.getInventory()).thenReturn(inventory);
        var bag=stack("test_sword",REQUIREMENT);var offhand=stack("test_sword",REQUIREMENT);var cursor=stack("test_sword",REQUIREMENT);
        ItemStack[] contents=new ItemStack[41];contents[0]=bag.item;contents[40]=offhand.item;
        when(inventory.getContents()).thenReturn(contents);when(player.getItemOnCursor()).thenReturn(cursor.item);
        weapons.refreshRequiredLevelLore(player,30);
        for(var item:List.of(bag,offhand,cursor))assertTrue(item.meta.getLore().isEmpty());
        verify(player).updateInventory();
    }

    @Test void alreadyBalancedWeaponsStillUpdateForTheCurrentHolder() {
        var weapon=stack("test_sword",REQUIREMENT);
        when(weapon.data.get(VERSION,PersistentDataType.INTEGER)).thenReturn(2);
        var refresh=new BalanceItemRefresh(plugin);refresh.refresh(weapon.item,30);
        assertTrue(weapon.meta.getLore().isEmpty());
        refresh.refresh(weapon.item,29);assertEquals(List.of(REQUIREMENT),weapon.meta.getLore());
    }

    @Test void receivedWeaponsRefreshAfterTheInventoryTransactionWithoutChangingTheSharedPreview() {
        var preview=stack("test_sword",REQUIREMENT);var received=stack("test_sword",REQUIREMENT);
        when(received.data.get(VERSION,PersistentDataType.INTEGER)).thenReturn(2);
        var player=mock(Player.class);when(player.isOnline()).thenReturn(true);
        var inventory=mock(PlayerInventory.class);when(player.getInventory()).thenReturn(inventory);
        when(inventory.getContents()).thenReturn(new ItemStack[0]);
        var stats=new PlayerStats();stats.setLevel(30);var manager=mock(PlayerStatsManager.class);
        when(plugin.getPlayerStatsManager()).thenReturn(manager);when(manager.getStats(player)).thenReturn(stats);
        var scheduler=mock(BukkitScheduler.class);var server=mock(Server.class);
        when(plugin.getServer()).thenReturn(server);when(server.getScheduler()).thenReturn(scheduler);
        var queued=new AtomicReference<Runnable>();
        when(scheduler.runTask(eq(plugin),any(Runnable.class))).thenAnswer(call->{queued.set(call.getArgument(1));return null;});
        var event=mock(InventoryClickEvent.class);when(event.getWhoClicked()).thenReturn(player);
        var refresh=spy(new BalanceItemRefresh(plugin));doNothing().when(refresh).updateSpeed(player);
        refresh.click(event);
        assertEquals(List.of(REQUIREMENT),received.meta.getLore());
        when(inventory.getContents()).thenReturn(new ItemStack[]{received.item});
        queued.get().run();
        assertTrue(received.meta.getLore().isEmpty());
        assertEquals(List.of(REQUIREMENT),preview.meta.getLore());
    }
}
