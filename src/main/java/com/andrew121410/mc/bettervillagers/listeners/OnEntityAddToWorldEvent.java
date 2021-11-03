package com.andrew121410.mc.bettervillagers.listeners;

import com.andrew121410.mc.bettervillagers.BetterVillagers;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class OnEntityAddToWorldEvent implements Listener {

    private BetterVillagers plugin;

    public OnEntityAddToWorldEvent(BetterVillagers plugin) {
        this.plugin = plugin;
        this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
    }

    @EventHandler
    public void onEntityAddToWorld(EntityAddToWorldEvent event) {
        if (event.getEntity().getType() == EntityType.VILLAGER) {
            Villager villager = (Villager) event.getEntity();
            this.plugin.handleNewVillager(villager);
        }
    }
}
