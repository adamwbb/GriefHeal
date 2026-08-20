package com.webbservermc.griefheal;

import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.*;

public class HealListener implements Listener {

    private final Set<EntityType> enabledExplosions = EnumSet.noneOf(EntityType.class);
    private final Set<String> blacklistedWorlds = new HashSet<>();
    private final Set<String> blacklistedBlocks = new HashSet<>();
    private boolean defaultBlockExplosions;
    private boolean healFireDamage;

    public void reloadSettings(FileConfiguration config) {
        enabledExplosions.clear();
        blacklistedWorlds.clear();
        blacklistedBlocks.clear();

        if (config.getBoolean("explosions.creeper", true)) enabledExplosions.add(EntityType.CREEPER);
        if (config.getBoolean("explosions.primed_tnt", true)) enabledExplosions.add(EntityType.TNT);
        if (config.getBoolean("explosions.fireball", true)) enabledExplosions.add(EntityType.FIREBALL);
        if (config.getBoolean("explosions.wither", true)) enabledExplosions.add(EntityType.WITHER);
        if (config.getBoolean("explosions.wither_skull", true)) enabledExplosions.add(EntityType.WITHER_SKULL);
        if (config.getBoolean("explosions.end_crystal", true)) enabledExplosions.add(EntityType.END_CRYSTAL);
        if (config.getBoolean("explosions.tnt_minecart", true)) enabledExplosions.add(EntityType.TNT_MINECART);

        defaultBlockExplosions = config.getBoolean("explosions.block_explosion", true);
        healFireDamage = config.getBoolean("features.heal-fire-damage", true);

        for (String w : config.getStringList("world-blacklist")) {
            blacklistedWorlds.add(w.toLowerCase(Locale.ROOT));
        }
        for (String b : config.getStringList("blacklist")) {
            blacklistedBlocks.add(b.toUpperCase(Locale.ROOT));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (blacklistedWorlds.contains(event.getLocation().getWorld().getName().toLowerCase(Locale.ROOT))) {
            return;
        }

        EntityType type = event.getEntityType();
        boolean enabled = (type != null) ? enabledExplosions.contains(type) : defaultBlockExplosions;
        if (!enabled) return;

        List<Block> targetBlocks = new ArrayList<>();
        for (Block block : event.blockList()) {
            if (!blacklistedBlocks.contains(block.getType().name())) {
                targetBlocks.add(block);
            }
        }

        event.setYield(0.0f);
        GriefHeal.getInstance().handleGrief(event.getLocation(), targetBlocks);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!defaultBlockExplosions) return;
        if (blacklistedWorlds.contains(event.getBlock().getWorld().getName().toLowerCase(Locale.ROOT))) {
            return;
        }

        List<Block> targetBlocks = new ArrayList<>();
        for (Block block : event.blockList()) {
            if (!blacklistedBlocks.contains(block.getType().name())) {
                targetBlocks.add(block);
            }
        }

        event.setYield(0.0f);
        GriefHeal.getInstance().handleGrief(event.getBlock().getLocation(), targetBlocks);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (!healFireDamage) return;
        if (blacklistedWorlds.contains(event.getBlock().getWorld().getName().toLowerCase(Locale.ROOT))) {
            return;
        }

        GriefHeal.getInstance().handleGrief(event.getBlock().getLocation(), Collections.singletonList(event.getBlock()));
    }
}
