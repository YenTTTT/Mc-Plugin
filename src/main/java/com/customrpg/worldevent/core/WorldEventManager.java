package com.customrpg.worldevent.core;

import com.customrpg.CustomRPG;
import com.customrpg.managers.MobManager;
import com.customrpg.worldevent.location.*;
import com.customrpg.worldevent.meteor.MeteorImpactEvent;
import com.customrpg.worldevent.mob.EventMobSpawner;
import com.customrpg.worldevent.persistence.WorldEventRepository;
import com.customrpg.worldevent.reward.*;
import com.customrpg.worldevent.scheduler.WorldEventScheduler;
import com.customrpg.worldevent.terrain.*;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitTask;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Main-thread coordinator. Content modules never own schedulers or persistence protocols. */
public final class WorldEventManager {
    private record ChunkKey(UUID world,int x,int z) {}
    private static final class Running {
        TerrainSnapshot snapshot;
        TerrainBatch batch;
        WorldEventDefinition.Animation animation;
        final Set<ChunkKey> tickets=new LinkedHashSet<>();
        boolean loading, removedEntities;
        String blocked="";
    }
    private static final class Search {
        final String type;
        final Location exact;
        final Consumer<String> feedback;
        final boolean testMode;
        final Map<String,Integer> rejections=new LinkedHashMap<>();
        Search(String type,Location exact,Consumer<String> feedback) {
            this(type,exact,feedback,false);
        }
        Search(String type,Location exact,Consumer<String> feedback,boolean testMode) {
            this.type=type; this.exact=exact; this.feedback=feedback;
            this.testMode=testMode;
        }
    }
    private final CustomRPG plugin;
    private WorldEventRepository repository;
    private WorldEventSettings settings;
    private WorldEventScheduler scheduler;
    private NaturalTerrainRegistry provenance;
    private EventLocationService locations;
    private ProtectionValidator protection;
    private EventMobSpawner spawner;
    private final EventContributionService contribution=new EventContributionService();
    private EventRewardService rewards;
    private final Map<String,WorldEventDefinition> definitions=new LinkedHashMap<>();
    private final Map<UUID,Running> running=new LinkedHashMap<>();
    private final Map<ChunkKey,Integer> ticketReferences=new HashMap<>();
    private final Random random=new Random();
    private BukkitTask task;
    private Search search;
    private int searchAttempts,ticks;
    private long persistenceRetryAt;
    private boolean stopping, readOnly, persistenceBlocked;
    private String diagnostic="Starting";

    public WorldEventManager(CustomRPG plugin) {
        this.plugin=plugin;
        try {
            Path data=plugin.getDataFolder().toPath().resolve("data/world-events");
            repository=new WorldEventRepository(data); repository.load();
            File file=new File(plugin.getDataFolder(),"config/world_events.yml");
            if(!file.exists()) plugin.saveResource("config/world_events.yml",false);
            YamlConfiguration config=new YamlConfiguration(); config.load(file);
            var root=config.getConfigurationSection("world-events");
            settings=WorldEventSettings.load(root);
            if(root.getBoolean("meteor.enabled",true)) register(new MeteorImpactEvent(root.getConfigurationSection("meteor")));
            provenance=new NaturalTerrainRegistry(data.resolve("natural-chunks.yml"));
            protection=new ProtectionValidator(plugin);
            locations=new EventLocationService(protection,settings.padding());
            scheduler=new WorldEventScheduler(settings,random);
            spawner=new EventMobSpawner(plugin,repository); rewards=new EventRewardService(repository,contribution);
            recover();
            for(WorldEventInstance event:repository.events.values()) if(event.reserved()) protection.reserve(event,settings.padding());
            repository.nextTriggerAt=scheduler.afterRestart(System.currentTimeMillis(),repository.nextTriggerAt);
            repository.save();
            diagnostic="Ready; next event "+new Date(repository.nextTriggerAt);
        } catch(Exception e) {
            readOnly=true; diagnostic="World events disabled: "+e.getMessage();
            plugin.getLogger().severe(diagnostic+". Recovery files preserved; no new terrain edits.");
            if(repository!=null) try {
                for(TerrainSnapshot s:repository.loadAllSnapshots()) if(!repository.events.containsKey(s.eventId())) {
                    WorldEventInstance quarantine=new WorldEventInstance(s.eventId(),"recovery-only",s.world(),s.world().toString(),
                            s.centerX(),s.centerY(),s.centerZ(),s.radius(),1,1,1,0,0,1);
                    quarantine.state=WorldEventState.CLEANING; quarantine.diagnostic="Read-only quarantine; repair manifest before restoring";
                    repository.events.put(quarantine.id,quarantine);
                }
            } catch(Exception ignored) { plugin.getLogger().severe("[WorldEvents] Some snapshot bounds unavailable; manual recovery required"); }
        }
    }

