package com.andrew121410.mc.bettervillagers.goals.farmer;

import com.andrew121410.mc.bettervillagers.BetterVillagers;
import com.destroystokyo.paper.entity.Pathfinder;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.craftbukkit.v1_17_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_17_R1.entity.CraftVillager;
import org.bukkit.entity.Villager;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class BetterFarmingGoal implements Goal<Villager> {

    public final static Map<UUID, List<Location>> currentlyTargetedBlocks = new HashMap<>();

    private BetterVillagers plugin;

    private final GoalKey<Villager> key;
    private final Villager bukkitVillager;

    private int coolDownTicks;
    private int ticks;

    //If still going after 30 seconds then just stop it; in ticks
    private int maximumTicks = 600;
    //After 1 minute you can run again; in ticks
    private int coolDownTimeTicks = 1200;

    private final net.minecraft.world.entity.npc.Villager minecraftVillager;
    private final ServerLevel serverLevel;

    private List<Block> blockList;
    private Block targetBlock;

    private Pathfinder.PathResult pathResult;

    public BetterFarmingGoal(BetterVillagers plugin, Villager villager) {
        this.plugin = plugin;
        this.key = GoalKey.of(Villager.class, new NamespacedKey(plugin, "better_farming"));
        this.bukkitVillager = villager;
        this.serverLevel = ((CraftWorld) bukkitVillager.getWorld()).getHandle();
        this.minecraftVillager = ((CraftVillager) bukkitVillager).getHandle();

        currentlyTargetedBlocks.put(this.bukkitVillager.getUniqueId(), new ArrayList<>());
    }

    @Override
    public boolean shouldActivate() {
        if (!this.bukkitVillager.getWorld().isDayTime()) return false;

        if (coolDownTicks > 0) {
            --coolDownTicks;
            return false;
        }
        this.blockList = BetterVillagers.getNearbyGrownWheat(bukkitVillager.getLocation(), 20);
        if (blockList.isEmpty()) {
            this.coolDownTicks = this.coolDownTimeTicks;
            return false;
        } else return true;
    }

    @Override
    public boolean shouldStayActive() {
        if (this.ticks > this.maximumTicks) return false;
        return !blockList.isEmpty();
    }

    @Override
    public void start() {
        this.findNewTargetBlockAndSetPath();
    }

    @Override
    public void stop() {
        this.coolDownTicks = this.coolDownTimeTicks;
        this.getCurrentlyTargetedBlocksList().clear();
        this.bukkitVillager.getPathfinder().stopPathfinding();
        this.ticks = 0;
    }

    @Override
    public void tick() {
        //Sometimes pathResult is null, because a path couldn't be calculated
        if (this.pathResult == null) {
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
            this.getCurrentlyTargetedBlocksList().remove(this.targetBlock.getLocation());
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
        if (this.blockList.isEmpty()) return;

        //Sort to get the nearest blocks first!
        this.blockList.sort(((o1, o2) -> {
            Location villagerLocation = this.bukkitVillager.getLocation();
            return (int) (o1.getLocation().distanceSquared(villagerLocation) - o2.getLocation().distanceSquared(villagerLocation));
        }));

        //Update the blockList because we don't want to target already targeted blocks by other farmer villagers
        List<Location> locationList = new ArrayList<>();
        currentlyTargetedBlocks.forEach((uuid, locations) -> locationList.addAll(locations));
        this.blockList = this.blockList.stream().filter(block -> !locationList.contains(block.getLocation())).collect(Collectors.toList());

        //Update the blockList because we don't want to target blocks that could already been harvested
        this.blockList = this.blockList.stream().filter(block -> {
            if (!(block.getBlockData() instanceof Ageable)) return false;
            Ageable ageable = (Ageable) block.getBlockData();
            return ageable.getAge() == ageable.getMaximumAge();
        }).collect(Collectors.toList());

        Optional<Block> blockOptional = this.blockList.stream().findFirst();
        if (blockOptional.isPresent()) {
            Block block = blockOptional.get();
            this.getCurrentlyTargetedBlocksList().add(block.getLocation());
            this.pathResult = bukkitVillager.getPathfinder().findPath(block.getLocation());
            this.targetBlock = block;
        }
    }

    private List<Location> getCurrentlyTargetedBlocksList() {
        return currentlyTargetedBlocks.get(this.bukkitVillager.getUniqueId());
    }
}