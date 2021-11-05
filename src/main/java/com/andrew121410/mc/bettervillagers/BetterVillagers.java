package com.andrew121410.mc.bettervillagers;

import com.andrew121410.mc.bettervillagers.goals.farmer.BetterFarmingGoal;
import com.andrew121410.mc.bettervillagers.listeners.OnEntityAddToWorldEvent;
import com.andrew121410.mc.bettervillagers.listeners.OnEntityDeathEvent;
import com.andrew121410.mc.bettervillagers.listeners.OnVillagerCareerChangeEvent;
import com.andrew121410.mc.world16utils.blocks.UniversalBlockUtils;
import com.destroystokyo.paper.entity.ai.GoalKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.stream.Collectors;

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

    public void removeAllOfOurGoals(Villager villager) {
        NamespacedKey betterFarmingKey = NamespacedKey.fromString("better_farming", this);
        if (betterFarmingKey != null) {
            Bukkit.getMobGoals().removeGoal(villager, GoalKey.of(Villager.class, betterFarmingKey));
        }
    }

    public void handleNewVillager(Villager villager) {
        hijackBrain(villager);
    }

    private void hijackBrain(Villager bukkitVillager) {
        removeAllOfOurGoals(bukkitVillager);

//        net.minecraft.world.entity.npc.Villager minecraftVillager = ((CraftVillager) bukkitVillager).getHandle();
//        ServerLevel serverLevel = ((CraftWorld) bukkitVillager.getWorld()).getHandle();
//
//        new BukkitRunnable() {
//            @Override
//            public void run() {
//                minecraftVillager.getBrain().stopAll(serverLevel, minecraftVillager);
//                try {
//                    ReflectionUtils.setVariableValueInObject(minecraftVillager, "bD", minecraftVillager.getBrain().copyWithoutBehaviors());
//                    Bukkit.getServer().broadcastMessage("We hijacked: " + bukkitVillager.getEntityId());
//                } catch (IllegalAccessException e) {
//                    e.printStackTrace();
//                } finally {
//                    registerModifiedBrain(minecraftVillager, serverLevel);
//                    handleCustomGoals(bukkitVillager);
//                }
//            }
//        }.runTaskLater(this, 40L);
        handleCustomGoals(bukkitVillager);
    }

//    private void registerModifiedBrain(net.minecraft.world.entity.npc.Villager minecraftVillager, ServerLevel serverLevel) {
//        Brain<net.minecraft.world.entity.npc.Villager> brain = minecraftVillager.getBrain();
//        VillagerProfession villagerprofession = minecraftVillager.getVillagerData().getProfession();
//
//        Method method;
//        try {
//            method = Schedule.class.getDeclaredMethod("a", String.class);
//            method.setAccessible(true);
//            ScheduleBuilder scheduleBuilder = (ScheduleBuilder) method.invoke(null, "bettervillagers");
//            scheduleBuilder.changeActivityAt(10, Activity.IDLE).changeActivityAt(12000, Activity.REST);
//            brain.setSchedule(scheduleBuilder.build());
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//
//        brain.addActivity(Activity.CORE, VillagerGoalPackages.getCorePackage(villagerprofession, 0.5F));
//        brain.addActivityWithConditions(Activity.MEET, VillagerGoalPackages.getMeetPackage(villagerprofession, 0.5F), ImmutableSet.of(Pair.of(MemoryModuleType.MEETING_POINT, MemoryStatus.VALUE_PRESENT)));
//        brain.addActivity(Activity.REST, VillagerGoalPackages.getRestPackage(villagerprofession, 0.5F));
//        brain.addActivity(Activity.IDLE, VillagerGoalPackages.getIdlePackage(villagerprofession, 0.5F));
//        brain.addActivity(Activity.PANIC, VillagerGoalPackages.getPanicPackage(villagerprofession, 0.5F));
//        brain.addActivity(Activity.PRE_RAID, VillagerGoalPackages.getPreRaidPackage(villagerprofession, 0.5F));
//        brain.addActivity(Activity.RAID, VillagerGoalPackages.getRaidPackage(villagerprofession, 0.5F));
//        brain.addActivity(Activity.HIDE, VillagerGoalPackages.getHidePackage(villagerprofession, 0.5F));
//
//        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
//        brain.setDefaultActivity(Activity.IDLE);
//        brain.setActiveActivityIfPossible(Activity.IDLE);
//        brain.updateActivityFromSchedule(serverLevel.getDayTime(), serverLevel.getGameTime());
//    }

    private void handleCustomGoals(Villager villager) {
        switch (villager.getProfession()) {
            case FARMER:
                Bukkit.getMobGoals().addGoal(villager, 2, new BetterFarmingGoal(this, villager));
        }
    }

    public List<Block> getNearbyGrownWheat(Location location, int radius) {
        return UniversalBlockUtils.getNearbyBlocks(location, radius).stream().filter(block -> {
            if (!(block.getBlockData() instanceof Ageable)) return false;
            Ageable ageable = (Ageable) block.getBlockData();
            return block.getType() == Material.WHEAT && ageable.getAge() == ageable.getMaximumAge();
        }).collect(Collectors.toList());
    }
}
