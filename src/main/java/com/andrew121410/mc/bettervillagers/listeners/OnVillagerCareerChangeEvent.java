package com.andrew121410.mc.bettervillagers.listeners;

import com.andrew121410.mc.bettervillagers.BetterFarmingGoal;
import com.andrew121410.mc.bettervillagers.BetterVillagers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.ScheduleBuilder;
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

import java.lang.reflect.Method;
import java.util.Optional;

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
                    registerModifiedBrain(minecraftVillager, serverLevel);
                    Bukkit.getMobGoals().addGoal(bukkitVillager, 2, new BetterFarmingGoal(plugin, bukkitVillager));
                }
            }
        }.runTaskLater(this.plugin, 40L);
    }

    private void registerModifiedBrain(net.minecraft.world.entity.npc.Villager minecraftVillager, ServerLevel serverLevel) {
        Brain<net.minecraft.world.entity.npc.Villager> brain = minecraftVillager.getBrain();
        VillagerProfession villagerprofession = minecraftVillager.getVillagerData().getProfession();

        Method method;
        try {
            method = Schedule.class.getDeclaredMethod("a", String.class);
            method.setAccessible(true);
            ScheduleBuilder scheduleBuilder = (ScheduleBuilder) method.invoke(null, "bettervillagers");
            scheduleBuilder.changeActivityAt(10, Activity.IDLE).changeActivityAt(12000, Activity.REST);
            brain.setSchedule(scheduleBuilder.build());
        } catch (Exception e) {
            e.printStackTrace();
        }

        brain.addActivity(Activity.CORE, this.getCustomCorePackage(villagerprofession, 0.5F));
        brain.addActivity(Activity.REST, VillagerGoalPackages.getRestPackage(villagerprofession, 0.5F));
        brain.addActivity(Activity.IDLE, VillagerGoalPackages.getIdlePackage(villagerprofession, 0.5F));

        brain.addActivity(Activity.PANIC, VillagerGoalPackages.getPanicPackage(villagerprofession, 0.5F));

        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.setActiveActivityIfPossible(Activity.IDLE);
        brain.updateActivityFromSchedule(serverLevel.getDayTime(), serverLevel.getGameTime());
    }

    //Unchecked but oh well
    public ImmutableList getCustomCorePackage(VillagerProfession var0, float var1) {
        return ImmutableList.of(
                Pair.of(0, new Swim(0.8F)),
                Pair.of(0, new InteractWithDoor()),
                Pair.of(0, new LookAtTargetSink(45, 90)),
                Pair.of(0, new VillagerPanicTrigger()),
                Pair.of(0, new WakeUp()),
                Pair.of(0, new ReactToBell()),
                Pair.of(0, new SetRaidStatus()),
                Pair.of(1, new MoveToTargetSink()),
                Pair.of(3, new LookAndFollowTradingPlayerSink(var1)),
                Pair.of(5, new GoToWantedItem(var1, false, 4)),
                Pair.of(7, new GoToPotentialJobSite(var1)),
                Pair.of(8, new YieldJobSite(var1)),
                Pair.of(10, new AcquirePoi(PoiType.HOME, MemoryModuleType.HOME, false, Optional.of((byte) 14))),
                Pair.of(10, new AcquirePoi(PoiType.MEETING, MemoryModuleType.MEETING_POINT, true, Optional.of((byte) 14))),
                Pair.of(10, new AssignProfessionFromJobSite()), Pair.of(10, new ResetProfession()));
    }
}
