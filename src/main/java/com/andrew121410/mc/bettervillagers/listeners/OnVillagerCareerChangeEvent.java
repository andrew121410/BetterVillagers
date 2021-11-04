package com.andrew121410.mc.bettervillagers.listeners;

import com.andrew121410.mc.bettervillagers.BetterVillagers;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.VillagerCareerChangeEvent;

public class OnVillagerCareerChangeEvent implements Listener {

    private BetterVillagers plugin;

    public OnVillagerCareerChangeEvent(BetterVillagers plugin) {
        this.plugin = plugin;
        this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
    }

    @EventHandler
    public void onVillagerCareerChangeEvent(VillagerCareerChangeEvent event) {
        if (event.getEntity().getProfession() == Villager.Profession.NONE) {
            this.plugin.handleNewVillager(event.getEntity());
        }
    }
}
