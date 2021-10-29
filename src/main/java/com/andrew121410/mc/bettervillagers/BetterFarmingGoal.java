package com.andrew121410.mc.bettervillagers;

import com.destroystokyo.paper.entity.Pathfinder;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_17_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_17_R1.entity.CraftVillager;
import org.bukkit.entity.Villager;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public class BetterFarmingGoal implements Goal<Villager> {

    private BetterVillagers plugin;

    private final GoalKey<Villager> key;
    private final Villager bukkitVillager;

    private int coolDownTicks;
    private int pathTicks;

    private final net.minecraft.world.entity.npc.Villager minecraftVillager;
    private final ServerLevel serverLevel;

    private List<Block> blockList;
    private Block targetBlock;

    public BetterFarmingGoal(BetterVillagers plugin, Villager villager) {
        this.plugin = plugin;
        this.key = GoalKey.of(Villager.class, new NamespacedKey(plugin, "better_farming"));
        this.bukkitVillager = villager;
        this.serverLevel = ((CraftWorld) bukkitVillager.getWorld()).getHandle();
        this.minecraftVillager = ((CraftVillager) bukkitVillager).getHandle();
    }

    @Override
    public boolean shouldActivate() {
        if (coolDownTicks > 0) {
            --coolDownTicks;
            return false;
        }
        this.blockList = BetterVillagers.getNearbyGrownWheat(bukkitVillager.getLocation(), 20);
        if (!blockList.isEmpty()) {
            Bukkit.broadcastMessage("ShouldActivate = true 2");
            return true;
        }
        return false;
    }

    @Override
    public boolean shouldStayActive() {
        return !blockList.isEmpty();
    }

    @Override
    public void start() {
        this.findNewTargetBlockAndSetPath();
    }

    @Override
    public void stop() {
        bukkitVillager.getPathfinder().stopPathfinding();
        coolDownTicks = 100;
    }

    private Pathfinder.PathResult pathResult;

    @Override
    public void tick() {
        if (this.pathResult == null) {
            Bukkit.broadcastMessage("this.pathResult == null");
            this.blockList.clear();
            return;
        }

        if (this.pathResult.getNextPoint() != null) {
            Bukkit.broadcastMessage("If");
            bukkitVillager.getPathfinder().moveTo(this.pathResult, 1F);
        } else {
            Bukkit.broadcastMessage("Else");
            List<Block> radiusBlock = BetterVillagers.getNearbyGrownWheat(bukkitVillager.getLocation(), 1);
            if (!radiusBlock.isEmpty()) {
                Iterator<Block> iterator = radiusBlock.iterator();
                while (iterator.hasNext()) {
                    Block wheatBlock = iterator.next();
                    this.serverLevel.destroyBlock(new BlockPos(wheatBlock.getX(), wheatBlock.getY(), wheatBlock.getZ()), true, minecraftVillager);
                    this.serverLevel.setBlock(new BlockPos(wheatBlock.getX(), wheatBlock.getY(), wheatBlock.getZ()), Blocks.WHEAT.defaultBlockState(), 3);
                    iterator.remove();
                }
            }
            this.blockList.remove(this.targetBlock);
            this.findNewTargetBlockAndSetPath();
        }
    }

    @Override
    public @NotNull GoalKey<Villager> getKey() {
        return this.key;
    }

    @Override
    public @NotNull EnumSet<GoalType> getTypes() {
        return EnumSet.of(GoalType.MOVE, GoalType.LOOK);
    }

    private void findNewTargetBlockAndSetPath() {
        Optional<Block> blockOptional = this.blockList.stream().findAny();
        if (blockOptional.isPresent()) {
            Block block = blockOptional.get();
            this.pathResult = bukkitVillager.getPathfinder().findPath(block.getLocation());
            this.targetBlock = block;
        }
    }
}
