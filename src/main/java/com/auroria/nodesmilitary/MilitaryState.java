package com.auroria.nodesmilitary;

import java.util.*;

/** Public schema-compatible JSON model for Nodes-Military 1.0.0 saves. */
public final class MilitaryState {
    private MilitaryState() {}
    public static final class State {
        public int schema = 1;
        public Map<String, Army> armies = new LinkedHashMap<>();
        public List<String> history = new ArrayList<>();
    }
    public static final class Army {
        public String id, name, nationId, commanderUuid, createdBy;
        public long createdAt;
        public String musterWorld;
        public Integer musterTerritoryId;
        public Map<String, Division> divisions = new LinkedHashMap<>();
    }
    public static final class Division {
        public String id, name, type, commanderUuid;
        public List<String> members = new ArrayList<>();
        public Map<String, String> ranks = new LinkedHashMap<>();
        public String order = "HOLD";
        public Integer targetTerritoryId;
        public long orderUpdatedAt;
    }
}
