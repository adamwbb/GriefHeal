package com.adamwbb.healer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class GriefHeal extends JavaPlugin implements Listener, CommandExecutor {

    private final List<BlockDataSnapshot> forceHealQueue = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("gh")).setExecutor(this);
        getLogger().info("GriefHeal by adamwbb enabled.");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("griefheal.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }

        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                sender.sendMessage("§aGriefHeal config reloaded!");
                return true;
            } else if (args[0].equalsIgnoreCase("healnow")) {
                int count = forceHealQueue.size();
                // Process all pending blocks immediately
                synchronized (forceHealQueue) {
                    for (BlockDataSnapshot snapshot : forceHealQueue) {
                        restoreBlock(snapshot);
                    }
                    forceHealQueue.clear();
                }
                sender.sendMessage("§aForce-healed " + count + " blocks immediately!");
                return true;
            }
        }
        sender.sendMessage("§eUsage: /gh <reload|healnow>");
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (!getConfig().getBoolean("features.heal-fire-damage", true)) return;
        if (isWorldBlacklisted(event.getBlock().getWorld())) return;

        processSingleBlockHeal(event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (isWorldBlacklisted(event.getLocation().getWorld())) return;

        EntityType source = event.getEntityType();
        String sourceName = (source == null) ? "block_explosion" : source.name().toLowerCase();
        if (!getConfig().getBoolean("explosions." + sourceName, true)) return;

        long delaySeconds = getConfig().getLong("restoration-delay", 360);
        List<String> blacklist = getConfig().getStringList("blacklist");
        int blocksPerTick = getConfig().getInt("blocks-per-tick", 5);
        boolean restoreChestItems = getConfig().getBoolean("features.restore-chest-items", true);

        event.setYield(0.0F);

        List<BlockDataSnapshot> batch = new ArrayList<>();

        for (Block block : event.blockList()) {
            if (blacklist.contains(block.getType().name())) continue;
            if (block.getType().isAir() || block.isLiquid()) continue;

            ItemStack[] items = null;
            if (restoreChestItems && block.getState() instanceof Container container) {
                items = container.getInventory().getContents().clone();
                container.getInventory().clear(); 
            }

            BlockDataSnapshot snapshot = new BlockDataSnapshot(
                block.getLocation().clone(), 
                block.getType(), 
                block.getBlockData().clone(), 
                items
            );
            batch.add(snapshot);
            forceHealQueue.add(snapshot);
        }

        scheduleHealTask(batch, delaySeconds, blocksPerTick);
    }

    private void processSingleBlockHeal(Block block) {
        long delaySeconds = getConfig().getLong("restoration-delay", 360);
        BlockDataSnapshot snapshot = new BlockDataSnapshot(
            block.getLocation().clone(), 
            block.getType(), 
            block.getBlockData().clone(), 
            null
        );
        
        forceHealQueue.add(snapshot);
        
        new BukkitRunnable() {
            @Override
            public void run() {
                if (forceHealQueue.contains(snapshot)) {
                    restoreBlock(snapshot);
                    forceHealQueue.remove(snapshot);
                }
            }
        }.runTaskLater(this, delaySeconds * 20L);
    }

    private void scheduleHealTask(List<BlockDataSnapshot> batch, long delay, int speed) {
        new BukkitRunnable() {
            @Override
            public void run() {
                new BukkitRunnable() {
                    int index = 0;
                    @Override
                    public void run() {
                        for (int i = 0; i < speed; i++) {
                            if (index >= batch.size()) {
                                this.cancel();
                                return;
                            }
                            BlockDataSnapshot snapshot = batch.get(index);
                            if (forceHealQueue.contains(snapshot)) {
                                restoreBlock(snapshot);
                                forceHealQueue.remove(snapshot);
                            }
                            index++;
                        }
                    }
                }.runTaskTimer(GriefHeal.this, 0L, 1L);
            }
        }.runTaskLater(this, delay * 20L);
    }

    private void restoreBlock(BlockDataSnapshot snapshot) {
        Block b = snapshot.loc.getBlock();
        if (b.getType() == Material.AIR || b.getType() == Material.FIRE || b.isLiquid()) {
            b.setType(snapshot.mat, false);
            b.setBlockData(snapshot.data, false);
            if (snapshot.items != null && b.getState() instanceof Container container) {
                container.getInventory().setContents(snapshot.items);
            }
            b.getState().update(true, false);
        }
    }

    private boolean isWorldBlacklisted(World world) {
        return getConfig().getStringList("world-blacklist").contains(world.getName());
    }

    private record BlockDataSnapshot(org.bukkit.Location loc, Material mat, BlockData data, ItemStack[] items) {}
}
