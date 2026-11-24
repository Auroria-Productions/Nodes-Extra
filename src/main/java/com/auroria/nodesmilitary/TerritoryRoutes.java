package com.auroria.nodesmilitary;
import java.util.*;
/** Bounded BFS over permitted Nodes territory IDs; no chunk-level graph scans. */
public final class TerritoryRoutes {
    private TerritoryRoutes(){}
    public static Map<Integer,Integer> distances(Map<Integer,Set<Integer>> edges,int start,int maxHops){
        if(!edges.containsKey(start))return Map.of();
        Map<Integer,Integer> out=new HashMap<>();ArrayDeque<Integer> queue=new ArrayDeque<>();
        queue.add(start);out.put(start,0);
        while(!queue.isEmpty()){
            int at=queue.removeFirst(),dist=out.get(at);
            if(dist>=maxHops)continue;
            for(int next:edges.getOrDefault(at,Set.of())) {
                if(!edges.containsKey(next)||out.containsKey(next))continue;
                out.put(next,dist+1);queue.addLast(next);
            }
        }
        return out;
    }
}
