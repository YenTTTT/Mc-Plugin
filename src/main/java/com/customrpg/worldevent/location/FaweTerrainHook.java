package com.customrpg.worldevent.location;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.entity.BaseEntity;
import com.sk89q.worldedit.entity.Entity;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import java.util.UUID;
import java.lang.reflect.InvocationTargetException;

/** Rejects FAWE edits in reserved event areas without reading Bukkit state on an edit worker thread. */
public final class FaweTerrainHook implements AutoCloseable {
    @FunctionalInterface public interface EditPermission {
        boolean canEdit(String worldName,int x,int y,int z);
    }

    private final EditPermission permission;
    private volatile boolean active=true;

    public FaweTerrainHook(EditPermission permission) { this.permission=permission; }

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

    public boolean active() { return active; }

    @Subscribe public void onEditSession(EditSessionEvent event) {
        if(event.getWorld()==null || event.getStage()!=EditSession.Stage.BEFORE_CHANGE
                && event.getStage()!=EditSession.Stage.BEFORE_HISTORY) return;
        String worldName=event.getWorld().getName();
        event.setExtent(new AbstractDelegateExtent(event.getExtent()) {
            private void beforeChange(int x,int y,int z) {
                if(!active||worldName==null||worldName.isBlank()
                        ||!permission.canEdit(worldName,x,y,z)) {
                    throw new IllegalStateException("CustomRPG: event/recovery area is protected from FAWE");
                }
            }
            @Override public <T extends BlockStateHolder<T>> boolean setBlock(BlockVector3 position,T block) throws WorldEditException {
                beforeChange(position.x(),position.y(),position.z());
                return super.setBlock(position,block);
            }
            // FAWE adds this overload; WorldEdit 7.3.9 does not declare it. At runtime
            // the matching FAWE signature still dispatches to this method.
            public <T extends BlockStateHolder<T>> boolean setBlock(int x,int y,int z,T block) throws WorldEditException {
                beforeChange(x,y,z);
                return super.setBlock(BlockVector3.at(x,y,z),block);
            }
            @Override public boolean setBiome(BlockVector3 position,BiomeType biome) {
                beforeChange(position.x(),position.y(),position.z());
                return super.setBiome(position,biome);
            }
            public boolean setBiome(int x,int y,int z,BiomeType biome) {
                beforeChange(x,y,z);
                return super.setBiome(BlockVector3.at(x,y,z),biome);
            }
            @Override public Entity createEntity(com.sk89q.worldedit.util.Location location,BaseEntity entity) {
                BlockVector3 position=location.toVector().toBlockPoint();
                beforeChange(position.x(),position.y(),position.z());
                return super.createEntity(location,entity);
            }
            public Entity createEntity(com.sk89q.worldedit.util.Location location,BaseEntity entity,UUID uuid) {
                BlockVector3 position=location.toVector().toBlockPoint();
                beforeChange(position.x(),position.y(),position.z());
                try {
                    return (Entity)com.sk89q.worldedit.extent.Extent.class
                            .getMethod("createEntity",com.sk89q.worldedit.util.Location.class,BaseEntity.class,UUID.class)
                            .invoke(getExtent(),location,entity,uuid);
                } catch(NoSuchMethodException error) {
                    return super.createEntity(location,entity);
                } catch(IllegalAccessException error) {
                    throw new IllegalStateException("FAWE entity edit could not be delegated",error);
                } catch(InvocationTargetException error) {
                    Throwable cause=error.getCause();
                    if(cause instanceof RuntimeException runtime) throw runtime;
                    if(cause instanceof Error failure) throw failure;
                    throw new IllegalStateException("FAWE entity edit failed",cause);
                }
            }
        });
    }

    @Override public void close() {
        active=false;
        WorldEdit.getInstance().getEventBus().unregister(this);
    }
}
