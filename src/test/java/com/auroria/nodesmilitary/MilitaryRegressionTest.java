package com.auroria.nodesmilitary;

import java.nio.file.*;
import java.util.*;

/** No Minecraft server required: old save compatibility, fail-closed load and bounded routing. */
public final class MilitaryRegressionTest {
    private static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);System.out.println("PASS "+name);}
    public static void main(String[] args) throws Exception {
        Path tmp=Files.createTempDirectory("nodes-military-test-");
        Path file=tmp.resolve("state.json");
        String existing="""
            {"schema":1,"armies":{"nation:royal":{"id":"nation:royal","name":"Royal","nationId":"nation","commanderUuid":"player-1","createdBy":"player-1","createdAt":1700000000,"musterWorld":"world","musterTerritoryId":101,"divisions":{"guards":{"id":"guards","name":"Guards","type":"infantry","commanderUuid":"player-2","members":["player-2","player-3"],"ranks":{"player-2":"CAPTAIN","player-3":"PRIVATE"},"order":"HOLD","targetTerritoryId":101,"orderUpdatedAt":1700000001}}}},"history":["original entry"]}
            """;
        Files.writeString(file,existing);
        MilitaryState.State original=MilitaryStorage.load(file);
        check(original.armies.size()==1,"v1.0.0 state loads without migration");
        MilitaryState.Army army=original.armies.get("nation:royal");
        check(army!=null && army.musterTerritoryId==101 && army.divisions.size()==1,"army territory and divisions preserved");
        check(army.divisions.get("guards").members.size()==2 && "CAPTAIN".equals(army.divisions.get("guards").ranks.get("player-2")),"member UUIDs and ranks preserved");
        MilitaryStorage.save(file,original);
        check(Files.exists(file.resolveSibling("state.json.bak")),"backup created before overwrite");
        MilitaryState.State reread=MilitaryStorage.load(file);
        check(reread.armies.get("nation:royal").divisions.get("guards").members.contains("player-3"),"save/load roundtrip retains enlistment");
        String malformed="{ definitely not JSON";Files.writeString(file,malformed);
        boolean refused=false;try{MilitaryStorage.load(file);}catch(Exception ex){refused=true;}
        check(refused && Files.readString(file).equals(malformed),"malformed input fails closed and remains unchanged");
        Map<Integer,Set<Integer>> graph=new HashMap<>();graph.put(1,Set.of(2));graph.put(2,Set.of(1,3));graph.put(3,Set.of(2,4));graph.put(4,Set.of(3));
        check(TerritoryRoutes.distances(graph,1,2).get(3)==2,"bounded supply route reaches within allowed hops");
        check(!TerritoryRoutes.distances(graph,1,2).containsKey(4),"supply route rejects beyond max hops");
        check(TerritoryRoutes.distances(graph,999,2).isEmpty(),"absent muster safely rejects graph");
        System.out.println("All 8 offline regression checks passed");
    }
}
