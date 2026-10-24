package com.customrpg.managers;

import com.customrpg.CustomRPG;
import com.customrpg.balance.CombatBalance;
import com.customrpg.players.*;
import com.customrpg.talents.*;
import com.customrpg.utils.SkillDamageGuard;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Runs the actual fire talent cast methods with YAML coefficients and deterministic game ticks.
 * Damage is measured before the server's mitigation/budget listeners; native burn never subtracts HP here. */
class FireTalentDamageTest {
    static MockedStatic<io.papermc.paper.registry.RegistryAccess> registryAccess;
    /** Paper's Sound/Potion constants are registry backed; provide API objects, without starting a server. */
    @BeforeAll @SuppressWarnings({"rawtypes","unchecked"}) static void apiRegistries() throws Exception {
        var types=new HashMap<Object,Class<?>>();
        for(var field:io.papermc.paper.registry.RegistryKey.class.getFields()) {
            if(field.getGenericType() instanceof java.lang.reflect.ParameterizedType parameterized) {
                var type=parameterized.getActualTypeArguments()[0];
                if(type instanceof java.lang.reflect.ParameterizedType nested) type=nested.getRawType();
                if(type instanceof Class<?> clazz) types.put(field.get(null),clazz);
            }
        }
        Map<Object,org.bukkit.Registry> registries=new HashMap<>();
        var access=mock(io.papermc.paper.registry.RegistryAccess.class,i->{
            Object key=i.getArgument(0);
            return registries.computeIfAbsent(key,k->{
                Class<?> type=k instanceof Class<?> clazz?clazz:types.get(k);
                if(type==null) throw new IllegalArgumentException("Missing API registry type: "+k);
                Map<Object,Object> entries=new HashMap<>();
                return mock(org.bukkit.Registry.class,call->{
                    String method=call.getMethod().getName();
                    if(method.equals("get")||method.equals("getOrThrow")) {
                        Object name=call.getArgument(0);
                        return entries.computeIfAbsent(name,n->mock(type,entry->{
                            if(entry.getMethod().getName().equals("getKey")) return n;
                            if(entry.getMethod().getName().equals("key")) return n;
                            return org.mockito.Answers.RETURNS_DEFAULTS.answer(entry);
                        }));
                    }
                    return org.mockito.Answers.RETURNS_DEFAULTS.answer(call);
                });
            });
        });
        registryAccess=mockStatic(io.papermc.paper.registry.RegistryAccess.class);
        registryAccess.when(io.papermc.paper.registry.RegistryAccess::registryAccess).thenReturn(access);
    }
    @AfterAll static void closeRegistries() { registryAccess.close(); }
    static final class Scheduled {
        Runnable action; long next,period; int id; boolean cancelled;
    }
    CustomRPG plugin; TalentManager talents; TalentSkillManager skills; Player caster; Zombie target;
    PlayerStats stats; PlayerTalents selection; World world; YamlConfiguration config; ItemStack item;
    BukkitScheduler scheduler; MockedStatic<Bukkit> bukkit; List<Scheduled> tasks=new ArrayList<>();
    AtomicReference<Double> hp=new AtomicReference<>(1000.0); AtomicInteger hurt=new AtomicInteger(20);
    List<Boolean> periodic=new ArrayList<>(); long tick;
    @BeforeEach void setup() throws Exception {
        config=new YamlConfiguration(); config.load(Path.of("src/main/resources/config/talents.yml").toFile());
        plugin=mock(CustomRPG.class); when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        talents=mock(TalentManager.class); when(plugin.getTalentManager()).thenReturn(talents);
        var sm=mock(PlayerStatsManager.class); when(plugin.getPlayerStatsManager()).thenReturn(sm);
        stats=new PlayerStats(); stats.setEquipmentMagic(20); stats.setRaceMagic(5); stats.setTalentMagic(10);
        stats.setEquipmentSpirit(6); stats.setRaceSpirit(2); stats.setTalentSpirit(4);
        when(sm.getStats(any(Player.class))).thenReturn(stats);
        var mana=mock(ManaManager.class); when(plugin.getManaManager()).thenReturn(mana); when(mana.hasMana(any(),anyDouble())).thenReturn(true);
        var balance=mock(CombatBalance.class); when(plugin.getCombatBalance()).thenReturn(balance);
        world=mock(World.class); when(world.getMaxHeight()).thenReturn(320); when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        caster=mock(Player.class); target=mock(Zombie.class);
        when(caster.getUniqueId()).thenReturn(UUID.randomUUID()); when(target.getUniqueId()).thenReturn(UUID.randomUUID());
        when(caster.getWorld()).thenReturn(world); when(target.getWorld()).thenReturn(world); when(caster.isOnline()).thenReturn(true);
        when(caster.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(caster.getLocation()).thenAnswer(i->new Location(world,0,64,0));
        when(caster.getEyeLocation()).thenAnswer(i->new Location(world,0,65.6,0));
        when(target.getLocation()).thenAnswer(i->new Location(world,0,64,15));
        when(target.getHealth()).thenAnswer(i->hp.get()); when(target.getNoDamageTicks()).thenAnswer(i->hurt.get());
        doAnswer(i->{hurt.set(i.getArgument(0));return null;}).when(target).setNoDamageTicks(anyInt());
        doAnswer(i->{
            assertTrue(SkillDamageGuard.isSkillDamage(caster)); assertFalse(SkillDamageGuard.isFireTick(caster));
            periodic.add(SkillDamageGuard.isPeriodic(caster));
            if(hurt.get()==0) { hp.updateAndGet(value->value-(double)i.getArgument(0)); hurt.set(20); }
            return null;
        }).when(target).damage(anyDouble(),eq(caster));
        doAnswer(i->{target.setFireTicks(i.getArgument(2));return null;}).when(balance).ignite(eq(caster),eq(target),anyInt());
        when(world.getNearbyEntities(any(Location.class),anyDouble(),anyDouble(),anyDouble())).thenReturn(List.of(target));
        var block=mock(Block.class); var air=mock(Material.class); when(air.isSolid()).thenReturn(false); when(block.getType()).thenReturn(air);
        when(world.getBlockAt(any(Location.class))).thenReturn(block);
        BlockDisplay display=mock(BlockDisplay.class); when(display.isValid()).thenReturn(true); when(display.teleport(any(Location.class))).thenReturn(true);
        when(world.spawn(any(Location.class),eq(BlockDisplay.class),any(Consumer.class))).thenAnswer(i->{
            Consumer<BlockDisplay> initialize=i.getArgument(2); initialize.accept(display); return display;
        });
        selection=new PlayerTalents(); when(talents.getPlayerTalents(caster)).thenReturn(selection);
        item=mock(ItemStack.class); when(item.getType()).thenReturn(Material.BLAZE_POWDER); when(item.clone()).thenReturn(item);
        scheduler=mock(BukkitScheduler.class); var server=mock(Server.class); when(plugin.getServer()).thenReturn(server); when(server.getScheduler()).thenReturn(scheduler);
        bukkit=mockStatic(Bukkit.class); bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(()->Bukkit.createBlockData(Material.MAGMA_BLOCK)).thenReturn(mock(BlockData.class));
        when(scheduler.runTaskTimer(eq(plugin),any(Runnable.class),anyLong(),anyLong())).thenAnswer(i->schedule(i.getArgument(1),i.getArgument(2),i.getArgument(3)));
        when(scheduler.runTaskLater(eq(plugin),any(Runnable.class),anyLong())).thenAnswer(i->schedule(i.getArgument(1),i.getArgument(2),0));
        doAnswer(i->{tasks.stream().filter(task->task.id==(int)i.getArgument(0)).forEach(task->task.cancelled=true);return null;}).when(scheduler).cancelTask(anyInt());
        skills=new TalentSkillManager(plugin);
    }
    BukkitTask schedule(Runnable action,long delay,long period) {
        Scheduled task=new Scheduled(); task.action=action; task.next=tick+Math.max(1,delay); task.period=period; task.id=tasks.size()+1;
        tasks.add(task); var handle=mock(BukkitTask.class); when(handle.getTaskId()).thenReturn(task.id);
        doAnswer(i->{task.cancelled=true;return null;}).when(handle).cancel(); return handle;
    }
    void ticks(int count) {
        for(int i=0;i<count;i++) {
            tick++;
            for(var task:new ArrayList<>(tasks)) if(!task.cancelled&&task.next<=tick) {
                task.action.run(); if(task.period==0) task.cancelled=true; else task.next=tick+task.period;
            }
        }
    }
    boolean cast(String id) {
        var row=config.getConfigurationSection("fire."+id); assertNotNull(row);
        var talent=new Talent(id,row.getString("name"),row.getString("description"),TalentType.ACTIVE,TalentBranch.FIRE,
                row.getInt("max-level"),1,List.of(),Talent.PrerequisiteMode.ALL,0,"BLAZE_POWDER","烈焰粉",row.getDouble("cooldown"),row.getDouble("manaCost"),0,"RIGHT_CLICK");
        var data=new Talent.TalentLevelData(); var effects=row.getConfigurationSection("levels.1.effect");
        effects.getValues(false).forEach((key,value)->{if(value instanceof Number n)data.addEffect(key,n.doubleValue());}); talent.addLevelData(1,data);
        when(talents.findTalent(id)).thenReturn(talent); selection.setTalentLevel(id,1); selection.setSelectedSkill(0,id);
        return skills.castSkillDirectly(caster,id,item);
    }
    @AfterEach void close() { skills.shutdownMeteor(); bukkit.close(); }

    @ParameterizedTest @CsvSource({
            "fire_spark,1,42,false", "destruction_orb,1,49,true", "flame_storm,20,8.4,true", "meteor,55,175,false",
            "flame_jet,5,2.916666666666667,true", "scorching_wave,1,38.5,false", "inferno_ring,20,28,true", "hell_domain,10,12.3,true"})
    void allEightSpellsDealTheirOwnAttributedDamageWithZeroAllocatedMagic(String id,int firstHit,double expected,boolean isPeriodic) {
        assertEquals(0,stats.getMagic()); assertEquals(0,stats.getSpirit());
        assertEquals(35,stats.getTotalMagic()); assertEquals(12,stats.getTotalSpirit());
        assertTrue(cast(id)); ticks(firstHit-1); assertEquals(1000,hp.get(),1e-8); ticks(1);
        assertEquals(1000-expected,hp.get(),1e-8,id+" must damage independently of burning");
        verify(target,times(1)).damage(doubleThat(value->Math.abs(value-expected)<1e-8),eq(caster));
        assertEquals(List.of(isPeriodic),periodic); assertEquals(20,hurt.get());
        verify(target).setFireTicks(anyInt()); assertFalse(SkillDamageGuard.isSkillDamage(caster)); assertFalse(SkillDamageGuard.isPeriodic(caster));
    }
    @Test void jetHitsOnlyOncePerPulseAndAllTwelvePulsesSurviveBurnHurtFrames() {
        assertTrue(cast("flame_jet")); ticks(60);
        assertEquals(965,hp.get(),1e-8); verify(target,times(12)).damage(anyDouble(),eq(caster)); assertEquals(20,hurt.get());
    }
    @Test void cancelledSkillDamageDoesNotBypassTheServerAndRestoresHurtFrames() {
        doAnswer(i->{assertEquals(0,hurt.get());return null;}).when(target).damage(anyDouble(),eq(caster));
        assertTrue(cast("flame_jet")); ticks(5);
        assertEquals(1000,hp.get(),1e-8); assertEquals(20,hurt.get()); assertFalse(SkillDamageGuard.isSkillDamage(caster));
    }
}