    public void register(WorldEventDefinition definition) {
        if(definitions.putIfAbsent(definition.type(),definition)!=null) throw new IllegalArgumentException("Duplicate event type");
    }
    public void start() {
        if(task!=null) throw new IllegalStateException("World event scheduler already started");
        if(!readOnly) protection.enableIntegrations(provenance,location->!stopping&&at(location)==null);
        plugin.getServer().getPluginManager().registerEvents(new WorldEventListener(plugin,this),plugin);
        // Only once at startup; even a read-only recovery must remove our orphan actors.
        for(World world:plugin.getServer().getWorlds()) for(Entity entity:world.getEntities()) onEntityLoad(entity);
        if(!readOnly) {
            task=plugin.getServer().getScheduler().runTaskTimer(plugin,this::tick,1L,1L);
        }
    }
    private void recover() throws Exception {
        for(WorldEventInstance e:repository.events.values()) if(e.reserved()) {
            Running runtime=new Running(); runtime.snapshot=repository.loadSnapshot(e.id);
            verifySnapshot(e,runtime.snapshot);
            if(e.state!=WorldEventState.CLEANING) {
                if(e.state.ordinal()<WorldEventState.COMPLETED.ordinal()) e.transition(WorldEventState.FAILED);
                e.transition(WorldEventState.CLEANING);
            }
            e.diagnostic="Restart recovery: restore terrain; never respawn event mobs";
            runtime.batch=new TerrainBatch(runtime.snapshot,true); running.put(e.id,runtime);
        }
    }
    private static void verifySnapshot(WorldEventInstance e,TerrainSnapshot s) {
        if(!e.world.equals(s.world())||e.x!=s.centerX()||e.y!=s.centerY()||e.z!=s.centerZ()||e.radius!=s.radius()) throw new IllegalStateException("Snapshot does not match event "+e.id);
    }

