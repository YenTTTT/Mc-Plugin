package com.customrpg.managers;

import com.customrpg.CustomRPG;
import com.customrpg.players.PlayerTalents;
import com.customrpg.talents.*;
import org.bukkit.*;
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
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EarthTalentSkillsTest {
    CustomRPG plugin;World world;Player player,ally;ExpandedTalentSkills combat;EarthTalentSkills skills;PluginManager events;PlayerTalents learned;TalentManager manager;
    final List<Entity> nearby=new ArrayList<>();final Map<UUID,Double> hp=new HashMap<>(),absorb=new HashMap<>(),speed=new HashMap<>();final Map<String,Talent> definitions=new HashMap<>();boolean transferCancelled;double transferred;
    @BeforeEach void setup(){
        plugin=mock(CustomRPG.class);var server=mock(Server.class);events=mock(PluginManager.class);when(plugin.getServer()).thenReturn(server);when(server.getPluginManager()).thenReturn(events);when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        world=mock(World.class);when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);when(world.getMinHeight()).thenReturn(-64);when(world.getMaxHeight()).thenReturn(320);var border=mock(WorldBorder.class);when(world.getWorldBorder()).thenReturn(border);when(border.isInside(any())).thenReturn(true);
        player=person(0,0);ally=person(0,5);learned=new PlayerTalents();manager=mock(TalentManager.class);when(plugin.getTalentManager()).thenReturn(manager);when(manager.getPlayerTalents(player)).thenReturn(learned);when(manager.findTalent(anyString())).thenAnswer(i->definitions.get(i.getArgument(0)));
        combat=spy(new ExpandedTalentSkills(plugin,()->0,mock(ExpandedTalentSkills.StatusEffects.class)));doReturn(true).when(combat).ally(player,player);doReturn(true).when(combat).ally(player,ally);
        var motion=mock(EarthTalentSkills.Motion.class);doAnswer(i->{LivingEntity e=i.getArgument(0);speed.put(e.getUniqueId(),i.getArgument(1));return null;}).when(motion).set(any(),anyDouble());doAnswer(i->{LivingEntity e=i.getArgument(0);speed.remove(e.getUniqueId());return null;}).when(motion).clear(any());
        var access=new EarthShields.Access(){public double amount(Player p){return absorb.getOrDefault(p.getUniqueId(),0.0);}public void amount(Player p,double n){absorb.put(p.getUniqueId(),n);}public void capacity(Player p,double n){}};
        skills=new EarthTalentSkills(plugin,combat,motion,access,(target,n,attacker)->{if(!transferCancelled){transferred+=n;hp.compute(target.getUniqueId(),(id,h)->h-n);}});
        when(world.getNearbyEntities(any(Location.class),anyDouble(),anyDouble(),anyDouble())).thenAnswer(i->new ArrayList<>(nearby));
        when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenAnswer(i->{int x=i.getArgument(0),y=i.getArgument(1),z=i.getArgument(2);var b=mock(Block.class);when(b.isPassable()).thenReturn(y>63);when(b.getBoundingBox()).thenReturn(new BoundingBox(x,y,z,x+1,y+1,z+1));return b;});
        when(world.getBlockAt(any(Location.class))).thenAnswer(i->{Location l=i.getArgument(0);return world.getBlockAt(l.getBlockX(),l.getBlockY(),l.getBlockZ());});
    }
    Player person(double x,double z){var p=mock(Player.class);init(p,x,z);when(p.isOnline()).thenReturn(true);when(p.getGameMode()).thenReturn(GameMode.SURVIVAL);when(p.hasLineOfSight(any(Entity.class))).thenReturn(true);when(p.getAbsorptionAmount()).thenAnswer(i->absorb.getOrDefault(p.getUniqueId(),0.0));return p;}
    void init(LivingEntity e,double x,double z){when(e.getUniqueId()).thenReturn(UUID.randomUUID());when(e.getWorld()).thenReturn(world);when(e.isValid()).thenReturn(true);when(e.getMaxHealth()).thenReturn(1000.0);hp.put(e.getUniqueId(),1000.0);when(e.getHealth()).thenAnswer(i->hp.get(e.getUniqueId()));when(e.isDead()).thenAnswer(i->hp.get(e.getUniqueId())<=0);when(e.getLocation()).thenAnswer(i->new Location(world,x,64,z));when(e.getEyeLocation()).thenAnswer(i->new Location(world,x,65.5,z));when(e.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));}
    LivingEntity mob(double x,double z){var e=mock(Zombie.class);init(e,x,z);doAnswer(i->{hp.compute(e.getUniqueId(),(id,h)->h-(double)i.getArgument(0));return null;}).when(e).damage(anyDouble(),eq(player));nearby.add(e);return e;}
    Talent talent(EarthSkill s){var t=new Talent(s.id,s.title,"test",TalentType.ACTIVE,TalentBranch.EARTH,s==EarthSkill.TITAN?1:3,1,List.of(),Talent.PrerequisiteMode.ALL,s.slot,"STONE","燧石",s.cooldown,s.mana,0,"RIGHT_CLICK");var data=new Talent.TalentLevelData();data.addEffect("baseDamage",s.damage);data.addEffect("range",s.range);t.addLevelData(1,data);definitions.put(s.id,t);learned.setTalentLevel(s.id,1);if(s==EarthSkill.TITAN)learned.setUltimateSkill(s.id);else{var selected=learned.getSelectedSkills();if(!Arrays.asList(selected).contains(s.id)){int slot=0;while(slot<4&&selected[slot]!=null)slot++;learned.setSelectedSkill(slot,s.id);}}return t;}
    void resolve(EntityDamageEvent e,double baseline){skills.mitigate(e,baseline);skills.share(e);}
    void ticks(int n){for(int i=0;i<n;i++)skills.tick();}
    void aimAlly(){var result=new RayTraceResult(ally.getEyeLocation().toVector(),ally);when(world.rayTrace(any(Location.class),any(Vector.class),anyDouble(),eq(FluidCollisionMode.NEVER),eq(true),anyDouble(),any())).thenReturn(result);}
    EntityDamageByEntityEvent attack(Player victim,LivingEntity attacker,double n){var e=mock(EntityDamageByEntityEvent.class);when(e.getEntity()).thenReturn(victim);when(e.getDamager()).thenReturn(attacker);when(e.getCause()).thenReturn(EntityDamageEvent.DamageCause.ENTITY_ATTACK);var amount=new AtomicReference<>(n);when(e.getDamage()).thenAnswer(i->amount.get());when(e.getFinalDamage()).thenAnswer(i->amount.get());doAnswer(i->{amount.set(i.getArgument(0));return null;}).when(e).setDamage(anyDouble());return e;}
    @Test void strikeHitsTheForwardWedgeOnceAndNeverTheRearOrSide(){var front=mob(0,3);var side=mob(2,1);var behind=mob(0,-2);skills.cast(player,talent(EarthSkill.STRIKE),1);ticks(4);verify(front,never()).damage(anyDouble(),eq(player));ticks(20);verify(front,times(1)).damage(6,player);verify(side,never()).damage(anyDouble(),eq(player));verify(behind,never()).damage(anyDouble(),eq(player));}
    @Test void directionalShieldAndCombinedDefenseHaveASeventyFivePercentCap(){var front=mob(0,3);var rear=mob(0,-3);skills.cast(player,talent(EarthSkill.AEGIS),1);var e=attack(player,front,10);resolve(e,10);assertEquals(6,e.getFinalDamage(),1e-8);var back=attack(player,rear,10);resolve(back,10);assertEquals(8.5,back.getFinalDamage(),1e-8);
        skills.cast(player,talent(EarthSkill.TITAN),1);ticks(25);skills.cast(player,talent(EarthSkill.REPRISAL),1);var heavy=attack(player,front,10);resolve(heavy,10);assertEquals(2.5,heavy.getFinalDamage(),1e-8);}
    @Test void sinkingFieldHitsAtMostThreeTimesForSevenAndQualityIsNotRequired(){var target=mob(0,12);skills.cast(player,talent(EarthSkill.SINK),1);ticks(100);verify(target,times(1)).damage(3,player);verify(target,times(2)).damage(2,player);assertEquals(993,hp.get(target.getUniqueId()));ticks(8);assertEquals(0,speed.getOrDefault(target.getUniqueId(),0.0));assertNull(plugin.getVfxService());}
    @Test void graspMovesOrdinaryEnemiesButBossOnlyReceivesSlow(){var normal=mob(0,6);var boss=mob(1,7);var mobs=mock(MobManager.class);when(plugin.getMobManager()).thenReturn(mobs);when(mobs.getCustomMobKey(boss)).thenReturn("boss");var data=mock(MobManager.MobData.class);when(mobs.getMobData("boss")).thenReturn(data);when(data.isBoss()).thenReturn(true);
        skills.cast(player,talent(EarthSkill.GRASP),1);ticks(14);verify(normal).teleport(any(Location.class),eq(PlayerTeleportEvent.TeleportCause.PLUGIN));verify(boss,never()).teleport(any(Location.class),any(PlayerTeleportEvent.TeleportCause.class));assertEquals(.2,speed.get(boss.getUniqueId()));verify(normal,never()).damage(anyDouble(),eq(player));}
    @Test void cancelledControlCannotPullOrApplySlow(){mob(0,7);doAnswer(i->{if(i.getArgument(0) instanceof EarthControlEvent e)e.setCancelled(true);return null;}).when(events).callEvent(any());skills.cast(player,talent(EarthSkill.GRASP),1);ticks(20);assertTrue(speed.isEmpty());verify(nearby.get(0),never()).teleport(any(Location.class),any(PlayerTeleportEvent.TeleportCause.class));}
    @Test void oathTransfersOnlyAcceptedFinalDamageUpToFiveAndBreaksAtNineBlocks(){var enemy=mob(0,6);aimAlly();skills.cast(player,talent(EarthSkill.OATH),1);var e=attack(ally,enemy,8);resolve(e,8);assertEquals(2,transferred,1e-8);assertEquals(6,e.getFinalDamage(),1e-7);var high=attack(ally,enemy,40);resolve(high,40);assertEquals(7,transferred,1e-8);assertEquals(35,high.getFinalDamage(),1e-7);
        when(ally.getLocation()).thenAnswer(i->new Location(world,0,64,9.1));ticks(1);resolve(attack(ally,enemy,8),8);assertEquals(7,transferred,1e-8);}
    @Test void cancelledTransferOrOriginalHitDoesNotGiveFreeMitigation(){var enemy=mob(0,6);aimAlly();skills.cast(player,talent(EarthSkill.OATH),1);transferCancelled=true;var e=attack(ally,enemy,8);resolve(e,8);assertEquals(8,e.getFinalDamage(),1e-8);assertEquals(0,transferred);transferCancelled=false;var cancelled=attack(ally,enemy,8);when(cancelled.isCancelled()).thenReturn(true);resolve(cancelled,8);assertEquals(0,transferred);}
    @Test void reprisalIsConsumedOnlyByTheFirstAcceptedDirectHit(){var enemy=mob(0,3);skills.cast(player,talent(EarthSkill.REPRISAL),1);var cancelled=attack(player,enemy,10);resolve(cancelled,10);when(cancelled.isCancelled()).thenReturn(true);skills.accepted(cancelled);var hit=attack(player,enemy,10);resolve(hit,10);assertEquals(5.5,hit.getFinalDamage(),1e-8);skills.accepted(hit);skills.accepted(attack(player,enemy,10));ticks(2);verify(enemy,times(1)).damage(5,player);}
    @Test void resilienceGainIsThrottledAndDefensiveCastConsumesThreeForFiveSecondShield(){var enemy=mob(0,3);talent(EarthSkill.AEGIS);for(int j=0;j<3;j++){skills.accepted(attack(player,enemy,8));skills.accepted(attack(player,enemy,8));if(j<2)ticks(40);}skills.cast(player,talent(EarthSkill.AEGIS),1);assertEquals(4,absorb.get(player.getUniqueId()));ticks(99);assertEquals(4,absorb.get(player.getUniqueId()));ticks(1);assertEquals(0,absorb.get(player.getUniqueId()));}
    @Test void rushStopsAtWallDoesNotShieldAnUnreachedAllyAndNeverMovesDuringPreview(){aimAlly();when(player.collidesAt(any(Location.class))).thenAnswer(i->((Location)i.getArgument(0)).getZ()>=2);when(player.teleport(any(Location.class),eq(PlayerTeleportEvent.TeleportCause.PLUGIN))).thenAnswer(i->{assertTrue(skills.internalRush(player));return true;});assertTrue(skills.cast(player,talent(EarthSkill.RUSH),1));var end=org.mockito.ArgumentCaptor.forClass(Location.class);verify(player).teleport(end.capture(),eq(PlayerTeleportEvent.TeleportCause.PLUGIN));assertTrue(end.getValue().getZ()<2);assertEquals(2,absorb.get(player.getUniqueId()));assertEquals(0,absorb.getOrDefault(ally.getUniqueId(),0.0));}
    @Test void successfulRushGivesBothShieldsAndCancellationGivesNone(){aimAlly();when(player.teleport(any(Location.class),eq(PlayerTeleportEvent.TeleportCause.PLUGIN))).thenReturn(false);assertFalse(skills.cast(player,talent(EarthSkill.RUSH),1));assertTrue(absorb.isEmpty());when(player.teleport(any(Location.class),eq(PlayerTeleportEvent.TeleportCause.PLUGIN))).thenReturn(true);assertTrue(skills.cast(player,talent(EarthSkill.RUSH),1));assertEquals(2,absorb.get(player.getUniqueId()));assertEquals(4,absorb.get(ally.getUniqueId()));ticks(80);assertEquals(0,absorb.get(ally.getUniqueId()));}
    @Test void titanDamageOccursOnceAtTwentyFiveTicksAndDefenseExpiresAfterEightSeconds(){var enemy=mob(0,3);skills.cast(player,talent(EarthSkill.TITAN),1);ticks(24);verify(enemy,never()).damage(anyDouble(),eq(player));ticks(1);verify(enemy,times(1)).damage(10,player);var incoming=attack(ally,enemy,8);resolve(incoming,8);assertEquals(6,incoming.getFinalDamage(),1e-8);ticks(160);verify(enemy,times(1)).damage(anyDouble(),eq(player));var after=attack(ally,enemy,8);resolve(after,8);assertEquals(8,after.getFinalDamage());}
    @Test void logoutAndWorldChangesCleanShieldsMovementAndPendingDamage(){aimAlly();when(player.teleport(any(Location.class),eq(PlayerTeleportEvent.TeleportCause.PLUGIN))).thenReturn(true);skills.cast(player,talent(EarthSkill.RUSH),1);skills.cast(player,talent(EarthSkill.AEGIS),1);skills.cancel(player);assertEquals(0,absorb.get(player.getUniqueId()));assertEquals(0,absorb.get(ally.getUniqueId()));assertEquals(0,speed.getOrDefault(player.getUniqueId(),0.0));var enemy=mob(0,3);skills.cast(player,talent(EarthSkill.TITAN),1);when(player.getWorld()).thenReturn(mock(World.class));ticks(30);verify(enemy,never()).damage(anyDouble(),eq(player));skills.shutdown();}
    @Test void pvpPullKeepsItsOwnSlowWhenTheSafeMoveFiresATeleportEvent(){
        var victim=person(0,6);nearby.add(victim);when(world.getPVP()).thenReturn(true);
        when(victim.teleport(any(Location.class),eq(PlayerTeleportEvent.TeleportCause.PLUGIN))).thenAnswer(i->{
            var event=mock(PlayerTeleportEvent.class);when(event.getPlayer()).thenReturn(victim);skills.teleport(event);return true;
        });
        assertTrue(skills.cast(player,talent(EarthSkill.GRASP),1));ticks(14);
        verify(victim).teleport(any(Location.class),eq(PlayerTeleportEvent.TeleportCause.PLUGIN));assertEquals(.2,speed.get(victim.getUniqueId()));ticks(40);assertEquals(0,speed.getOrDefault(victim.getUniqueId(),0.0));
    }

    @SuppressWarnings("deprecation")
    @Test void anAcceptedDirectHitConsumedByAbsorptionStillTriggersTheSingleReprisal(){
        var enemy=mob(0,3);skills.cast(player,talent(EarthSkill.REPRISAL),1);var e=attack(player,enemy,8);
        when(e.getFinalDamage()).thenReturn(0.0);when(e.isApplicable(EntityDamageEvent.DamageModifier.ABSORPTION)).thenReturn(true);when(e.getDamage(EntityDamageEvent.DamageModifier.ABSORPTION)).thenReturn(-8.0);
        skills.mitigate(e,8);skills.accepted(e);ticks(2);verify(enemy,times(1)).damage(5,player);
    }

}
