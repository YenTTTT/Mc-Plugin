package com.customrpg.worldevent.location;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.entity.BaseEntity;
import com.sk89q.worldedit.entity.Entity;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import java.io.IOException;
import java.util.function.Predicate;

/** Standard synchronous WorldEdit only. FAWE remains unsupported and fails closed. */
public final class WorldEditTerrainHook implements AutoCloseable {
    private final NaturalTerrainRegistry provenance;
    private final Predicate<Location> canEdit;
    private volatile boolean active=true;
    private volatile boolean healthy=true;

    public WorldEditTerrainHook(NaturalTerrainRegistry provenance,Predicate<Location> canEdit) {
        this.provenance=provenance; this.canEdit=canEdit;
    }
    public void register() {
        var bus=WorldEdit.getInstance().getEventBus();
        try { bus.register(this); }
        catch(RuntimeException|LinkageError error) {
            active=false;
            try { bus.unregister(this); }
            catch(RuntimeException|LinkageError cleanup) { error.addSuppressed(cleanup); }
            throw error;
        }
    }
    @Override public void close() {
        active=false;
        WorldEdit.getInstance().getEventBus().unregister(this);
    }
    public boolean active() { return active&&healthy; }

    @Subscribe public void onEditSession(EditSessionEvent event) {
        if(event.getStage()!=EditSession.Stage.BEFORE_CHANGE||event.getWorld()==null) return;
        World resolved;
        try { resolved=BukkitAdapter.adapt(event.getWorld()); }
        catch(RuntimeException|LinkageError error) { healthy=false; resolved=null; }
        if(resolved==null) healthy=false;
        World world=resolved;
        event.setExtent(new AbstractDelegateExtent(event.getExtent()) {
            private void beforeChange(int x,int y,int z) {
                if(!active()||world==null||!Bukkit.isPrimaryThread()) throw new IllegalStateException("CustomRPG: unavailable, asynchronous or stale WorldEdit session refused");
                if(!canEdit.test(new Location(world,x,y,z))) throw new IllegalStateException("CustomRPG: event/recovery area is protected from WorldEdit");
                try {
                    // Persist revocation BEFORE allowing a write; a failed save must not leave trusted modified terrain.
                    if(!provenance.healthy()) throw new IllegalStateException("CustomRPG: natural terrain tracking unavailable");
                    provenance.revoke(world.getUID(),x>>4,z>>4);
                } catch(IOException error) { throw new IllegalStateException("CustomRPG: cannot persist WorldEdit terrain change",error); }
            }
            @Override public <T extends BlockStateHolder<T>> boolean setBlock(BlockVector3 position,T block) throws WorldEditException {
                beforeChange(position.x(),position.y(),position.z());
                return super.setBlock(position,block);
            }
            @Override public boolean setBiome(BlockVector3 position,BiomeType biome) {
                beforeChange(position.x(),position.y(),position.z());
                return super.setBiome(position,biome);
            }
            @Override public Entity createEntity(com.sk89q.worldedit.util.Location location,BaseEntity entity) {
                BlockVector3 position=location.toVector().toBlockPoint();
                beforeChange(position.x(),position.y(),position.z());
                return super.createEntity(location,entity);
            }
        });
    }
}
