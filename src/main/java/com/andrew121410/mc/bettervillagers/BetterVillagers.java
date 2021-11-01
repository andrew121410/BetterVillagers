package com.andrew121410.mc.bettervillagers;

import com.andrew121410.mc.bettervillagers.listeners.OnEntityDeathEvent;
import com.andrew121410.mc.bettervillagers.listeners.OnVillagerCareerChangeEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
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
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public static List<Block> getNearbyBlocks(Location location, int radius) {
        List<Block> blocks = new ArrayList<>();
        for (int x = location.getBlockX() - radius; x <= location.getBlockX() + radius; x++) {
            for (int y = location.getBlockY() - radius; y <= location.getBlockY() + radius; y++) {
                for (int z = location.getBlockZ() - radius; z <= location.getBlockZ() + radius; z++) {
                    blocks.add(location.getWorld().getBlockAt(x, y, z));
                }
            }
        }
        return blocks;
    }

    public static List<Block> getNearbyGrownWheat(Location location, int radius) {
        return getNearbyBlocks(location, radius).stream().filter(block -> {
            if (!(block.getBlockData() instanceof Ageable)) return false;
            Ageable ageable = (Ageable) block.getBlockData();
            return block.getType() == Material.WHEAT && ageable.getAge() == ageable.getMaximumAge();
        }).collect(Collectors.toList());
    }
}
