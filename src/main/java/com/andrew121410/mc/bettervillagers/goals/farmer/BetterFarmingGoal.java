package com.andrew121410.mc.bettervillagers.goals.farmer;

import com.andrew121410.mc.bettervillagers.BetterVillagers;
import com.andrew121410.mc.world16utils.blocks.UniversalBlockUtils;
import com.destroystokyo.paper.entity.Pathfinder;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Directional;
import org.bukkit.craftbukkit.v1_17_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_17_R1.entity.CraftVillager;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
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

    private boolean hasChest;
    private boolean needsToUnload;
    private Block chestBlock;

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
        this.blockList = this.plugin.getNearbyGrownWheat(bukkitVillager.getLocation(), 20);
        if (blockList.isEmpty()) {
            this.coolDownTicks = this.coolDownTimeTicks;
            return false;
        } else return true;
    }

    @Override
    public boolean shouldStayActive() {
        if (this.ticks > this.maximumTicks) return false;
        return !this.blockList.isEmpty() || this.needsToUnload;
    }

    @Override
    public void start() {
        List<Block> potentialChests = UniversalBlockUtils.getNearbyBlocks(this.bukkitVillager.getLocation(), 50).stream().filter(block -> block.getType() == Material.CHEST).collect(Collectors.toList());

        potentialChests = potentialChests.stream().filter(block -> {
            if (block.getState() instanceof Chest) {
                Directional directional = (Directional) block.getBlockData();
                Block inFront = block.getRelative(directional.getFacing());
                if (inFront.getState() instanceof Sign) {
                    Sign sign = (Sign) inFront.getState();
                    return sign.getLine(0).equalsIgnoreCase("BetterFarmer");
                }
            }
            return false;
        }).collect(Collectors.toList());

        this.chestBlock = null;
        this.hasChest = false;
        this.needsToUnload = false;

        if (!potentialChests.isEmpty()) {
            this.chestBlock = potentialChests.stream().findFirst().get();
            this.hasChest = true;
            this.needsToUnload = true;
        }

        this.findNewTargetBlockAndSetPath();
    }

    @Override
    public void stop() {
        this.coolDownTicks = this.coolDownTimeTicks;
        this.runChestUnloadOnce = false;
        this.getCurrentlyTargetedBlocksList().clear();
        this.bukkitVillager.getPathfinder().stopPathfinding();
        this.ticks = 0;
    }

    private boolean runChestUnloadOnce;

    @Override
    public void tick() {
        //Sometimes pathResult is null, because a path couldn't be calculated
        if (this.pathResult == null) {
            this.blockList.clear();
            this.needsToUnload = false;
            return;
        }
        ticks++;

        if (this.pathResult.getNextPoint() != null) {
            //Look at the target if less than 6 blocks away
            Location finalPoint = this.pathResult.getFinalPoint();
            if (finalPoint != null)
                if (this.bukkitVillager.getLocation().distanceSquared(finalPoint) < 36) //36 is 6 blocks
                    this.minecraftVillager.getLookControl().setLookAt(new Vec3(finalPoint.getBlockX(), finalPoint.getBlockY(), finalPoint.getBlockZ()));
            //This should actually only be called once. But since we still have the vanilla goals;
            // We have to call this every tick or else it will go like halfway then do something else
            bukkitVillager.getPathfinder().moveTo(this.pathResult, 0.8F);
        } else {
            if (this.blockList.isEmpty() && this.hasChest && this.needsToUnload && !this.runChestUnloadOnce) {
                this.runChestUnloadOnce = true;
                Inventory villagerInventory = this.bukkitVillager.getInventory();

                BlockState blockState = this.chestBlock.getState();
                if (blockState instanceof Chest) {
                    Chest chestState = (Chest) blockState;
                    List<ItemStack> toBeRemoved = new ArrayList<>();
                    for (ItemStack itemStack : villagerInventory) {
                        if (itemStack == null) continue;
                        chestState.getInventory().addItem(itemStack);
                        toBeRemoved.add(itemStack);
                    }
                    for (ItemStack itemStack : toBeRemoved) if (itemStack != null) villagerInventory.remove(itemStack);
                }
                this.needsToUnload = false;
                return;
            }
            List<Block> radiusBlock = this.plugin.getNearbyGrownWheat(bukkitVillager.getLocation(), 1);
            if (!radiusBlock.isEmpty()) {
                for (Block wheatBlock : radiusBlock) {
                    this.serverLevel.destroyBlock(new BlockPos(wheatBlock.getX(), wheatBlock.getY(), wheatBlock.getZ()), false, minecraftVillager);
                    List<ItemStack> itemStacks = getDropsForWheat(true, 1);
                    for (ItemStack itemStack : itemStacks) this.bukkitVillager.getInventory().addItem(itemStack);
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
        //If done harvesting set the path to the chest.
        if (this.blockList.isEmpty() && this.hasChest) {
            List<Block> potentialPaths = UniversalBlockUtils.getNearbyBlocks(this.chestBlock.getLocation(), 1, false).stream().filter(block -> !block.isSolid() || Tag.SIGNS.isTagged(block.getType())).sorted(((o1, o2) -> {
                Location villagerLocation = this.bukkitVillager.getLocation();
                return (int) (o1.getLocation().distanceSquared(villagerLocation) - o2.getLocation().distanceSquared(villagerLocation));
            })).collect(Collectors.toList());

            if (potentialPaths.isEmpty()) {
                this.needsToUnload = false;
                return;
            }
            Block block = potentialPaths.stream().findFirst().get();
            this.pathResult = bukkitVillager.getPathfinder().findPath(block.getLocation());
            return;
        }

        List<Location> locationList = new ArrayList<>();
        currentlyTargetedBlocks.forEach((uuid, locations) -> locationList.addAll(locations));
        this.blockList = this.blockList.stream().sorted(((o1, o2) -> {
            //Sort to get the nearest blocks first!
            Location villagerLocation = this.bukkitVillager.getLocation();
            return (int) (o1.getLocation().distanceSquared(villagerLocation) - o2.getLocation().distanceSquared(villagerLocation));
        })).filter(block -> {
            //Don't target already targeted blocks by other farmer villagers
            if (locationList.contains(block.getLocation())) return false;
            //Not harvestable
            if (!(block.getBlockData() instanceof Ageable)) return false;
            Ageable ageable = (Ageable) block.getBlockData();
            //Checks if crop is fully grown again; to make sure it hasn't been harvested already
            return ageable.getAge() == ageable.getMaximumAge();
        }).collect(Collectors.toList());

        Optional<Block> optionalTargetBlock = this.blockList.stream().findFirst();
        if (optionalTargetBlock.isPresent()) {
            Block block = optionalTargetBlock.get();
            this.getCurrentlyTargetedBlocksList().add(block.getLocation());
            this.pathResult = bukkitVillager.getPathfinder().findPath(block.getLocation());
            this.targetBlock = block;
        } else {
            this.pathResult = null;
        }
    }

    private List<Location> getCurrentlyTargetedBlocksList() {
        return currentlyTargetedBlocks.get(this.bukkitVillager.getUniqueId());
    }

    //https://www.spigotmc.org/threads/issue-with-block-getdrops.168815/
    public List<ItemStack> getDropsForWheat(boolean fullyGrown, int lootingEnchantmentLevel) {
        List<ItemStack> itemStacks = new ArrayList<>();

        if (fullyGrown) {
            itemStacks.add(new ItemStack(Material.WHEAT));
            for (int i = 0; i < 3 + lootingEnchantmentLevel; i++) {
                if (ThreadLocalRandom.current().nextInt(15) <= 7) {
                    itemStacks.add(new ItemStack(Material.WHEAT_SEEDS));
                }
            }
        } else itemStacks.add(new ItemStack(Material.WHEAT_SEEDS));
        return itemStacks;
    }
}