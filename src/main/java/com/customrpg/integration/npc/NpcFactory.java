package com.customrpg.integration.npc;
import fr.skytasul.quests.api.npcs.BqInternalNpc;
import fr.skytasul.quests.api.npcs.BqInternalNpcFactory;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import java.util.Collection;
public class NpcFactory implements BqInternalNpcFactory.BqInternalNpcFactoryCreatable {
    public static final String FACTORY_ID = "customrpg_npc";
    private final NpcManager npcManager;
    public NpcFactory(NpcManager npcManager) { this.npcManager = npcManager; }
    @Override public int getTimeToWaitForNPCs() { return 0; }
    @Override public boolean isNPC(Entity entity) { return npcManager.isNpcEntity(entity); }
    @Override public Collection<String> getIDs() { return npcManager.getAllInternalIds(); }
    @Override public BqInternalNpc fetchNPC(String id) { return npcManager.getNpcByInternalId(id); }
    @Override public boolean isValidEntityType(EntityType type) { return true; }
    @Override public BqInternalNpc create(Location location, EntityType entityType, String name, String id) {
        return npcManager.createNpc(location, entityType, name, id);
    }
}
