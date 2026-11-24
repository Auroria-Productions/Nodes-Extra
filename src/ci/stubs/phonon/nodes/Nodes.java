package phonon.nodes;
import java.util.*;
import org.bukkit.entity.Player;
import phonon.nodes.objects.*;
/** API signatures only. Never packaged or run; runtime comes from installed MilzzyNodes. */
public final class Nodes {
    public static final Nodes INSTANCE = new Nodes();
    public Resident getResident(Player p){throw new UnsupportedOperationException();}
    public Territory getTerritoryFromPlayer(Player p){throw new UnsupportedOperationException();}
    public Territory getTerritoryFromBlock(int x,int z){throw new UnsupportedOperationException();}
    public Map<String,Nation> getNations$nodes(){throw new UnsupportedOperationException();}
    public Map<String,Port> getPorts$nodes(){throw new UnsupportedOperationException();}
    public Iterable<Map.Entry<TerritoryId,Territory>> iterTerritories(){throw new UnsupportedOperationException();}
}
