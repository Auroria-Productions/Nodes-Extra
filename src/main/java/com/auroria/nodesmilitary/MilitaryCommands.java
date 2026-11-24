package com.auroria.nodesmilitary;

import org.bukkit.command.*;
import org.bukkit.entity.Player;
import phonon.nodes.objects.*;
import java.util.*;
import java.util.stream.Collectors;

/** Authoritative command layer shared by chat commands and clickable GUI actions. */
final class MilitaryCommands implements CommandExecutor,TabCompleter {
    static final Set<String> DIVISION_TYPES=Set.of("infantry","cavalry","artillery","engineers","naval","reconnaissance");
    static final List<String> RANKS=List.of("private","corporal","sergeant","lieutenant","captain","major","colonel","general");
    private final MilitaryPlugin plugin;
    MilitaryCommands(MilitaryPlugin plugin){this.plugin=plugin;}
    @Override public boolean onCommand(CommandSender sender,Command command,String alias,String[] args){
        if(!sender.hasPermission("nodesmilitary.use") && !sender.hasPermission("nodesmilitary.admin")){msg(sender,"You need nodesmilitary.use");return true;}
        if(args.length==0){help(sender);return true;}
        String action=args[0].toLowerCase(Locale.ROOT);
        try{
            switch(action){
                case "help" -> help(sender);
                case "gui","menu" -> {Player p=player(sender);if(p!=null){if(!plugin.getConfig().getBoolean("gui.enabled",true))msg(sender,"Military GUI disabled in config.yml");else plugin.gui().openRoot(p);}}
                case "overview" -> overview(sender);
                case "army" -> army(sender,args);
                case "division" -> division(sender,args);
                case "enlist" -> enlist(sender,args);
                case "leave" -> leave(sender);
                case "muster" -> muster(sender,args);
                case "order" -> order(sender,args);
                case "report" -> report(sender,args);
                case "history" -> history(sender);
                case "save" -> {if(admin(sender)){plugin.persist();msg(sender,"Military state and public map export saved");}}
                case "reload" -> {if(admin(sender)){plugin.reloadConfig();msg(sender,"Settings reloaded; saved military data untouched");}}
                default -> help(sender);
            }
        }catch(Exception ex){
            msg(sender,"Operation failed; no data was intentionally deleted. Check server console.");
            plugin.getLogger().warning("Military command error: "+ex.getMessage());
        }
        return true;
    }
    static Player player(CommandSender s){if(s instanceof Player p)return p;s.sendMessage("§c[Nodes-Military] Use this command in-game");return null;}
    static void msg(CommandSender s,String line){s.sendMessage("§6[Nodes-Military] §f"+line);}
    private boolean admin(CommandSender s){if(s.hasPermission("nodesmilitary.admin"))return true;msg(s,"Administrator permission required");return false;}
    boolean manageNation(Player p,Nation nation){
        if(p.hasPermission("nodesmilitary.admin")||p.hasPermission("nodesextra.admin"))return true;
        if(nation==null || !plugin.nodes().citizen(p,nation))return false;
        return !plugin.requireMarshal() || plugin.nodes().sovereign(p,nation) || plugin.nodes().townCommander(p,nation) || plugin.extra().marshalOrHead(p,nation);
    }
    boolean manageArmy(Player p,Nation nation,MilitaryState.Army army){
        return army!=null && (manageNation(p,nation) || p.getUniqueId().toString().equals(army.commanderUuid));
    }
    boolean manageDivision(Player p,Nation nation,MilitaryState.Army army,MilitaryState.Division division){
        return division!=null && (manageArmy(p,nation,army)||p.getUniqueId().toString().equals(division.commanderUuid));
    }
    private Nation nation(Player p){Nation n=plugin.nodes().nation(p);if(n==null)msg(p,"Join a Nodes nation first");return n;}
    private boolean allowedRead(Player p,Nation n){return n!=null && (plugin.nodes().citizen(p,n)||p.hasPermission("nodesmilitary.admin"));}
    List<MilitaryState.Army> armies(Nation nation){
        if(nation==null)return List.of();
        return plugin.state().armies.values().stream().filter(a->nation.getUuid().toString().equals(a.nationId)).sorted(Comparator.comparing(a->a.name.toLowerCase(Locale.ROOT))).toList();
    }
    MilitaryState.Army findArmy(Nation nation,String name){
        if(nation==null)return null;
        return armies(nation).stream().filter(a->a.name.equalsIgnoreCase(name)).findFirst().orElse(null);
    }
    MilitaryState.Division findDivision(MilitaryState.Army army,String name){
        if(army==null)return null;
        return army.divisions.values().stream().filter(d->d.name.equalsIgnoreCase(name)).findFirst().orElse(null);
    }
    private static boolean validName(String name){return name!=null&&name.matches("[A-Za-z0-9_-]{2,24}");}
    private static String key(String name){return name.toLowerCase(Locale.ROOT);}
    private void overview(CommandSender sender){
        Player p=player(sender);if(p==null)return;
        Nation n=nation(p);if(n==null)return;
        List<MilitaryState.Army> armies=armies(n);
        msg(sender,"§e"+n.getName()+"§f has "+armies.size()+" armies");
        if(armies.isEmpty())msg(sender,"No armies yet. Leaders can create one with /nmilitary army create <name>");
        for(MilitaryState.Army a:armies){
            int total=a.divisions.values().stream().mapToInt(d->d.members.size()).sum();
            msg(sender,"§e"+a.name+"§f: "+a.divisions.size()+" divisions, "+total+" enlisted; muster="+(a.musterTerritoryId==null?"unset":"#"+a.musterTerritoryId));
        }
    }
    private void army(CommandSender sender,String[] args){
        if(args.length<2){msg(sender,"/nmilitary army <create|info|commander|disband> ...");return;}
        Player p=player(sender);if(p==null)return;
        Nation n=nation(p);if(n==null)return;
        String mode=args[1].toLowerCase(Locale.ROOT);
        if(args.length<3){msg(sender,"Specify an army name");return;}
        String name=args[2];
        MilitaryState.Army a=findArmy(n,name);
        switch(mode){
            case "create" -> {
                if(!manageNation(p,n)){noAuthority(sender);return;}
                if(!validName(name)){msg(sender,"Army name must have 2-24 letters, numbers, - or _");return;}
                if(a!=null){msg(sender,"Army name already exists");return;}
                if(armies(n).size()>=plugin.maxArmies()){msg(sender,"Maximum number of armies reached");return;}
                a=new MilitaryState.Army();a.nationId=n.getUuid().toString();a.id=a.nationId+":"+key(name);
                a.name=name;a.createdBy=p.getUniqueId().toString();a.createdAt=System.currentTimeMillis();
                plugin.state().armies.put(a.id,a);plugin.audit(p.getName()+" created army "+a.name+" for "+n.getName());msg(sender,"Army "+a.name+" created");
            }
            case "info" -> {
                if(a==null){msg(sender,"Army not found");return;}
                msg(sender,"Army "+a.name+" | commander: "+plugin.nodes().citizenName(n,a.commanderUuid)+" | muster: "+(a.musterTerritoryId==null?"unset":"#"+a.musterTerritoryId));
                for(MilitaryState.Division d:a.divisions.values())msg(sender,d.name+" ("+d.type+") "+d.members.size()+" members, order "+d.order);
            }
            case "commander" -> {
                if(a==null||args.length<4){msg(sender,"/nmilitary army commander <army> <citizen>");return;}
                if(!manageNation(p,n)){noAuthority(sender);return;}
                Resident r=plugin.nodes().citizenByName(n,args[3]);if(r==null){msg(sender,"Citizen not found in your nation");return;}
                a.commanderUuid=r.getUuid().toString();plugin.audit(p.getName()+" appointed "+r.getName()+" as commander of "+a.name);msg(sender,"Army commander appointed: "+r.getName());
            }
            case "disband" -> {
                if(a==null){msg(sender,"Army not found");return;}
                if(!manageArmy(p,n,a)){noAuthority(sender);return;}
                plugin.state().armies.remove(a.id);plugin.audit(p.getName()+" disbanded army "+a.name);msg(sender,"Disbanded "+a.name+" and all its divisions");
            }
            default -> msg(sender,"/nmilitary army <create|info|commander|disband> ...");
        }
    }
    private void division(CommandSender sender,String[] args){
        if(args.length<4){msg(sender,"/nmilitary division <create|commander|rank|roster|disband> <army> <division> ...");return;}
        Player p=player(sender);if(p==null)return;
        Nation n=nation(p);if(n==null)return;
        MilitaryState.Army army=findArmy(n,args[2]);
        if(army==null){msg(sender,"Army not found");return;}
        String mode=args[1].toLowerCase(Locale.ROOT),name=args[3];
        MilitaryState.Division div=findDivision(army,name);
        switch(mode){
            case "create" -> {
                if(!manageArmy(p,n,army)){noAuthority(sender);return;}
                if(args.length<5){msg(sender,"/nmilitary division create <army> <division> <type>");return;}
                String type=args[4].toLowerCase(Locale.ROOT);
                if(!validName(name)||!DIVISION_TYPES.contains(type)){msg(sender,"Invalid division name or type: "+String.join(", ",DIVISION_TYPES));return;}
                if(div!=null){msg(sender,"Division already exists");return;}
                if(army.divisions.size()>=plugin.maxDivisions()){msg(sender,"Maximum divisions reached");return;}
                div=new MilitaryState.Division();div.id=key(name);div.name=name;div.type=type;
                army.divisions.put(div.id,div);plugin.audit(p.getName()+" created "+type+" division "+name+" in "+army.name);msg(sender,"Division "+name+" created");
            }
            case "roster" -> {
                if(div==null){msg(sender,"Division not found");return;}
                msg(sender,"§e"+div.name+"§f ("+div.type+") members: "+div.members.size()+" / "+plugin.maxMembers());
                for(String id:div.members)msg(sender,plugin.nodes().citizenName(n,id)+" — "+div.ranks.getOrDefault(id,"PRIVATE"));
            }
            case "commander" -> {
                if(div==null||args.length<5){msg(sender,"/nmilitary division commander <army> <division> <enlisted-citizen>");return;}
                if(!manageArmy(p,n,army)){noAuthority(sender);return;}
                Resident r=plugin.nodes().citizenByName(n,args[4]);
                if(r==null||!div.members.contains(r.getUuid().toString())){msg(sender,"Citizen must already be enlisted in this division");return;}
                div.commanderUuid=r.getUuid().toString();plugin.audit(p.getName()+" appointed "+r.getName()+" to command "+div.name);msg(sender,"Division commander appointed");
            }
            case "rank" -> {
                if(div==null||args.length<6){msg(sender,"/nmilitary division rank <army> <division> <citizen> <rank>");return;}
                if(!manageDivision(p,n,army,div)){noAuthority(sender);return;}
                Resident r=plugin.nodes().citizenByName(n,args[4]);String rank=args[5].toLowerCase(Locale.ROOT);
                if(r==null||!div.members.contains(r.getUuid().toString())||!RANKS.contains(rank)){msg(sender,"Citizen must be enlisted; valid rank: "+RANKS);return;}
                div.ranks.put(r.getUuid().toString(),rank.toUpperCase(Locale.ROOT));plugin.audit(p.getName()+" assigned "+r.getName()+" rank "+rank);msg(sender,"Rank updated");
            }
            case "disband" -> {
                if(div==null){msg(sender,"Division not found");return;}
                if(!manageArmy(p,n,army)){noAuthority(sender);return;}
                army.divisions.remove(div.id);plugin.audit(p.getName()+" disbanded division "+div.name);msg(sender,"Division disbanded");
            }
            default -> msg(sender,"/nmilitary division <create|commander|rank|roster|disband> ...");
        }
    }
    private boolean enlisted(String uuid){
        for(MilitaryState.Army army:plugin.state().armies.values())for(MilitaryState.Division d:army.divisions.values())if(d.members.contains(uuid))return true;
        return false;
    }
    private void enlist(CommandSender sender,String[] args){
        if(args.length!=3){msg(sender,"/nmilitary enlist <army> <division>");return;}
        Player p=player(sender);if(p==null)return;Nation n=nation(p);if(n==null)return;
        MilitaryState.Army army=findArmy(n,args[1]);MilitaryState.Division div=findDivision(army,args[2]);
        if(div==null){msg(sender,"Army/division not found in your nation");return;}
        String uuid=p.getUniqueId().toString();
        if(enlisted(uuid)){msg(sender,"You are already enlisted in a division. Use /nmilitary leave first");return;}
        if(div.members.size()>=plugin.maxMembers()){msg(sender,"Division is full");return;}
        div.members.add(uuid);div.ranks.put(uuid,"PRIVATE");plugin.audit(p.getName()+" enlisted in "+army.name+"/"+div.name);msg(sender,"Enlisted in "+div.name);
    }
    private void leave(CommandSender sender){
        Player p=player(sender);if(p==null)return;String uuid=p.getUniqueId().toString();boolean changed=false;
        for(MilitaryState.Army army:plugin.state().armies.values())for(MilitaryState.Division div:army.divisions.values()){
            if(div.members.remove(uuid)){div.ranks.remove(uuid);if(uuid.equals(div.commanderUuid))div.commanderUuid=null;changed=true;}
        }
        if(changed){plugin.audit(p.getName()+" left their division");msg(sender,"You left the military division");}
        else msg(sender,"You are not enlisted");
    }
    private void muster(CommandSender sender,String[] args){
        if(args.length!=2){msg(sender,"/nmilitary muster <army>");return;}
        Player p=player(sender);if(p==null)return;Nation n=nation(p);if(n==null)return;
        MilitaryState.Army army=findArmy(n,args[1]);if(army==null){msg(sender,"Army not found");return;}
        if(!manageArmy(p,n,army)){noAuthority(sender);return;}
        Territory territory=plugin.nodes().territory(p);
        if(!plugin.nodes().owns(territory,n)){msg(sender,"Stand in a territory owned by your nation");return;}
        army.musterWorld=p.getWorld().getName();army.musterTerritoryId=plugin.nodes().id(territory);
        plugin.audit(p.getName()+" set muster of "+army.name+" to #"+army.musterTerritoryId);msg(sender,"Muster changed to #"+army.musterTerritoryId);
    }
    private void order(CommandSender sender,String[] args){
        if(args.length!=5){msg(sender,"/nmilitary order <army> <division> <hold|move|retreat> <territory-id>");return;}
        Player p=player(sender);if(p==null)return;Nation n=nation(p);if(n==null)return;
        MilitaryState.Army army=findArmy(n,args[1]);MilitaryState.Division div=findDivision(army,args[2]);
        if(div==null){msg(sender,"Division not found");return;}
        if(!manageDivision(p,n,army,div)){noAuthority(sender);return;}
        String mode=args[3].toUpperCase(Locale.ROOT);
        if(!Set.of("HOLD","MOVE","RETREAT").contains(mode)){msg(sender,"Invalid strategic order");return;}
        int id;try{id=Integer.parseInt(args[4]);}catch(NumberFormatException ex){msg(sender,"Territory ID must be a number");return;}
        if(!plugin.nodes().owns(plugin.nodes().territories().get(id),n)){msg(sender,"Order territory must be owned by your nation");return;}
        div.order=mode;div.targetTerritoryId=id;div.orderUpdatedAt=System.currentTimeMillis();
        plugin.audit(p.getName()+" ordered "+div.name+" to "+mode+" at #"+id);msg(sender,"Recorded "+mode+" for "+div.name+" at #"+id+" (strategic record only)");
    }
    private void report(CommandSender sender,String[] args){
        if(args.length!=2){msg(sender,"/nmilitary report <army>");return;}
        Player p=player(sender);if(p==null)return;Nation n=nation(p);if(n==null)return;
        MilitaryState.Army army=findArmy(n,args[1]);if(army==null){msg(sender,"Army not found");return;}
        msg(sender,army.name+" | divisions: "+army.divisions.size()+" | members: "+army.divisions.values().stream().mapToInt(d->d.members.size()).sum());
        msg(sender,"Supply readiness: "+plugin.extra().report(army,n,plugin.maxHops()));
    }
    private void history(CommandSender sender){
        if(!admin(sender))return;
        List<String> logs=plugin.state().history;msg(sender,"Last "+Math.min(logs.size(),10)+" military changes:");
        for(int i=Math.max(0,logs.size()-10);i<logs.size();i++)msg(sender,logs.get(i));
    }
    private static void noAuthority(CommandSender sender){msg(sender,"Leadership authority required (Marshal, executive, sovereign, town commander or appointed commander)");}
    private void help(CommandSender sender){
        msg(sender,"/nmilitary gui — open clickable military menu");
        msg(sender,"/nmilitary overview | army <create|info|commander|disband> ...");
        msg(sender,"/nmilitary division <create|roster|commander|rank|disband> ...");
        msg(sender,"/nmilitary enlist <army> <division> | leave | muster <army>");
        msg(sender,"/nmilitary order <army> <division> <hold|move|retreat> <territory-id>");
        msg(sender,"/nmilitary report <army> | history | save | reload");
        msg(sender,"All commands also work through /nodes military when the bridge is available");
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(args.length==0)return List.of();
        if(args.length==1)return matches(List.of("help","gui","overview","army","division","enlist","leave","muster","order","report","history","save","reload"),args[0]);
        if(!(sender instanceof Player p))return List.of();Nation n=plugin.nodes().nation(p);
        if(n==null)return List.of();
        List<MilitaryState.Army> armies=armies(n);
        if(args.length==2){
            return switch(args[0].toLowerCase(Locale.ROOT)){
                case "army" -> matches(List.of("create","info","commander","disband"),args[1]);
                case "division" -> matches(List.of("create","roster","commander","rank","disband"),args[1]);
                case "enlist","muster","report","order" -> matches(armies.stream().map(a->a.name).toList(),args[1]);
                default -> List.of();
            };
        }
        if(args.length==3){
            if(args[0].equalsIgnoreCase("army") && !args[1].equalsIgnoreCase("create"))return matches(armies.stream().map(a->a.name).toList(),args[2]);
            if(args[0].equalsIgnoreCase("division"))return matches(armies.stream().map(a->a.name).toList(),args[2]);
            if(args[0].equalsIgnoreCase("enlist")||args[0].equalsIgnoreCase("order")){
                MilitaryState.Army a=findArmy(n,args[1]);return matches(a==null?List.of():a.divisions.values().stream().map(d->d.name).toList(),args[2]);
            }
        }
        if(args.length==4 && args[0].equalsIgnoreCase("division")){
            MilitaryState.Army a=findArmy(n,args[2]);return matches(a==null?List.of():a.divisions.values().stream().map(d->d.name).toList(),args[3]);
        }
        if(args.length==5 && args[0].equalsIgnoreCase("division") && args[1].equalsIgnoreCase("create"))return matches(DIVISION_TYPES,args[4]);
        if(args.length==4 && args[0].equalsIgnoreCase("order"))return matches(List.of("hold","move","retreat"),args[3]);
        if(args.length==6 && args[0].equalsIgnoreCase("division") && args[1].equalsIgnoreCase("rank"))return matches(RANKS,args[5]);
        return List.of();
    }
    private static List<String> matches(Collection<String> values,String start){return values.stream().filter(s->s.toLowerCase(Locale.ROOT).startsWith(start.toLowerCase(Locale.ROOT))).sorted().toList();}
}
