package com.auroria.nodesmilitary;

import org.bukkit.entity.Player;
import phonon.nodes.Nodes;
import phonon.nodes.objects.*;
import java.lang.reflect.*;
import java.util.*;

/** Direct MilzzyNodes API connection; reflective Kotlin inline accessors avoid hard-coded compiler mangling. */
final class NodesBridge {
    private final Method territoryId, territoryNeighbors;
    NodesBridge() {
        territoryId = findMethod(Territory.class,"getId-",int.class);
        territoryNeighbors = findMethod(Territory.class,"getNeighbors-",int[].class);
    }
    private static Method findMethod(Class<?> cls, String prefix, Class<?> output) {
        for(Method m:cls.getMethods()) if(m.getName().startsWith(prefix) && m.getReturnType()==output && m.getParameterCount()==0) return m;
        throw new IllegalStateException("Unsupported MilzzyNodes territory API: missing " + prefix);
    }
    Resident resident(Player player){return Nodes.INSTANCE.getResident(player);}
    Nation nation(Player player){Resident r=resident(player); return r==null?null:r.getNation();}
    Territory territory(Player player){return Nodes.INSTANCE.getTerritoryFromPlayer(player);}
    Nation nationById(String uuid){
        if(uuid==null)return null;
        for(Nation n:Nodes.INSTANCE.getNations$nodes().values())if(n.getUuid().toString().equals(uuid))return n;
        return null;
    }
    String nationName(String uuid){Nation n=nationById(uuid);return n==null?null:n.getName();}
    boolean citizen(Player p,Nation n){Nation own=nation(p);return n!=null && own!=null && own.getUuid().equals(n.getUuid());}
    boolean sovereign(Player p,Nation n){return n!=null && n.getCapital()!=null && n.getCapital().getLeader()!=null && p.getUniqueId().equals(n.getCapital().getLeader().getUuid());}
    boolean townCommander(Player p,Nation n){
        if(!citizen(p,n))return false;
        Resident r=resident(p);Town t=r==null?null:r.getTown();
        return t!=null && n.getUuid().equals(t.getNation().getUuid()) && ((t.getLeader()!=null && t.getLeader().getUuid().equals(p.getUniqueId())) || t.getOfficers().contains(r) || t.getGenerals().contains(r));
    }
    Resident citizenByName(Nation n,String name){
        if(n==null || name==null)return null;
        for(Resident r:n.getResidents())if(r.getName().equalsIgnoreCase(name))return r;
        return null;
    }
    String citizenName(Nation n,String uuid){
        if(n==null || uuid==null)return "Unassigned";
        for(Resident r:n.getResidents())if(r.getUuid().toString().equals(uuid))return r.getName();
        return "Unknown citizen";
    }
    int id(Territory t){try{return (Integer)territoryId.invoke(t);}catch(ReflectiveOperationException ex){throw new IllegalStateException("Unable to read territory id",ex);}}
    int[] neighbors(Territory t){try{return (int[])territoryNeighbors.invoke(t);}catch(ReflectiveOperationException ex){throw new IllegalStateException("Unable to read territory neighbors",ex);}}
    Map<Integer,Territory> territories(){
        Map<Integer,Territory> result=new LinkedHashMap<>();
        for(Map.Entry<?,Territory> e:Nodes.INSTANCE.iterTerritories())result.put(id(e.getValue()),e.getValue());
        return result;
    }
    boolean owns(Territory t,Nation n){return t!=null && t.getTown()!=null && n!=null && t.getTown().getNation()!=null && t.getTown().getNation().getUuid().equals(n.getUuid());}
    boolean portsShareGroup(int a,int b){
        Set<String> first=new HashSet<>(),second=new HashSet<>();
        for(Port p:Nodes.INSTANCE.getPorts$nodes().values()){
            Territory terr=Nodes.INSTANCE.getTerritoryFromBlock(p.getLocX(),p.getLocZ());
            if(terr==null)continue;int id=id(terr);
            if(id!=a && id!=b)continue;
            for(PortGroup pg:p.getGroups())(id==a?first:second).add(pg.getName());
        }
        first.retainAll(second);return !first.isEmpty();
    }
}
