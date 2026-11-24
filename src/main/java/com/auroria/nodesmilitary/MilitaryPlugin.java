package com.auroria.nodesmilitary;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;

/** Nodes-Military v1.1.0; replacement for v1.0.0 with compatible data schema and in-game GUIs. */
public final class MilitaryPlugin extends JavaPlugin {
    private MilitaryState.State state;
    private NodesBridge nodes;
    private ExtraBridge extra;
    private MilitaryGui gui;
    private NodesCommandHook hook;
    private BukkitTask task;
    private Path statePath,publicPath;
    @Override public void onEnable(){
        if(!Bukkit.getPluginManager().isPluginEnabled("milzzynodes")){
            getLogger().severe("milzzynodes is required");Bukkit.getPluginManager().disablePlugin(this);return;
        }
        saveDefaultConfig();
        try {
            nodes=new NodesBridge();
            statePath=safePath(getConfig().getString("storage.stateFilename","state.json"));
            publicPath=safePath(getConfig().getString("storage.publicFilename","public.json"));
            state=MilitaryStorage.load(statePath);
        }catch(Exception ex){
            getLogger().log(Level.SEVERE,"Cannot load Nodes-Military state; original file will not be overwritten",ex);
            Bukkit.getPluginManager().disablePlugin(this);return;
        }
        extra=new ExtraBridge(this);
        MilitaryCommands commands=new MilitaryCommands(this);
        Objects.requireNonNull(getCommand("nmilitary"),"nmilitary missing in plugin.yml").setExecutor(commands);
        Objects.requireNonNull(getCommand("nmilitary")).setTabCompleter(commands);
        gui=new MilitaryGui(this);
        Bukkit.getPluginManager().registerEvents(gui,this);
        hook=NodesCommandHook.install(this,commands);
        long seconds=Math.max(20,Math.min(3600,getConfig().getLong("storage.saveIntervalSeconds",120)));
        task=Bukkit.getScheduler().runTaskTimer(this,this::persist,seconds*20L,seconds*20L);
        getLogger().info("Nodes-Military v1.1.0 enabled; armies="+state.armies.size()+", GUI enabled="+getConfig().getBoolean("gui.enabled",true)+", Nodes-Extra optional");
    }
    @Override public void onDisable(){
        if(task!=null)task.cancel();
        if(hook!=null)hook.uninstall();
        if(gui!=null)gui.shutdown();
        if(state!=null && nodes!=null)persist();
    }
    private Path safePath(String configured){
        String name=configured==null?"":configured.trim();
        if(!name.matches("[A-Za-z0-9_.-]+\\.json") || name.equals(".."))throw new IllegalArgumentException("Invalid state filename: "+configured);
        return getDataFolder().toPath().resolve(name);
    }
    synchronized void persist(){
        if(state==null)return;
        try {
            MilitaryStorage.save(statePath,state);
            if(getConfig().getBoolean("map.exportPublicData",true))MilitaryStorage.export(publicPath,state,nodes);
        }catch(IOException ex){getLogger().log(Level.SEVERE,"Failed to save Nodes-Military; state remains in memory",ex);}
    }
    void audit(String message){
        state.history.add(System.currentTimeMillis()+" "+message);
        if(state.history.size()>300)state.history.subList(0,state.history.size()-300).clear();
        persist();
    }
    MilitaryState.State state(){return state;}
    NodesBridge nodes(){return nodes;}
    ExtraBridge extra(){return extra;}
    MilitaryGui gui(){return gui;}
    int maxArmies(){return bound("military.maxArmiesPerNation",6,1,50);}
    int maxDivisions(){return bound("military.maxDivisionsPerArmy",8,1,100);}
    int maxMembers(){return bound("military.maxMembersPerDivision",50,1,500);}
    int maxHops(){return bound("military.supply.maxTerritoryHops",12,0,128);}
    private int bound(String setting,int fallback,int low,int high){return Math.max(low,Math.min(high,getConfig().getInt(setting,fallback)));}
    boolean requireMarshal(){return getConfig().getBoolean("military.requireMarshalAuthority",true);}
}
