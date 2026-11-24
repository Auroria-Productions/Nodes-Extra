package com.auroria.nodesmilitary;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import java.util.*;

/** Wraps the current /nodes executor without stealing other namespaces from MilzzyNodes or Nodes-Extra. */
final class NodesCommandHook implements CommandExecutor,TabCompleter {
    private final PluginCommand command;
    private final CommandExecutor previous;
    private final TabCompleter previousComplete;
    private final MilitaryCommands military;
    private NodesCommandHook(PluginCommand command,MilitaryCommands military){
        this.command=command;this.previous=command.getExecutor();this.previousComplete=command.getTabCompleter();this.military=military;
    }
    static NodesCommandHook install(MilitaryPlugin plugin,MilitaryCommands commands){
        PluginCommand command=Bukkit.getPluginCommand("nodes");
        if(command==null || command.getPlugin()==plugin || !command.getPlugin().getName().equalsIgnoreCase("milzzynodes")){
            plugin.getLogger().warning("/nodes command bridge unavailable; /nmilitary remains usable");return null;
        }
        NodesCommandHook hook=new NodesCommandHook(command,commands);
        command.setExecutor(hook);command.setTabCompleter(hook);
        return hook;
    }
    void uninstall(){if(command.getExecutor()==this)command.setExecutor(previous);if(command.getTabCompleter()==this)command.setTabCompleter(previousComplete);}
    @Override public boolean onCommand(CommandSender sender,Command command,String alias,String[] args){
        if(args.length>0 && args[0].equalsIgnoreCase("military"))
            return military.onCommand(sender,command,"nodes military",Arrays.copyOfRange(args,1,args.length));
        return previous==null || previous.onCommand(sender,command,alias,args);
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(args.length>0 && args[0].equalsIgnoreCase("military"))
            return military.onTabComplete(sender,command,alias,Arrays.copyOfRange(args,1,args.length));
        List<String> prior=previousComplete==null?List.of():previousComplete.onTabComplete(sender,command,alias,args);
        List<String> result=new ArrayList<>(prior==null?List.of():prior);
        if(args.length==1 && "military".startsWith(args[0].toLowerCase(Locale.ROOT)) && !result.contains("military"))result.add("military");
        return result;
    }
}
