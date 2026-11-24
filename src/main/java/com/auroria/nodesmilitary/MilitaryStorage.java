package com.auroria.nodesmilitary;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Fail-closed persistence and privacy-safe export. Never replace an invalid save. */
final class MilitaryStorage {
    private static final long MAX_BYTES = 15_000_000;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private MilitaryStorage() {}

    static MilitaryState.State load(Path path) throws IOException {
        if (!Files.exists(path)) return new MilitaryState.State();
        if (Files.size(path) > MAX_BYTES) throw new IOException("Military state is too large");
        MilitaryState.State state;
        try { state = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), MilitaryState.State.class); }
        catch (RuntimeException ex) { throw new IOException("Invalid Nodes-Military state JSON; refusing to overwrite", ex); }
        if (state == null || state.schema != 1 || state.armies == null || state.history == null)
            throw new IOException("Unsupported Nodes-Military state schema; refusing to overwrite");
        for (Map.Entry<String,MilitaryState.Army> ent : state.armies.entrySet()) {
            MilitaryState.Army a = ent.getValue();
            if (a == null || a.id == null || a.name == null || a.nationId == null || a.divisions == null)
                throw new IOException("Invalid army record " + ent.getKey());
            for (MilitaryState.Division d : a.divisions.values())
                if (d == null || d.id == null || d.name == null || d.members == null || d.ranks == null)
                    throw new IOException("Invalid division in army " + a.name);
        }
        return state;
    }
    static void save(Path path, MilitaryState.State state) throws IOException { writeAtomic(path, GSON.toJson(state), true); }
    static void export(Path path, MilitaryState.State state, NodesBridge nodes) throws IOException {
        Map<String,Object> data = new LinkedHashMap<>();
        data.put("schema", 1); data.put("generatedAt", Instant.now().toEpochMilli());
        List<Map<String,Object>> armies = new ArrayList<>();
        for (MilitaryState.Army a : state.armies.values()) {
            if (a == null) continue;
            Map<String,Object> info = new LinkedHashMap<>();
            info.put("id", a.id); info.put("name", a.name); info.put("nationId", a.nationId);
            String nationName = nodes.nationName(a.nationId);
            info.put("nation", nationName == null ? "Unknown" : nationName);
            info.put("musterWorld", a.musterWorld); info.put("musterTerritoryId", a.musterTerritoryId);
            List<Map<String,Object>> divisions = new ArrayList<>();
            for (MilitaryState.Division d : a.divisions.values()) {
                Map<String,Object> one = new LinkedHashMap<>();
                one.put("name", d.name); one.put("type", d.type);
                one.put("headcount", d.members.size()); one.put("order", d.order);
                one.put("targetTerritoryId", d.targetTerritoryId); one.put("orderUpdatedAt", d.orderUpdatedAt);
                divisions.add(one);
            }
            info.put("divisions", divisions); info.put("headcount", divisions.stream().mapToInt(d -> ((Number)d.get("headcount")).intValue()).sum());
            armies.add(info);
        }
        data.put("armies", armies);
        writeAtomic(path, GSON.toJson(data), false);
    }
    private static void writeAtomic(Path target, String content, boolean backup) throws IOException {
        Path parent = target.getParent();
        Files.createDirectories(parent);
        Path tmp = Files.createTempFile(parent, target.getFileName().toString()+"-", ".tmp");
        boolean finished = false;
        try {
            Files.writeString(tmp, content, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
            if (backup && Files.exists(target)) Files.copy(target, target.resolveSibling(target.getFileName()+".bak"), StandardCopyOption.REPLACE_EXISTING);
            try { Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING); }
            finished = true;
        } finally { if (!finished) Files.deleteIfExists(tmp); }
    }
}
