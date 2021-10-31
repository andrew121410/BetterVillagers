package com.andrew121410.mc.bettervillagers.goals.farmer;

import com.andrew121410.mc.bettervillagers.BetterVillagers;
import com.andrew121410.mc.world16utils.World16Utils;
import com.andrew121410.mc.world16utils.blocks.MarkerColor;
import com.destroystokyo.paper.entity.Pathfinder;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_17_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_17_R1.entity.CraftVillager;
import org.bukkit.entity.Villager;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

public class BetterFarmingGoal implements Goal<Villager> {

    private BetterVillagers plugin;

    private final GoalKey<Villager> key;
    private final Villager bukkitVillager;

    private int coolDownTicks;
    private int ticks;

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
        if (!this.bukkitVillager.getWorld().isDayTime()) return false;

        if (coolDownTicks > 0) {
            --coolDownTicks;
            return false;
        }
        this.blockList = BetterVillagers.getNearbyGrownWheat(bukkitVillager.getLocation(), 20);
        if (!blockList.isEmpty()) {
            Bukkit.broadcastMessage("ShouldActivate = true"); //DEBUG
            return true;
        }
        return false;
    }

    @Override
    public boolean shouldStayActive() {
        //30 seconds
        if (ticks > 600) {
            Bukkit.broadcastMessage("Times up; " + this.ticks); //DEBUG
            return false;
        }
        return !blockList.isEmpty();
    }

    @Override
    public void start() {
        this.findNewTargetBlockAndSetPath();
    }

    @Override
    public void stop() {
        Bukkit.broadcastMessage("stop()"); //DEBUG
        //20 second delay
        this.coolDownTicks = 400;
        this.bukkitVillager.getPathfinder().stopPathfinding();
        this.ticks = 0;
        World16Utils.getInstance().getClassWrappers().getPackets().sendDebugGameTestClearPacket(this.bukkitVillager.getWorld()); //DEBUG
    }

    private Pathfinder.PathResult pathResult;

    @Override
    public void tick() {
        //Sometimes pathResult is null, because a path couldn't be calculated
        if (this.pathResult == null) {
            Bukkit.broadcastMessage("this.pathResult is null..."); //DEBUG
            this.blockList.clear();
            return;
        }
        ticks++;

        if (this.pathResult.getNextPoint() != null) {
            bukkitVillager.getPathfinder().moveTo(this.pathResult, 0.8F);
        } else {
            List<Block> radiusBlock = BetterVillagers.getNearbyGrownWheat(bukkitVillager.getLocation(), 1);
            if (!radiusBlock.isEmpty()) {
                for (Block wheatBlock : radiusBlock) {
                    this.serverLevel.destroyBlock(new BlockPos(wheatBlock.getX(), wheatBlock.getY(), wheatBlock.getZ()), true, minecraftVillager);
                    this.serverLevel.setBlock(new BlockPos(wheatBlock.getX(), wheatBlock.getY(), wheatBlock.getZ()), Blocks.WHEAT.defaultBlockState(), 3);
                }
                this.blockList.removeAll(radiusBlock);
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
        return EnumSet.of(GoalType.MOVE, GoalType.TARGET, GoalType.LOOK);
    }

    private void findNewTargetBlockAndSetPath() {
        World16Utils.getInstance().getClassWrappers().getPackets().sendDebugGameTestClearPacket(this.bukkitVillager.getWorld()); //DEBUG
        //Sort to get the nearest blocks first!
        this.blockList.sort(((o1, o2) -> {
            Location villagerLocation = this.bukkitVillager.getLocation();
            return (int) (o1.getLocation().distanceSquared(villagerLocation) - o2.getLocation().distanceSquared(villagerLocation));
        }));
        Optional<Block> blockOptional = this.blockList.stream().findFirst();
        if (blockOptional.isPresent()) {
            Block block = blockOptional.get();
            this.pathResult = bukkitVillager.getPathfinder().findPath(block.getLocation());
            this.targetBlock = block;
            World16Utils.getInstance().getClassWrappers().getPackets().sendDebugCreateMarkerPacket(this.bukkitVillager.getWorld(), block.getLocation(), MarkerColor.GREEN, "TargetBlock"); //DEBUG
        }
    }
}