    private void tick() {
        if(stopping||readOnly) return;
        try {
            if(persistenceBlocked) {
                if(System.currentTimeMillis()<persistenceRetryAt) return;
                repository.save(); persistenceBlocked=false;
            }
            ticks++;
            for(var entry:new ArrayList<>(running.entrySet())) {
                WorldEventInstance e=repository.events.get(entry.getKey());
                Running runtime=entry.getValue();
                if(!runtime.blocked.isBlank()) continue;
                try { tickEvent(e,runtime); }
                catch(Exception error) {
                    if(e.state==WorldEventState.CLEANING) {
                        runtime.blocked=error.getMessage(); e.diagnostic="Recovery blocked: "+runtime.blocked;
                        plugin.getLogger().severe("[WorldEvent "+e.id+"] "+e.diagnostic+"; snapshot retained"); persist();
                    } else fail(e,"Event failure: "+error.getMessage());
                }
                if(persistenceBlocked) return;
            }
            if(ticks%100==0&&!running.isEmpty()) persist();
            if(search!=null) searchStep();
            if(ticks%20==0&&search==null&&settings.enabled()&&definitions.size()>0&&System.currentTimeMillis()>=repository.nextTriggerAt) {
                repository.nextTriggerAt=scheduler.retry(System.currentTimeMillis()); persist();
                if(!persistenceBlocked&&capacity()&&players().size()>=settings.minimumPlayers()) {
                    search=new Search(definitions.keySet().iterator().next(),null,message->{}); searchAttempts=0;
                }
            }
        } catch(Exception e) { persistenceBlocked=true; persistenceRetryAt=System.currentTimeMillis()+5000; diagnostic="Persistence paused: "+e.getMessage(); plugin.getLogger().severe(diagnostic); }
    }
    private void tickEvent(WorldEventInstance e,Running runtime) throws Exception {
        World world=plugin.getServer().getWorld(e.world);
        if(world==null) { e.diagnostic="Waiting for world "+e.worldName; return; }
        if(!ensureChunks(e,runtime,world)) return;
        if(e.state!=WorldEventState.CLEANING && e.state!=WorldEventState.COMPLETED && System.currentTimeMillis()>=e.expiresAt) {
            e.transition(WorldEventState.EXPIRED); persist(); beginCleanup(e,runtime); return;
        }
        WorldEventDefinition definition=definitions.get(e.type);
        switch(e.state) {
            case ANNOUNCED -> {
                if(definition==null) { fail(e,"Event definition unavailable"); return; }
                e.transition(WorldEventState.FALLING); persist();
                if(!persistenceBlocked) runtime.animation=definition.animation(plugin,e,world,entity->EventMobSpawner.tag(entity,e));
            }
            case FALLING -> {
                if(runtime.animation==null) { fail(e,"Animation interrupted"); return; }
                if(runtime.animation.tick()) {
                    runtime.animation.close();
                    runtime.animation=null;
                    Location center=new Location(world,e.x,e.y,e.z);
                    var others=repository.events.values().stream().filter(other->!other.id.equals(e.id)).toList();
                    var site=e.generatedArena?locations.validateArena(center,definition.requirements(),others,e.testMode,definition.arenaClearance())
                            :e.testMode?locations.validateForTesting(center,definition.requirements(),others):locations.validate(center,definition.requirements(),others);
                    if(site==null||e.generatedArena&&site.center().getBlockY()!=e.y) {
                        fail(e,"Impact revalidation failed: "+(site==null?locations.lastRejection:"Arena airspace changed")); return;
                    }
                    e.transition(WorldEventState.IMPACTING); persist();
                    runtime.batch=new TerrainBatch(runtime.snapshot,false);
                }
            }
            case IMPACTING -> {
                if(runtime.batch!=null&&!runtime.batch.done()) {
                    if(!evacuate(e,world)||persistenceBlocked) return;
                    applyBatch(e,runtime,world,false);
                } else {
                    if(e.mobs.isEmpty()) spawner.plan(e,definition);
                    if(!spawner.spawnNext(e,world)) {
                        e.transition(WorldEventState.ACTIVE); persist();
                        if(!persistenceBlocked) broadcast(e,"隕石撞擊完成！討伐所有守衛以解鎖核心。"+(e.generatedArena?" 使用 /events join 進入隕石島。":""));
                    }
                }
            }
            case ACTIVE -> { if(ticks%20==0) keepMobsInArea(e,world); }
            case COMPLETED -> {
                if(ticks%20==0) coreParticles(e,world);
                if(System.currentTimeMillis()>=e.cleanupAt) beginCleanup(e,runtime);
            }
            case FAILED, EXPIRED -> beginCleanup(e,runtime);
            case CLEANING -> {
                if(!runtime.removedEntities) { removeEntities(e,world); runtime.removedEntities=true; }
                if(persistenceBlocked) return;
                if(!evacuate(e,world)||persistenceBlocked) return;
                if(runtime.batch==null) runtime.batch=new TerrainBatch(runtime.snapshot,true);
                applyBatch(e,runtime,world,true);
                if(runtime.batch.done()) {
                    // Save terrain before publishing CLOSED; the immutable snapshot remains available for diagnostics.
                    world.save();
                    e.transition(WorldEventState.CLOSED); persist();
                    if(!persistenceBlocked) release(e.id,runtime);
                }
            }
            case CLOSED -> release(e.id,runtime);
            default -> fail(e,"Incomplete startup state");
        }
    }
    private void applyBatch(WorldEventInstance e,Running runtime,World world,boolean restore) {
        runtime.batch.step(settings.blocksPerTick(),(position,data)->{
            var change=runtime.snapshot.blocks().get(runtime.batch.cursor());
            var block=world.getBlockAt(position.x(),position.y(),position.z());
            String current=block.getBlockData().getAsString();
            BlockData target=Bukkit.createBlockData(data);
            String before=Bukkit.createBlockData(change.before()).getAsString(),after=Bukkit.createBlockData(change.after()).getAsString();
            if(restore) {
                if(current.equals(before)) return;
                if(!current.equals(after)) throw new IllegalStateException("Terrain conflict at "+position.key()+"; refused to overwrite unknown changes");
            } else if(!current.equals(before)) throw new IllegalStateException("Terrain changed after snapshot at "+position.key());
            block.setBlockData(target,false);
        });
    }
    private boolean ensureChunks(WorldEventInstance e,Running runtime,World world) {
        Set<ChunkKey> required=new LinkedHashSet<>(); int guard=e.radius+settings.padding();
        for(int x=(e.x-guard)>>4;x<=(e.x+guard)>>4;x++) for(int z=(e.z-guard)>>4;z<=(e.z+guard)>>4;z++) required.add(new ChunkKey(e.world,x,z));
        if(e.state==WorldEventState.CLEANING) for(var mob:e.mobs.values()) if(mob.entity!=null) required.add(new ChunkKey(e.world,mob.chunkX,mob.chunkZ));
        for(ChunkKey key:required) {
            if(runtime.tickets.contains(key)) continue;
            if(!world.isChunkLoaded(key.x,key.z)) {
                if(!runtime.loading) {
                    runtime.loading=true;
                    world.getChunkAtAsync(key.x,key.z,false,chunk->{
                        runtime.loading=false;
                        if(stopping||!running.containsKey(e.id)) return;
                        if(chunk==null) { runtime.blocked="Recovery chunk missing: "+key.x+","+key.z; return; }
                        pin(runtime,key,world);
                    });
                }
                return false;
            }
            pin(runtime,key,world);
        }
        return true;
    }
    private void pin(Running runtime,ChunkKey key,World world) {
        if(!runtime.tickets.add(key)) return;
        if(ticketReferences.merge(key,1,Integer::sum)==1) world.addPluginChunkTicket(key.x,key.z,plugin);
    }
    private void release(UUID id,Running runtime) {
        if(runtime.animation!=null) runtime.animation.close();
        for(ChunkKey key:runtime.tickets) {
            Integer count=ticketReferences.computeIfPresent(key,(k,n)->n==1?null:n-1);
            if(count==null) { World world=plugin.getServer().getWorld(key.world); if(world!=null) world.removePluginChunkTicket(key.x,key.z,plugin); }
        }
        running.remove(id);
        WorldEventInstance event=find(id);
        if(event!=null&&!event.reserved()&&protection!=null) protection.release(id);
    }
    private boolean evacuate(WorldEventInstance e,World world) {
        Location edge=null;
        for(Player player:world.getPlayers()) {
            Location current=player.getLocation();
            boolean participant=e.generatedArena&&e.arenaReturns.containsKey(player.getUniqueId())
                    &&e.contains(e.world,current.getBlockX(),current.getBlockZ(),settings.padding());
            if((!occupies(e,current,1)&&!participant)||player.isDead()) continue;
            if(participant) {
                if(!returnFromArena(e,player)) { e.diagnostic="Waiting for participant return"; return false; }
                continue;
            }
            if(edge==null) {
                edge=safeEdge(e,world);
                if(edge==null) {
                    // Last resort: do not strand a player inside a pending restore indefinitely.
                    Location spawn=world.getSpawnLocation();
                    if(spawn!=null&&spawn.getWorld()==world
                            &&!e.contains(e.world,spawn.getBlockX(),spawn.getBlockZ(),settings.padding())
                            &&world.isChunkLoaded(spawn.getBlockX()>>4,spawn.getBlockZ()>>4)
                            &&world.getWorldBorder().isInside(spawn)) edge=spawn;
                }
            }
            if(edge==null||!player.teleport(edge)) { e.diagnostic="Waiting for safe player evacuation"; return false; }
            player.sendMessage("§e[世界事件] 地形施工／還原中，已移至場地邊緣。");
        }
        return true;
    }
    public Location safeEdge(WorldEventInstance e,World world) {
        for(int offset:new int[]{2,6,12,20}) for(int i=0;i<32;i++) {
            double angle=i*Math.PI/16;
            int radius=e.radius+settings.padding()+offset;
            int x=e.x+(int)Math.round(Math.cos(angle)*radius),z=e.z+(int)Math.round(Math.sin(angle)*radius);
            if(e.contains(e.world,x,z,settings.padding())||!world.isChunkLoaded(x>>4,z>>4)) continue;
            Location candidate=new Location(world,x+0.5,world.getHighestBlockYAt(x,z)+1,z+0.5);
            Location safe=e.testMode?plugin.getMobManager().findSafeSpawnLocationExactForTesting(candidate):plugin.getMobManager().findSafeSpawnLocationExact(candidate);
            if(safe!=null) return safe;
        }
        return null;
    }
    private void keepMobsInArea(WorldEventInstance e,World world) {
        int index=0;
        for(var mob:e.mobs.values()) {
            Entity raw=mob.entity==null?null:plugin.getServer().getEntity(mob.entity);
            if(raw instanceof LivingEntity living&&!living.isDead()) {
                mob.chunkX=living.getLocation().getBlockX()>>4; mob.chunkZ=living.getLocation().getBlockZ()>>4;
                if(!occupies(e,living.getLocation(),settings.padding()-2)||e.generatedArena&&living.getLocation().getY()<e.y-0.5) {
                    Location home=spawner.findSpawn(e,world,index);
                    if(home==null||!living.teleport(home)) { fail(e,"Event mob could not return to arena"); return; }
                }
            }
            index++;
        }
    }
    private void removeEntities(WorldEventInstance e,World world) {
        for(var mob:e.mobs.values()) {
            if(mob.entity!=null) { Entity entity=plugin.getServer().getEntity(mob.entity); if(entity!=null) entity.remove(); }
            if(mob.state!=WorldEventInstance.MobState.DEAD) mob.state=WorldEventInstance.MobState.REMOVED_BY_CLEANUP;
        }
        Set<UUID> checked=new HashSet<>();
        for(ChunkKey key:running.get(e.id).tickets) {
            if(!world.isChunkLoaded(key.x,key.z)) continue;
            for(Entity entity:world.getChunkAt(key.x,key.z).getEntities()) {
                if(checked.add(entity.getUniqueId())&&e.id.equals(EventMobSpawner.eventId(entity))) entity.remove();
            }
        }
        persist();
    }
    private void beginCleanup(WorldEventInstance e,Running runtime) {
        if(runtime.animation!=null) { runtime.animation.close(); runtime.animation=null; }
        if(e.state!=WorldEventState.CLEANING) e.transition(WorldEventState.CLEANING);
        runtime.batch=new TerrainBatch(runtime.snapshot,true); runtime.blocked=""; runtime.removedEntities=false; persist();
    }
    public void fail(WorldEventInstance e,String reason) {
        if(!e.reserved()||e.state==WorldEventState.CLEANING) return;
        Running runtime=running.get(e.id);
        if(runtime!=null&&runtime.animation!=null) { runtime.animation.close(); runtime.animation=null; }
        if(e.state.ordinal()<WorldEventState.COMPLETED.ordinal()) e.transition(WorldEventState.FAILED);
        e.diagnostic=reason; plugin.getLogger().warning("[WorldEvent "+e.id+"] "+reason); persist();
    }
    private void persist() {
        try { repository.save(); }
        catch(IOException e) { persistenceBlocked=true; persistenceRetryAt=System.currentTimeMillis()+5000; diagnostic="Persistence paused: "+e.getMessage(); plugin.getLogger().severe(diagnostic); }
    }
    private List<Player> players() {
        return new ArrayList<>(plugin.getServer().getOnlinePlayers().stream().filter(p->p.getWorld().getEnvironment()==World.Environment.NORMAL
                &&(p.getGameMode()==GameMode.SURVIVAL||p.getGameMode()==GameMode.ADVENTURE)).toList());
    }
    private boolean capacity() { return running.size()<settings.maxActive(); }
    public String requestStart(String type,Location exact) {
        return requestStart(type,exact,message->{});
    }
    public String requestStart(String type,Location exact,Consumer<String> feedback) {
        return requestStart(type,exact,feedback,false);
    }
    public String requestTestStart(String type,Location exact,Consumer<String> feedback) {
        if(exact==null||exact.getWorld()==null) return "§c測試模式需要玩家目前的位置。";
        return requestStart(type,exact,feedback,true);
    }
    private String requestStart(String type,Location exact,Consumer<String> feedback,boolean testMode) {
        if(readOnly||stopping||persistenceBlocked) return "§c"+diagnostic;
        if(!settings.enabled()) return "§cWorld events disabled in config";
        if(!definitions.containsKey(type)) return "§cUnknown/disabled event: "+type;
        if(search!=null||!capacity()) return "§cEvent/search/recovery capacity is full";
        if(exact==null&&players().size()<settings.minimumPlayers()) return "§cNot enough eligible online players";
        search=new Search(type,exact==null?null:exact.clone(),Objects.requireNonNull(feedback),testMode); searchAttempts=0;
        if(testMode) return "§e已開始目前位置的事件測試；額外略過本插件安全區。\n§e隕石將生成於原地形上方的臨時島嶼，完成後可用 /events join 進入。\n§e測試期間請勿使用 WorldEdit／FAWE 編輯場地；領地與存檔保護仍保留。";
        return "§a已開始安全選址；失敗會取消並延後，不會強制修改地形。";
    }
    private void searchStep() throws Exception {
        if(!capacity()) { endSearch("Event/search/recovery capacity is full"); return; }
        WorldEventDefinition definition=definitions.get(search.type);
        Location candidate=search.exact;
        if(candidate==null) {
            List<Player> players=players();
            if(players.size()<settings.minimumPlayers()) { endSearch("Not enough online players"); return; }
            Location origin=players.get(random.nextInt(players.size())).getLocation();
            double angle=random.nextDouble()*Math.PI*2,distance=random.nextInt(settings.minDistance(),settings.maxDistance()+1);
            candidate=origin.clone().add(Math.cos(angle)*distance,0,Math.sin(angle)*distance);
        }
        searchAttempts++;
        EventLocationService.Site site=definition.generatedArena()?locations.validateArena(candidate,definition.requirements(),repository.events.values(),search.testMode,definition.arenaClearance())
                :search.testMode?locations.validateForTesting(candidate,definition.requirements(),repository.events.values())
                :locations.validate(candidate,definition.requirements(),repository.events.values());
        if(site==null) {
            rejectCandidate(locations.lastRejection);
            return;
        }
        Location center=site.center(); var zone=plugin.getWorldZoneManager().getZone(center);
        int min=Math.max(zone.minLevel(),plugin.getWorldZoneManager().getDimensionMinLevel(center.getWorld()));
        long now=System.currentTimeMillis(),seed=random.nextLong();
        WorldEventInstance e=new WorldEventInstance(UUID.randomUUID(),definition.type(),center.getWorld().getUID(),center.getWorld().getName(),
                center.getBlockX(),center.getBlockY(),center.getBlockZ(),definition.requirements().radius(),min,Math.max(min,zone.maxLevel()),
                plugin.getWorldZoneManager().rollLevel(center),seed,now,now+definition.durationMillis());
        e.rewards.putAll(definition.rewards()); e.minimumDamage=definition.minimumDamage(); e.minimumShare=definition.minimumShare();
        e.testMode=search.testMode;
        e.generatedArena=definition.generatedArena(); e.arenaDepth=definition.requirements().depth();
        TerrainSnapshot snapshot;
        try { snapshot=locations.snapshot(e.id,definition,site,seed); }
        catch(IllegalArgumentException unsafe) {
            rejectCandidate(unsafe.getMessage());
            return;
        }
        // Both immutable snapshot and initial event record must be durable before announcement, spawning, or block writes.
        repository.saveSnapshot(snapshot);
        Running runtime=new Running(); runtime.snapshot=snapshot; running.put(e.id,runtime);
        repository.events.put(e.id,e); repository.nextTriggerAt=scheduler.next(now); search=null;
        protection.reserve(e,settings.padding());
        repository.save();
        e.transition(WorldEventState.ANNOUNCED); repository.save(); search=null;
        diagnostic=(e.testMode?"Admin test event":"Event")+" announced: "+e.id;
        broadcast(e,"一顆神秘隕石即將墜落！");
    }
    private void rejectCandidate(String reason) {
        diagnostic=reason;
        search.rejections.merge(reason,1,Integer::sum);
        if(search.exact!=null||searchAttempts>=settings.attempts()) endSearch(reason);
    }
    private void endSearch(String reason) {
        Search ended=search;
        String counts=ended.rejections.entrySet().stream()
                .map(entry->entry.getKey()+" x"+entry.getValue()).collect(Collectors.joining("; "));
        search=null; diagnostic="Aborted / rescheduled: "+reason+"; attempts="+searchAttempts+(counts.isEmpty()?"":"; rejections: "+counts);
        repository.nextTriggerAt=scheduler.retry(System.currentTimeMillis()); persist();
        plugin.getLogger().info("[WorldEvents] "+diagnostic);
        String details=ended.rejections.isEmpty()?searchRejectionMessage(reason):ended.rejections.entrySet().stream()
                .sorted(Map.Entry.<String,Integer>comparingByValue().reversed())
                .map(entry->searchRejectionMessage(entry.getKey())+" ×"+entry.getValue()).collect(Collectors.joining("\n§7"));
        String terminal=ended.rejections.isEmpty()||ended.rejections.containsKey(reason)?"":searchRejectionMessage(reason)+"\n§7";
        ended.feedback.accept("§c世界事件選址已取消；已嘗試 "+searchAttempts+" 個位置。\n§7"+terminal+details
                +"\n§e自動排程已延後 "+settings.retrySeconds()+" 秒；可用 /mmorpg event info 查看診斷。");
    }
    private static String searchRejectionMessage(String reason) {
        return switch(reason) {
            case "Candidate touches unloaded chunk" -> "場地涵蓋未載入區塊；請靠近已載入區域，或檢查搜尋距離與伺服器視距";
            case "Overworld in non-peaceful difficulty required" -> "需要主世界且難度不能為和平";
            case "Build height unsuitable" -> "超出可施工高度";
            case "Invalid snapshot size" -> "場地需要修改的方塊過多，已改試其他位置";
            case "Not enough online players" -> "符合資格的主世界生存／冒險玩家人數不足";
            case "Event/search/recovery capacity is full" -> "其他事件或恢復作業已占滿事件名額";
            default -> reason;
        };
    }
    private void broadcast(WorldEventInstance e,String message) {
        plugin.getServer().broadcastMessage("§6[World Event] "+(e.testMode?"§e[管理員測試] ":"")+"§f"+message+"\n§e"+e.type+" §7| "+e.worldName+" X: "+e.x+" Y: "+e.y+" Z: "+e.z
                +"\n§b推薦等級 Lv."+e.minLevel+"～"+e.maxLevel+" §7| "+(e.generatedArena&&e.state==WorldEventState.ACTIVE?"/events join":"/events info"));
    }
    private void coreParticles(WorldEventInstance e,World world) {
        Location center=new Location(world,e.x+0.5,e.y+1,e.z+0.5);
        for(Player player:world.getNearbyPlayers(center,64)) player.spawnParticle(Particle.END_ROD,center,8,1,1,1,0.01);
    }

