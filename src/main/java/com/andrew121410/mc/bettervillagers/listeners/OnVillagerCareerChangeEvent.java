package com.andrew121410.mc.bettervillagers.listeners;

import com.andrew121410.mc.bettervillagers.BetterVillagers;
import org.bukkit.Bukkit;
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
        if (event.getReason() == VillagerCareerChangeEvent.ChangeReason.LOSING_JOB) {
            Bukkit.broadcastMessage("Villager lost their job.");
            this.plugin.removeAllOfOurGoals(event.getEntity());
            return;
        }

        //If the past profession was nothing then handle the villager
        if (event.getEntity().getProfession() == Villager.Profession.NONE) {
            this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> this.plugin.handleNewVillager(event.getEntity()), 20L);
        }
    }
}
