package com.andrew121410.mc.bettervillagers.listeners;

import com.andrew121410.mc.bettervillagers.BetterFarmingGoal;
import com.andrew121410.mc.bettervillagers.BetterVillagers;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.VillagerGoalPackages;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.libs.org.codehaus.plexus.util.ReflectionUtils;
import org.bukkit.craftbukkit.v1_17_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_17_R1.entity.CraftVillager;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class OnVillagerCareerChangeEvent implements Listener {

    private BetterVillagers plugin;

    public OnVillagerCareerChangeEvent(BetterVillagers plugin) {
        this.plugin = plugin;
        this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVillagerCareerChangeEvent(VillagerCareerChangeEvent event) {
        if (event.getProfession() == Villager.Profession.FARMER) {
            hijackBrain(event.getEntity());
        }
    }

    private void hijackBrain(Villager bukkitVillager) {
        net.minecraft.world.entity.npc.Villager minecraftVillager = ((CraftVillager) bukkitVillager).getHandle();
        ServerLevel serverLevel = ((CraftWorld) bukkitVillager.getWorld()).getHandle();

        new BukkitRunnable() {
            @Override
            public void run() {
                minecraftVillager.getBrain().stopAll(serverLevel, minecraftVillager);
                try {
                    ReflectionUtils.setVariableValueInObject(minecraftVillager, "bD", minecraftVillager.getBrain().copyWithoutBehaviors());
                    Bukkit.getServer().broadcastMessage("We hijacked: " + bukkitVillager.getEntityId());
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } finally {
                    registerBrainGoals(minecraftVillager, serverLevel);
                    Bukkit.getMobGoals().addGoal(bukkitVillager, 2, new BetterFarmingGoal(plugin, bukkitVillager));
                }
            }
        }.runTaskLater(this.plugin, 40L);
    }

    private void registerBrainGoals(net.minecraft.world.entity.npc.Villager minecraftVillager, ServerLevel serverLevel) {
        Brain<net.minecraft.world.entity.npc.Villager> brain = minecraftVillager.getBrain();
        VillagerProfession villagerprofession = minecraftVillager.getVillagerData().getProfession();

        brain.setSchedule(Schedule.VILLAGER_DEFAULT);
        brain.addActivityWithConditions(Activity.WORK, VillagerGoalPackages.getWorkPackage(villagerprofession, 0.5F), ImmutableSet.of(Pair.of(MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT)));

        brain.addActivity(Activity.CORE, VillagerGoalPackages.getCorePackage(villagerprofession, 0.5F));
        brain.addActivityWithConditions(Activity.MEET, VillagerGoalPackages.getMeetPackage(villagerprofession, 0.5F), ImmutableSet.of(Pair.of(MemoryModuleType.MEETING_POINT, MemoryStatus.VALUE_PRESENT)));
        brain.addActivity(Activity.REST, VillagerGoalPackages.getRestPackage(villagerprofession, 0.5F));
        brain.addActivity(Activity.IDLE, VillagerGoalPackages.getIdlePackage(villagerprofession, 0.5F));
        brain.addActivity(Activity.PANIC, VillagerGoalPackages.getPanicPackage(villagerprofession, 0.5F));
        brain.addActivity(Activity.PRE_RAID, VillagerGoalPackages.getPreRaidPackage(villagerprofession, 0.5F));
        brain.addActivity(Activity.RAID, VillagerGoalPackages.getRaidPackage(villagerprofession, 0.5F));
        brain.addActivity(Activity.HIDE, VillagerGoalPackages.getHidePackage(villagerprofession, 0.5F));

        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.WORK);
        brain.setActiveActivityIfPossible(Activity.WORK);
        brain.updateActivityFromSchedule(serverLevel.getDayTime(), serverLevel.getGameTime());
    }
}