    public WorldEventInstance.EventMob mob(WorldEventInstance e,Entity entity) {
        UUID id=EventMobSpawner.mobId(entity);
        var mob=id==null?null:e.mobs.get(id);
        return mob!=null && entity.getUniqueId().equals(mob.entity)?mob:null;
    }
    public void damage(Entity entity,UUID player,double amount) {
        if(readOnly||stopping) return;
        WorldEventInstance e=find(EventMobSpawner.eventId(entity)); if(e==null) return;
        var mob=mob(e,entity); if(mob!=null) contribution.damage(e,mob,player,amount);
    }
    public void death(LivingEntity entity) {
        if(readOnly||stopping) return;
        WorldEventInstance e=find(EventMobSpawner.eventId(entity)); if(e==null||e.state!=WorldEventState.ACTIVE) return;
        var mob=mob(e,entity);
        if(mob==null||mob.state==WorldEventInstance.MobState.DEAD) return;
        if(mob.damage.isEmpty()) { fail(e,"Required mob died without credited player combat"); return; }
        if(!contribution.defeat(e,mob)) return;
        if(e.allDefeated()) {
            e.transition(WorldEventState.COMPLETED); e.cleanupAt=System.currentTimeMillis()+definitions.get(e.type).cleanupDelayMillis();
            persist(); if(!persistenceBlocked) broadcast(e,"討伐完成！右鍵隕石核心領取個人素材，或使用 /events claim "+e.id);
        } else persist();
    }
    public void onEntityLoad(Entity entity) {
        UUID id=EventMobSpawner.eventId(entity); if(id==null) return;
        if(readOnly) { entity.remove(); return; }
        WorldEventInstance e=find(id);
        if(e==null||e.state!=WorldEventState.ACTIVE&&e.state!=WorldEventState.IMPACTING) { entity.remove(); return; }
        var mob=mob(e,entity);
        if(mob==null||mob.state==WorldEventInstance.MobState.DEAD||mob.state==WorldEventInstance.MobState.REMOVED_BY_CLEANUP) entity.remove();
        else if(mob.state==WorldEventInstance.MobState.UNLOADED) mob.state=WorldEventInstance.MobState.ALIVE;
    }
    public void onEntityUnload(Entity entity) {
        if(readOnly||stopping) return;
        WorldEventInstance e=find(EventMobSpawner.eventId(entity)); if(e==null) return;
        var mob=mob(e,entity);
        if(mob!=null&&mob.state==WorldEventInstance.MobState.ALIVE) {
            mob.state=WorldEventInstance.MobState.UNLOADED; mob.chunkX=entity.getLocation().getBlockX()>>4; mob.chunkZ=entity.getLocation().getBlockZ()>>4; persist();
        }
    }
    public WorldEventInstance at(Location location) {
        if(location==null||location.getWorld()==null||repository==null) return null;
        return repository.events.values().stream().filter(e->e.reserved()&&occupies(e,location,settings==null?16:settings.padding())).findFirst().orElse(null);
    }
    public static boolean occupies(WorldEventInstance e,Location location,int padding) {
        return location!=null&&location.getWorld()!=null&&e.occupies(location.getWorld().getUID(),location.getBlockX(),location.getY(),location.getBlockZ(),padding);
    }
    public String joinArena(Player player,UUID eventId) {
        if(readOnly||stopping||persistenceBlocked) return "§c事件目前無法進入，請稍後重試。";
        if(player.isDead()||player.isInsideVehicle()) return "§c請先離開載具並保持存活再進入。";
        if(events().stream().anyMatch(e->e.arenaReturns.containsKey(player.getUniqueId()))) return "§e你已有事件返回紀錄，請先使用 /events leave。";
        WorldEventInstance e=events().stream().filter(event->event.generatedArena&&(event.state==WorldEventState.ACTIVE||event.state==WorldEventState.COMPLETED)
                &&(eventId==null||event.id.equals(eventId))).findFirst().orElse(null);
        if(e==null) return "§e目前沒有可進入的隕石島；請等待場地與怪物生成完成。";
        World world=plugin.getServer().getWorld(e.world);
        if(world==null) return "§c事件世界尚未載入。";
        Location landing=spawner.findSpawn(e,world,1000);
        if(landing==null) return "§c暫時找不到可用的入場點，請稍後重試。";
        Location origin=player.getLocation();
        if(occupies(e,origin,settings.padding())) return "§e你已在隕石島上。";
        e.arenaReturns.put(player.getUniqueId(),new WorldEventInstance.ReturnPoint(origin.getWorld().getUID(),origin.getX(),origin.getY(),origin.getZ(),origin.getYaw(),origin.getPitch()));
        try { repository.save(); }
        catch(IOException error) { e.arenaReturns.remove(player.getUniqueId()); return "§c返回位置儲存失敗，未傳送。"; }
        if(!player.teleport(landing)) {
            e.arenaReturns.remove(player.getUniqueId()); persist(); return "§c傳送遭到取消，未進入事件。";
        }
        player.setFallDistance(0);
        return "§a已進入隕石島；使用 /events leave 可返回原位置。";
    }
    public String leaveArena(Player player) {
        WorldEventInstance e=events().stream().filter(event->event.arenaReturns.containsKey(player.getUniqueId())).findFirst().orElse(null);
        if(e==null) return "§e沒有待返回的事件位置。";
        return returnFromArena(e,player)?"§a已離開隕石島。":"§c暫時無法返回，返回紀錄已保留，請稍後再試。";
    }
    private Location returnLocation(WorldEventInstance e,Player player) {
        var saved=e.arenaReturns.get(player.getUniqueId());
        if(saved!=null) {
            World world=plugin.getServer().getWorld(saved.world());
            if(world!=null) {
                Location target=new Location(world,saved.x(),saved.y(),saved.z(),saved.yaw(),saved.pitch());
                if(target.getY()>world.getMinHeight()&&target.getY()+2<world.getMaxHeight()&&world.getWorldBorder().isInside(target)
                        &&!occupies(e,target,settings.padding())&&target.getBlock().isPassable()
                        &&target.clone().add(0,1,0).getBlock().isPassable()
                        &&target.clone().add(0,-1,0).getBlock().isSolid()) return target;
            }
        }
        World world=plugin.getServer().getWorld(e.world);
        if(world==null) return null;
        Location edge=safeEdge(e,world);
        if(edge!=null) return edge;
        Location spawn=world.getSpawnLocation();
        return spawn!=null&&!occupies(e,spawn,settings.padding())&&world.getWorldBorder().isInside(spawn)?spawn:null;
    }
    private boolean returnFromArena(WorldEventInstance e,Player player) {
        if(player.isDead()) return false;
        Location target=returnLocation(e,player);
        if(target==null||!player.teleport(target)) return false;
        player.setFallDistance(0);
        e.arenaReturns.remove(player.getUniqueId()); persist();
        return true;
    }
    public void recoverArenaPlayer(Player player,boolean respawn) {
        if(settings==null||repository==null||player.isDead()) return;
        for(WorldEventInstance e:events()) if(e.arenaReturns.containsKey(player.getUniqueId())) {
            Location current=player.getLocation();
            boolean inside=occupies(e,current,settings.padding());
            boolean column=e.contains(current.getWorld().getUID(),current.getBlockX(),current.getBlockZ(),settings.padding());
            boolean open=e.state==WorldEventState.ACTIVE||e.state==WorldEventState.COMPLETED;
            if(respawn||column&&(!open||!inside)) {
                if(returnFromArena(e,player)) player.sendMessage("§e[世界事件] 已返回事件前的位置。");
            } else if(!inside) { e.arenaReturns.remove(player.getUniqueId()); persist(); }
        }
    }
    public WorldEventInstance find(UUID id) { return id==null||repository==null?null:repository.events.get(id); }
    public Collection<WorldEventInstance> events() { return repository==null?List.of():Collections.unmodifiableCollection(repository.events.values()); }
    public String claim(UUID id,UUID player) {
        if(readOnly||persistenceBlocked||rewards==null) return "§cPersistence unavailable; claim not changed";
        WorldEventInstance e=find(id); if(e==null) return "§cUnknown event ID";
        return switch(rewards.claim(e,player)) {
            case GRANTED -> "§a素材已存入個人錢包："+e.rewards+" §7（/events wallet）";
            case ALREADY_CLAIMED -> "§e本事件獎勵已領取。";
            case NOT_COMPLETED -> "§c事件尚未成功完成，不能領獎。";
            case INELIGIBLE -> "§c有效戰鬥貢獻不足，無法領取獎勵。";
            case SAVE_FAILED -> "§c儲存失敗，沒有入帳或消耗領獎資格，請稍後重試。";
        };
    }
    public Map<String,Long> wallet(UUID player) { return rewards==null?Map.of():rewards.balance(player); }
    public String stop(UUID id) {
        if(readOnly||persistenceBlocked) return "Persistence unavailable: "+diagnostic;
        WorldEventInstance e=find(id); if(e==null) return "Unknown event";
        if(!e.reserved()) return "Event already closed";
        fail(e,"Cancelled by administrator"); Running runtime=running.get(id);
        if(runtime!=null) beginCleanup(e,runtime); return "Cleanup requested: "+id;
    }
    public String diagnostic() {
        String integrations=protection==null?"":protection.diagnostic();
        return diagnostic+(search==null?"":"; searching "+searchAttempts+"/"+settings.attempts())
                +(integrations.isBlank()?"":"\nIntegrations: "+integrations);
    }
    public String debug(WorldEventInstance e) {
        Running runtime=running.get(e.id);
        return e.id+" "+e.state+"; test-mode="+e.testMode+"; generated-arena="+e.generatedArena+"; mobs="+e.mobs.values().stream().collect(java.util.stream.Collectors.groupingBy(m->m.state,java.util.stream.Collectors.counting()))
                +"; contributors="+e.contribution.size()+"; claimed="+e.claimed.size()+"; snapshot="+e.id+".yml; cursor="+(runtime==null||runtime.batch==null?0:runtime.batch.cursor())
                +"; blocked="+(runtime==null?"":runtime.blocked)+"; "+e.diagnostic;
    }
    public NaturalTerrainRegistry provenance() { return provenance; }
    public ProtectionValidator protection() { return protection; }
    public void shutdown() {
        stopping=true; search=null; if(task!=null) { task.cancel(); task=null; }
        if(protection!=null) protection.close();
        if(readOnly||repository==null) return;
        for(var entry:new ArrayList<>(running.entrySet())) {
            WorldEventInstance e=find(entry.getKey()); Running runtime=entry.getValue();
            if(runtime.animation!=null) runtime.animation.close();
            if(e.state!=WorldEventState.CLEANING&&e.state!=WorldEventState.CLOSED) {
                if(e.state.ordinal()<WorldEventState.COMPLETED.ordinal()) e.transition(WorldEventState.FAILED);
                e.transition(WorldEventState.CLEANING);
            }
        }
        persist(); // Never do an unbounded synchronous terrain restore during disable. Next startup resumes CLEANING.
        for(var entry:new ArrayList<>(running.entrySet())) {
            WorldEventInstance e=find(entry.getKey()); World world=plugin.getServer().getWorld(e.world);
            if(world!=null) { if(e.generatedArena) evacuate(e,world); removeEntities(e,world); }
        }
        for(var entry:new ArrayList<>(running.entrySet())) release(entry.getKey(),entry.getValue());
    }
}
