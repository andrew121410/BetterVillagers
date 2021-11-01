package com.andrew121410.mc.bettervillagers.listeners;

import com.andrew121410.mc.bettervillagers.BetterVillagers;
import com.andrew121410.mc.bettervillagers.goals.farmer.BetterFarmingGoal;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public class OnEntityDeathEvent implements Listener {

    private BetterVillagers plugin;

    public OnEntityDeathEvent(BetterVillagers plugin) {
        this.plugin = plugin;
        this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
    }

    @EventHandler
    public void onEntityKilledEvent(EntityDeathEvent event) {
        if (event.getEntity().getType() == EntityType.VILLAGER) {
            BetterFarmingGoal.currentlyTargetedBlocks.remove(event.getEntity().getUniqueId());
        }
    }
}
