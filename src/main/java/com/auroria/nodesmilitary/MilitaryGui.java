package com.auroria.nodesmilitary;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import phonon.nodes.objects.Nation;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Native Bukkit GUI; all mutations are routed through the authoritative commands. */
final class MilitaryGui implements Listener {
    private final MilitaryPlugin plugin;
    private final MilitaryCommands commands;
    private final Map<UUID,Prompt> pending = new ConcurrentHashMap<>();
    private static final int[] CONTENT_SLOTS={10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
    private record Prompt(String nationId,String template,boolean number,long createdAt){}
    private interface Action{void execute(Player p);}
    private static final class Menu implements InventoryHolder {
        Inventory inventory;
        final String nationId;
        final Map<Integer,Action> slots=new HashMap<>();
        Menu(String nationId){this.nationId=nationId;}
        @Override public Inventory getInventory(){return inventory;}
    }
    MilitaryGui(MilitaryPlugin plugin){this.plugin=plugin;this.commands=new MilitaryCommands(plugin);}
    void shutdown(){pending.clear();}
    private Menu menu(Player p,String title){
        Nation n=plugin.nodes().nation(p);
        Menu holder=new Menu(n==null?"":n.getUuid().toString());
        holder.inventory=Bukkit.createInventory(holder,54,ChatColor.DARK_GREEN+"Nodes Military: "+title);
        ItemStack filler=item(Material.GRAY_STAINED_GLASS_PANE," ");
        for(int i=0;i<54;i++) if(i<9||i>=45||i%9==0||i%9==8)holder.inventory.setItem(i,filler);
        return holder;
    }
    private ItemStack item(Material material,String title,String...lore){
        ItemStack item=new ItemStack(material);
        ItemMeta meta=item.getItemMeta();
        if(meta!=null){meta.setDisplayName(ChatColor.GOLD+title);List<String> lines=new ArrayList<>();for(String line:lore)lines.add(ChatColor.GRAY+line);meta.setLore(lines);item.setItemMeta(meta);}
        return item;
    }
    private void action(Menu m,int slot,Material mat,String title,Action a,String...lore){m.inventory.setItem(slot,item(mat,title,lore));if(a!=null)m.slots.put(slot,a);}
    private void open(Menu m,Player p){p.openInventory(m.inventory);}
    void openRoot(Player p){openRoot(p,0);}
    private void openRoot(Player p,int page){
        Menu m=menu(p,"Your Nation");Nation nation=plugin.nodes().nation(p);
        if(nation==null){action(m,22,Material.BARRIER,"Join a Nodes nation",null,"A nation is required to use military features");open(m,p);return;}
        List<MilitaryState.Army> armies=commands.armies(nation);
        action(m,4,Material.WHITE_BANNER,nation.getName()+" Military",null,"Armies: "+armies.size(),"Select an army to view divisions");
        int start=page*CONTENT_SLOTS.length;
        for(int i=start;i<Math.min(armies.size(),start+CONTENT_SLOTS.length);i++){
            MilitaryState.Army army=armies.get(i);
            int headcount=army.divisions.values().stream().mapToInt(d->d.members.size()).sum();
            action(m,CONTENT_SLOTS[i-start],Material.SHIELD,army.name,pl->openArmy(pl,army.name,0),
                army.divisions.size()+" divisions · "+headcount+" members","Muster: "+(army.musterTerritoryId==null?"Not assigned":"#"+army.musterTerritoryId),"Click to inspect");
        }
        if(page>0)action(m,45,Material.ARROW,"Previous page",pl->openRoot(pl,page-1));
        if(start+CONTENT_SLOTS.length<armies.size())action(m,53,Material.ARROW,"Next page",pl->openRoot(pl,page+1));
        if(commands.manageNation(p,nation))action(m,49,Material.EMERALD,"Create Army",pl->prompt(pl,"army create %s",false,null,null),"Enter the army name in chat","Requires authorised leadership");
        action(m,50,Material.BOOK,"Military overview",pl->{pl.closeInventory();run(pl,"overview");},"List all armies in chat");
        action(m,51,Material.COMPASS,"Refresh",pl->openRoot(pl,page));
        open(m,p);
    }
    void openArmy(Player p,String armyName,int page){
        Nation n=plugin.nodes().nation(p);MilitaryState.Army a=commands.findArmy(n,armyName);
        if(n==null||a==null){openRoot(p);return;}
        Menu m=menu(p,a.name);
        boolean manage=commands.manageArmy(p,n,a);
        action(m,4,Material.IRON_SWORD,a.name,null,"Commander: "+plugin.nodes().citizenName(n,a.commanderUuid),"Muster: "+(a.musterTerritoryId==null?"Not assigned":"#"+a.musterTerritoryId));
        List<MilitaryState.Division> divisions=new ArrayList<>(a.divisions.values());
        divisions.sort(Comparator.comparing(d->d.name.toLowerCase(Locale.ROOT)));
        int start=page*CONTENT_SLOTS.length;
        for(int i=start;i<Math.min(divisions.size(),start+CONTENT_SLOTS.length);i++){
            MilitaryState.Division d=divisions.get(i);
            action(m,CONTENT_SLOTS[i-start],Material.IRON_HELMET,d.name,pl->openDivision(pl,a.name,d.name),
                d.type.toUpperCase(Locale.ROOT),d.members.size()+" members", "Order: "+d.order,"Click to inspect or enlist");
        }
        if(page>0)action(m,46,Material.ARROW,"Previous divisions",pl->openArmy(pl,a.name,page-1));
        if(start+CONTENT_SLOTS.length<divisions.size())action(m,52,Material.ARROW,"Next divisions",pl->openArmy(pl,a.name,page+1));
        action(m,45,Material.ARROW,"Back to nations",this::openRoot);
        action(m,47,Material.MAP,"Supply report",pl->{pl.closeInventory();run(pl,"report "+a.name);},"Shows depot reachability when Nodes-Extra is installed");
        if(manage){
            action(m,48,Material.COMPASS,"Set Muster Here",pl->{pl.closeInventory();run(pl,"muster "+a.name);},"Must be standing in land owned by your nation");
            action(m,49,Material.EMERALD,"Create Division",pl->openTypeSelection(pl,a.name),"Select a division type first");
            if(commands.manageNation(p,n))action(m,50,Material.NAME_TAG,"Appoint Army Commander",pl->prompt(pl,"army commander "+a.name+" %s",false,a.name,null),"Type an existing citizen name in chat");
            action(m,51,Material.BARRIER,"Disband Army",pl->confirm(pl,"Disband Army: "+a.name,"army disband "+a.name),"Permanent action — confirmation required");
        }
        open(m,p);
    }
    private void openTypeSelection(Player p,String army){
        Nation n=plugin.nodes().nation(p);MilitaryState.Army a=commands.findArmy(n,army);
        if(a==null||!commands.manageArmy(p,n,a)){MilitaryCommands.msg(p,"Leadership authority required");return;}
        Menu m=menu(p,"Division Type");
        action(m,4,Material.BOOK,"Select formation",null,"You will then type a division name in chat");
        int i=0;
        for(String type:List.of("infantry","cavalry","artillery","engineers","naval","reconnaissance")){
            action(m,CONTENT_SLOTS[i++],Material.IRON_HELMET,capital(type),pl->prompt(pl,"division create "+a.name+" %s "+type,false,a.name,null));
        }
        action(m,45,Material.ARROW,"Back",pl->openArmy(pl,a.name,0));open(m,p);
    }
    void openDivision(Player p,String armyName,String divName){
        Nation n=plugin.nodes().nation(p);MilitaryState.Army a=commands.findArmy(n,armyName);
        MilitaryState.Division d=commands.findDivision(a,divName);
        if(n==null||d==null){openRoot(p);return;}
        Menu m=menu(p,d.name);
        boolean enlisted=d.members.contains(p.getUniqueId().toString());
        boolean managing=commands.manageDivision(p,n,a,d);
        action(m,4,Material.IRON_HELMET,d.name+" · "+capital(d.type==null?"unknown":d.type),null,
            "Members: "+d.members.size()+" / "+plugin.maxMembers(),"Commander: "+plugin.nodes().citizenName(n,d.commanderUuid),
            "Order: "+d.order+(d.targetTerritoryId==null?"":" #"+d.targetTerritoryId));
        int slot=0;
        for(String uuid:d.members){
            if(slot>=CONTENT_SLOTS.length)break;
            String memberName=plugin.nodes().citizenName(n,uuid);
            action(m,CONTENT_SLOTS[slot++],Material.PLAYER_HEAD,memberName,managing?pl->openRankSelection(pl,a.name,d.name,uuid):null,
                "Rank: "+d.ranks.getOrDefault(uuid,"PRIVATE"),managing?"Click to adjust rank":"Roster member");
        }
        action(m,45,Material.ARROW,"Back to army",pl->openArmy(pl,a.name,0));
        if(enlisted)action(m,46,Material.REDSTONE,"Leave Division",pl->{pl.closeInventory();run(pl,"leave");},"You can re-enlist in another division");
        else action(m,46,Material.EMERALD,"Enlist",pl->{pl.closeInventory();run(pl,"enlist "+a.name+" "+d.name);},"Only one division at a time");
        action(m,47,Material.BOOK,"View roster",pl->{pl.closeInventory();run(pl,"division roster "+a.name+" "+d.name);});
        if(managing){
            action(m,48,Material.NAME_TAG,"Appoint Division Commander",pl->prompt(pl,"division commander "+a.name+" "+d.name+" %s",false,a.name,d.name),"Citizen must already be enlisted");
            action(m,49,Material.COMPASS,"Order: Hold",pl->prompt(pl,"order "+a.name+" "+d.name+" hold %s",true,a.name,d.name),"Enter an owned territory ID in chat");
            action(m,50,Material.MAP,"Order: Move",pl->prompt(pl,"order "+a.name+" "+d.name+" move %s",true,a.name,d.name),"Strategic record only; no teleporting");
            action(m,51,Material.ARROW,"Order: Retreat",pl->prompt(pl,"order "+a.name+" "+d.name+" retreat %s",true,a.name,d.name),"Strategic record only");
            if(commands.manageArmy(p,n,a))action(m,52,Material.BARRIER,"Disband Division",pl->confirm(pl,"Disband Division: "+d.name,"division disband "+a.name+" "+d.name),"Permanent action — confirmation required");
        }
        open(m,p);
    }
    private void openRankSelection(Player p,String army,String division,String uuid){
        Nation n=plugin.nodes().nation(p);MilitaryState.Army a=commands.findArmy(n,army);MilitaryState.Division d=commands.findDivision(a,division);
        if(d==null || !d.members.contains(uuid) || !commands.manageDivision(p,n,a,d)){MilitaryCommands.msg(p,"You cannot edit this rank");return;}
        String name=plugin.nodes().citizenName(n,uuid);
        if(name.equals("Unknown citizen")){MilitaryCommands.msg(p,"Citizen no longer belongs to this nation");return;}
        Menu m=menu(p,"Rank: "+name);
        action(m,4,Material.NAME_TAG,"Choose rank for "+name,null,"All changes are checked by military permissions");
        int i=0;
        for(String rank:MilitaryCommands.RANKS)action(m,CONTENT_SLOTS[i++],Material.GOLDEN_HELMET,capital(rank),pl->{pl.closeInventory();run(pl,"division rank "+a.name+" "+d.name+" "+name+" "+rank);},"Assign rank");
        action(m,45,Material.ARROW,"Back",pl->openDivision(pl,a.name,d.name));open(m,p);
    }
    private void confirm(Player p,String title,String command){
        Menu m=menu(p,"Confirm action");
        action(m,4,Material.BARRIER,title,null,"This removes existing saved military data");
        action(m,21,Material.LIME_WOOL,"Confirm",pl->{pl.closeInventory();run(pl,command);},"Cannot be undone without a backup");
        action(m,23,Material.RED_WOOL,"Cancel",this::openRoot);
        open(m,p);
    }
    private void prompt(Player p,String template,boolean number,String returnArmy,String returnDivision){
        Nation n=plugin.nodes().nation(p);
        if(n==null)return;
        pending.put(p.getUniqueId(),new Prompt(n.getUuid().toString(),template,number,System.currentTimeMillis()));
        p.closeInventory();MilitaryCommands.msg(p,"Type "+(number?"the territory ID":"a name")+" in chat, or type cancel. The message will not be broadcast.");
    }
    private void run(Player player,String subcommand){
        // All GUI actions are executed as the player; authoritative command checks re-run.
        Bukkit.dispatchCommand(player,"nmilitary "+subcommand);
    }
    private static String capital(String value){return value.isEmpty()?value:Character.toUpperCase(value.charAt(0))+value.substring(1);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false)
    public void click(InventoryClickEvent event){
        if(!(event.getView().getTopInventory().getHolder() instanceof Menu menu))return;
        event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player p))return;
        if(event.getRawSlot()<0||event.getRawSlot()>=menu.inventory.getSize())return;
        Nation n=plugin.nodes().nation(p);
        if(n==null || !menu.nationId.equals(n.getUuid().toString())){p.closeInventory();MilitaryCommands.msg(p,"Your nation has changed. Reopen the GUI.");return;}
        Action action=menu.slots.get(event.getRawSlot());
        if(action!=null)Bukkit.getScheduler().runTask(plugin,()->{if(p.isOnline())action.execute(p);});
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event){if(event.getView().getTopInventory().getHolder() instanceof Menu)event.setCancelled(true);}
    @EventHandler
    public void quit(PlayerQuitEvent event){pending.remove(event.getPlayer().getUniqueId());}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void chat(AsyncPlayerChatEvent event){
        Prompt prompt=pending.remove(event.getPlayer().getUniqueId());
        if(prompt==null)return;
        event.setCancelled(true);
        String value=event.getMessage().trim();
        if(value.equalsIgnoreCase("cancel")){event.getPlayer().sendMessage("§6[Nodes-Military] §7Cancelled");return;}
        if(System.currentTimeMillis()-prompt.createdAt>120_000){event.getPlayer().sendMessage("§c[Nodes-Military] Menu input timed out. Reopen the GUI.");return;}
        if(prompt.number ? !value.matches("[0-9]{1,9}") : !value.matches("[A-Za-z0-9_-]{2,24}")){
            event.getPlayer().sendMessage("§c[Nodes-Military] Invalid "+(prompt.number?"territory ID":"name")+". Reopen the GUI to try again.");return;
        }
        Bukkit.getScheduler().runTask(plugin,()->{
            Player p=event.getPlayer();if(!p.isOnline())return;
            Nation nation=plugin.nodes().nation(p);
            if(nation==null || !prompt.nationId.equals(nation.getUuid().toString())){MilitaryCommands.msg(p,"Nation changed; action cancelled");return;}
            run(p,prompt.template.replace("%s",value));
        });
    }
}
