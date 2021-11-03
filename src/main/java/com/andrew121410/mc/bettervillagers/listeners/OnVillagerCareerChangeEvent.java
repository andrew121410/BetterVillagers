package com.andrew121410.mc.bettervillagers.listeners;

import com.andrew121410.mc.bettervillagers.BetterVillagers;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.VillagerCareerChangeEvent;

public class OnVillagerCareerChangeEvent implements Listener {

    private BetterVillagers plugin;

    public OnVillagerCareerChangeEvent(BetterVillagers plugin) {
        this.plugin = plugin;
        this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVillagerCareerChangeEvent(VillagerCareerChangeEvent event) {
        this.plugin.handleNewVillager(event.getEntity());
    }
}
