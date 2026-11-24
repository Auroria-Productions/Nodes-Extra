package com.auroria.nodesmilitary;

import com.google.gson.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import phonon.nodes.objects.Nation;
import phonon.nodes.objects.Territory;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Optional read-only Nodes-Extra integration. Never writes the logistics or government state. */
final class ExtraBridge {
    record Depot(String nation, int territory, int food,int equipment,int fuel,long blockadedUntil){}
    record Link(String nation,int from,int to,String type){}
    record Snapshot(boolean available,String issue,JsonObject governments,List<Depot> depots,List<Link> links){}
    private final MilitaryPlugin plugin;
    private long lastRead = -1;
    private Snapshot cached = new Snapshot(false,"Nodes-Extra not installed",new JsonObject(),List.of(),List.of());
    ExtraBridge(MilitaryPlugin plugin){this.plugin=plugin;}
    synchronized Snapshot snapshot(boolean force){
        long now=System.currentTimeMillis();
        if(!force && lastRead>=0 && now-lastRead<5000)return cached;
        lastRead=now;
        if(!Bukkit.getPluginManager().isPluginEnabled("Nodes-Extra")) {
            return cached=new Snapshot(false,"Nodes-Extra not installed",new JsonObject(),List.of(),List.of());
        }
        Path file=Bukkit.getPluginManager().getPlugin("Nodes-Extra").getDataFolder().toPath().resolve("state.json");
        try {
            if(!Files.isRegularFile(file))return cached=new Snapshot(false,"Run /nextra save to initialise logistics data",new JsonObject(),List.of(),List.of());
            if(Files.size(file)>15_000_000)throw new IllegalStateException("Nodes-Extra file too large");
            JsonObject doc=JsonParser.parseString(Files.readString(file,StandardCharsets.UTF_8)).getAsJsonObject();
            if(getInt(doc,"schema",-1)!=1)throw new IllegalArgumentException("Unsupported Nodes-Extra state schema");
            JsonObject governments=object(doc,"governments");
            List<Depot> depots=new ArrayList<>();
            for(JsonElement e:object(doc,"depots").asMap().values()) {
                if(!e.isJsonObject())continue;JsonObject d=e.getAsJsonObject();
                depots.add(new Depot(getStr(d,"nationId",""),getInt(d,"territoryId",-1),getInt(d,"food",0),getInt(d,"equipment",0),getInt(d,"fuel",0),getLong(d,"blockadedUntil",0)));
            }
            List<Link> links=new ArrayList<>();
            for(JsonElement e:object(doc,"links").asMap().values()) {
                if(!e.isJsonObject())continue;JsonObject d=e.getAsJsonObject();
                links.add(new Link(getStr(d,"nationId",""),getInt(d,"from",-1),getInt(d,"to",-1),getStr(d,"type","")));
            }
            return cached=new Snapshot(true,"",governments,depots,links);
        }catch(Exception ex){
            plugin.getLogger().warning("Nodes-Extra data not usable (read-only bridge): " + ex.getMessage());
            return cached=new Snapshot(false,"Nodes-Extra saved data unavailable",new JsonObject(),List.of(),List.of());
        }
    }
    boolean marshalOrHead(Player player,Nation nation){
        if(nation==null)return false;
        Snapshot ss=snapshot(false);
        if(!ss.available)return false;
        JsonObject record=object(ss.governments,nation.getUuid().toString());
        String self=player.getUniqueId().toString();
        return self.equals(getStr(record,"head",null)) || self.equals(getStr(object(record,"offices"),"MARSHAL",null));
    }
    String report(MilitaryState.Army army,Nation nation,int maxHops){
        if(army.musterTerritoryId==null)return "No muster territory assigned";
        Snapshot ss=snapshot(false);
        if(!ss.available)return ss.issue;
        if(nation==null)return "Nation is missing";
        Map<Integer,Territory> territory=plugin.nodes().territories();
        Territory start=territory.get(army.musterTerritoryId);
        if(!plugin.nodes().owns(start,nation))return "Muster territory is not owned by this nation";
        Map<Integer,Set<Integer>> graph=new HashMap<>();
        for(Map.Entry<Integer,Territory> one:territory.entrySet()) {
            if(!plugin.nodes().owns(one.getValue(),nation))continue;
            Set<Integer> neigh=graph.computeIfAbsent(one.getKey(),k->new HashSet<>());
            for(int n:plugin.nodes().neighbors(one.getValue()))if(plugin.nodes().owns(territory.get(n),nation))neigh.add(n);
        }
        for(Link link:ss.links){
            if(!nation.getUuid().toString().equals(link.nation))continue;
            if(!graph.containsKey(link.from)||!graph.containsKey(link.to))continue;
            // In Nodes-Extra ROAD/RAIL must use native adjacency; SEA requires existing linked ports.
            boolean canConnect=link.type.equalsIgnoreCase("SEA") && plugin.nodes().portsShareGroup(link.from,link.to);
            if(canConnect){graph.get(link.from).add(link.to);graph.get(link.to).add(link.from);}
        }
        Map<Integer,Integer> distances=TerritoryRoutes.distances(graph,army.musterTerritoryId,maxHops);
        boolean reachableEmpty=false,reachableBlocked=false;
        for(Depot depot:ss.depots){
            if(!nation.getUuid().toString().equals(depot.nation)||!distances.containsKey(depot.territory))continue;
            if(depot.blockadedUntil>System.currentTimeMillis()){reachableBlocked=true;continue;}
            if(depot.food>0 && depot.equipment>0)return "SUPPLIED ("+distances.get(depot.territory)+" territory hops)";
            if(depot.food>0 || depot.equipment>0)return "LIMITED ("+distances.get(depot.territory)+" territory hops)";
            reachableEmpty=true;
        }
        if(reachableBlocked)return "BLOCKADED";
        if(reachableEmpty)return "EMPTY DEPOTS";
        return "UNSUPPLIED (no stocked depot within "+maxHops+" hops)";
    }
    static JsonObject object(JsonObject o,String k){if(o==null)return new JsonObject();JsonElement e=o.get(k);return e!=null&&e.isJsonObject()?e.getAsJsonObject():new JsonObject();}
    static String getStr(JsonObject o,String k,String fallback){try{JsonElement e=o.get(k);return e!=null&&!e.isJsonNull()?e.getAsString():fallback;}catch(Exception ex){return fallback;}}
    static int getInt(JsonObject o,String k,int fallback){try{JsonElement e=o.get(k);return e!=null&&!e.isJsonNull()?e.getAsInt():fallback;}catch(Exception ex){return fallback;}}
    static long getLong(JsonObject o,String k,long fallback){try{JsonElement e=o.get(k);return e!=null&&!e.isJsonNull()?e.getAsLong():fallback;}catch(Exception ex){return fallback;}}
}
