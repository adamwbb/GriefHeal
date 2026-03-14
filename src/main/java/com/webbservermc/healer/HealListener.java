package com.webbservermc.griefheal;

import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.block.BlockState;
import java.util.List;
import java.util.stream.Collectors;

public class HealListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        GriefHeal plugin = GriefHeal.getInstance();
        if (plugin.getConfig().getStringList("world-blacklist").contains(event.getLocation().getWorld().getName())) return;

        EntityType type = event.getEntityType();
        boolean enabled = switch (type) {
            case CREEPER -> plugin.getConfig().getBoolean("explosions.creeper");
            case TNT -> plugin.getConfig().getBoolean("explosions.primed_tnt");
            case FIREBALL -> plugin.getConfig().getBoolean("explosions.fireball");
            case WITHER -> plugin.getConfig().getBoolean("explosions.wither");
            case WITHER_SKULL -> plugin.getConfig().getBoolean("explosions.wither_skull");
            case END_CRYSTAL -> plugin.getConfig().getBoolean("explosions.end_crystal");
            case TNT_MINECART -> plugin.getConfig().getBoolean("explosions.tnt_minecart");
            default -> plugin.getConfig().getBoolean("explosions.block_explosion");
        };

        if (!enabled) return;

        List<String> blacklist = plugin.getConfig().getStringList("blacklist");
        List<BlockState> states = event.blockList().stream()
                .filter(block -> !blacklist.contains(block.getType().name()))
                .map(org.bukkit.block.Block::getState)
                .collect(Collectors.toList());

        event.setYield(0);
        plugin.handleGrief(event.getLocation(), states);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (!GriefHeal.getInstance().getConfig().getBoolean("features.heal-fire-damage")) return;
        GriefHeal.getInstance().handleGrief(event.getBlock().getLocation(), List.of(event.getBlock().getState()));
    }
}