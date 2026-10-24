package com.customrpg.utils;

import com.customrpg.CustomRPG;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.*;
import org.bukkit.inventory.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;

/** Temporary real composters with a write-ahead restoration journal; never loads chunks. */
public final class TemporaryChickenNests implements Listener {
    public interface Lease {Location location();boolean active();void close();}
    private record Key(UUID world,int x,int y,int z){}
    private final class Entry implements Lease {
        final Key key;final String before;boolean active=true;
        Entry(Key key,String before){this.key=key;this.before=before;}
        public Location location(){return new Location(plugin.getServer().getWorld(key.world),key.x+.5,key.y,key.z+.5);}
        public boolean active(){var w=plugin.getServer().getWorld(key.world);return active&&w!=null&&w.isChunkLoaded(key.x>>4,key.z>>4)&&w.getBlockAt(key.x,key.y,key.z).getType()==Material.COMPOSTER;}
        public void close(){restore(this);}
    }
    private final CustomRPG plugin;private final java.util.function.Supplier<ItemStack> item;private final Predicate<Location> protectedAt;private final Map<Key,Entry> entries=new HashMap<>();private boolean ready;private Key notifying;
    public TemporaryChickenNests(CustomRPG p,Predicate<Location> protection){this(p,protection,()->new ItemStack(Material.COMPOSTER));}
    TemporaryChickenNests(CustomRPG p,Predicate<Location> protection,java.util.function.Supplier<ItemStack> items){plugin=p;protectedAt=protection;item=items;}
    private Key key(Block b){return new Key(b.getWorld().getUID(),b.getX(),b.getY(),b.getZ());}
    private static boolean air(Material m){return m==Material.AIR||m==Material.CAVE_AIR||m==Material.VOID_AIR;}
    private boolean owns(Block b){return b!=null&&entries.containsKey(key(b));}
    private Path journal(){return plugin.getDataFolder().toPath().resolve("chicken-nests.yml");}
    private void save()throws Exception{
        var yaml=new YamlConfiguration();var rows=new ArrayList<Map<String,Object>>();
        for(var e:entries.values()){var r=new LinkedHashMap<String,Object>();r.put("world",e.key.world.toString());r.put("x",e.key.x);r.put("y",e.key.y);r.put("z",e.key.z);r.put("before",e.before);rows.add(r);}yaml.set("blocks",rows);
        Files.createDirectories(journal().getParent());Path next=journal().resolveSibling("chicken-nests.yml.pending");Files.writeString(next,yaml.saveToString(),java.nio.charset.StandardCharsets.UTF_8);
        try{Files.move(next,journal(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(next,journal(),StandardCopyOption.REPLACE_EXISTING);}
    }
    public void start(){
        if(ready)return;try{
            if(Files.exists(journal())){var yaml=new YamlConfiguration();yaml.load(journal().toFile());for(var r:yaml.getMapList("blocks")){var k=new Key(UUID.fromString((String)r.get("world")),((Number)r.get("x")).intValue(),((Number)r.get("y")).intValue(),((Number)r.get("z")).intValue());String prior=(String)r.get("before");BlockData data=Bukkit.createBlockData(prior);if(!air(data.getMaterial()))throw new IllegalArgumentException("Non-air nest restoration record");entries.put(k,new Entry(k,prior));}}
            ready=true;for(var e:new ArrayList<>(entries.values()))restore(e);plugin.getServer().getPluginManager().registerEvents(this,plugin);
        }catch(Exception bad){ready=false;plugin.getLogger().severe("Chicken nests unavailable; restoration journal preserved: "+bad.getClass().getSimpleName());}
    }
    public Lease place(Player owner,Location at){
        if(!ready||entries.size()>=64||!at.getWorld().isChunkLoaded(at.getBlockX()>>4,at.getBlockZ()>>4)||protectedAt.test(at))return null;
        var b=at.getBlock();if(!at.getWorld().getWorldBorder().isInside(b.getLocation())||!air(b.getType())||owns(b)||!b.getRelative(BlockFace.DOWN).isSolid())return null;
        if(b.getWorld().getNearbyEntities(new org.bukkit.util.BoundingBox(b.getX(),b.getY(),b.getZ(),b.getX()+1,b.getY()+1,b.getZ()+1)).stream().anyMatch(e->e instanceof org.bukkit.entity.LivingEntity))return null;
        var previous=b.getState();var e=new Entry(key(b),b.getBlockData().getAsString());entries.put(e.key,e);
        try{
            save();b.setType(Material.COMPOSTER,false);
            var event=new BlockPlaceEvent(b,previous,b.getRelative(BlockFace.DOWN),item.get(),owner,true,EquipmentSlot.HAND);
            notifying=e.key;try{plugin.getServer().getPluginManager().callEvent(event);}finally{notifying=null;}
            if(event.isCancelled()||!event.canBuild()||!e.active()){restore(e);return null;}return e;
        }catch(Exception failure){restore(e);plugin.getLogger().warning("Chicken nest placement failed: "+failure.getClass().getSimpleName());return null;}
    }

    private void restore(Entry e){if(!e.active||entries.get(e.key)!=e)return;var w=plugin.getServer().getWorld(e.key.world);if(w==null||!w.isChunkLoaded(e.key.x>>4,e.key.z>>4))return;
        try{var b=w.getBlockAt(e.key.x,e.key.y,e.key.z);if(b.getType()==Material.COMPOSTER)b.setBlockData(Bukkit.createBlockData(e.before),false);e.active=false;entries.remove(e.key);save();}catch(Exception failure){plugin.getLogger().warning("Chicken nest restoration deferred: "+failure.getClass().getSimpleName());}
    }
    public void shutdown(){if(!ready)return;for(var e:new ArrayList<>(entries.values()))restore(e);HandlerList.unregisterAll(this);ready=false;}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void interact(PlayerInteractEvent e){if(owns(e.getClickedBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void breakBlock(BlockBreakEvent e){if(owns(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void placeBlock(BlockPlaceEvent e){if(owns(e.getBlock())&&!key(e.getBlock()).equals(notifying))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void burn(BlockBurnEvent e){if(owns(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void physics(BlockPhysicsEvent e){if(owns(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void flow(BlockFromToEvent e){if(owns(e.getToBlock())||owns(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void entityBlock(EntityChangeBlockEvent e){if(owns(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void explode(EntityExplodeEvent e){e.blockList().removeIf(this::owns);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void explode(BlockExplodeEvent e){e.blockList().removeIf(this::owns);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void piston(BlockPistonExtendEvent e){if(e.getBlocks().stream().anyMatch(b->owns(b)||owns(b.getRelative(e.getDirection()))))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void piston(BlockPistonRetractEvent e){if(e.getBlocks().stream().anyMatch(b->owns(b)||owns(b.getRelative(e.getDirection()))))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void hopper(InventoryMoveItemEvent e){if(e.getSource().getHolder() instanceof BlockInventoryHolder b&&owns(b.getBlock())||e.getDestination().getHolder() instanceof BlockInventoryHolder d&&owns(d.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void unload(ChunkUnloadEvent event){for(var e:new ArrayList<>(entries.values()))if(e.key.world.equals(event.getWorld().getUID())&&e.key.x>>4==event.getChunk().getX()&&e.key.z>>4==event.getChunk().getZ())restore(e);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void worldUnload(WorldUnloadEvent event){for(var e:new ArrayList<>(entries.values()))if(e.key.world.equals(event.getWorld().getUID()))restore(e);}
    @EventHandler public void load(ChunkLoadEvent event){for(var e:new ArrayList<>(entries.values()))if(e.key.world.equals(event.getWorld().getUID())&&e.key.x>>4==event.getChunk().getX()&&e.key.z>>4==event.getChunk().getZ())restore(e);}
}
