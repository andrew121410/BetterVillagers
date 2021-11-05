package com.andrew121410.mc.bettervillagers.listeners;

import com.andrew121410.mc.bettervillagers.BetterVillagers;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class OnVillagerCareerChangeEvent implements Listener {

    private BetterVillagers plugin;

    public OnVillagerCareerChangeEvent(BetterVillagers plugin) {
        this.plugin = plugin;
        this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
    }

    @EventHandler
    public void onVillagerCareerChangeEvent(VillagerCareerChangeEvent event) {
        if (event.getProfession() == Villager.Profession.NONE) {
            this.plugin.removeAllOfOurGoals(event.getEntity());
            return;
        }

        //If the past profession was nothing then handle the villager
        if (event.getEntity().getProfession() == Villager.Profession.NONE) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    plugin.handleNewVillager(event.getEntity());
                }
            }.runTaskLater(this.plugin, 20L);
        }
    }
}
