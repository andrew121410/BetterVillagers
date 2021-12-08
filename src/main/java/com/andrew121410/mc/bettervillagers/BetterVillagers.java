package com.andrew121410.mc.bettervillagers;

import com.andrew121410.mc.bettervillagers.goals.farmer.BetterFarmingGoal;
import com.andrew121410.mc.bettervillagers.listeners.OnEntityAddToWorldEvent;
import com.andrew121410.mc.bettervillagers.listeners.OnEntityDeathEvent;
import com.andrew121410.mc.bettervillagers.listeners.OnVillagerCareerChangeEvent;
import com.destroystokyo.paper.entity.ai.GoalKey;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public final class BetterVillagers extends JavaPlugin {

    @Override
    public void onEnable() {
        registerListeners();
    }

    private void registerListeners() {
        new OnVillagerCareerChangeEvent(this);
        new OnEntityDeathEvent(this);
        new OnEntityAddToWorldEvent(this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public void handleNewVillager(Villager villager) {
        hijackBrain(villager);
    }

    private void hijackBrain(Villager bukkitVillager) {
        removeAllOfOurGoals(bukkitVillager);
        handleCustomGoals(bukkitVillager);
    }

    private void handleCustomGoals(Villager villager) {
        switch (villager.getProfession()) {
            case FARMER -> Bukkit.getMobGoals().addGoal(villager, 2, new BetterFarmingGoal(this, villager));
        }
    }

    public void removeAllOfOurGoals(Villager villager) {
        NamespacedKey betterFarmingKey = NamespacedKey.fromString("better_farming", this);
        if (betterFarmingKey != null) {
            Bukkit.getMobGoals().removeGoal(villager, GoalKey.of(Villager.class, betterFarmingKey));
        }
        removeCustomDataForGoals(villager.getUniqueId());
    }

    public void removeCustomDataForGoals(UUID uuid) {
        BetterFarmingGoal.currentlyTargetedBlocks.remove(uuid);
    }
}
