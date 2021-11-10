package com.andrew121410.mc.bettervillagers.goals.farmer;

import com.andrew121410.mc.bettervillagers.BetterVillagers;
import com.andrew121410.mc.world16utils.blocks.UniversalBlockUtils;
import com.destroystokyo.paper.entity.Pathfinder;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.WorkAtComposter;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.*;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Directional;
import org.bukkit.craftbukkit.v1_17_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_17_R1.entity.CraftVillager;
import org.bukkit.entity.Villager;
import org.bukkit.entity.memory.MemoryKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class BetterFarmingGoal implements Goal<Villager> {

    //Shared with all farmer villagers that have this goal, so they don't target the same targets as each other
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

    private List<ToFarmBlock> toFarmBlocks;

    private Block targetBlock;
    private ToFarmBlock targetToFarmBlock;

    private Pathfinder.PathResult pathResult;

    private boolean needsToUnload;

    private Block chestBlock;
    private Block composterBlock;

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
        this.toFarmBlocks = getHarvestableToFarmBlocks(bukkitVillager.getLocation(), 20);
        if (toFarmBlocks.isEmpty()) {
            this.coolDownTicks = this.coolDownTimeTicks;
            return false;
        } else return true;
    }

    @Override
    public boolean shouldStayActive() {
        if (this.ticks > this.maximumTicks) return false;
        return !this.toFarmBlocks.isEmpty() || this.needsToUnload;
    }

    @Override
    public void start() {
        List<Block> potentialChests = UniversalBlockUtils.getNearbyBlocks(this.bukkitVillager.getLocation(), 50).stream().filter(block -> block.getType() == Material.CHEST).collect(Collectors.toList());

        potentialChests = potentialChests.stream().filter(block -> {
            if (block.getState() instanceof Chest) {
                Directional directional = (Directional) block.getBlockData();
                Block inFront = block.getRelative(directional.getFacing());
                if (inFront.getState() instanceof Sign sign) {
                    return sign.getLine(0).equalsIgnoreCase("BetterFarmer");
                }
            }
            return false;
        }).collect(Collectors.toList());

        this.chestBlock = null;
        this.composterBlock = null;
        this.needsToUnload = false;

        if (!potentialChests.isEmpty()) {
            this.chestBlock = potentialChests.stream().findFirst().get();
            this.needsToUnload = true;
        } else {
            Location jobSiteMemory = this.bukkitVillager.getMemory(MemoryKey.JOB_SITE);
            if (jobSiteMemory != null) {
                this.composterBlock = jobSiteMemory.getBlock();
                this.needsToUnload = true;
            }
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
            this.toFarmBlocks.clear();
            this.needsToUnload = false;
            return;
        }
        ticks++;

        if (this.pathResult.getNextPoint() != null) {
            //Both setLookAt and moveTo methods should actually only be called once
            //But since we still have the vanilla goals we have to call this every tick or else it would stop then do something else abruptly
            Location toLookAtLocation = this.targetBlock.getLocation();
            if (this.bukkitVillager.getLocation().distanceSquared(toLookAtLocation) < 36) { //36 is 6 blocks
                this.minecraftVillager.getLookControl().setLookAt(new Vec3(toLookAtLocation.getBlockX(), toLookAtLocation.getBlockY(), toLookAtLocation.getBlockZ()));
            }
            this.bukkitVillager.getPathfinder().moveTo(this.pathResult, 0.8F);
        } else {
            if (this.toFarmBlocks.isEmpty() && this.needsToUnload) {
                if (this.chestBlock != null && !this.runChestUnloadOnce) {
                    this.runChestUnloadOnce = true;
                    Inventory villagerInventory = this.bukkitVillager.getInventory();

                    BlockState blockState = this.chestBlock.getState();
                    if (blockState instanceof Chest chestState) {
                        List<ItemStack> toBeRemoved = new ArrayList<>();
                        for (ItemStack itemStack : villagerInventory) {
                            if (itemStack == null) continue;
                            chestState.getInventory().addItem(itemStack);
                            toBeRemoved.add(itemStack);
                        }
                        for (ItemStack itemStack : toBeRemoved)
                            if (itemStack != null) villagerInventory.remove(itemStack);
                    }
                } else if (this.composterBlock != null) {
                    WorkAtComposter workAtComposter = new WorkAtComposter();
                    Method method;
                    try {
                        method = WorkAtComposter.class.getDeclaredMethod("doWork", ServerLevel.class, net.minecraft.world.entity.npc.Villager.class);
                        method.setAccessible(true);
                        method.invoke(workAtComposter, this.serverLevel, this.minecraftVillager);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                this.needsToUnload = false;
                return;
            }
            List<ToFarmBlock> radiusToFarmBlocks = this.toFarmBlocks.stream()
                    .filter(toFarmBlock -> this.bukkitVillager.getLocation().distanceSquared(toFarmBlock.getBlock().getLocation()) <= 20 && toFarmBlock.getFarmingType() == this.targetToFarmBlock.getFarmingType())
                    .collect(Collectors.toList());

            if (!radiusToFarmBlocks.isEmpty()) {
                for (ToFarmBlock toFarmBlock : radiusToFarmBlocks) {
                    Block cropBlock = toFarmBlock.getBlock();
                    Material material = cropBlock.getType();
                    List<ItemStack> dropsList = toFarmBlock.getBlockDrops(); //We have to get the drops before block is destroyed

                    boolean needsToSetType = false;
                    if (this.targetToFarmBlock.getFarmingType() == FarmingType.DEFAULT || this.targetToFarmBlock.getFarmingType() == FarmingType.PUMPKINS_AND_MELONS) {
                        this.serverLevel.destroyBlock(new BlockPos(cropBlock.getX(), cropBlock.getY(), cropBlock.getZ()), false, minecraftVillager);
                        if (!ToFarmBlock.isPumpkinOrMelon(material))
                            needsToSetType = true;
                    } else if (this.targetToFarmBlock.getFarmingType() == FarmingType.SWEET_BERRIES) {
                        if (cropBlock.getBlockData() instanceof Ageable ageable) {
                            ageable.setAge(1);
                            cropBlock.setBlockData(ageable);
                        }
                    } else if (this.targetToFarmBlock.getFarmingType() == FarmingType.SUGAR_CANE_AND_BAMBOO) {
                        if (material == Material.SUGAR_CANE) {
                            Block up = cropBlock.getRelative(BlockFace.UP);
                            if (up.getType() == Material.SUGAR_CANE) {
                                up.setType(Material.AIR);
                            }
                            cropBlock.setType(Material.AIR);
                        } else {
                            List<Block> bambooBlocks = new ArrayList<>();
                            for (int i = cropBlock.getY(); i <= cropBlock.getWorld().getMaxHeight(); i++) {
                                Block toCheck = cropBlock.getWorld().getBlockAt(new Location(cropBlock.getWorld(), cropBlock.getX(), i, cropBlock.getZ()));
                                if (toCheck.getType() == Material.BAMBOO) {
                                    bambooBlocks.add(toCheck);
                                } else break;
                            }
                            bambooBlocks.sort((o1, o2) -> o2.getY() - o1.getY());
                            for (Block bambooBlock : bambooBlocks) {
                                bambooBlock.setType(Material.AIR);
                            }
                        }
                    }

                    if (dropsList != null) {
                        for (ItemStack itemStack : dropsList) {
                            this.bukkitVillager.getInventory().addItem(itemStack);
                        }
                    }

                    if (needsToSetType) {
                        cropBlock.setType(material, false);
                    }
                }
                this.toFarmBlocks.removeAll(radiusToFarmBlocks);
            }
            this.getCurrentlyTargetedBlocksList().remove(this.targetToFarmBlock.getBlock().getLocation());
            this.toFarmBlocks.remove(this.targetToFarmBlock);
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
        //If done harvesting set the path to the chest or composter
        if (this.toFarmBlocks.isEmpty()) {
            List<Block> potentialPaths = null;

            if (this.chestBlock != null) {
                potentialPaths = UniversalBlockUtils.getNearbyBlocks(this.chestBlock.getLocation(), 1, false).stream().filter(block -> !block.isSolid() || Tag.SIGNS.isTagged(block.getType())).sorted(((o1, o2) -> {
                    Location villagerLocation = this.bukkitVillager.getLocation();
                    return (int) (o1.getLocation().distanceSquared(villagerLocation) - o2.getLocation().distanceSquared(villagerLocation));
                })).collect(Collectors.toList());
            } else if (this.composterBlock != null) {
                potentialPaths = UniversalBlockUtils.getNearbyBlocks(this.composterBlock.getLocation(), 1, false).stream().filter(block -> !block.isSolid()).sorted(((o1, o2) -> {
                    Location villagerLocation = this.bukkitVillager.getLocation();
                    return (int) (o1.getLocation().distanceSquared(villagerLocation) - o2.getLocation().distanceSquared(villagerLocation));
                })).collect(Collectors.toList());
            }

            //This shouldn't be ran at all ever. But just in case...
            if (potentialPaths == null) {
                this.needsToUnload = false;
                return;
            }

            if (potentialPaths.isEmpty()) {
                this.needsToUnload = false;
                return;
            }
            Block block = potentialPaths.stream().findFirst().get();
            this.pathResult = bukkitVillager.getPathfinder().findPath(block.getLocation());
            this.targetBlock = this.composterBlock != null ? this.composterBlock : this.chestBlock != null ? this.chestBlock : null;
            return;
        }

        List<Location> locationList = new ArrayList<>();
        currentlyTargetedBlocks.forEach((uuid, locations) -> locationList.addAll(locations));
        this.toFarmBlocks = this.toFarmBlocks.stream().sorted(((o1, o2) -> {
            Location villagerLocation = this.bukkitVillager.getLocation();
            Location one = o1.getBlock().getLocation();
            Location two = o2.getBlock().getLocation();

            //Sort to get the nearest blocks first!
            return (int) (one.distanceSquared(villagerLocation) - two.distanceSquared(villagerLocation));
        })).filter(toFarmBlock -> {
            //Don't target already targeted blocks by other farmer villagers
            if (locationList.contains(toFarmBlock.getBlock().getLocation())) return false;
            //Checks if crop is fully grown again; to make sure it hasn't been harvested already
            return toFarmBlock.isGrown();
        }).collect(Collectors.toList());

        Optional<ToFarmBlock> optionalTargetToFarmBlock = this.toFarmBlocks.stream().findFirst();
        if (optionalTargetToFarmBlock.isPresent()) {
            ToFarmBlock toFarmBlock = optionalTargetToFarmBlock.get();
            this.getCurrentlyTargetedBlocksList().add(toFarmBlock.getBlock().getLocation());

            List<Block> potentialPaths = UniversalBlockUtils.getNearbyBlocks(toFarmBlock.getBlock().getLocation(), 1, false).stream().filter(block -> !block.isSolid()).sorted(((o1, o2) -> {
                Location villagerLocation = this.bukkitVillager.getLocation();
                return (int) (o1.getLocation().distanceSquared(villagerLocation) - o2.getLocation().distanceSquared(villagerLocation));
            })).collect(Collectors.toList());

            Block toGoBlock = toFarmBlock.getBlock();
            if (!potentialPaths.isEmpty()) {
                toGoBlock = potentialPaths.stream().findFirst().get();
            }
            this.pathResult = bukkitVillager.getPathfinder().findPath(toGoBlock.getLocation());
            this.targetBlock = toFarmBlock.getBlock();
            this.targetToFarmBlock = toFarmBlock;
        } else {
            this.pathResult = null;
        }
    }

    private List<Location> getCurrentlyTargetedBlocksList() {
        return currentlyTargetedBlocks.get(this.bukkitVillager.getUniqueId());
    }

    public List<ToFarmBlock> getHarvestableToFarmBlocks(Location location, int radius) {
        return UniversalBlockUtils.getNearbyBlocks(location, radius).stream()
                .filter(block -> FarmingType.getFarmingTypeByMaterial(block.getType()) != null)
                .map(ToFarmBlock::new)
                .filter(ToFarmBlock::isGrown)
                .sorted()
                .collect(Collectors.toList());
    }
}

enum FarmingType {
    DEFAULT(1), //Wheat, Carrot, Potato, Beetroot
    SWEET_BERRIES(2),
    PUMPKINS_AND_MELONS(3),
    SUGAR_CANE_AND_BAMBOO(4);

    //Weight is used to determine what should be harvested first in ascending order
    private int weight;

    FarmingType(int weight) {
        this.weight = weight;
    }

    public int getWeight() {
        return weight;
    }

    public static FarmingType getFarmingTypeByMaterial(Material material) {
        List<Material> defaultList = Arrays.asList(
                Material.WHEAT,
                Material.CARROTS,
                Material.POTATOES,
                Material.BEETROOTS
        );

        if (defaultList.contains(material)) return DEFAULT;
        if (material == Material.SWEET_BERRY_BUSH) return SWEET_BERRIES;
        if (material == Material.PUMPKIN || material == Material.MELON) return PUMPKINS_AND_MELONS;
        if (material == Material.BAMBOO || material == Material.SUGAR_CANE) return SUGAR_CANE_AND_BAMBOO;
        return null;
    }
}

class ToFarmBlock implements Comparable<ToFarmBlock> {
    private Block block;
    private FarmingType farmingType;

    public ToFarmBlock(Block block, FarmingType farmingType) {
        this.block = block;

        if (farmingType == null) {
            throw new NullPointerException("FarmingType can't be null");
        }
        this.farmingType = farmingType;
    }

    public ToFarmBlock(Block block) {
        this(block, FarmingType.getFarmingTypeByMaterial(block.getType()));
    }

    public boolean isGrown() {
        if (farmingType == FarmingType.DEFAULT || farmingType == FarmingType.SWEET_BERRIES) {
            if (!(block.getBlockData() instanceof Ageable ageable)) return false;
            return ageable.getAge() == ageable.getMaximumAge();
        } else if (farmingType == FarmingType.SUGAR_CANE_AND_BAMBOO) {
            if (isSugarcaneOrBamboo(this.getBlock().getType())) {
                if (isSugarcaneOrBamboo(this.block.getRelative(0, -1, 0).getType())) {
                    return !isSugarcaneOrBamboo(this.block.getRelative(0, -2, 0).getType());
                }
            }
            return false;
        } else if (farmingType == FarmingType.PUMPKINS_AND_MELONS) {
            List<Block> blockList = UniversalBlockUtils.getNearbyBlocks(this.block.getLocation(), 1, false).stream().filter(possibleStem -> {
                if (this.block.getType() == Material.PUMPKIN) {
                    return possibleStem.getType() == Material.ATTACHED_PUMPKIN_STEM;
                } else {
                    return possibleStem.getType() == Material.ATTACHED_MELON_STEM;
                }
            }).collect(Collectors.toList());
            return !blockList.isEmpty();
        }
        return false;
    }

    public List<ItemStack> getBlockDrops() {
        switch (this.block.getType()) {
            case WHEAT:
                //https://www.spigotmc.org/threads/issue-with-block-getdrops.168815/
                List<ItemStack> itemStacks = new ArrayList<>();

                itemStacks.add(new ItemStack(Material.WHEAT));
                for (int i = 0; i < 3 + 1; i++) {
                    if (ThreadLocalRandom.current().nextInt(15) <= 7) {
                        itemStacks.add(new ItemStack(Material.WHEAT_SEEDS));
                    }
                }
                return itemStacks;
            case CARROTS:
                return Collections.singletonList(new ItemStack(Material.CARROT, 3));
            case POTATOES:
                return Collections.singletonList(new ItemStack(Material.POTATO, 3));
            case BEETROOTS:
                return Arrays.asList(new ItemStack(Material.BEETROOT, 1), new ItemStack(Material.BEETROOT_SEEDS, 2));
            case SWEET_BERRY_BUSH:
                return Collections.singletonList(new ItemStack(Material.SWEET_BERRIES, 2));
            case MELON:
                return Collections.singletonList(new ItemStack(Material.MELON_SLICE, 4));
            case PUMPKIN:
                return Collections.singletonList(new ItemStack(Material.PUMPKIN, 1));
            case SUGAR_CANE:
                return Collections.singletonList(new ItemStack(Material.SUGAR_CANE, this.block.getRelative(BlockFace.UP).getType() == Material.SUGAR_CANE ? 2 : 1));
            case BAMBOO:
                int count = 0;
                for (int i = this.block.getLocation().getBlockY(); i <= this.block.getWorld().getMaxHeight(); i++) {
                    Block toCheck = this.block.getWorld().getBlockAt(new Location(this.block.getWorld(), this.block.getX(), i, this.block.getZ()));
                    if (toCheck.getType() == Material.BAMBOO) {
                        count++;
                    } else break;
                }
                return Collections.singletonList(new ItemStack(Material.BAMBOO, count));
        }
        return null;
    }

    public static boolean isSugarcaneOrBamboo(Material type) {
        return type == Material.SUGAR_CANE || type == Material.BAMBOO;
    }

    public static boolean isPumpkinOrMelon(Material type) {
        return type == Material.PUMPKIN || type == Material.MELON;
    }

    public Block getBlock() {
        return block;
    }

    public FarmingType getFarmingType() {
        return farmingType;
    }

    @Override
    public int compareTo(@NotNull ToFarmBlock o) {
        return this.farmingType.getWeight() - o.farmingType.getWeight();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ToFarmBlock toFarmBlock)) return false;
        return this.farmingType == toFarmBlock.farmingType && this.block.equals(toFarmBlock.getBlock());
    }

    @Override
    public int hashCode() {
        return Objects.hash(block, farmingType);
    }
}