package com.customrpg.managers;

import com.customrpg.CustomRPG;
import com.customrpg.players.PlayerTalents;
import com.customrpg.talents.*;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.PluginManager;
import org.bukkit.util.*;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FrostTalentSkillsTest {
    CustomRPG plugin;FrostTalentSkills skills;ExpandedTalentSkills combat;Player player;World world;PlayerTalents learned;
    List<LivingEntity> nearby=new ArrayList<>();PluginManager events;Map<UUID,Double> health=new HashMap<>();Map<UUID,Double> modifiers=new HashMap<>();
    @BeforeEach void setup() {
        plugin=mock(CustomRPG.class);var server=mock(Server.class);events=mock(PluginManager.class);
        when(plugin.getServer()).thenReturn(server);when(server.getPluginManager()).thenReturn(events);when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        world=mock(World.class);when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);when(world.getMinHeight()).thenReturn(-64);when(world.getMaxHeight()).thenReturn(320);
        player=mock(Player.class);when(player.getUniqueId()).thenReturn(UUID.randomUUID());when(player.isOnline()).thenReturn(true);when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getWorld()).thenReturn(world);when(player.getLocation()).thenAnswer(i->new Location(world,0,64,0));when(player.getEyeLocation()).thenAnswer(i->new Location(world,0,65.5,0));
        when(player.hasLineOfSight(any(Entity.class))).thenReturn(true);
        learned=new PlayerTalents();var manager=mock(TalentManager.class);when(plugin.getTalentManager()).thenReturn(manager);when(manager.getPlayerTalents(player)).thenReturn(learned);
        combat=spy(new ExpandedTalentSkills(plugin,()->0,mock(ExpandedTalentSkills.StatusEffects.class)));var motion=mock(FrostTalentSkills.MotionEffects.class);
        doAnswer(i->{LivingEntity e=i.getArgument(0);double value=i.getArgument(1);if(value==0)modifiers.remove(e.getUniqueId());else modifiers.put(e.getUniqueId(),value);return null;}).when(motion).set(any(),anyDouble());
        doAnswer(i->{LivingEntity e=i.getArgument(0);modifiers.remove(e.getUniqueId());return null;}).when(motion).clear(any());
        skills=new FrostTalentSkills(plugin,combat,motion);
        when(world.getNearbyEntities(any(Location.class),anyDouble(),anyDouble(),anyDouble())).thenAnswer(i->new ArrayList<>(nearby));
        when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenAnswer(i->{int x=i.getArgument(0),y=i.getArgument(1),z=i.getArgument(2);var b=mock(Block.class);
            when(b.getType()).thenReturn(y<=63?Material.STONE:Material.AIR);when(b.isPassable()).thenReturn(y>63);when(b.getBoundingBox()).thenReturn(new BoundingBox(x,y,z,x+1,y+1,z+1));return b;});
        var border=mock(WorldBorder.class);when(world.getWorldBorder()).thenReturn(border);when(border.isInside(any())).thenReturn(true);
        when(world.getBlockAt(any(Location.class))).thenAnswer(i->{Location at=i.getArgument(0);return world.getBlockAt(at.getBlockX(),at.getBlockY(),at.getBlockZ());});
    }
    LivingEntity mob(double x,double z) {
        var mob=mock(Zombie.class);when(mob.getUniqueId()).thenReturn(UUID.randomUUID());when(mob.getWorld()).thenReturn(world);
        when(mob.isValid()).thenReturn(true);when(mob.getMaxHealth()).thenReturn(1000.0);when(mob.getLocation()).thenAnswer(i->new Location(world,x,64,z));when(mob.getEyeLocation()).thenAnswer(i->new Location(world,x,65.3,z));
        when(mob.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));when(mob.hasLineOfSight(any(Entity.class))).thenReturn(true);
        health.put(mob.getUniqueId(),1000.0);when(mob.getHealth()).thenAnswer(i->health.get(mob.getUniqueId()));when(mob.isDead()).thenAnswer(i->health.get(mob.getUniqueId())<=0);
        doAnswer(i->{health.compute(mob.getUniqueId(),(id,hp)->hp-(double)i.getArgument(0));return null;}).when(mob).damage(anyDouble(),eq(player));
        nearby.add(mob);return mob;
    }
    Talent talent(FrostSkill skill) {
        var t=new Talent(skill.id,skill.title,"test",TalentType.ACTIVE,TalentBranch.FROST,skill==FrostSkill.CROWN?1:3,1,List.of(),Talent.PrerequisiteMode.ALL,skill.slot,"BLUE_ICE","木棒",skill.cooldown,skill.mana,0,"RIGHT_CLICK");
        var data=new Talent.TalentLevelData();data.addEffect("baseDamage",skill.damage);data.addEffect("range",skill.range);t.addLevelData(1,data);
        learned.setTalentLevel(skill.id,1);if(skill==FrostSkill.CROWN)learned.setUltimateSkill(skill.id);else learned.setSelectedSkill(0,skill.id);return t;
    }
    void ticks(int count){for(int i=0;i<count;i++)skills.tick();}
    void aimed(LivingEntity target){doReturn(target).when(combat).aim(player,10);doReturn(target).when(combat).aim(player,12);}
    @Test void piercerTravelsInSegmentsHitsOnceAndDoesNotPierceABlockingWall() {
        var target=mob(0,6);var wall=new java.util.concurrent.atomic.AtomicBoolean(false);
        when(world.rayTrace(any(Location.class),any(Vector.class),anyDouble(),eq(FluidCollisionMode.NEVER),eq(true),anyDouble(),any())).thenAnswer(i->{
            Location from=i.getArgument(0);double range=i.getArgument(2),z=wall.get()?3:6;
            if(from.getZ()>z||from.getZ()+range<z)return null;
            return wall.get()?new RayTraceResult(new Vector(0,65.5,z),mock(Block.class),org.bukkit.block.BlockFace.NORTH):new RayTraceResult(new Vector(0,65.5,z),target);
        });
        var t=talent(FrostSkill.PIERCER);assertTrue(skills.cast(player,t,1));ticks(6);verify(target,never()).damage(anyDouble(),eq(player));
        ticks(10);verify(target,times(1)).damage(7,player);clearInvocations(target);wall.set(true);
        assertTrue(skills.cast(player,t,1));ticks(20);verify(target,never()).damage(anyDouble(),eq(player));
    }
    @Test void phantomStepUsesRecentMovementAndStopsBeforeCollisions() {
        skills.move(new PlayerMoveEvent(player,new Location(world,0,64,0),new Location(world,.2,64,0)));
        when(player.collidesAt(any(Location.class))).thenAnswer(i->((Location)i.getArgument(0)).getX()>=2);
        var destination=new AtomicReference<Location>();
        when(player.teleport(any(Location.class),eq(PlayerTeleportEvent.TeleportCause.PLUGIN))).thenAnswer(i->{assertTrue(skills.internalStep(player));destination.set(i.getArgument(0));return true;});
        assertTrue(skills.cast(player,talent(FrostSkill.STEP),1));assertNotNull(destination.get());assertEquals(1.75,destination.get().getX());assertEquals(0,destination.get().getZ());assertFalse(skills.internalStep(player));
    }
    @Test void unloadedTerrainAndCancelledTeleportNeverStartADash() {
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(false);
        assertFalse(skills.cast(player,talent(FrostSkill.STEP),1));verify(player,never()).teleport(any(Location.class),any(PlayerTeleportEvent.TeleportCause.class));
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);when(player.teleport(any(Location.class),any(PlayerTeleportEvent.TeleportCause.class))).thenReturn(false);
        assertFalse(skills.cast(player,talent(FrostSkill.STEP),1));
    }
    @Test void groundFangsHaveStaggeredDamageButNeverHitOneVictimMoreThanOnce() {
        var target=mob(0,3);assertTrue(skills.cast(player,talent(FrostSkill.FANGS),1));ticks(5);verify(target,never()).damage(anyDouble(),eq(player));
        ticks(20);verify(target,times(1)).damage(8,player);
    }
    @Test void mistDamagesOnlyThreeTimesAndItsSingleMarkCannotFreezeByItself() {
        var target=mob(0,12);assertTrue(skills.cast(player,talent(FrostSkill.MIST),1));ticks(39);verify(target,never()).damage(anyDouble(),eq(player));
        ticks(1);verify(target).damage(2,player);ticks(80);verify(target,times(3)).damage(2,player);
        assertFalse(modifiers.getOrDefault(target.getUniqueId(),0.0)==1);ticks(12);assertTrue(!modifiers.containsKey(target.getUniqueId()));
    }
    @Test void crossHasNoDamageButThreeCastsFreezeAnOrdinaryEnemyAndControlCancellationPreventsMarks() {
        var target=mob(0,4);aimed(target);var t=talent(FrostSkill.CROSS);
        for(int i=0;i<3;i++)assertTrue(skills.cast(player,t,1));verify(target,never()).damage(anyDouble(),eq(player));
        assertTrue(modifiers.getOrDefault(target.getUniqueId(),0.0)==1);ticks(16);
        assertTrue(modifiers.getOrDefault(target.getUniqueId(),0.0)==.5);skills.clear();
        doAnswer(i->{if(i.getArgument(0) instanceof FrostControlEvent e)e.setCancelled(true);return null;}).when(events).callEvent(any());
        assertFalse(skills.cast(player,t,1));assertTrue(!modifiers.containsKey(target.getUniqueId()));
    }
    @Test void rootedMobsCannotWalkOrBePushedHorizontallyUntilControlExpires() {
        var target=mob(0,4);aimed(target);var t=talent(FrostSkill.CROSS);for(int i=0;i<3;i++)skills.cast(player,t,1);
        var move=new io.papermc.paper.event.entity.EntityMoveEvent(target,new Location(world,0,64,4),new Location(world,1,64,5));skills.mobMove(move);
        assertEquals(0,move.getTo().getX());assertEquals(4,move.getTo().getZ());ticks(16);
        var next=new io.papermc.paper.event.entity.EntityMoveEvent(target,new Location(world,0,64,4),new Location(world,1,64,5));skills.mobMove(next);assertEquals(1,next.getTo().getX());
    }
    @Test void crownHitsAtThirtyTicksOnceAndCannotAddAPassiveFreezeOnTop() {
        var target=mob(0,11);var cross=talent(FrostSkill.CROSS);aimed(target);skills.cast(player,cross,1);skills.cast(player,cross,1);
        assertTrue(skills.cast(player,talent(FrostSkill.CROWN),1));ticks(29);verify(target,never()).damage(anyDouble(),eq(player));
        ticks(1);verify(target,times(1)).damage(16,player);ticks(80);verify(target,times(1)).damage(anyDouble(),eq(player));
        assertTrue(!modifiers.containsKey(target.getUniqueId()));
    }
    @Test void bossReceivesOnlySlowAndKeepsTheEightSecondPassiveControlImmunity() {
        var target=mob(0,4);var mobs=mock(MobManager.class);when(plugin.getMobManager()).thenReturn(mobs);when(mobs.getCustomMobKey(target)).thenReturn("boss");
        var data=mock(MobManager.MobData.class);when(mobs.getMobData("boss")).thenReturn(data);when(data.isBoss()).thenReturn(true);
        aimed(target);var t=talent(FrostSkill.CROSS);for(int i=0;i<3;i++)skills.cast(player,t,1);
        assertFalse(modifiers.getOrDefault(target.getUniqueId(),0.0)==1);
    }
    @Test void prismUsesDistinctTargetsAndContinuesFromTheFirstKillLocation() {
        var first=mob(0,5);var second=mob(1,8);var third=mob(-1,10);health.put(first.getUniqueId(),5.0);aimed(first);
        assertTrue(skills.cast(player,talent(FrostSkill.PRISM),1));ticks(12);verify(first).damage(6,player);ticks(12);
        verify(second,times(1)).damage(4,player);verify(third,times(1)).damage(4,player);
    }
    @Test void cancellingTheFirstPrismHitPreventsAllLaterChainDamage() {
        var first=mob(0,5);var second=mob(1,8);aimed(first);doNothing().when(first).damage(anyDouble(),eq(player));
        skills.cast(player,talent(FrostSkill.PRISM),1);ticks(30);verify(second,never()).damage(anyDouble(),eq(player));
    }
    @Test void mirrorReducesDamageButCounterRequiresAcceptedDamageAndOnlyFiresOnce() {
        var attacker=mob(0,3);skills.cast(player,talent(FrostSkill.MIRROR),1);
        var hit=mock(EntityDamageByEntityEvent.class);when(hit.getEntity()).thenReturn(player);when(hit.getDamager()).thenReturn(attacker);when(hit.getDamage()).thenReturn(8.0);
        when(hit.getFinalDamage()).thenReturn(0.0);skills.defend(hit);verify(hit).setDamage(6);skills.counter(hit);ticks(1);verify(attacker,never()).damage(anyDouble(),eq(player));
        when(hit.getFinalDamage()).thenReturn(6.0);skills.counter(hit);skills.counter(hit);ticks(5);verify(attacker,never()).damage(anyDouble(),eq(player));ticks(1);verify(attacker,times(1)).damage(3,player);
    }
    @Test void resetLogoutAndWorldChangesStopScheduledDamageAndRemoveMovementModifiers() {
        var target=mob(0,12);skills.cast(player,talent(FrostSkill.MIST),1);ticks(5);assertTrue(modifiers.containsKey(target.getUniqueId()));
        learned.resetAllTalents();ticks(40);verify(target,never()).damage(anyDouble(),eq(player));skills.clear();assertTrue(!modifiers.containsKey(target.getUniqueId()));
        skills.cast(player,talent(FrostSkill.CROWN),1);when(player.getWorld()).thenReturn(mock(World.class));ticks(40);verify(target,never()).damage(anyDouble(),eq(player));
    }
    @Test void previewServiceIsNeverNeededForCombatAndCannotChangeMistDamageCadence() {
        var target=mob(0,12);assertNull(plugin.getVfxService());skills.cast(player,talent(FrostSkill.MIST),1);ticks(121);verify(target,times(3)).damage(2,player);
    }
    @Test void ordinarySlowCleanseNeverRemovesTheFrostRoot(){
        var target=mob(0,4);aimed(target);var t=talent(FrostSkill.CROSS);for(int i=0;i<3;i++)skills.cast(player,t,1);assertEquals(1,modifiers.get(target.getUniqueId()));assertTrue(skills.cleanseOrdinarySlow(target));assertEquals(1,modifiers.get(target.getUniqueId()));ticks(16);assertNull(modifiers.get(target.getUniqueId()));
    }

}
