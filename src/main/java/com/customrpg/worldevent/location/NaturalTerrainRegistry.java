package com.customrpg.worldevent.location;

import com.customrpg.worldevent.persistence.WorldEventRepository;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Only chunks populated while the listener was running are known. Player edits revoke provenance permanently. */
public final class NaturalTerrainRegistry {
    private final Path file;
    private final Set<String> natural = new LinkedHashSet<>();
    private boolean healthy = true;
    public NaturalTerrainRegistry(Path file) throws Exception {
        this.file = file;
        if(Files.exists(file)) {
            YamlConfiguration yaml = new YamlConfiguration(); yaml.load(file.toFile());
            if(yaml.getInt("version")!=1) throw new IOException("Invalid natural terrain registry");
            for(String entry:yaml.getStringList("natural-chunks")) {
                String[] parts=entry.split(":",-1);
                if(parts.length!=3) throw new IOException("Invalid chunk provenance");
                UUID.fromString(parts[0]); Integer.parseInt(parts[1]); Integer.parseInt(parts[2]); natural.add(entry);
            }
        }
    }
    public boolean eligible(UUID world,int x,int z) { return healthy && natural.contains(key(world,x,z)); }
    public void populated(UUID world,int x,int z) throws IOException {
        if(!healthy) return;
        if(natural.add(key(world,x,z))) persist();
    }
    public void revoke(UUID world,int x,int z) throws IOException {
        if(natural.remove(key(world,x,z))) persist();
    }
    private static String key(UUID world,int x,int z) { return world+":"+x+":"+z; }
    private void persist() throws IOException {
        YamlConfiguration yaml=new YamlConfiguration(); yaml.set("version",1); yaml.set("natural-chunks",new ArrayList<>(natural));
        try { WorldEventRepository.writeDurably(file,yaml.saveToString()); }
        catch(IOException e) { healthy=false; throw e; }
    }
    public boolean healthy() { return healthy; }
}
