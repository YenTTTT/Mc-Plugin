package com.customrpg.worldevent.location;

import com.customrpg.CustomRPG;
import org.bukkit.Location;
import java.util.*;
import java.util.function.Predicate;

/** Claim checks are mandatory; automatic events additionally require terrain editor tracking. */
public final class ProtectionValidator implements AutoCloseable {
    private final CustomRPG plugin;
    private final Map<String, Predicate<Location>> adapters = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String,String> integrationFailures = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private AutoCloseable editorHook;
    private static final List<String> CLAIM_PLUGINS = List.of("WorldGuard","GriefPrevention","GriefDefender","Lands",
            "Towny","Residence","HuskClaims","HuskTowns","Factions","PlotSquared");
    private static final List<String> EDITOR_PLUGINS=List.of("WorldEdit","FastAsyncWorldEdit");
    public ProtectionValidator(CustomRPG plugin) { this.plugin=plugin; }
    public void enableIntegrations(NaturalTerrainRegistry provenance,Predicate<Location> canEdit) {
        var plugins=plugin.getServer().getPluginManager();
        if(plugins.isPluginEnabled("WorldGuard")) try {
            register("WorldGuard",new WorldGuardProtectionAdapter());
            plugin.getLogger().info("[WorldEvents] WorldGuard 7 region protection connected");
        } catch(RuntimeException|LinkageError error) {
            integrationFailures.put("WorldGuard",failure(error));
            plugin.getLogger().warning("[WorldEvents] WorldGuard integration unavailable: "+error);
        }
        if(plugins.isPluginEnabled("FastAsyncWorldEdit")) {
            String reason="FastAsyncWorldEdit (FAWE) is enabled; asynchronous terrain edit tracking is not supported";
            integrationFailures.put("WorldEdit",reason); integrationFailures.put("FastAsyncWorldEdit",reason);
            plugin.getLogger().warning("[WorldEvents] "+reason+"; automatic events remain blocked; admin test meteor here can run with claim checks");
        } else if(plugins.isPluginEnabled("WorldEdit")) try {
            var hook=new WorldEditTerrainHook(provenance,canEdit); hook.register(); editorHook=hook;
            register("WorldEdit",location->hook.active()&&provenance.healthy());
            plugin.getLogger().info("[WorldEvents] WorldEdit 7 terrain edit tracking connected");
        } catch(RuntimeException|LinkageError error) {
            integrationFailures.put("WorldEdit",failure(error));
            plugin.getLogger().warning("[WorldEvents] WorldEdit integration unavailable: "+error);
        }
    }
    private static String failure(Throwable error) {
        String message=error.getMessage();
        return error.getClass().getSimpleName()+(message==null?"":": "+message.replace('\n',' ').replace('\r',' '));
    }
    /** Predicate must return true only when terrain edits are allowed. An exception denies the candidate. */
    public void register(String pluginName, Predicate<Location> isSafe) {
        String canonical=canonicalName(pluginName);
        adapters.put(canonical,Objects.requireNonNull(isSafe)); integrationFailures.remove(canonical);
    }
    private static String canonicalName(String name) {
        for(String candidate:CLAIM_PLUGINS) if(candidate.equalsIgnoreCase(name)) return candidate;
        for(String candidate:EDITOR_PLUGINS) if(candidate.equalsIgnoreCase(name)) return candidate;
        return Objects.requireNonNull(name);
    }
    public String rejection(Location location) {
        return rejection(location,false);
    }
    public String rejectionForTesting(Location location) { return rejection(location,true); }
    private String rejection(Location location,boolean testMode) {
        if(!testMode&&plugin.getSafeZoneManager().isInSafeZone(location)) return "Spawn / safe zone";
        if(plugin.getProtectionAreaManager().isInProtectedArea2D(location)) return "CustomRPG protected region";
        if(plugin.getBossZoneManager().isInsideBossZone(location)) return "Boss region";
        for(String name:CLAIM_PLUGINS) if(plugin.getServer().getPluginManager().isPluginEnabled(name)) {
            if(!adapters.containsKey(name)) return missingAdapter(name);
        }
        if(!testMode) for(String name:EDITOR_PLUGINS) if(plugin.getServer().getPluginManager().isPluginEnabled(name)) {
            if(!adapters.containsKey(name)) return missingAdapter(name);
        }
        for(var entry:adapters.entrySet()) if(plugin.getServer().getPluginManager().isPluginEnabled(entry.getKey())) {
            String name=entry.getKey();
            // A fixed admin test already explicitly permits old/modified terrain. Editors are not claim providers.
            if(testMode&&EDITOR_PLUGINS.stream().anyMatch(editor->editor.equalsIgnoreCase(name))) continue;
            try { if(!entry.getValue().test(location)) return name + " denied terrain edits"; }
            catch(RuntimeException|LinkageError e) { return name + " protection lookup failed"; }
        }
        return null;
    }
    private String missingAdapter(String name) {
        String reason=integrationFailures.get(name);
        return reason==null?"No protection adapter for "+name:name+" integration unavailable: "+reason;
    }
    /** Exposed in event info so initialization errors are visible without finding startup log lines. */
    public String diagnostic() {
        List<String> statuses=new ArrayList<>();
        List<String> names=new ArrayList<>(CLAIM_PLUGINS); names.addAll(EDITOR_PLUGINS);
        var plugins=plugin.getServer().getPluginManager();
        for(String name:names) if(plugins.isPluginEnabled(name)) {
            String status=adapters.containsKey(name)?"adapter registered":missingAdapter(name);
            var installed=plugins.getPlugin(name);
            String version=installed==null?"":" "+installed.getDescription().getVersion();
            statuses.add(name+version+": "+status);
        }
        return statuses.isEmpty()?"":String.join("; ",statuses);
    }
    @Override public void close() {
        if(editorHook!=null) try { editorHook.close(); }
        catch(Exception|LinkageError error) { plugin.getLogger().warning("[WorldEvents] WorldEdit hook cleanup failed: "+error); }
        editorHook=null;
    }
}
